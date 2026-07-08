# -*- coding: utf-8 -*-
"""
中国疾控中心 · 健康科普栏目合规采集脚本

采集源：https://www.chinacdc.cn/jkkp/
分类：传染病、慢性非传染性疾病、免疫规划、公共卫生事件、烟草控制、
      营养与健康、环境健康、职业健康与中毒控制、放射卫生

原则：
- 仅采集公开发布的健康科普列表页 + 文章摘要
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
CDC_OUT = ROOT / "data" / "crawl" / "chinacdc_jkkp"
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

CDC_BASE = "https://www.chinacdc.cn"

CDC_CATEGORIES = {
    "crb":      {"name": "传染病",             "subcats": ["jcr", "yclr", "czb", "ajc", "gzbd"]},
    "mxfcrxjb": {"name": "慢性非传染性疾病",   "subcats": ["gxy", "tnb", "zlf", "jssb"]},
    "mygh":     {"name": "免疫规划",           "subcats": ["myzs", "jkm"]},
    "ggws":     {"name": "公共卫生事件",       "subcats": ["tfggws", "yzsj"]},
    "yckz":     {"name": "烟草控制",           "subcats": ["yxjk", "whj"]},
    "yyjk":     {"name": "营养与健康",         "subcats": ["hspy", "yyzs"]},
    "hjjk":     {"name": "环境健康",           "subcats": ["kqzl", "xdgr"]},
    "zyjk":     {"name": "职业健康与中毒控制", "subcats": ["zyws", "hxd"]},
    "fsws":     {"name": "放射卫生",           "subcats": ["hfs", "fsfz"]},
}


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
    """从子分类列表页提取文章标题、链接、日期、摘要"""
    resp = polite_get(list_url, headers, delay)
    if not resp:
        return []
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()

    # 中国疾控中心列表页的文章链接通常以 .html/.htm 结尾
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        if not title or len(title) < 4 or len(title) > 200:
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        if not href.endswith(".html") and not href.endswith(".htm"):
            continue
        # 过滤导航链接等非文章链接
        if "/jkkp/" not in href and not href.startswith("./") and not href.startswith("../"):
            continue
        full_url = urljoin(list_url, href)
        if full_url in seen:
            continue
        # 排除列表页本身
        if full_url.rstrip("/") == list_url.rstrip("/"):
            continue
        seen.add(full_url)

        # 尝试从相邻节点提取日期
        date_text = ""
        parent = a.parent
        if parent:
            parent_text = parent.get_text(" ", strip=True)
            date_match = re.search(r"(\d{4}-\d{2}-\d{2})", parent_text)
            if date_match:
                date_text = date_match.group(1)

        # 尝试从 li 中提取摘要
        summary = ""
        li = a.find_parent("li")
        if li:
            all_text = li.get_text(" ", strip=True)
            if len(all_text) > len(title) + 10:
                summary = all_text.replace(title, "", 1).replace(date_text, "", 1).strip()
                summary = re.sub(r"\s+", " ", summary)
                if len(summary) > 200:
                    summary = summary[:200]

        items.append({
            "title": title,
            "url": full_url,
            "date": date_text,
            "summary": summary,
        })

    return items


def crawl_article_summary(url: str, headers: dict, delay: float) -> str:
    """访问文章详情页，提取前300字作为摘要"""
    resp = polite_get(url, headers, delay)
    if not resp:
        return ""
    soup = BeautifulSoup(resp.text, "html.parser")

    # 常见的正文容器选择器
    selectors = [
        ".pages_content", ".article_content", "#UCAP-CONTENT",
        ".TRS_Editor", ".content", ".article", "div.article-content",
    ]
    for sel in selectors:
        el = soup.select_one(sel)
        if not el:
            continue
        paras = [p.get_text(" ", strip=True) for p in el.select("p") if len(p.get_text(strip=True)) > 10]
        if paras:
            text = " ".join(paras)
            return text[:300]
        text = el.get_text(" ", strip=True)
        if len(text) > 20:
            return text[:300]

    # 兜底：从 body 中提取
    body = soup.select_one("body")
    if body:
        text = body.get_text(" ", strip=True)
        if len(text) > 50:
            clean = re.sub(r"\s+", " ", text)
            # 跳过导航等头部内容
            start = clean.find("当前位置")
            if start > 0:
                clean = clean[start:]
            return clean[:300]
    return ""


def crawl_category(cat_code: str, cat_info: dict, headers: dict, delay: float,
                   fetch_detail: bool, max_detail: int) -> dict:
    """采集一个分类下所有子分类的文章"""
    all_items = []
    subcats = cat_info["subcats"]

    for subcat in subcats:
        list_url = f"{CDC_BASE}/jkkp/{cat_code}/{subcat}/"
        print(f"  列表页: {list_url}")
        items = crawl_list_page(list_url, headers, delay)
        for item in items:
            item["categoryCode"] = cat_code
            item["categoryName"] = cat_info["name"]
            item["subCategory"] = subcat
        all_items.extend(items)
        print(f"    -> {len(items)} 条")

    # 对没有摘要的文章抓取详情页
    detail_count = 0
    if fetch_detail:
        for item in all_items:
            if detail_count >= max_detail:
                break
            if not item.get("summary"):
                summary = crawl_article_summary(item["url"], headers, delay)
                if summary:
                    item["summary"] = summary
                    detail_count += 1

    return {
        "sourceId": f"chinacdc_jkkp_{cat_code}",
        "sourceName": f"中国疾控中心·{cat_info['name']}",
        "categoryCode": "KNOWLEDGE",
        "status": "success" if all_items else "partial",
        "recordCount": len(all_items),
        "items": all_items,
    }


def build_content(title: str, url: str, summary: str, attribution: str) -> str:
    summary = summary or "（请点击原文链接查阅完整内容）"
    return (
        f"<p>{summary}</p>"
        f"<p><em>本文为中国疾控中心健康科普公开信息索引，摘要仅供参考，以原文为准。</em></p>"
        f'<p>原文链接：<a href="{url}" target="_blank" rel="noopener">{title}</a></p>'
        f"<p>{attribution}</p>"
    )


def import_to_cms(all_results: list[dict], db_cfg: dict) -> dict:
    """将采集结果写入 cms_content 表（通过 source_url 去重）"""
    conn = pymysql.connect(
        host=db_cfg["host"], user=db_cfg["user"], password=db_cfg["password"],
        database=db_cfg["database"], charset="utf8mb4",
    )
    cur = conn.cursor()
    inserted, updated, skipped = 0, 0, 0

    for bundle in all_results:
        attribution = bundle.get("sourceName", "")
        for item in bundle.get("items", []):
            url = item.get("url", "")
            title = item.get("title", "").strip()
            if not title or not url:
                skipped += 1
                continue

            cur.execute("SELECT id FROM cms_content WHERE source_url = %s LIMIT 1", (url,))
            row = cur.fetchone()

            summary = item.get("summary") or title[:120]
            content = build_content(title, url, summary, attribution)
            author = "合规采集·中国疾控中心健康科普"

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
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }

    # 读取本源的配置
    cdc_cfg = {}
    for src in cfg.get("cmsSources", []):
        if src.get("id") == "chinacdc_jkkp":
            cdc_cfg = src
            break
    if not cdc_cfg:
        print("未在 crawl-sources.json 中找到 chinacdc_jkkp 配置，使用默认参数")
        cdc_cfg = {"fetchDetail": True, "maxDetailFetch": 20, "maxListItems": 50}

    fetch_detail = cdc_cfg.get("fetchDetail", True)
    max_detail = int(cdc_cfg.get("maxDetailFetch", 20))

    CDC_OUT.mkdir(parents=True, exist_ok=True)
    results = []

    print("=" * 60)
    print("中国疾控中心 · 健康科普栏目采集")
    print(f"采集时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"限速: {delay}s / 请求")
    print("=" * 60)

    for cat_code, cat_info in CDC_CATEGORIES.items():
        print(f"\n[{cat_info['name']}] ({cat_code})")
        try:
            res = crawl_category(cat_code, cat_info, headers, delay, fetch_detail, max_detail)
            # 保存到 data/crawl/chinacdc_jkkp/
            out_file = CDC_OUT / f"{cat_code}.json"
            with open(out_file, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)

            # 镜像到后端 resources/data/crawl/chinacdc_jkkp/
            backend_file = (
                ROOT / "health-portal-backend" / "src" / "main" / "resources"
                / "data" / "crawl" / "chinacdc_jkkp" / f"{cat_code}.json"
            )
            backend_file.parent.mkdir(parents=True, exist_ok=True)
            with open(backend_file, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)

            results.append(res)
            print(f"  合计: {res['recordCount']} 条")
        except Exception as e:
            print(f"  ERROR: {e}")
            results.append({
                "sourceId": f"chinacdc_jkkp_{cat_code}",
                "sourceName": f"中国疾控中心·{cat_info['name']}",
                "status": "failed",
                "recordCount": 0,
                "error": str(e),
                "items": [],
            })

    total_items = sum(r.get("recordCount", 0) for r in results)
    print(f"\n{'=' * 60}")
    print(f"采集完成: 共 {total_items} 条, {len(results)} 个分类")
    print(f"{'=' * 60}")

    # 保存采集日志
    log = {
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "sources": [
            {"sourceId": r.get("sourceId"), "recordCount": r.get("recordCount", 0)}
            for r in results
        ],
        "totalItems": total_items,
    }
    with open(CDC_OUT / "last-crawl.json", "w", encoding="utf-8") as f:
        json.dump(log, f, ensure_ascii=False, indent=2)

    # --import 模式：写入 CMS 数据库
    if "--import" in sys.argv or "--cms" in sys.argv:
        print("\n正在写入 CMS 数据库...")
        db_cfg = load_db_config()
        stats = import_to_cms(results, db_cfg)
        print(f"CMS 写入: inserted={stats['inserted']} updated={stats['updated']} skipped={stats['skipped']}")
        log["cmsImport"] = stats
        with open(CDC_OUT / "last-import.json", "w", encoding="utf-8") as f:
            json.dump(log, f, ensure_ascii=False, indent=2)

    return 0


if __name__ == "__main__":
    sys.exit(main())
