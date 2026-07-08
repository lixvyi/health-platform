# -*- coding: utf-8 -*-
"""将政策速递、资源池更新、互联网采集结果同步到新闻中心（NEWS）"""
from __future__ import annotations

import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
LOG_PATHS = [
    ROOT / "data" / "crawl" / "last-run.json",
    ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "crawl" / "last-run.json",
]
CRAWL_DIRS = [
    ROOT / "data" / "crawl" / "internet",
    ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "crawl",
]
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

NEWS_KW = [
    "健康", "卫生", "医疗", "数据", "统计", "GDP", "CPI", "PMI", "发布", "就业", "收入",
    "医保", "养老", "疾控", "公卫", "医院", "疫苗", "规划", "政策", "纲要", "解读",
]


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
    return cfg


def load_last_run() -> dict:
    for p in LOG_PATHS:
        if p.exists():
            with open(p, encoding="utf-8") as f:
                return json.load(f)
    return {}


def load_crawl_json(cid: str) -> dict | None:
    for base in CRAWL_DIRS:
        path = base / f"{cid}.json"
        if path.exists():
            with open(path, encoding="utf-8") as f:
                return json.load(f)
    return None


def match_news(title: str) -> bool:
    return any(k in title for k in NEWS_KW)


def build_content(title: str, url: str, summary: str, attribution: str) -> str:
    link = url if url.startswith("http") else f"/data-pool"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本文为门户聚合索引，详情请查阅原文或资源池。</em></p>"
        f'<p>链接：<a href="{link}" target="_blank" rel="noopener">{title}</a></p>'
        f"<p>{attribution}</p>"
    )


def upsert_news(cur, source_url: str, title: str, summary: str, content: str, author: str) -> str:
    cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (source_url,))
    row = cur.fetchone()
    if row:
        cur.execute(
            "UPDATE cms_content SET title=%s, summary=%s, content=%s, publish_time=NOW(), updated_at=NOW() WHERE id=%s",
            (title, summary[:200], content, row[0]),
        )
        return "updated"
    cur.execute(
        "INSERT INTO cms_content (category_code, title, summary, content, source_url, author, view_count, status, publish_time) "
        "VALUES ('NEWS',%s,%s,%s,%s,%s,0,1,NOW())",
        (title, summary[:200], content, source_url, author),
    )
    return "inserted"


def sync_pool_daily(cur) -> int:
    last_run = load_last_run()
    if not last_run:
        return 0
    internet = 0
    files = 0
    for s in last_run.get("sources", []):
        if s.get("files"):
            files += len(s["files"])
        elif "recordCount" in s and "gov" in s.get("sourceId", "") or "stats" in s.get("sourceId", ""):
            internet += int(s.get("recordCount") or 0)
    today = datetime.now().strftime("%Y-%m-%d")
    title = f"【资源池速递】{today} 数据资源池已更新：互联网资讯 {internet} 条，开放数据文件 {files} 个"
    summary = f"今日采集任务已完成，可在数据资源池页面查看详情。"
    source_url = f"portal://data-pool/{today}"
    content = (
        f"<p>{summary}</p>"
        f"<p>互联网采集：{internet} 条；开放数据文件同步：{files} 个。</p>"
        f'<p><a href="/data-pool">前往统一数据资源池</a> · '
        f'<a href="/data">开放数据资源目录</a></p>'
        f"<p>来源：门户自动采集任务</p>"
    )
    upsert_news(cur, source_url, title, summary, content, "资源池自动同步")
    return 1


def sync_policies_as_news(cur, days: int = 3) -> int:
    n = 0
    cur.execute(
        "SELECT title, summary, source_url, content FROM cms_content "
        "WHERE category_code='POLICY' AND status=1 "
        "AND publish_time >= DATE_SUB(NOW(), INTERVAL %s DAY)",
        (days,),
    )
    for title, summary, url, _ in cur.fetchall():
        if not url:
            continue
        news_key = f"news:policy:{url}"
        news_title = f"[政策速递] {title}"
        s = summary or title[:100]
        content = build_content(title, url, s, "来源：近期政府公开政策，详见原文")
        if upsert_news(cur, news_key, news_title, s, content, "政策速递·自动同步") == "inserted":
            n += 1
    return n


def sync_internet_as_news(cur) -> int:
    n = 0
    seen = set()
    for cid in ["gov_cn_shuju", "stats_gov_cn_sj"]:
        data = load_crawl_json(cid)
        if not data:
            continue
        for item in data.get("items", []):
            title = (item.get("title") or "").strip()
            url = (item.get("url") or "").strip()
            if not title or not url or url in seen or len(title) < 8:
                continue
            if not match_news(title):
                continue
            if title in ("时政要闻", "统计新闻", "数据发布", "卫生和社会服务"):
                continue
            seen.add(url)
            summary = title[:120]
            attr = item.get("attribution") or "来源：政府官网公开信息"
            content = build_content(title, url, summary, attr)
            if upsert_news(cur, url, f"[资讯] {title}", summary, content, "互联网采集") == "inserted":
                n += 1
    return n


def main() -> int:
    db = load_db_config()
    conn = pymysql.connect(
        host=db["host"], user=db["user"], password=db["password"],
        database=db["database"], charset="utf8mb4",
    )
    cur = conn.cursor()
    pool_n = sync_pool_daily(cur)
    policy_n = sync_policies_as_news(cur, 3)
    internet_n = sync_internet_as_news(cur)
    conn.commit()
    cur.close()
    conn.close()
    print(f"NEWS-SYNC pool={pool_n} policy={policy_n} internet={internet_n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
