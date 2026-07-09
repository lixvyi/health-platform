# -*- coding: utf-8 -*-
"""
中国疾病预防控制中心 · 全国法定传染病疫情情况合规采集脚本

采集源：https://www.chinacdc.cn/jksj/jksj01/
数据类型：月度传染病疫情通报（结构化统计数据）

原则：
- 仅采集公开发布的疫情通报列表页 + 正文摘要
- 遵守限速、User-Agent 标识；存摘要 + 原文链接，不全文转载
- 注明来源；以官网为准
- 写入 cms_content 表，category_code = NEWS（或可扩展为 EPIDEMIC）
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
EPIDEMIC_OUT = ROOT / "data" / "crawl" / "epidemic"
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

CDC_BASE = "https://www.chinacdc.cn"
EPIDEMIC_LIST_URL = "https://www.chinacdc.cn/jksj/jksj01/"


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


def parse_epidemic_summary(text: str) -> dict:
    """从疫情通报正文中提取关键统计指标"""
    stats = {}
    
    # 提取总发病数
    total_match = re.search(r'共报告法定传染病(\d+)例', text)
    if total_match:
        stats['total_cases'] = int(total_match.group(1))
    
    # 提取总死亡数
    death_match = re.search(r'死亡(\d+)人', text)
    if death_match:
        stats['total_deaths'] = int(death_match.group(1))
    
    # 提取甲类传染病数据
    jia_class_match = re.search(r'甲类传染病.*?发病(\d+)例.*?死亡(\d+)', text)
    if jia_class_match:
        stats['class_a_cases'] = int(jia_class_match.group(1))
        stats['class_a_deaths'] = int(jia_class_match.group(2))
    
    # 提取乙类传染病数据
    yi_class_match = re.search(r'乙类传染病.*?发病(\d+)例.*?死亡(\d+)', text)
    if yi_class_match:
        stats['class_b_cases'] = int(yi_class_match.group(1))
        stats['class_b_deaths'] = int(yi_class_match.group(2))
    
    # 提取丙类传染病数据
    bing_class_match = re.search(r'丙类传染病.*?发病(\d+)例', text)
    if bing_class_match:
        stats['class_c_cases'] = int(bing_class_match.group(1))
    
    # 提取前5位病种
    top5_match = re.search(r'报告发病数居前5位的病种依次为([^。]+)', text)
    if top5_match:
        diseases = [d.strip() for d in top5_match.group(1).split('、')]
        stats['top5_diseases'] = diseases[:5]
    
    return stats


def crawl_list_page(list_url: str, headers: dict, delay: float) -> list[dict]:
    """从列表页提取疫情通报文章链接"""
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
        
        # 过滤条件：必须是疫情通报标题
        if not title or len(title) < 10:
            continue
        if not re.search(r'\d{4}年\d{1,2}月.*传染病.*概况', title):
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        
        full_url = urljoin(list_url, href)
        if full_url in seen:
            continue
        seen.add(full_url)
        
        # 尝试提取日期
        date_match = re.search(r'(\d{4}-\d{2}-\d{2})', title)
        pub_date = date_match.group(1) if date_match else ""
        
        items.append({
            "title": title,
            "url": full_url,
            "pubDate": pub_date,
        })
    
    return items


def crawl_article_detail(url: str, headers: dict, delay: float) -> tuple[str, dict]:
    """访问文章详情页，提取摘要和统计数据"""
    resp = polite_get(url, headers, delay)
    if not resp:
        return "", {}
    
    soup = BeautifulSoup(resp.text, "html.parser")
    
    # 常见的正文容器选择器
    selectors = [
        ".pages_content", ".article_content", "#UCAP-CONTENT",
        ".TRS_Editor", ".content", ".article", "div.article-content",
    ]
    
    full_text = ""
    for sel in selectors:
        el = soup.select_one(sel)
        if not el:
            continue
        paras = [p.get_text(" ", strip=True) for p in el.select("p") if len(p.get_text(strip=True)) > 20]
        if paras:
            full_text = "\n".join(paras)
            break
    
    if not full_text:
        body = soup.select_one("body")
        if body:
            full_text = body.get_text(" ", strip=True)
    
    # 提取前500字作为摘要
    summary = full_text[:500].replace("\n", " ").strip()
    if len(full_text) > 500:
        summary += "..."
    
    # 解析统计数据
    stats = parse_epidemic_summary(full_text)
    
    return summary, stats


def build_content(title: str, url: str, summary: str, stats: dict, attribution: str) -> str:
    """构建CMS内容HTML"""
    content_parts = [f"<p>{summary}</p>"]
    
    # 添加统计数据表格（如果有）
    if stats:
        content_parts.append("<h4>📊 关键数据</h4><ul>")
        if 'total_cases' in stats:
            content_parts.append(f"<li>总发病数：<strong>{stats['total_cases']:,}</strong> 例</li>")
        if 'total_deaths' in stats:
            content_parts.append(f"<li>总死亡数：<strong>{stats['total_deaths']:,}</strong> 人</li>")
        if 'class_a_cases' in stats:
            content_parts.append(f"<li>甲类传染病：发病 {stats['class_a_cases']} 例，死亡 {stats.get('class_a_deaths', 0)} 人</li>")
        if 'class_b_cases' in stats:
            content_parts.append(f"<li>乙类传染病：发病 {stats['class_b_cases']:,} 例，死亡 {stats.get('class_b_deaths', 0):,} 人</li>")
        if 'top5_diseases' in stats:
            diseases_str = "、".join(stats['top5_diseases'])
            content_parts.append(f"<li>发病数前5位病种：{diseases_str}</li>")
        content_parts.append("</ul>")
    
    content_parts.extend([
        "<p><em>本数据为中国疾控中心官方发布的疫情通报摘要，完整信息请查阅原文。</em></p>",
        f'<p>原文链接：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>',
        f"<p>{attribution}</p>"
    ])
    
    return "".join(content_parts)


def import_to_cms(items: list[dict], db_cfg: dict) -> dict:
    """将采集结果写入 cms_content 表"""
    conn = pymysql.connect(
        host=db_cfg["host"], user=db_cfg["user"], password=db_cfg["password"],
        database=db_cfg["database"], charset="utf8mb4",
    )
    cur = conn.cursor()
    inserted, updated, skipped = 0, 0, 0
    
    attribution = "来源：中国疾病预防控制中心"
    
    for item in items:
        url = item.get("url", "")
        title = item.get("title", "").strip()
        if not title or not url:
            skipped += 1
            continue
        
        cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (url,))
        row = cur.fetchone()
        
        summary = item.get("summary", "")
        stats = item.get("stats", {})
        content = build_content(title, url, summary, stats, attribution)
        author = "合规采集·中国疾控中心疫情通报"
        
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
                ("NEWS", title, summary[:200], content, url, author),
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
    
    EPIDEMIC_OUT.mkdir(parents=True, exist_ok=True)
    
    print("=" * 70)
    print("中国疾控中心 · 全国法定传染病疫情情况采集")
    print(f"采集时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"限速: {delay}s / 请求")
    print("=" * 70)
    
    # 采集列表页
    print(f"\n📋 采集列表页: {EPIDEMIC_LIST_URL}")
    list_items = crawl_list_page(EPIDEMIC_LIST_URL, headers, delay)
    print(f"   找到 {len(list_items)} 条疫情通报")
    
    if not list_items:
        print("❌ 未找到任何疫情通报，请检查网站结构是否变化")
        return 1
    
    # 采集每篇文章详情
    results = []
    max_articles = min(len(list_items), 12)  # 最多采集最近12个月
    
    print(f"\n📄 开始采集文章详情（最多 {max_articles} 篇）...")
    for idx, item in enumerate(list_items[:max_articles]):
        print(f"  [{idx+1}/{max_articles}] {item['title'][:50]}...")
        summary, stats = crawl_article_detail(item["url"], headers, delay)
        
        if summary:
            item["summary"] = summary
            item["stats"] = stats
            results.append(item)
            print(f"      ✓ 提取到 {len(stats)} 个统计指标")
        else:
            print(f"      ✗ 未能提取内容")
    
    # 保存采集结果
    output_data = {
        "sourceId": "chinacdc_epidemic",
        "sourceName": "中国疾控中心·传染病疫情通报",
        "categoryCode": "NEWS",
        "status": "success" if results else "partial",
        "recordCount": len(results),
        "collectedAt": datetime.now(timezone.utc).isoformat(),
        "items": results,
    }
    
    out_file = EPIDEMIC_OUT / "chinacdc_epidemic.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    # 镜像到后端 resources
    backend_file = (
        ROOT / "health-portal-backend" / "src" / "main" / "resources"
        / "data" / "crawl" / "epidemic" / "chinacdc_epidemic.json"
    )
    backend_file.parent.mkdir(parents=True, exist_ok=True)
    with open(backend_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    print(f"\n{'=' * 70}")
    print(f"✅ 采集完成: 共 {len(results)} 条疫情通报")
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
        with open(EPIDEMIC_OUT / "last-import.json", "w", encoding="utf-8") as f:
            json.dump(log, f, ensure_ascii=False, indent=2)
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
