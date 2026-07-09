# -*- coding: utf-8 -*-
"""Import local policy documents from data/policies into cms_content.

This replaces earlier POLICY rows imported from broad government/statistics
mirrors. NEWS and POLICY are kept separate by source_url and category.
"""
from __future__ import annotations

import csv
import html
import os
import re
from datetime import datetime
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
POLICY_DIR = ROOT / "data" / "policies"
JDBC = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "jdbc.properties"

POLICY_FILES = [
    {
        "file": "nhc_documents.csv",
        "source_name": "国家卫生健康委员会",
        "author": "本地政策库·国家卫生健康委员会",
    },
    {
        "file": "nhsa_documents.csv",
        "source_name": "国家医疗保障局",
        "author": "本地政策库·国家医疗保障局",
    },
    {
        "file": "nhsa_zcjd_documents.csv",
        "source_name": "国家医疗保障局政策解读",
        "author": "本地政策库·国家医疗保障局",
    },
    {
        "file": "nmpa_regulatory_documents.csv",
        "source_name": "国家药品监督管理局",
        "author": "本地政策库·国家药品监督管理局",
    },
]

POLICY_HINTS = (
    "通知", "公告", "规定", "办法", "方案", "意见", "规划", "纲要", "目录", "标准",
    "规范", "条例", "解读", "批复", "医保", "药品", "医疗", "卫生", "健康",
)


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
            elif line.startswith("mysql.url="):
                m = re.search(r"//([^:/]+)", line)
                if m:
                    cfg["host"] = m.group(1)
                db = re.search(r"3306/([^?]+)", line)
                if db:
                    cfg["database"] = db.group(1)
    return cfg


def open_csv(path: Path):
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            fh = path.open(encoding=encoding, newline="")
            sample = fh.read(2048)
            fh.seek(0)
            if "\ufffd" not in sample:
                return fh
            fh.close()
        except UnicodeDecodeError:
            continue
    return path.open(encoding="utf-8", errors="replace", newline="")


def clean(value: str | None) -> str:
    if not value:
        return ""
    return re.sub(r"\s+", " ", value).strip()


def paragraphize(text: str, max_chars: int = 2400) -> str:
    text = (text or "").strip()
    if len(text) > max_chars:
        text = text[:max_chars].rstrip() + "..."
    blocks = [b.strip() for b in re.split(r"\n\s*\n|\r\n\s*\r\n", text) if b.strip()]
    if not blocks:
        return ""
    return "".join(f"<p>{html.escape(block)}</p>" for block in blocks[:8])


def parse_date(value: str) -> str | None:
    value = clean(value)
    if not value:
        return None
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%Y.%m.%d"):
        try:
            return datetime.strptime(value, fmt).strftime("%Y-%m-%d")
        except ValueError:
            pass
    return None


def is_policy_document(title: str, content: str) -> bool:
    text = f"{title} {content[:200]}"
    return any(keyword in text for keyword in POLICY_HINTS)


def iter_policy_rows():
    seen = set()
    for spec in POLICY_FILES:
        path = POLICY_DIR / spec["file"]
        if not path.exists():
            continue
        with open_csv(path) as fh:
            reader = csv.DictReader(fh)
            for row in reader:
                title = clean(row.get("title"))
                link = clean(row.get("link"))
                content = (row.get("content") or "").strip()
                if not title or not link or link in seen:
                    continue
                if not is_policy_document(title, content):
                    continue
                seen.add(link)
                summary = clean(content).replace("来源：", "")
                if not summary:
                    summary = title
                yield {
                    "title": title,
                    "source_url": link,
                    "summary": summary[:200],
                    "content": build_content(title, link, content, spec["source_name"]),
                    "source_name": spec["source_name"],
                    "author": spec["author"],
                    "source_publish_date": parse_date(row.get("date")),
                }


def build_content(title: str, link: str, body: str, source_name: str) -> str:
    body_html = paragraphize(body) or f"<p>{html.escape(title)}</p>"
    return (
        body_html
        + "<p><em>本文来源于本地政策文件库，展示为公开政策索引，正式内容以原文链接为准。</em></p>"
        + f'<p>原文链接：<a href="{html.escape(link)}" target="_blank" rel="noopener">{html.escape(title)}</a></p>'
        + f"<p>来源：{html.escape(source_name)}</p>"
    )


def cleanup_wrong_sources(cur) -> tuple[int, int]:
    wrong_policy_where = """
        category_code='POLICY'
        AND (
          author LIKE '合规采集·国家统计局%'
          OR author LIKE '合规采集·中国政府网%'
          OR author LIKE '互联网采集%'
          OR source_url LIKE 'https://www.stats.gov.cn/%'
          OR source_url LIKE 'http://www.gov.cn/%'
          OR source_url LIKE 'https://www.gov.cn/%'
          OR (source_url IS NULL AND author NOT LIKE '本地政策库%')
        )
    """
    cur.execute(f"DELETE FROM cms_content WHERE {wrong_policy_where}")
    policy_deleted = cur.rowcount

    cur.execute(
        """
        DELETE FROM cms_content
        WHERE category_code='NEWS'
          AND (source_url LIKE 'news:policy:%' OR title LIKE '[政策速递] %')
        """
    )
    news_deleted = cur.rowcount
    cur.execute(
        """
        DELETE n FROM cms_content n
        JOIN cms_content p
          ON p.category_code='POLICY'
         AND p.source_url IS NOT NULL
         AND p.source_url = n.source_url
        WHERE n.category_code='NEWS'
        """
    )
    news_deleted += cur.rowcount
    cur.execute(
        """
        DELETE FROM cms_content
        WHERE category_code='NEWS'
          AND author='互联网采集'
          AND (
            title REGEXP '规划|纲要|条例|意见|通知|方案|规定|办法|政策|解读|目录|标准|规范|公告|批复|分类|制度'
            OR source_url LIKE '%/zhengce/content/%'
            OR source_url LIKE '%/xw/tjxw/tzgg/%'
          )
        """
    )
    news_deleted += cur.rowcount
    return policy_deleted, news_deleted


def upsert_policy(cur, item: dict) -> str:
    cur.execute("SELECT id FROM cms_content WHERE source_url=%s LIMIT 1", (item["source_url"],))
    row = cur.fetchone()
    if row:
        cur.execute(
            """
            UPDATE cms_content
            SET category_code='POLICY', title=%s, summary=%s, content=%s,
                source_name=%s, source_publish_date=%s, author=%s, status=1,
                verification_status='VERIFIED', updated_at=NOW()
            WHERE id=%s
            """,
            (
                item["title"], item["summary"], item["content"], item["source_name"],
                item["source_publish_date"], item["author"], row[0],
            ),
        )
        return "updated"
    cur.execute(
        """
        INSERT INTO cms_content
          (category_code, title, summary, content, source_url, source_name,
           source_publish_date, author, view_count, status, publish_time,
           content_type, is_medical, verification_status)
        VALUES
          ('POLICY', %s, %s, %s, %s, %s, %s, %s, 0, 1,
           COALESCE(%s, CURDATE()), 'ARTICLE', 1, 'VERIFIED')
        """,
        (
            item["title"], item["summary"], item["content"], item["source_url"],
            item["source_name"], item["source_publish_date"], item["author"],
            item["source_publish_date"],
        ),
    )
    return "inserted"


def main() -> int:
    db = load_db_config()
    conn = pymysql.connect(
        host=db["host"], user=db["user"], password=db["password"],
        database=db["database"], charset="utf8mb4",
    )
    cur = conn.cursor()
    policy_deleted, news_deleted = cleanup_wrong_sources(cur)
    inserted = updated = 0
    for item in iter_policy_rows():
        result = upsert_policy(cur, item)
        inserted += result == "inserted"
        updated += result == "updated"
    conn.commit()
    cur.close()
    conn.close()
    print(
        "LOCAL-POLICY-IMPORT "
        f"inserted={inserted} updated={updated} "
        f"deleted_wrong_policy={policy_deleted} deleted_news_overlap={news_deleted}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
