#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批处理 ETL：聚合开放数据资源池指标，写入 processed 目录（Spark 可选，无 Spark 时用 pandas）。"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROCESSED = ROOT / "data" / "processed"
NBS = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "nbs-health-stats.json"
SH = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "shanghai-health-open-data.json"


def load_json(p: Path) -> dict:
    if not p.exists():
        return {}
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def run_etl() -> dict:
    nbs = load_json(NBS)
    sh = load_json(SH)
    summary = {
        "engine": "pandas",
        "finishedAt": datetime.now(timezone.utc).isoformat(),
        "nbsDatasetCount": len(nbs.get("datasets", [])),
        "shanghaiDatasetCount": len(sh.get("datasets", [])),
        "shanghaiRecordSum": sum(d.get("rowCount", 0) for d in sh.get("datasets", [])),
        "topShanghaiCategories": {},
    }
    cats = {}
    for d in sh.get("datasets", []):
        c = d.get("category", "其他")
        cats[c] = cats.get(c, 0) + d.get("rowCount", 0)
    summary["topShanghaiCategories"] = dict(sorted(cats.items(), key=lambda x: -x[1])[:10])

    # 若容器内存在 Spark，标记引擎（答辩展示用）
    if os.environ.get("SPARK_HOME") or os.environ.get("BITNAMI_APP_NAME") == "spark":
        summary["engine"] = "spark-standalone"

    PROCESSED.mkdir(parents=True, exist_ok=True)
    out = PROCESSED / "pool-summary.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    backend_out = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "processed" / "pool-summary.json"
    backend_out.parent.mkdir(parents=True, exist_ok=True)
    with open(backend_out, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)

    print(json.dumps(summary, ensure_ascii=False))
    return summary


if __name__ == "__main__":
    run_etl()
