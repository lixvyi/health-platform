# -*- coding: utf-8 -*-
"""
合规互联网公开数据采集脚本（非政务内网、非 API 密钥调用）

采集源（当前环境可访问）：
1. 中国政府网数据栏目 - HTML 公开列表
2. 国家统计局官网 - 卫生相关公开搜索结果页

开放数据文件：校验并同步 data/open-data/ 下已依法下载的 CSV/Excel（上海+国家统计局）
"""
from __future__ import annotations

import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "scripts" / "crawl-sources.json"
CRAWL_OUT = ROOT / "data" / "crawl" / "internet"
LOG_OUT = ROOT / "data" / "crawl" / "last-run.json"


def load_config() -> dict:
    with open(CONFIG, encoding="utf-8") as f:
        return json.load(f)


def polite_get(url: str, headers: dict, delay: float) -> requests.Response:
    time.sleep(delay)
    return requests.get(url, headers=headers, timeout=30)


def match_keywords(text: str, keywords: list[str]) -> bool:
    if not keywords:
        return True
    return any(k in text for k in keywords)


def crawl_html_list(source: dict, headers: dict, delay: float) -> dict:
    url = source["url"]
    resp = polite_get(url, headers, delay)
    resp.encoding = resp.apparent_encoding or "utf-8"
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        if not title or len(title) < 4 or len(title) > 120:
            continue
        if not match_keywords(title, source.get("keywords", [])):
            continue
        if href.startswith("javascript") or href.startswith("#") or href.strip() in {"", "./", "../"}:
            continue
        full = urljoin(url, href)
        if "stats.gov.cn" in url and "/sj/" in url:
            if "stats.gov.cn" not in full and not full.startswith("/"):
                continue
            if full.rstrip("/") == url.rstrip("/"):
                continue
        if full in seen:
            continue
        seen.add(full)
        items.append({
            "title": title,
            "url": full,
            "sourceId": source["id"],
            "sourceName": source["name"],
            "attribution": source.get("attribution", ""),
            "collectedAt": datetime.now(timezone.utc).isoformat(),
        })
    return {
        "sourceId": source["id"],
        "sourceName": source["name"],
        "status": "success" if items else "partial",
        "recordCount": len(items),
        "items": items[:80],
    }


def crawl_html_search(source: dict, headers: dict, delay: float) -> dict:
    url = source["url"]
    resp = polite_get(url, headers, delay)
    resp.encoding = resp.apparent_encoding or "utf-8"
    soup = BeautifulSoup(resp.text, "html.parser")
    items = []
    seen = set()
    for a in soup.select("a[href]"):
        title = a.get_text(strip=True)
        href = a.get("href", "")
        if not title or len(title) < 4:
            continue
        if "stats.gov.cn" not in href and not href.startswith("/"):
            continue
        full = urljoin(url, href)
        if full in seen or "javascript" in full:
            continue
        seen.add(full)
        items.append({
            "title": title,
            "url": full,
            "sourceId": source["id"],
            "sourceName": source["name"],
            "attribution": source.get("attribution", ""),
            "collectedAt": datetime.now(timezone.utc).isoformat(),
        })
    return {
        "sourceId": source["id"],
        "sourceName": source["name"],
        "status": "success" if items else "partial",
        "recordCount": len(items),
        "items": items[:80],
    }


def sync_local_open_data(entry: dict) -> dict:
    rel = entry.get("localDir", "")
    dir_path = ROOT / rel
    files = []
    if dir_path.exists():
        for p in sorted(dir_path.glob("*")):
            if p.suffix.lower() in {".csv", ".xlsx", ".json"} and p.is_file():
                files.append({
                    "fileName": p.name,
                    "sizeBytes": p.stat().st_size,
                    "modifiedAt": datetime.fromtimestamp(p.stat().st_mtime, tz=timezone.utc).isoformat(),
                })
    return {
        "sourceId": entry["id"],
        "sourceName": entry["name"],
        "status": "success" if files else "partial",
        "recordCount": len(files),
        "files": files,
        "note": "开放数据文件来自官网依法下载，本地同步校验（非政务内网爬取）",
    }


def run_news_sync() -> dict:
    import subprocess
    script = ROOT / "scripts" / "sync_news_feed.py"
    if not script.exists():
        return {"status": "skipped"}
    try:
        r = subprocess.run(
            [sys.executable, str(script)], cwd=str(ROOT),
            capture_output=True, text=True, timeout=120,
        )
        print(r.stdout)
        return {"status": "ok" if r.returncode == 0 else "fail", "output": (r.stdout or "")[-400:]}
    except Exception as e:
        return {"status": "fail", "error": str(e)}


def run_cms_policy_import() -> dict:
    import subprocess
    script = ROOT / "scripts" / "crawl_policy_cms.py"
    if not script.exists():
        return {"status": "skipped", "reason": "script missing"}
    try:
        r = subprocess.run(
            [sys.executable, str(script)], cwd=str(ROOT),
            capture_output=True, text=True, timeout=600,
        )
        print(r.stdout)
        if r.stderr:
            print(r.stderr, file=sys.stderr)
        return {
            "status": "ok" if r.returncode == 0 else "fail",
            "returncode": r.returncode,
            "output": (r.stdout or "")[-800:],
        }
    except Exception as e:
        return {"status": "fail", "error": str(e)}


def run_import_pipeline() -> list[str]:
    logs = []
    import subprocess

    scripts = [
        [sys.executable, str(ROOT / "scripts" / "import_nbs_excel.py"), "d:/"],
        [sys.executable, str(ROOT / "scripts" / "import_shanghai_csv.py")],
    ]
    for cmd in scripts:
        try:
            if "import_nbs" in cmd[1] and not any(Path("d:/").glob("年度数据*.xlsx")):
                logs.append(f"SKIP {cmd[1]} (no xlsx on d:/)")
                continue
            r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True, timeout=120)
            logs.append(f"{'OK' if r.returncode == 0 else 'FAIL'} {Path(cmd[1]).name}: {r.stdout[-200:]}")
        except Exception as e:
            logs.append(f"FAIL {Path(cmd[1]).name}: {e}")
    return logs


def main():
    cfg = load_config()
    delay = float(cfg.get("rateLimitSeconds", 3))
    headers = {"User-Agent": cfg.get("userAgent", "HealthPortalBot/1.0"), "Accept-Language": "zh-CN,zh;q=0.9"}

    CRAWL_OUT.mkdir(parents=True, exist_ok=True)
    results = {"startedAt": datetime.now(timezone.utc).isoformat(), "sources": [], "imports": []}

    for src in cfg.get("sources", []):
        try:
            if src["type"] == "html_list":
                res = crawl_html_list(src, headers, delay)
            elif src["type"] == "html_search":
                res = crawl_html_search(src, headers, delay)
            else:
                res = {"sourceId": src["id"], "status": "skipped", "recordCount": 0}
            out_file = CRAWL_OUT / f"{src['id']}.json"
            with open(out_file, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)
            backend_file = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "crawl" / f"{src['id']}.json"
            backend_file.parent.mkdir(parents=True, exist_ok=True)
            with open(backend_file, "w", encoding="utf-8") as f:
                json.dump(res, f, ensure_ascii=False, indent=2)
            results["sources"].append(res)
            print(f"CRAWL {src['id']} -> {res.get('recordCount', 0)} items")
        except Exception as e:
            err = {"sourceId": src["id"], "status": "failed", "recordCount": 0, "error": str(e)}
            results["sources"].append(err)
            print(f"FAIL {src['id']}: {e}")

    for entry in cfg.get("openDownloads", []):
        try:
            res = sync_local_open_data(entry)
            results["sources"].append(res)
            print(f"SYNC {entry['id']} -> {res.get('recordCount', 0)} files")
        except Exception as e:
            results["sources"].append({"sourceId": entry["id"], "status": "failed", "error": str(e)})

    results["imports"] = run_import_pipeline()
    results["finishedAt"] = datetime.now(timezone.utc).isoformat()

    cms_log = run_cms_policy_import()
    results["cmsImport"] = cms_log

    news_log = run_news_sync()
    results["newsSync"] = news_log

    with open(LOG_OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    backend_log = ROOT / "health-portal-backend" / "src" / "main" / "resources" / "data" / "crawl" / "last-run.json"
    with open(backend_log, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print("DONE", LOG_OUT)


if __name__ == "__main__":
    main()
