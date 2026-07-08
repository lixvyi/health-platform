"""Import additional traceable health encyclopedia articles.

Sources:
- China CDC Health Popularization pages that are directly readable.
- People's Daily Online health channel pages, filtered by health keywords.

The importer stores only title, summary, source, and original URL. It does not
invent medical content and does not copy full articles.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from urllib.parse import urljoin

import pymysql
import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "data" / "processed" / "health-encyclopedia-more"


@dataclass
class Article:
    title: str
    url: str
    summary: str
    source_name: str
    category_code: str
    content_type: str = "ARTICLE"


SOURCES = [
    {
        "id": "people_health_lifestyle",
        "sourceName": "人民网健康·生活方式",
        "url": "http://health.people.com.cn/GB/408573/index.html",
        "defaultCategory": "HEALTH_POPULARIZATION",
        "maxItems": 45,
    },
    {
        "id": "people_health_channel",
        "sourceName": "人民网健康频道",
        "url": "http://health.people.com.cn/GB/408644/index.html",
        "defaultCategory": "HEALTH_POPULARIZATION",
        "maxItems": 30,
    },
    {
        "id": "chinacdc_crb_jcr",
        "sourceName": "中国疾控中心·传染病防控",
        "url": "https://www.chinacdc.cn/jkkp/crb/jcr/",
        "defaultCategory": "EPIDEMIC",
        "maxItems": 20,
    },
    {
        "id": "chinacdc_hjjk_kqzl",
        "sourceName": "中国疾控中心·环境健康",
        "url": "https://www.chinacdc.cn/jkkp/hjjk/kqzl/",
        "defaultCategory": "HEALTH_POPULARIZATION",
        "maxItems": 30,
    },
    {
        "id": "chinacdc_hjjk_xdgr",
        "sourceName": "中国疾控中心·环境健康",
        "url": "https://www.chinacdc.cn/jkkp/hjjk/xdgr/",
        "defaultCategory": "HEALTH_POPULARIZATION",
        "maxItems": 30,
    },
    {
        "id": "chinacdc_fsws_hfs",
        "sourceName": "中国疾控中心·放射卫生",
        "url": "https://www.chinacdc.cn/jkkp/fsws/hfs/",
        "defaultCategory": "HEALTH_POPULARIZATION",
        "maxItems": 20,
    },
]

HEALTH_KEYWORDS = [
    "健康", "疾病", "医疗", "医学", "医生", "医院", "患者", "用药", "药品", "疫苗", "接种",
    "传染", "感染", "病毒", "流感", "肺炎", "鼠疫", "消毒", "防护", "营养", "膳食",
    "运动", "睡眠", "心理", "妇女", "儿童", "老年", "中医", "高血压", "糖尿病", "癌",
    "肿瘤", "肥胖", "孤独症", "近视", "口腔", "心血管", "慢病", "科普",
]

SKIP_KEYWORDS = [
    "酒店", "维权", "退款", "商家", "消费提示", "预订", "景区", "摊点", "保健食品推销",
]


def clean(text: str | None) -> str:
    if not text:
        return ""
    return re.sub(r"\s+", " ", text).strip()


def fetch(url: str) -> requests.Response:
    headers = {
        "User-Agent": "HealthPortalBot/1.0 (educational; source-link-only)",
        "Accept-Language": "zh-CN,zh;q=0.9",
    }
    response = requests.get(url, headers=headers, timeout=20)
    response.raise_for_status()
    response.encoding = "utf-8"
    return response


def is_health_title(title: str) -> bool:
    if any(word in title for word in SKIP_KEYWORDS):
        return False
    return any(word in title for word in HEALTH_KEYWORDS)


def classify(title: str, default: str) -> tuple[str, str]:
    if any(word in title for word in ["说明书", "药品", "用药", "药物", "处方"]):
        return "DRUG", "DRUG"
    if any(word in title for word in ["疫苗", "接种", "免疫规划"]):
        return "VACCINE", "VACCINE"
    if any(word in title for word in ["传染", "感染", "病毒", "流感", "肺炎", "鼠疫", "消毒", "防护", "疫情"]):
        return "EPIDEMIC", "EPIDEMIC"
    if any(word in title for word in ["高血压", "糖尿病", "癌", "肿瘤", "孤独症", "肥胖", "近视", "疾病", "患者", "症"]):
        return "DISEASE", "DISEASE"
    return default, "ARTICLE"


def extract_links(source: dict) -> list[tuple[str, str]]:
    response = fetch(source["url"])
    soup = BeautifulSoup(response.text, "html.parser")
    links: list[tuple[str, str]] = []
    seen = set()
    for a in soup.find_all("a", href=True):
        title = clean(a.get_text(" ", strip=True))
        href = urljoin(source["url"], a["href"])
        if not title or len(title) < 6:
            continue
        if source["url"].startswith("http://health.people.com.cn"):
            if "/n1/" not in href:
                continue
            if not is_health_title(title):
                continue
        else:
            if "/jkkp/" not in href or not re.search(r"/t\d+_\d+\.html$", href):
                continue
        if href in seen:
            continue
        seen.add(href)
        links.append((title, href))
        if len(links) >= source["maxItems"]:
            break
    return links


def extract_summary(url: str) -> str:
    try:
        response = fetch(url)
    except Exception:
        return ""
    soup = BeautifulSoup(response.text, "html.parser")
    for selector in [".artDet", ".rm_txt_con", ".article", ".TRS_Editor", ".pages_content", "#UCAP-CONTENT"]:
        element = soup.select_one(selector)
        if not element:
            continue
        paragraphs = [clean(p.get_text(" ", strip=True)) for p in element.find_all("p")]
        paragraphs = [p for p in paragraphs if len(p) >= 20]
        text = clean(" ".join(paragraphs) or element.get_text(" ", strip=True))
        if len(text) >= 30:
            return text[:280]
    body_text = clean(soup.body.get_text(" ", strip=True) if soup.body else "")
    return body_text[:280] if len(body_text) >= 30 else ""


def collect(delay: float, detail_limit: int) -> list[Article]:
    articles: list[Article] = []
    seen = set()
    details = 0
    for source in SOURCES:
        try:
            links = extract_links(source)
        except Exception as exc:
            print(json.dumps({"source": source["id"], "error": str(exc)}, ensure_ascii=False))
            continue
        print(json.dumps({"source": source["id"], "links": len(links)}, ensure_ascii=False))
        for title, url in links:
            if url in seen:
                continue
            seen.add(url)
            summary = ""
            if details < detail_limit:
                time.sleep(delay)
                summary = extract_summary(url)
                details += 1
            if not summary:
                summary = "请点击原文链接查看完整内容。"
            category, content_type = classify(title, source["defaultCategory"])
            articles.append(Article(
                title=title,
                url=url,
                summary=summary,
                source_name=source["sourceName"],
                category_code=category,
                content_type=content_type,
            ))
            time.sleep(delay)
    return articles


def content_html(article: Article) -> str:
    return (
        f"<p>{article.summary}</p>"
        f"<p><em>本条为公开健康科普信息索引，内容仅供科普参考，不能替代医生诊断；请以原文和主管部门最新发布为准。</em></p>"
        f'<p>原文链接：<a href="{article.url}" target="_blank" rel="noopener noreferrer">{article.title}</a></p>'
        f"<p>来源：{article.source_name}</p>"
    )


def connect():
    password = os.getenv("HEALTH_DB_PASSWORD")
    if not password:
        raise SystemExit("Missing HEALTH_DB_PASSWORD")
    return pymysql.connect(
        host=os.getenv("HEALTH_DB_HOST", "localhost"),
        user=os.getenv("HEALTH_DB_USER", "root"),
        password=password,
        database=os.getenv("HEALTH_DB_NAME", "health_portal"),
        charset="utf8mb4",
        autocommit=False,
    )


def apply(articles: list[Article]) -> dict:
    conn = connect()
    inserted = updated = 0
    try:
        with conn.cursor() as cur:
            for article in articles:
                cur.execute("SELECT id FROM cms_content WHERE source_url=%s LIMIT 1", (article.url,))
                row = cur.fetchone()
                now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                if row:
                    content_id = int(row[0])
                    cur.execute(
                        """
                        UPDATE cms_content
                        SET title=%s, summary=%s, content=%s, source_name=%s, author=%s,
                            content_type=%s, is_medical=1, verification_status='IMPORTED'
                        WHERE id=%s
                        """,
                        (
                            article.title, article.summary[:240], content_html(article),
                            article.source_name, article.source_name, article.content_type, content_id,
                        ),
                    )
                    updated += 1
                else:
                    cur.execute(
                        """
                        INSERT INTO cms_content
                        (category_code,title,summary,content,source_url,source_name,author,view_count,status,
                         publish_time,created_by,content_type,is_medical,verification_status)
                        VALUES ('KNOWLEDGE',%s,%s,%s,%s,%s,%s,0,1,%s,1,%s,1,'IMPORTED')
                        """,
                        (
                            article.title, article.summary[:240], content_html(article), article.url,
                            article.source_name, article.source_name, now, article.content_type,
                        ),
                    )
                    content_id = int(cur.lastrowid)
                    inserted += 1
                cur.execute(
                    """
                    INSERT IGNORE INTO content_category_rel (content_id, category_code, source_type)
                    VALUES (%s, %s, 'AUTO')
                    """,
                    (content_id, article.category_code),
                )
            cur.execute(
                """
                INSERT INTO data_resource_dataset
                (dataset_code,dataset_name,dataset_type,source_name,source_url,record_count,duplicate_count,
                 error_count,update_status,last_imported_at)
                VALUES ('HEALTH_ENCYCLOPEDIA_PUBLIC_ARTICLES','健康百科公开科普文章扩充','CMS',
                        '中国疾控中心/人民网健康','https://www.chinacdc.cn/jkkp/',%s,%s,0,'SUCCESS',NOW())
                ON DUPLICATE KEY UPDATE record_count=VALUES(record_count),duplicate_count=VALUES(duplicate_count),
                    update_status='SUCCESS',last_imported_at=NOW()
                """,
                (len(articles), updated),
            )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    return {"inserted": inserted, "updated": updated, "total": len(articles)}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--delay", type=float, default=0.6)
    parser.add_argument("--detail-limit", type=int, default=80)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    articles = collect(args.delay, args.detail_limit)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    payload = [article.__dict__ for article in articles]
    (OUT_DIR / "articles.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "collected": len(articles),
        "byCategory": {c: sum(1 for a in articles if a.category_code == c) for c in sorted({a.category_code for a in articles})},
        "exportedTo": str(OUT_DIR / "articles.json"),
        "databaseWrite": bool(args.apply),
    }, ensure_ascii=False))
    if args.apply:
        print(json.dumps(apply(articles), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
