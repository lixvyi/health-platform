"""
热词共现计算脚本
用法: python compute_cooccurrence.py <year>
输出: JSON 到 stdout，包含 { nodes: [{name, symbolSize, frequency}], edges: [{source, target, weight}] }

热词来源: scripts/policies/static/hotwords_data.json 中的键
"""

import glob
import json
import os
import pandas as pd
import sys

# 强制 stdout 使用 UTF-8，避免 Windows 下 GBK 编码导致乱码
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(os.path.dirname(SCRIPT_DIR), "data", "policies")

# 从 hotwords_data.json 加载热词列表
HOTWORDS_JSON = os.path.join(SCRIPT_DIR, "policies", "static", "hotwords_data.json")


def load_hotwords():
    if not os.path.exists(HOTWORDS_JSON):
        # 尝试备用路径
        alt = os.path.join(os.getcwd(), "scripts", "policies", "static", "hotwords_data.json")
        if os.path.exists(alt):
            path = alt
        else:
            # 使用内置默认值（防止找不到文件时完全失效）
            return ["疫情", "肺炎", "新冠", "医疗", "服务", "药品", "管理", "患者", "医院", "防控"]
    else:
        path = HOTWORDS_JSON

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        words = list(data.keys())
        if words:
            return words
    except Exception:
        pass

    # fallback
    return ["疫情", "肺炎", "新冠", "医疗", "服务", "药品", "管理", "患者", "医院", "防控"]


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"nodes": [], "edges": []}))
        return

    try:
        year = int(sys.argv[1])
    except ValueError:
        print(json.dumps({"nodes": [], "edges": []}))
        return

    hotwords = load_hotwords()

    csv_files = glob.glob(os.path.join(DATA_DIR, "*.csv"))
    if not csv_files:
        csv_files = glob.glob(os.path.join(os.getcwd(), "data", "policies", "*.csv"))

    word_freq = {}
    cooc = {}

    for csv_file in csv_files:
        try:
            df = pd.read_csv(csv_file, encoding="utf-8", dtype=str, keep_default_na=False)
        except Exception:
            try:
                df = pd.read_csv(csv_file, encoding="gbk", dtype=str, keep_default_na=False)
            except Exception:
                continue

        title_col = "title" if "title" in df.columns else "标题" if "标题" in df.columns else None
        date_col = "date" if "date" in df.columns else "日期" if "日期" in df.columns else None
        content_col = "content" if "content" in df.columns else "正文" if "正文" in df.columns else None

        if title_col is None or date_col is None:
            continue

        df["_year"] = df[date_col].astype(str).str.extract(r"(\d{4})")
        df["_year"] = pd.to_numeric(df["_year"], errors="coerce")

        yr_df = df[df["_year"] == year]
        if yr_df.empty:
            continue

        for _, row in yr_df.iterrows():
            text = row.get(title_col, "")
            if content_col and content_col in row:
                text += " " + row.get(content_col, "")

            text_lower = text.lower()

            matched = []
            for hw in hotwords:
                if hw.lower() in text_lower:
                    matched.append(hw)

            if len(matched) < 2:
                continue

            for w in matched:
                word_freq[w] = word_freq.get(w, 0) + 1

            for i in range(len(matched)):
                for j in range(i + 1, len(matched)):
                    a, b = matched[i], matched[j]
                    if a not in cooc:
                        cooc[a] = {}
                    cooc[a][b] = cooc[a].get(b, 0) + 1
                    if b not in cooc:
                        cooc[b] = {}
                    cooc[b][a] = cooc[b].get(a, 0) + 1

    valid_words = {w for w, freq in word_freq.items() if freq >= 2}

    nodes = []
    for w in valid_words:
        freq = word_freq[w]
        symbol_size = max(10, min(50, freq * 3))
        nodes.append({"name": w, "symbolSize": symbol_size, "frequency": freq})

    edges = []
    seen = set()
    for a in valid_words:
        if a not in cooc:
            continue
        for b, weight in cooc[a].items():
            if b not in valid_words:
                continue
            key = tuple(sorted([a, b]))
            if key in seen:
                continue
            seen.add(key)
            if weight >= 2:
                edges.append({"source": a, "target": b, "weight": weight})

    result = {"nodes": nodes, "edges": edges}
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
