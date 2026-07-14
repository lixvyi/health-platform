"""Repair health encyclopedia category relations from traceable existing data.

This script does not fabricate medical facts. It:
1. Builds content_category_rel from existing cms_content source/author/title.
2. Creates two vaccine encyclopedia articles from imported vaccine tables.
"""

from __future__ import annotations

import os
import sys
from datetime import datetime

import pymysql


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


def upsert_article(cur, title: str, summary: str, content: str, source_url: str, source_name: str) -> int:
    cur.execute("SELECT id FROM cms_content WHERE source_url=%s LIMIT 1", (source_url,))
    row = cur.fetchone()
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    if row:
        content_id = int(row[0])
        cur.execute(
            """
            UPDATE cms_content
            SET title=%s, summary=%s, content=%s, source_name=%s, author=%s,
                status=1, publish_time=COALESCE(publish_time, %s),
                content_type='VACCINE', is_medical=1, verification_status='IMPORTED'
            WHERE id=%s
            """,
            (title, summary, content, source_name, source_name, now, content_id),
        )
        return content_id
    cur.execute(
        """
        INSERT INTO cms_content
        (category_code,title,summary,content,source_url,source_name,author,view_count,status,
         publish_time,created_by,content_type,is_medical,verification_status)
        VALUES
        ('KNOWLEDGE',%s,%s,%s,%s,%s,%s,0,1,%s,1,'VACCINE',1,'IMPORTED')
        """,
        (title, summary, content, source_url, source_name, source_name, now),
    )
    return int(cur.lastrowid)


def relate(cur, content_id: int, category_code: str, source_type: str = "AUTO") -> None:
    cur.execute(
        """
        INSERT IGNORE INTO content_category_rel (content_id, category_code, source_type)
        VALUES (%s, %s, %s)
        """,
        (content_id, category_code, source_type),
    )


def html_table(headers, rows) -> str:
    head = "".join(f"<th>{h}</th>" for h in headers)
    body = []
    for row in rows:
        body.append("<tr>" + "".join(f"<td>{'' if v is None else v}</td>" for v in row) + "</tr>")
    return f"<table><thead><tr>{head}</tr></thead><tbody>{''.join(body)}</tbody></table>"


def create_vaccine_articles(cur) -> list[int]:
    ids: list[int] = []
    cur.execute(
        """
        SELECT preventable_disease, vaccine_name, route, dose, abbreviation, age_schedule_json,
               source_name, source_file, source_sheet, source_year
        FROM medical_vaccine_schedule
        ORDER BY source_row
        """
    )
    rows = cur.fetchall()
    if rows:
        table = html_table(
            ["可预防疾病", "疫苗名称", "接种途径", "剂量", "缩写", "接种程序"],
            [(r[0], r[1], r[2], r[3], r[4], r[5]) for r in rows],
        )
        source_name = rows[0][6]
        source_file = rows[0][7]
        source_sheet = rows[0][8]
        source_year = rows[0][9]
        content = (
            "<p>以下内容由用户提供的国家免疫规划疫苗表格导入，仅作科普展示，具体接种请以接种门诊和主管部门最新要求为准。</p>"
            f"{table}"
            f"<p class=\"source\">来源：{source_name}；文件：{source_file}；工作表：{source_sheet}；年份：{source_year or ''}</p>"
        )
        ids.append(upsert_article(
            cur,
            "国家免疫规划儿童免疫程序表",
            f"根据导入表格整理，共 {len(rows)} 条国家免疫规划疫苗接种程序记录。",
            content,
            "external-import://vaccine-schedule-2021",
            source_name,
        ))

    cur.execute(
        """
        SELECT vaccine_name, hiv_infected_symptomatic, hiv_infected_asymptomatic,
               hiv_unknown_symptomatic, hiv_unknown_asymptomatic, hiv_uninfected,
               source_name, source_file, source_sheet, source_year
        FROM medical_vaccine_hiv_guidance
        ORDER BY source_row
        """
    )
    rows = cur.fetchall()
    if rows:
        table = html_table(
            ["疫苗", "HIV感染有症状", "HIV感染无症状", "HIV状况不详有症状", "HIV状况不详无症状", "HIV未感染"],
            [(r[0], r[1], r[2], r[3], r[4], r[5]) for r in rows],
        )
        source_name = rows[0][6]
        source_file = rows[0][7]
        source_sheet = rows[0][8]
        source_year = rows[0][9]
        content = (
            "<p>以下内容由用户提供的 HIV 感染母亲所生儿童疫苗接种建议表导入，仅作科普展示，具体接种请由专业机构评估。</p>"
            f"{table}"
            f"<p class=\"source\">来源：{source_name}；文件：{source_file}；工作表：{source_sheet}；年份：{source_year or ''}</p>"
        )
        ids.append(upsert_article(
            cur,
            "HIV感染母亲所生儿童疫苗接种建议",
            f"根据导入表格整理，共 {len(rows)} 条 HIV 感染母亲所生儿童疫苗接种建议记录。",
            content,
            "external-import://vaccine-hiv-guidance-2021",
            source_name,
        ))
    return ids


def repair_relations(cur) -> None:
    rules = [
        ("DISEASE", "author LIKE '%疾病知识%' OR source_url LIKE 'icd10://%'"),
        ("MEDICAL_TERM", "source_url LIKE 'icd10://%'"),
        ("DRUG", "author LIKE '%药品监督管理局%' OR title LIKE '%药品说明%' OR title LIKE '%说明书%'"),
        ("VACCINE", "title LIKE '%疫苗%' OR title LIKE '%接种%'"),
        ("EPIDEMIC", "author LIKE '%传染病防控%' OR title LIKE '%鼠疫%' OR title LIKE '%流感%' OR title LIKE '%传染病%'"),
        ("HEALTH_SCIENCE", "author LIKE '%环境健康%' OR author LIKE '%放射卫生%' OR author LIKE '%健康科普%' OR author LIKE '%人民网%'"),
    ]
    for code, where_sql in rules:
        cur.execute(
            f"""
            INSERT IGNORE INTO content_category_rel (content_id, category_code, source_type)
            SELECT id, '{code}', 'AUTO'
            FROM cms_content
            WHERE category_code='KNOWLEDGE' AND status=1 AND ({where_sql})
            """
        )


def main() -> int:
    conn = connect()
    try:
        with conn.cursor() as cur:
            vaccine_ids = create_vaccine_articles(cur)
            for content_id in vaccine_ids:
                relate(cur, content_id, "VACCINE", "AUTO")
            repair_relations(cur)
            cur.execute(
                "SELECT category_code, COUNT(*) FROM content_category_rel GROUP BY category_code ORDER BY category_code"
            )
            rows = cur.fetchall()
        conn.commit()
        for code, count in rows:
            print(f"{code}: {count}")
        return 0
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
