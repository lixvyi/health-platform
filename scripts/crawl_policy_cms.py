# -*- coding: utf-8 -*-
"""
合规政策/健康知识采集 → 自动写入 CMS（cms_content）

原则（实训/门户聚合，非政务内网数据）：
- 仅采集政府官网公开发布的政策、解读、健康科普列表页
- 遵守限速、User-Agent 标识；存摘要 + 原文链接，不全文转载
- 注明来源；以官网为准
"""
from __future__ import annotations

import json
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
CMS_OUT = ROOT / "data" / "crawl" / "cms"
CRAWL_DIRS = [
    ROOT / "data" / "crawl" / "internet",
    ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "crawl",
]
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

HEALTH_KW = [
    "健康", "卫生", "医疗", "医养", "疾控", "公卫", "公共卫生", "医院", "医保",
    "疫苗", "接种", "养老", "妇幼", "基层", "中医", "药品", "传染病", "慢病",
    "营养", "心理", "康复", "体检", "生育",
]
POLICY_KW = ["规划", "纲要", "条例", "意见", "通知", "方案", "规定", "办法", "政策", "解读"]
KNOWLEDGE_KW = ["科普", "建议", "饮食", "预防", "指南", "宣传", "保健", "用药"]
LIFESTYLE_KW = [
    "健康", "睡眠", "运动", "饮食", "营养", "作息", "锻炼", "养生", "保健", "防病",
    "预防", "心理", "儿童", "老人", "孕妇", "慢病", "高血压", "糖尿病", "减肥", "饮水",
    "护眼", "疫苗", "康复", "体重", "骨骼", "视力", "口腔", "戒烟", "戒酒", "防暑",
]


def load_config() -> dict:
    with open(CONFIG, encoding="utf-8") as f:
        return json.load(f)


def load_db_config() -> dict:
    cfg = {"host": "localhost", "user": "root", "password": "060508", "database": "health_portal"}
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


def polite_get(url: str, headers: dict, delay: float) -> requests.Response:
    time.sleep(delay)
    r = requests.get(url, headers=headers, timeout=30)
    r.encoding = r.apparent_encoding or "utf-8"
    return r


def match_keywords(text: str, keywords: list[str]) -> bool:
    if not keywords:
        return True
    return any(k in text for k in keywords)


def classify_category(title: str, default: str = "POLICY") -> str:
    if any(k in title for k in KNOWLEDGE_KW):
        return "KNOWLEDGE"
    if any(k in title for k in POLICY_KW) or any(k in title for k in HEALTH_KW):
        return "POLICY"
    return default


def extract_paragraphs(soup: BeautifulSoup) -> list[str]:
    selectors = [
        ".pages_content", ".article", "#UCAP-CONTENT", ".content",
        ".trs_editor_view", ".xl-cont", ".detail-content", "div.article-content",
    ]
    for sel in selectors:
        el = soup.select_one(sel)
        if not el:
            continue
        paras = [p.get_text(" ", strip=True) for p in el.select("p") if len(p.get_text(strip=True)) > 15]
        if paras:
            return paras
        text = el.get_text(" ", strip=True)
        if len(text) > 40:
            return [text[:500]]
    return []


def fetch_detail(url: str, headers: dict, delay: float) -> str:
    try:
        resp = polite_get(url, headers, delay)
        soup = BeautifulSoup(resp.text, "html.parser")
        paras = extract_paragraphs(soup)
        if paras:
            return paras[0][:300]
    except Exception:
        pass
    return ""


def crawl_policy_list(source: dict, headers: dict, delay: float, max_items: int) -> dict:
    url = source["url"]
    resp = polite_get(url, headers, delay)
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()
    keywords = source.get("keywords", [])
    link_pattern = source.get("linkPattern", "")
    require_health = source.get("requireHealthKeyword", False)
    fetch_detail_flag = source.get("fetchDetail", True)
    max_detail = int(source.get("maxDetailFetch", 12))
    default_category = source.get("categoryCode", "POLICY")

    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        if not title or len(title) < 6 or len(title) > 150:
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        full = urljoin(url, href)
        if full in seen or full.rstrip("/") == url.rstrip("/"):
            continue
        if link_pattern and link_pattern not in full:
            continue
        if keywords and not match_keywords(title, keywords):
            continue
        if require_health and not match_keywords(title, HEALTH_KW):
            continue
        if not link_pattern and keywords and not match_keywords(title, keywords):
            continue
        if not link_pattern and not keywords and not match_keywords(title, HEALTH_KW + POLICY_KW):
            continue
        seen.add(full)
        cat = source.get("categoryCode") or classify_category(title, default_category)
        items.append({
            "title": title,
            "url": full,
            "sourceId": source["id"],
            "sourceName": source["name"],
            "categoryCode": cat,
            "attribution": source.get("attribution", ""),
            "collectedAt": datetime.now(timezone.utc).isoformat(),
        })
        if len(items) >= max_items:
            break

    detail_count = 0
    if fetch_detail_flag:
        for item in items:
            if detail_count >= max_detail:
                break
            excerpt = fetch_detail(item["url"], headers, delay)
            if excerpt:
                item["summary"] = excerpt
                detail_count += 1

    return {
        "sourceId": source["id"],
        "sourceName": source["name"],
        "categoryCode": default_category,
        "status": "success" if items else "partial",
        "recordCount": len(items),
        "items": items,
    }


def load_seed_knowledge(source: dict) -> dict:
    seed_file = ROOT / source.get("localFile", "scripts/seed_health_knowledge.json")
    if not seed_file.exists():
        return {"sourceId": source["id"], "status": "failed", "recordCount": 0, "items": []}
    with open(seed_file, encoding="utf-8") as f:
        data = json.load(f)
    items = []
    for raw in data.get("items", []):
        items.append({
            "title": raw["title"],
            "url": raw.get("sourceUrl", f"seed://{raw['title']}"),
            "summary": raw.get("summary", ""),
            "contentHtml": raw.get("content", ""),
            "sourceId": source["id"],
            "sourceName": source.get("name", "官方科普种子库"),
            "categoryCode": "KNOWLEDGE",
            "attribution": source.get("attribution", "来源：官方公开健康指南摘要"),
        })
    return {
        "sourceId": source["id"],
        "sourceName": source["name"],
        "categoryCode": "KNOWLEDGE",
        "status": "success",
        "recordCount": len(items),
        "items": items,
    }


def crawl_article_list(source: dict, headers: dict, delay: float, max_items: int) -> dict:
    cfg = dict(source)
    if not cfg.get("keywords"):
        cfg["keywords"] = LIFESTYLE_KW
    cfg["requireHealthKeyword"] = cfg.get("requireHealthKeyword", True)
    return crawl_policy_list(cfg, headers, delay, max_items)


def load_crawl_json(crawl_id: str) -> dict | None:
    for base in CRAWL_DIRS:
        path = base / f"{crawl_id}.json"
        if path.exists():
            with open(path, encoding="utf-8") as f:
                return json.load(f)
    return None


def mirror_internet_crawl(source: dict) -> dict:
    crawl_ids = source.get("crawlIds", [])
    keywords = source.get("keywords", HEALTH_KW)
    require_health = source.get("requireHealthKeyword", True)
    default_category = source.get("categoryCode", "NEWS")
    items = []
    seen = set()

    for cid in crawl_ids:
        data = load_crawl_json(cid)
        if not data or not data.get("items"):
            continue
        for raw in data["items"]:
            title = raw.get("title", "").strip()
            url = raw.get("url", "").strip()
            if not title or not url or len(title) < 6:
                continue
            if url in seen:
                continue
            if require_health and not match_keywords(title, keywords):
                continue
            if not require_health and keywords and not match_keywords(title, keywords):
                continue
            seen.add(url)
            items.append({
                "title": title,
                "url": url,
                "sourceId": source["id"],
                "sourceName": raw.get("sourceName") or source["name"],
                "categoryCode": classify_category(title, default_category),
                "attribution": raw.get("attribution") or source.get("attribution", ""),
                "collectedAt": raw.get("collectedAt") or datetime.now(timezone.utc).isoformat(),
            })

    return {
        "sourceId": source["id"],
        "sourceName": source["name"],
        "categoryCode": default_category,
        "status": "success" if items else "partial",
        "recordCount": len(items),
        "items": items[: int(source.get("maxListItems", 50))],
    }


def ensure_schema(conn) -> None:
    cur = conn.cursor()
    cur.execute("SHOW COLUMNS FROM cms_content LIKE 'source_url'")
    if not cur.fetchone():
        cur.execute(
            "ALTER TABLE cms_content ADD COLUMN source_url VARCHAR(512) NULL "
            "COMMENT '原文链接(采集去重)' AFTER content"
        )
        try:
            cur.execute("CREATE INDEX idx_cms_source_url ON cms_content(source_url(191))")
        except Exception:
            pass
    conn.commit()
    cur.close()


def build_content(title: str, url: str, summary: str, attribution: str) -> str:
    summary = summary or "（请点击原文链接查阅完整内容）"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本文为互联网公开信息索引，摘要仅供参考，以原文为准。</em></p>"
        f'<p>原文链接：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>'
        f"<p>{attribution}</p>"
    )


def import_to_cms(all_results: list[dict], db_cfg: dict) -> dict:
    conn = pymysql.connect(
        host=db_cfg["host"], user=db_cfg["user"], password=db_cfg["password"],
        database=db_cfg["database"], charset="utf8mb4",
    )
    ensure_schema(conn)
    cur = conn.cursor()
    inserted, updated = 0, 0

    for bundle in all_results:
        category = bundle.get("categoryCode", "POLICY")
        attribution = bundle.get("sourceName", "")
        for item in bundle.get("items", []):
            url = item.get("url", "")
            title = item.get("title", "").strip()
            if not title or not url:
                continue
            cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (url,))
            row = cur.fetchone()
            summary = item.get("summary") or title[:120]
            if item.get("contentHtml"):
                content = (
                    item["contentHtml"]
                    + f"<p><em>本文为互联网公开信息索引，摘要仅供参考，以原文为准。</em></p>"
                    + f"<p>{item.get('attribution') or attribution}</p>"
                )
            else:
                content = build_content(title, url, summary, item.get("attribution") or attribution)
            author = "合规采集·" + (item.get("sourceName") or attribution)

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
                    (item.get("categoryCode", category), title, summary[:200], content, url, author),
                )
                inserted += 1

    conn.commit()
    cur.close()
    conn.close()
    return {"inserted": inserted, "updated": updated}


def main():
    cfg = load_config()
    delay = float(cfg.get("rateLimitSeconds", 3))
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    cms_sources = cfg.get("cmsSources", [])
    if not cms_sources:
        print("NO cmsSources configured")
        return 0

    CMS_OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for src in cms_sources:
        try:
            stype = src.get("type", "policy_list")
            if stype == "internet_mirror":
                res = mirror_internet_crawl(src)
            elif stype == "local_seed":
                res = load_seed_knowledge(src)
            elif stype == "article_list":
                max_items = int(src.get("maxListItems", 40))
                res = crawl_article_list(src, headers, delay, max_items)
            elif stype == "policy_list":
                max_items = int(src.get("maxListItems", 40))
                res = crawl_policy_list(src, headers, delay, max_items)
            else:
                continue
            out = CMS_OUT / f"{src['id']}.json"
            with open(out, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)
            backend_out = (
                ROOT / "health-portal-backend" / "src" / "main" / "resources"
                / "data" / "crawl" / "cms" / f"{src['id']}.json"
            )
            backend_out.parent.mkdir(parents=True, exist_ok=True)
            with open(backend_out, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)
            results.append(res)
            print(f"CMS-CRAWL {src['id']} -> {res.get('recordCount', 0)} items")
        except Exception as e:
            print(f"CMS-FAIL {src.get('id')}: {e}")
            results.append({"sourceId": src.get("id"), "status": "failed", "error": str(e), "items": []})

    db_cfg = load_db_config()
    stats = import_to_cms(results, db_cfg)
    print(f"CMS-IMPORT inserted={stats['inserted']} updated={stats['updated']}")
    log = {
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "sources": [{"sourceId": r.get("sourceId"), "recordCount": r.get("recordCount", 0)} for r in results],
        "cmsImport": stats,
    }
    with open(CMS_OUT / "last-import.json", "w", encoding="utf-8") as f:
        json.dump(log, f, ensure_ascii=False, indent=2)
    return 0


if __name__ == "__main__":
    sys.exit(main())
