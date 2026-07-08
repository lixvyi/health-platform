# -*- coding: utf-8 -*-
"""
国家卫健委 · 视频专区 & 科普文章合规采集脚本

采集源：
- 视频专区: http://www.nhc.gov.cn/wjw/spxw/list.shtml
- 专题专栏: http://www.nhc.gov.cn/wjw/zhuant/ztzl.shtml

数据类型：健康科普视频索引、专题文章

原则：
- 仅采集公开发布的视频列表页 + 简介
- 遵守限速、User-Agent 标识；存简介 + 原文链接，不下载视频文件
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
NHC_OUT = ROOT / "data" / "crawl" / "nhc"
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

NHC_BASE = "http://www.nhc.gov.cn"
VIDEO_LIST_URL = "http://www.nhc.gov.cn/wjw/spxw/list.shtml"
TOPIC_LIST_URL = "http://www.nhc.gov.cn/wjw/zhuant/ztzl.shtml"


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


def crawl_video_list(list_url: str, headers: dict, delay: float, max_pages: int = 3) -> list[dict]:
    """从视频列表页提取视频标题、链接、日期"""
    all_items = []
    seen = set()
    
    for page in range(max_pages):
        if page == 0:
            url = list_url
        else:
            # 假设分页格式为 list_1.shtml, list_2.shtml
            url = list_url.replace("list.shtml", f"list_{page}.shtml")
        
        print(f"  📄 采集第 {page + 1} 页: {url}")
        resp = polite_get(url, headers, delay)
        if not resp:
            break
        
        soup = BeautifulSoup(resp.text, "html.parser")
        page_items = []
        
        # 查找视频链接（通常在列表项中）
        for a in soup.select("a[href]"):
            title = a.get_text(strip=True)
            href = a.get("href", "")
            
            # 过滤条件：必须是视频相关链接
            if not title or len(title) < 6 or len(title) > 200:
                continue
            if href.startswith("javascript") or href.startswith("#"):
                continue
            
            full_url = urljoin(url, href)
            if full_url in seen:
                continue
            
            # 只保留视频相关的链接
            is_video = (
                "/spxw/" in href or
                "/video/" in href or
                any(kw in title for kw in ["访谈", "视频", "在线", "解读", "宣传"])
            )
            if not is_video:
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
            
            page_items.append({
                "title": title,
                "url": full_url,
                "pubDate": date_text,
                "type": "video",
            })
        
        all_items.extend(page_items)
        print(f"     ✓ 找到 {len(page_items)} 条视频")
        
        # 如果本页没有内容，停止翻页
        if not page_items:
            break
    
    return all_items


def crawl_topic_list(list_url: str, headers: dict, delay: float) -> list[dict]:
    """从专题列表页提取专题文章"""
    resp = polite_get(list_url, headers, delay)
    if not resp:
        return []
    
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()
    
    # 查找专题链接
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        
        if not title or len(title) < 6 or len(title) > 200:
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        
        full_url = urljoin(list_url, href)
        if full_url in seen:
            continue
        
        # 只保留专题相关的链接
        is_topic = (
            "/zhuant/" in href or
            "/ztzl/" in href or
            any(kw in title for kw in ["行动", "专题", "科普", "健康"])
        )
        if not is_topic:
            continue
        
        seen.add(full_url)
        
        items.append({
            "title": title,
            "url": full_url,
            "type": "topic",
        })
    
    return items


def build_video_content(title: str, url: str, summary: str, attribution: str) -> str:
    """构建视频内容的HTML"""
    summary = summary or "（请点击原文链接观看完整视频）"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本视频为国家卫健委官方发布的健康科普内容，请前往原网站观看。</em></p>"
        f'<p>📺 观看视频：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>'
        f"<p>{attribution}</p>"
    )


def build_topic_content(title: str, url: str, summary: str, attribution: str) -> str:
    """构建专题内容的HTML"""
    summary = summary or "（请点击原文链接查阅完整内容）"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本专题为国家卫健委官方发布的健康知识专题，摘要仅供参考，以原文为准。</em></p>"
        f'<p>📖 查看专题：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>'
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
    
    attribution = "来源：国家卫生健康委员会"
    
    for item in items:
        url = item.get("url", "")
        title = item.get("title", "").strip()
        item_type = item.get("type", "video")
        
        if not title or not url:
            skipped += 1
            continue
        
        cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (url,))
        row = cur.fetchone()
        
        summary = item.get("summary", "") or title[:120]
        
        if item_type == "video":
            content = build_video_content(title, url, summary, attribution)
            author = "合规采集·国家卫健委视频专区"
        else:
            content = build_topic_content(title, url, summary, attribution)
            author = "合规采集·国家卫健委专题专栏"
        
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
    
    NHC_OUT.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("国家卫健委 · 视频专区 & 专题专栏采集")
    print(f"采集时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"限速: {delay}s / 请求")
    print("=" * 70)
    
    results = []
    
    # 1. 采集视频专区
    print("\n🎥 [视频专区]")
    print(f"   URL: {VIDEO_LIST_URL}")
    video_items = crawl_video_list(VIDEO_LIST_URL, headers, delay, max_pages=3)
    print(f"   ✓ 共采集 {len(video_items)} 条视频")
    
    if video_items:
        video_result = {
            "sourceId": "nhc_video",
            "sourceName": "国家卫健委·视频专区",
            "categoryCode": "KNOWLEDGE",
            "status": "success",
            "recordCount": len(video_items),
            "collectedAt": datetime.now(timezone.utc).isoformat(),
            "items": video_items[:20],  # 最多保存20条
        }
        results.append(video_result)
    
    # 2. 采集专题专栏
    print("\n📚 [专题专栏]")
    print(f"   URL: {TOPIC_LIST_URL}")
    topic_items = crawl_topic_list(TOPIC_LIST_URL, headers, delay)
    print(f"   ✓ 共采集 {len(topic_items)} 个专题")
    
    if topic_items:
        topic_result = {
            "sourceId": "nhc_topic",
            "sourceName": "国家卫健委·专题专栏",
            "categoryCode": "KNOWLEDGE",
            "status": "success",
            "recordCount": len(topic_items),
            "collectedAt": datetime.now(timezone.utc).isoformat(),
            "items": topic_items[:15],  # 最多保存15个
        }
        results.append(topic_result)
    
    # 保存采集结果
    output_data = {
        "sourceId": "nhc_kepu",
        "sourceName": "国家卫健委·健康科普",
        "categoryCode": "KNOWLEDGE",
        "status": "success" if results else "partial",
        "recordCount": sum(r.get("recordCount", 0) for r in results),
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "subSources": results,
    }
    
    out_file = NHC_OUT / "nhc_kepu.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    # 镜像到后端 resources
    backend_file = (
        ROOT / "health-portal-backend" / "src" / "main" / "resources"
        / "data" / "crawl" / "nhc" / "nhc_kepu.json"
    )
    backend_file.parent.mkdir(parents=True, exist_ok=True)
    with open(backend_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    total_items = output_data["recordCount"]
    print(f"\n{'=' * 70}")
    print(f"✅ 采集完成: 共 {total_items} 条内容")
    print(f"   - 视频: {len(video_items)} 条")
    print(f"   - 专题: {len(topic_items)} 个")
    print(f"💾 已保存至: {out_file}")
    print(f"{'=' * 70}")
    
    # 写入CMS数据库
    if "--import" in sys.argv or "--cms" in sys.argv:
        print("\n📝 正在写入 CMS 数据库...")
        db_cfg = load_db_config()
        
        # 合并所有items
        all_items = []
        for result in results:
            all_items.extend(result.get("items", []))
        
        stats = import_to_cms(all_items, db_cfg)
        print(f"   CMS 写入: inserted={stats['inserted']} updated={stats['updated']} skipped={stats['skipped']}")
        
        log = {
            "finishedAt": datetime.now(timezone.utc).isoformat(),
            "recordCount": total_items,
            "cmsImport": stats,
        }
        with open(NHC_OUT / "last-import.json", "w", encoding="utf-8") as f:
            json.dump(log, f, ensure_ascii=False, indent=2)
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
