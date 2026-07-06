# -*- coding: utf-8 -*-
"""Import Shanghai open data CSV files into JSON for portal."""
import csv
import hashlib
import json
import os
import re
import shutil
import sys
from pathlib import Path

SOURCE = "上海市公共数据开放平台"
SOURCE_URL = "https://data.sh.gov.cn"
ATTRIBUTION = "来源：上海市公共数据开放平台"

# Explicit file list (user provided)
FILE_SPECS = [
    ("sh-01", "宝山区区级医院", "医疗机构", "宝山区", r"d:\宝山区区级医院.csv"),
    ("sh-02", "青浦区康复服务机构", "康复服务", "青浦区", r"d:\青浦区2024年康复服务机构基本信息.csv"),
    ("sh-03", "金山区卡介苗门诊点", "预防接种", "金山区", r"d:\金山区卡介苗门诊点-接种时间.csv"),
    ("sh-04", "普陀区社区卫生服务中心", "基层卫生", "普陀区", r"d:\普陀区社区卫生服务中心信息.csv"),
    ("sh-05", "闵行区动物诊疗机构", "动物诊疗", "闵行区", r"d:\闵行区动物诊疗机构名单2021V2.0.csv"),
    ("sh-06", "虹口区医院信息", "医疗机构", "虹口区", r"d:\虹口区医院信息250922.csv"),
    ("sh-07", "静安区母婴保健技术服务点(2025)", "母婴保健", "静安区", r"d:\2504_静安区母婴保健技术服务点一览表.csv"),
    ("sh-08", "静安区母婴保健技术服务点(2024)", "母婴保健", "静安区", r"d:\2409_静安区母婴保健技术服务点一览表.csv"),
    ("sh-09", "静安区家庭病床管理(2024)", "家庭病床", "静安区", r"d:\2409_静安区家庭病床管理.csv"),
    ("sh-10", "静安区家庭病床管理", "家庭病床", "静安区", r"d:\静安区家庭病床管理.csv"),
    ("sh-11", "家庭病床统计", "家庭病床", "上海市", r"d:\家庭病床.csv"),
    ("sh-12", "静安区社区健身苑", "健康促进", "静安区", r"d:\2509_静安区街道社区健身苑.csv"),
    ("sh-13", "静安区门诊预防接种机构", "预防接种", "静安区", r"d:\2509_静安区门诊预防接种机构.csv"),
    ("sh-14", "静安区卫生健康事业单位", "机构信息", "静安区", r"d:\2504_静安区卫生健康系统下属事业单位信息2023.csv"),
    ("sh-15", "静安区艾滋病自愿咨询检测点", "公共卫生", "静安区", r"d:\2504_静安区免费艾滋病自愿咨询检测点一览表.csv"),
    ("sh-16", "静安区从业人员健康检查机构", "职业健康", "静安区", r"d:\2509_静安区从业人员预防性健康检查机构.csv"),
    ("sh-17", "静安区体质指导中心与健康驿站", "健康促进", "静安区", r"d:\2504_静安区体质指导中心与健康驿站一览表.csv"),
    ("sh-18", "静安区孕产妇建卡管理", "妇幼保健", "静安区", r"d:\2409_静安区孕产妇建卡管理.csv"),
    ("sh-19", "静安区二级医院信息", "医疗机构", "静安区", r"d:\2504_静安区二级医院信息一览表.csv"),
    ("sh-20", "学生体质健康监测中心", "学生健康", "上海市", r"d:\学生体质健康监测中心名单.csv"),
    ("sh-21", "金山区重点医疗指标(男)", "医疗指标", "金山区", r"d:\金山区重点医疗指标统计-分年龄段（男）.csv"),
]

ENCODINGS = ("utf-8-sig", "utf-8", "gbk", "gb18030")


def read_csv_rows(path: str) -> tuple[list[str], list[list[str]]]:
    last_err = None
    for enc in ENCODINGS:
        try:
            with open(path, "r", encoding=enc, newline="") as f:
                reader = csv.reader(f)
                rows = [row for row in reader if any(cell.strip() for cell in row)]
            if not rows:
                continue
            headers = [h.strip() for h in rows[0]]
            data = [[cell.strip() for cell in row] for row in rows[1:]]
            return headers, data
        except Exception as e:
            last_err = e
    raise RuntimeError(f"Cannot read {path}: {last_err}")


def normalize_cell(value: str) -> str:
    if not value:
        return value
    # scientific notation phone numbers -> plain string without losing digits badly
    if re.match(r"^\d+\.?\d*[Ee][+-]?\d+$", value):
        try:
            f = float(value)
            if f > 1e7:
                return str(int(f))
        except ValueError:
            pass
    return value


def row_to_record(headers: list[str], row: list[str]) -> dict:
    record = {}
    for i, h in enumerate(headers):
        if not h:
            h = f"col_{i}"
        val = normalize_cell(row[i] if i < len(row) else "")
        record[h] = val
    return record


def detect_type(headers: list[str], rows: list[list[str]]) -> str:
    if len(headers) <= 3 and len(rows) <= 5:
        numeric_cols = sum(
            1 for h in headers[1:]
            if rows and rows[0] and len(rows[0]) > headers.index(h)
            and re.match(r"^-?\d+(\.\d+)?$", rows[0][headers.index(h)] or "")
        )
        if numeric_cols >= 1:
            return "stats"
    return "table"


def stats_from_table(headers: list[str], rows: list[list[str]]) -> list[dict]:
    indicators = []
    label_col = headers[0]
    for row in rows:
        if not row:
            continue
        name = row[0].strip()
        if not name:
            continue
        values = {}
        for i, h in enumerate(headers[1:], start=1):
            if i >= len(row):
                continue
            raw = row[i].strip()
            if re.match(r"^-?\d+(\.\d+)?$", raw):
                values[h] = float(raw)
        if values:
            indicators.append({"name": name, "values": values})
    return indicators


def file_hash(path: str) -> str:
    h = hashlib.md5()
    with open(path, "rb") as f:
        h.update(f.read())
    return h.hexdigest()[:12]


def main():
    platform_root = Path(__file__).resolve().parents[1]
    out_dir = platform_root / "data" / "open-data" / "shanghai"
    out_dir.mkdir(parents=True, exist_ok=True)

    datasets = []
    seen_hashes = set()

    for ds_id, title, category, district, src_path in FILE_SPECS:
        if not os.path.exists(src_path):
            print("SKIP missing:", src_path)
            continue
        h = file_hash(src_path)
        if h in seen_hashes:
            print("SKIP duplicate content:", os.path.basename(src_path))
            continue
        seen_hashes.add(h)

        safe_name = re.sub(r"[^\w\u4e00-\u9fff.-]+", "_", os.path.basename(src_path))
        dst = out_dir / safe_name
        shutil.copy2(src_path, dst)

        headers, rows = read_csv_rows(src_path)
        records = [row_to_record(headers, r) for r in rows]
        dtype = detect_type(headers, rows)

        ds = {
            "id": ds_id,
            "title": title,
            "category": category,
            "district": district,
            "source": SOURCE,
            "sourceUrl": SOURCE_URL,
            "attribution": ATTRIBUTION,
            "openType": "无条件开放",
            "fileName": os.path.basename(src_path),
            "type": dtype,
            "columns": headers,
            "rowCount": len(records),
            "previewLimit": 50,
        }

        if dtype == "stats":
            ds["indicators"] = stats_from_table(headers, rows)
        else:
            ds["rows"] = records[:200]  # cap for JSON size; full CSV kept on disk

        datasets.append(ds)
        print("OK", ds_id, title, dtype, len(records), "rows")

    payload = {
        "source": SOURCE,
        "sourceUrl": SOURCE_URL,
        "attribution": ATTRIBUTION,
        "datasets": datasets,
    }

    json_paths = [
        out_dir / "shanghai-health-open-data.json",
        platform_root / "health-portal-backend" / "src" / "main" / "resources" / "data" / "shanghai-health-open-data.json",
    ]
    for p in json_paths:
        p.parent.mkdir(parents=True, exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print("WROTE", p)

    print("TOTAL:", len(datasets))


if __name__ == "__main__":
    main()
