"""
政策文件预处理：批量处理同一文件夹下所有CSV文件，分词、自动生成热词、计算热词逐年占比，输出 hotwords_data.json
"""
import glob
import jieba
import jieba.analyse
import jieba.posseg as pseg
import json
import numpy as np
import os
import pandas as pd
import re

# ---------- 配置 ----------
CSV_FOLDER = '.'  # CSV文件所在文件夹，默认为当前目录
STOPWORDS_FILE = 'stopwords.txt'
OUTPUT_JSON = 'static/hotwords_data.json'
TOP_K = 10  # 每年选取的 TF-IDF top 词数
FINAL_WORD_LIMIT = 25  # 最终保留的热词数量上限
TOP_KEYWORDS_PER_DOC = 10  # 每篇提取的关键词个数
allow_pos = ('n', 'nr', 'ns', 'nt', 'nz', 'v', 'vn')  # 只保留名词和动词类

# ---------- 加载词典和停用词 ----------
with open(STOPWORDS_FILE, encoding='utf-8') as f:
    stopwords = set(f.read().split())
jieba.analyse.set_stop_words(STOPWORDS_FILE)


# ---------- 辅助函数 ----------
def clean_and_cut(text):
    """文本清洗与分词"""
    # 修正：正确的 Unicode 中文字符范围 \u4e00-\u9fa5
    text = re.sub(r'[^\u4e00-\u9fa5]', '', str(text))
    words = jieba.cut(text)
    return [w for w in words if w not in stopwords and len(w) > 1]


def extract_keywords_with_pos(text, topK=10, allowed_pos={'n', 'nr', 'ns', 'nt', 'nz', 'v', 'vn'}):
    """提取关键词并过滤词性"""
    raw_keywords = jieba.analyse.extract_tags(str(text), topK=topK * 2)
    words_with_pos = list(pseg.cut(str(text)))
    pos_map = {word: flag for word, flag in words_with_pos}
    filtered = [w for w in raw_keywords if pos_map.get(w) in allowed_pos and w not in stopwords]
    return filtered[:topK]


# ---------- 查找所有CSV文件 ----------
csv_files = glob.glob(os.path.join(CSV_FOLDER, '*.csv'))
print(f"找到 {len(csv_files)} 个CSV文件：")
for f in csv_files:
    print(f"  - {os.path.basename(f)}")

if not csv_files:
    print("错误：未找到任何CSV文件！")
    exit(1)

# ---------- 合并所有CSV数据 ----------
all_dfs = []
for csv_file in csv_files:
    try:
        df = pd.read_csv(csv_file, encoding='utf-8')
        # 提取年份（处理 "2026/6/30" 格式）
        if 'date' in df.columns:
            df['year'] = df['date'].str.extract(r'(\d{4})').astype(float).astype('Int64')
        elif '发布日期' in df.columns:
            df['year'] = df['发布日期'].str.extract(r'(\d{4})').astype(float).astype('Int64')
        else:
            print(f"警告：{os.path.basename(csv_file)} 缺少日期列，跳过")
            continue

        df = df.dropna(subset=['year'])
        df['year'] = df['year'].astype(int)
        all_dfs.append(df)
        print(f"  ✓ {os.path.basename(csv_file)}: {len(df)} 条记录，年份 {df['year'].min()}-{df['year'].max()}")
    except Exception as e:
        print(f"  ✗ 处理 {os.path.basename(csv_file)} 时出错：{e}")

if not all_dfs:
    print("错误：没有成功读取任何CSV数据！")
    exit(1)

# 合并所有数据
df = pd.concat(all_dfs, ignore_index=True)
print(f"\n合并后总记录数：{len(df)} 条")
print(f"年份范围：{df['year'].min()} - {df['year'].max()}")

# ---------- 文本处理 ----------
# 确定内容列名
content_col = 'content' if 'content' in df.columns else '正文' if '正文' in df.columns else None
if content_col is None:
    print("错误：未找到内容列（'content' 或 '正文'）")
    exit(1)

print(f"使用内容列：{content_col}")

# 分词
df['words'] = df[content_col].apply(clean_and_cut)

# ---------- 自动热词发现 ----------
# 提取关键词
df['keywords'] = df[content_col].apply(
    lambda text: extract_keywords_with_pos(text, topK=TOP_KEYWORDS_PER_DOC, allowed_pos=allow_pos)
)

# 收集候选词（再次过滤停用词）
candidate_words = set()
for kw_list in df['keywords']:
    for w in kw_list:
        if len(w) >= 2 and not w.isdigit() and w not in stopwords:
            candidate_words.add(w)

print(f"候选词数量：{len(candidate_words)}")

# 构建年份-关键词文档占比矩阵
years = sorted(df['year'].unique())
year_word_prop = {}
for yr in years:
    yr_df = df[df['year'] == yr]
    total_docs = len(yr_df)
    prop_dict = {}
    for w in candidate_words:
        count = yr_df['keywords'].apply(lambda lst: w in lst).sum()
        prop_dict[w] = count / total_docs if total_docs > 0 else 0
    year_word_prop[yr] = prop_dict

# 计算统计量
word_stats = []
for w in candidate_words:
    values = [year_word_prop[yr][w] for yr in years]
    arr = np.array(values)
    mean = arr.mean()
    std = arr.std()
    cv = std / mean if mean > 0 else 0
    range_val = arr.max() - arr.min()
    total_years = sum(1 for v in values if v > 0)
    word_stats.append({
        'word': w,
        'mean': mean,
        'cv': cv,
        'range': range_val,
        'total_years': total_years
    })

# 筛选热词
stats_df = pd.DataFrame(word_stats)
filtered = stats_df[(stats_df['total_years'] >= 2) & (stats_df['cv'] >= 0.3)]
filtered = filtered.sort_values('range', ascending=False)
final_words = filtered['word'].head(FINAL_WORD_LIMIT).tolist()

# 最终去重（再次确保无停用词）
final_words = [w for w in final_words if w not in stopwords]

print(f"\n自动生成的热词（共{len(final_words)}个）：")
print(final_words)

# ---------- 计算逐年占比 ----------
result = {}
for word in final_words:
    yearly_prop = []
    for yr in years:
        yr_df = df[df['year'] == yr]
        if len(yr_df) == 0:
            yearly_prop.append({'year': int(yr), 'value': 0})
        else:
            count = yr_df['keywords'].apply(lambda lst: word in lst).sum()
            prop = round(count / len(yr_df), 4)
            yearly_prop.append({'year': int(yr), 'value': prop})
    result[word] = yearly_prop

# ---------- 输出JSON ----------
# 确保输出目录存在
os.makedirs(os.path.dirname(OUTPUT_JSON), exist_ok=True)

with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print(f"\n预处理完成，结果保存至 {OUTPUT_JSON}")
print(f"共处理 {len(csv_files)} 个CSV文件，合并 {len(df)} 条记录，生成 {len(final_words)} 个热词")
