# -*- coding: utf-8 -*-
"""
中华医学会 · 科普图文合规采集脚本

采集源：https://www.cma.org.cn/col/col4584/index.html
数据类型：医学科普文章（疾病知识、健康指导）

原则：
- 仅采集公开发布的科普文章列表页 + 摘要
- 遵守限速、User-Agent 标识；存摘要 + 原文链接，不全文转载
- 注明来源；以官网为准
- 写入 cms_content 表，category_code = KNOWLEDGE
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin

import pymysql
import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "scripts" / "crawl-sources.json"
CMA_OUT = ROOT / "data" / "crawl" / "cma"
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

CMA_BASE = "https://www.cma.org.cn"
CMA_LIST_URL = "https://www.cma.org.cn/col/col4584/index.html"


def load_config() -> dict:
    with open(CONFIG, encoding="utf-8") as f:
        return json.load(f)


def load_db_config() -> dict:
    cfg = {
        "host": os.getenv("HEALTH_DB_HOST", "localhost"),
        "user": os.getenv("HEALTH_DB_USER", "root"),
        "password": os.getenv("HEALTH_DB_PASSWORD", ""),
        "database": os.getenv("HEALTH_DB_NAME", "health_portal"),
    }
    if JDBC.exists():
        for line in JDBC.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("mysql.password="):
                cfg["password"] = line.split("=", 1)[1]
            elif line.startswith("mysql.username="):
                cfg["user"] = line.split("=", 1)[1]
            elif line.startswith("mysql.url=") and "3306" in line:
                m = re.search(r"//([^:/]+)", line)
                if m:
                    cfg["host"] = m.group(1)
                db = re.search(r"/([^?]+)", line.split("3306", 1)[-1])
                if db:
                    cfg["database"] = db.group(1)
    return cfg


def polite_get(url: str, headers: dict, delay: float) -> requests.Response | None:
    """带限速的HTTP GET请求"""
    time.sleep(delay)
    try:
        r = requests.get(url, headers=headers, timeout=30)
        r.encoding = r.apparent_encoding or "utf-8"
        if r.status_code == 200:
            return r
        print(f"  HTTP {r.status_code}: {url}")
        return None
    except Exception as e:
        print(f"  REQUEST ERROR: {url} -> {e}")
        return None


def crawl_list_page(list_url: str, headers: dict, delay: float) -> list[dict]:
    """从列表页提取科普文章链接"""
    resp = polite_get(list_url, headers, delay)
    if not resp:
        return []
    
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()
    
    # 查找所有文章链接
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        
        # 过滤条件：必须是科普文章标题
        if not title or len(title) < 6 or len(title) > 200:
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        
        full_url = urljoin(list_url, href)
        if full_url in seen:
            continue
        if full_url.rstrip("/") == list_url.rstrip("/"):
            continue
        
        # 只保留科普相关的链接（根据URL或标题判断）
        is_kepu = (
            "/col/" in href or 
            "/art/" in href or
            any(kw in title for kw in ["科普", "知识", "预防", "治疗", "保健", "指南", "常识"])
        )
        if not is_kepu:
            continue
        
        seen.add(full_url)
        
        # 尝试提取日期
        date_text = ""
        parent = a.parent
        if parent:
            parent_text = parent.get_text(" ", strip=True)
            date_match = re.search(r'(\d{4}-\d{2}-\d{2})', parent_text)
            if date_match:
                date_text = date_match.group(1)
        
        items.append({
            "title": title,
            "url": full_url,
            "pubDate": date_text,
        })
    
    return items


def crawl_article_detail(url: str, headers: dict, delay: float) -> str:
    """访问文章详情页，提取摘要"""
    resp = polite_get(url, headers, delay)
    if not resp:
        return ""
    
    soup = BeautifulSoup(resp.text, "html.parser")
    
    # 常见的正文容器选择器
    selectors = [
        ".pages_content", ".article_content", "#UCAP-CONTENT",
        ".TRS_Editor", ".content", ".article", "div.article-content",
        ".detail-content", ".main-content",
    ]
    
    for sel in selectors:
        el = soup.select_one(sel)
        if not el:
            continue
        
        paras = [p.get_text(" ", strip=True) for p in el.select("p") if len(p.get_text(strip=True)) > 15]
        if paras:
            text = "\n".join(paras[:5])  # 取前5段
            return text[:400].replace("\n", " ").strip()
        
        text = el.get_text(" ", strip=True)
        if len(text) > 30:
            return text[:400].strip()
    
    # 兜底：从 body 中提取
    body = soup.select_one("body")
    if body:
        text = body.get_text(" ", strip=True)
        if len(text) > 50:
            clean = re.sub(r'\s+', ' ', text)
            return clean[:400].strip()
    
    return ""


def build_content(title: str, url: str, summary: str, attribution: str) -> str:
    """构建CMS内容HTML"""
    summary = summary or "（请点击原文链接查阅完整内容）"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本文为中华医学会官方科普内容索引，摘要仅供参考，以原文为准。</em></p>"
        f'<p>原文链接：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>'
        f"<p>{attribution}</p>"
    )


def import_to_cms(items: list[dict], db_cfg: dict) -> dict:
    """将采集结果写入 cms_content 表"""
    conn = pymysql.connect(
        host=db_cfg["host"], user=db_cfg["user"], password=db_cfg["password"],
        database=db_cfg["database"], charset="utf8mb4",
    )
    cur = conn.cursor()
    inserted, updated, skipped = 0, 0, 0
    
    attribution = "来源：中华医学会科普图文"
    
    for item in items:
        url = item.get("url", "")
        title = item.get("title", "").strip()
        if not title or not url:
            skipped += 1
            continue
        
        cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (url,))
        row = cur.fetchone()
        
        summary = item.get("summary", "") or title[:120]
        content = build_content(title, url, summary, attribution)
        author = "合规采集·中华医学会"
        
        if row:
            cur.execute(
                "UPDATE cms_content SET title=%s, summary=%s, content=%s, updated_at=NOW() WHERE id=%s",
                (title, summary[:200], content, row[0]),
            )
            updated += 1
        else:
            cur.execute(
                "INSERT INTO cms_content "
                "(category_code, title, summary, content, source_url, author, view_count, status, publish_time) "
                "VALUES (%s,%s,%s,%s,%s,%s,0,1,NOW())",
                ("KNOWLEDGE", title, summary[:200], content, url, author),
            )
            inserted += 1
    
    conn.commit()
    cur.close()
    conn.close()
    return {"inserted": inserted, "updated": updated, "skipped": skipped}


def main() -> int:
    cfg = load_config()
    delay = float(cfg.get("rateLimitSeconds", 3))
    headers = {
        "User-Agent": "HealthPortalBot/1.0 (CSU training; open data; +https://www.gov.cn)",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    
    CMA_OUT.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("中华医学会 · 科普图文采集")
    print(f"采集时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"限速: {delay}s / 请求")
    print("=" * 70)
    
    # 采集列表页
    print(f"\n📋 采集列表页: {CMA_LIST_URL}")
    list_items = crawl_list_page(CMA_LIST_URL, headers, delay)
    print(f"   找到 {len(list_items)} 条科普文章")
    
    if not list_items:
        print("❌ 未找到任何科普文章，请检查网站结构是否变化")
        return 1
    
    # 采集每篇文章详情
    results = []
    max_articles = min(len(list_items), 20)  # 最多采集20篇
    
    print(f"\n📄 开始采集文章详情（最多 {max_articles} 篇）...")
    for idx, item in enumerate(list_items[:max_articles]):
        print(f"  [{idx+1}/{max_articles}] {item['title'][:50]}...")
        summary = crawl_article_detail(item["url"], headers, delay)
        
        if summary:
            item["summary"] = summary
            results.append(item)
            print(f"      ✓ 提取到摘要 ({len(summary)} 字)")
        else:
            print(f"      ✗ 未能提取摘要")
    
    # 保存采集结果
    output_data = {
        "sourceId": "cma_kepu",
        "sourceName": "中华医学会·科普图文",
        "categoryCode": "KNOWLEDGE",
        "status": "success" if results else "partial",
        "recordCount": len(results),
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "items": results,
    }
    
    out_file = CMA_OUT / "cma_kepu.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    # 镜像到后端 resources
    backend_file = (
        ROOT / "health-portal-backend" / "src" / "main" / "resources"
        / "data" / "crawl" / "cma" / "cma_kepu.json"
    )
    backend_file.parent.mkdir(parents=True, exist_ok=True)
    with open(backend_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    print(f"\n{'=' * 70}")
    print(f"✅ 采集完成: 共 {len(results)} 条科普文章")
    print(f"💾 已保存至: {out_file}")
    print(f"{'=' * 70}")
    
    # 写入CMS数据库
    if "--import" in sys.argv or "--cms" in sys.argv:
        print("\n📝 正在写入 CMS 数据库...")
        db_cfg = load_db_config()
        stats = import_to_cms(results, db_cfg)
        print(f"   CMS 写入: inserted={stats['inserted']} updated={stats['updated']} skipped={stats['skipped']}")
        
        log = {
            "finishedAt": datetime.now(timezone.utc).isoformat(),
            "recordCount": len(results),
            "cmsImport": stats,
        }
        with open(CMA_OUT / "last-import.json", "w", encoding="utf-8") as f:
            json.dump(log, f, ensure_ascii=False, indent=2)
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
