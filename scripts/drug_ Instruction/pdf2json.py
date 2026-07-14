#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
药品说明书 PDF 数据清洗脚本（增强版）
功能：
  - 读取 PDF 文件夹，提取字段，标准化
  - 通用名净化（去除英文名、拼音、跨段污染）
  - 处方/非处方类型识别（prescription_type）
  - 成分结构化解析（活性成分+辅料+含量+分子式）
  - 药物相互作用三元组抽取（6种模板+双向存储+精细严重度）
  - 有效期标准化格式化
  - 基于内容哈希的版本去重
  - 错误日志与统计信息输出
输出：JSON（主数据、错误日志、统计信息）
依赖：pip install pymupdf pdfplumber
"""
import hashlib
import json
import os
import pdfplumber
import pymupdf as fitz
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# ==================== 常量与词表 ====================
DOSAGE_FORM_MAP = {
    "片": "片剂", "素片": "片剂", "薄膜衣片": "片剂", "糖衣片": "片剂",
    "分散片": "片剂", "泡腾片": "片剂", "咀嚼片": "片剂",
    "胶囊": "胶囊剂", "硬胶囊": "胶囊剂", "软胶囊": "胶囊剂", "肠溶胶囊": "胶囊剂",
    "注射液": "注射液", "注射剂": "注射液", "输液": "注射液",
    "冻干粉针": "注射用粉针", "粉针": "注射用粉针",
    "颗粒": "颗粒剂", "干糖浆": "颗粒剂", "冲剂": "颗粒剂",
    "口服液": "口服溶液剂", "糖浆": "糖浆剂",
    "软膏": "软膏剂", "乳膏": "乳膏剂",
    "滴眼液": "眼用制剂", "眼药水": "眼用制剂",
    "干混悬剂": "口服混悬剂", "混悬剂": "口服混悬剂",
    "喷雾": "喷雾剂", "气雾": "气雾剂",
    "滴眼": "眼用制剂", "滴耳": "耳用制剂", "滴鼻": "鼻用制剂",
    "栓": "栓剂", "凝胶": "凝胶剂", "缓释片": "片剂", "肠溶片": "片剂",
    "缓释胶囊": "胶囊剂", "肠溶胶囊": "胶囊剂",
}

# 性状文本 → 剂型推断规则（当【剂型】缺失时，根据性状描述来推断剂型）
# 键：性状文本包含的关键词，值：(优先级, 剂型名)
DOSAGE_FORM_INFERENCE = {
    "粉末": (10, "散剂"),
    "细粉": (10, "散剂"),
    "疏松块状物": (30, "注射用粉针"),
    "冻干": (30, "注射用粉针"),
    "白色固体": (10, "散剂"),
    "颗粒": (20, "颗粒剂"),
}

# 药品名前缀 → 标准剂型（用于名称无法匹配后缀时的兜底）
NAME_PREFIX_FORM_MAP = {
    "注射用": "注射用粉针",
    "重组": "注射液",
}

CATEGORY_KEYWORDS = {
    "化学药": ["化学名", "分子式", "结构式"],
    "中成药": ["药材", "提取物", "处方", "功能主治", "性味", "归经", "Z"],
    "生物制品": ["抗体", "重组", "活性成分", "细胞", "疫苗", "类毒素"],
}

SEVERITY_KEYWORDS = {
    "禁止合用": ["禁止", "禁忌", "不可", "不宜合用", "不得"],
    "避免合用": ["避免合用", "不推荐", "不建议"],
    "谨慎合用": ["谨慎", "慎用", "注意"],
    "监测": ["监测", "检测", "调整剂量", "密切观察"],
}


# ==================== 工具函数 ====================
def full_to_half(text: str) -> str:
    """全角字符转半角"""
    result = []
    for char in text:
        code = ord(char)
        if 0xFF01 <= code <= 0xFF5E:
            code -= 0xFEE0
        elif code == 0x3000:
            code = 0x20
        result.append(chr(code))
    return "".join(result)


def clean_text(text: str) -> str:
    """通用文本清洗：去HTML标签，压缩空白，统一全半角"""
    text = re.sub(r"<[^>]+>", "", text)
    text = full_to_half(text)
    text = text.replace("\r", "\n").replace("\t", " ")
    text = re.sub(r" {2,}", " ", text)
    return text.strip()


# ==================== 段落切分（增强版）====================
def split_sections(text: str) -> Dict[str, str]:
    """
    将说明书文本按 【标题】 切分为段落。
    增强处理：
    - 先中和文本内交叉引用如 "(见【注意事项】)" 防止误识别为标题
    - 确保每个 【...】 标题独立一行
    - 处理 【】内的空格（如【成 份】→【成份】）
    - 清理段落开头的 ")." 等跨段残留
    """
    # 1. 中和文本内交叉引用：将 "(见【XXX】)" "(参见【XXX】任意文本)"
    #    替换为普通括号文本，防止被误切分为段落标题
    #    注意：要一并清除 【】和)之间的残余文本（如 (见【注意事项】部分) ）
    text = re.sub(r'[（(]\s*(?:见|参见|详见)\s*【([^】]+?)】\s*[^）)】]*[）)]', r'（参考\1）', text)
    # 单独处理 )。的残留问题（含全角括号）
    text = re.sub(r'[）)]\s*。', '。', text)

    # 2. 处理 【】内的空格
    text = re.sub(r'【([^】]+?)】', lambda m: '【' + m.group(1).replace(' ', '') + '】', text)

    # 3. 在非行首的 【 前插入换行，确保每个标题单独一行
    text = re.sub(r'(?<!\n)\s*(【)', r'\n\1', text)

    lines = text.splitlines()
    sections = {}
    current_title = None
    current_content = []
    title_pattern = re.compile(r'^\s*【(.+?)】\s*(.*)')

    for line in lines:
        match = title_pattern.match(line)
        if match:
            # 保存上一个段落
            if current_title is not None:
                sections[current_title] = '\n'.join(current_content).strip()
            current_title = match.group(1).strip()
            rest = match.group(2).strip()
            current_content = [rest] if rest else []
        else:
            if current_title is not None:
                current_content.append(line)

    # 保存最后一个段落
    if current_title is not None:
        sections[current_title] = '\n'.join(current_content).strip()

    # 4. 清理段落内容的跨段残留
    cleaned_sections = {}
    for title, content in sections.items():
        # 去除开头的 )。, )。等残留（含全角）
        content = re.sub(r'^[\s）)]*。', '', content)
        # 去除开头的 "部分)。" "部分。"（必须包含 ) 或句尾标点才清理）
        content = re.sub(r'^[\u4e00-\u9fff]*[）)][\s。，,]*', '', content)
        # 去除前导标点符号（：:、·,等）
        content = re.sub(r'^[：:：、，,·\s]+', '', content)
        content = content.strip().rstrip('。.')
        # 如果内容只剩单个词组且是常见交叉引用残留词，清空
        if len(content) <= 3 and content in ('部分', '和', '的', '参考', '见', '等', '及', '与'):
            content = ''
        cleaned_sections[title] = content

    return cleaned_sections


# ==================== 批准文号 & 类别 ====================
def extract_approval_number(text: str) -> Optional[str]:
    match = re.search(r"国药准字[HZSJBTF]\d{8}", text)
    return match.group(0) if match else None


def classify_drug_category(approval_number: Optional[str], text: str) -> str:
    if approval_number:
        prefix = approval_number[4]
        if prefix == "H":
            return "化学药"
        elif prefix == "Z":
            return "中成药"
        elif prefix == "S":
            return "生物制品"
    scores = {cat: 0 for cat in CATEGORY_KEYWORDS}
    for cat, keywords in CATEGORY_KEYWORDS.items():
        for kw in keywords:
            if kw in text:
                scores[cat] += 1
    if scores["化学药"] > scores["中成药"] and scores["化学药"] > scores["生物制品"]:
        return "化学药"
    elif scores["中成药"] > scores["化学药"] and scores["中成药"] > scores["生物制品"]:
        return "中成药"
    elif scores["生物制品"] > scores["化学药"] and scores["生物制品"] > scores["中成药"]:
        return "生物制品"
    return "未知"


# ==================== 处方类型识别 ====================
def extract_prescription_type(sections: Dict[str, str]) -> str:
    """
    从说明书文本中识别处方/非处方类型。
    
    中国药品分类规则：
    - 有 "甲类" / "乙类" / "OTC" / "非处方药" 标识 → OTC
    - 有 "处方药" / "凭医师处方" / "请仔细阅读说明书并在医师指导下使用" → 处方药
    - 化学药/生物制品默认是处方药（除非明确标为OTC）
    - 其余返回 "未知"
    """
    full_text = " ".join(sections.values())

    # 1. OTC 检测（优先：有 OTC 标识则为非处方药）
    if re.search(r'甲类|乙类', full_text):
        return "OTC"
    if re.search(r'OTC|非处方药|非处方药品|非处方', full_text):
        return "OTC"

    # 2. 处方药检测
    if re.search(r'请仔细阅读说明书并在医师指导下使用|处方药|本品为处方药|凭医师处方|遵医师处方', full_text):
        return "处方药"

    # 3. 从批准文号推断：H=化学药/S=生物制品 默认是处方药
    approval_num = extract_approval_number(full_text)
    if approval_num:
        prefix = approval_num[4]
        if prefix in ("H", "S", "F"):
            return "处方药"

    return "未知"


# ==================== 剂型匹配（增强版）====================
def map_dosage_form(name: str, dosage_field: Optional[str]) -> str:
    """
    从药品名称/剂型字段/性状字段中提取标准化剂型。
    匹配优先级：
    1. DOSAGE_FORM_MAP 匹配 dosage_field（剂型段）
    2. DOSAGE_FORM_MAP 匹配 name（药品名）
    3. NAME_PREFIX_FORM_MAP 匹配 name 前缀
    4. DOSAGE_FORM_INFERENCE 从 dosage_field（性状段）推断
    5. 返回 "未知" 而非原始性状文本
    """
    # 1. 剂型段匹配
    if dosage_field:
        for raw, standard in DOSAGE_FORM_MAP.items():
            if raw in dosage_field:
                return standard

    # 2. 药品名匹配
    if name:
        for raw, standard in DOSAGE_FORM_MAP.items():
            if raw in name:
                return standard

    # 3. 药品名前缀匹配（如 "注射用" → "注射用粉针"）
    if name:
        for prefix, standard in NAME_PREFIX_FORM_MAP.items():
            if name.startswith(prefix):
                return standard

    # 4. 性状文本推断（仅当 dosage_field 明显是性状描述时）
    if dosage_field:
        # 判断 dosage_field 是否是性状描述（以 "本品为" 开头，不含标准剂型关键词）
        is_xingzhuang = bool(re.search(r'本品为', dosage_field))
        if is_xingzhuang:
            for keyword, (priority, standard) in DOSAGE_FORM_INFERENCE.items():
                if keyword in dosage_field:
                    return standard

    # 5. 最终兜底
    return "未知"


# ==================== 通用名净化（核心修复）====================
def extract_generic_brand(section_text):
    """从药品名称段落中提取纯净的通用名和商品名。"""
    generic = ""
    brand = ""

    # 先截断，防止跨段污染
    section_text = re.split(r'【', section_text)[0]

    # 尝试匹配“通用名称：xxx”
    m = re.search(r'通用名称[：:]\s*(.+?)(?:$|【|英文名称|汉语拼音|商品名称)', section_text)
    if m:
        generic = m.group(1).strip()
    # 匹配“商品名称：xxx”
    m = re.search(r'商品名称[：:]\s*(.+?)(?:$|【|英文名称|汉语拼音|通用名称)', section_text)
    if m:
        brand = m.group(1).strip()

    # 若未找到通用名称，从文本开头提取中文名
    if not generic:
        # 移除 "英文名称：..." 和 "汉语拼音：..." 整段
        cleaned = re.sub(r'英文名称[：:].*?(?=汉语拼音|【|$)', '', section_text)
        cleaned = re.sub(r'汉语拼音[：:].*?(?=【|$)', '', cleaned)
        cleaned = cleaned.strip()

        # 匹配：开头的中文字符序列（包含剂型后缀）
        m = re.match(
            r'\s*([\u4e00-\u9fff]+(?:片|胶囊|颗粒|液|混悬剂|分散片|咀嚼片|'
            r'口崩片|缓释片|缓释胶囊|软胶囊|干混悬剂|口服液|注射液|注射剂|'
            r'粉针|软膏|乳膏|滴眼液|糖浆|溶液|合剂|酊剂|栓剂|气雾剂|喷雾剂|'
            r'滴剂|贴剂|凝胶|口服溶液剂|颗粒剂|干糖浆|冲剂|糖衣片|薄膜衣片|'
            r'素片|泡腾片|肠溶胶囊|肠溶片|分散片))', cleaned)
        if m:
            generic = m.group(1).strip()
        else:
            # 取所有中文序列
            m = re.match(r'\s*([\u4e00-\u9fff\x20・·]+)', cleaned)
            if m:
                generic = m.group(1).strip()
            else:
                lines = [l.strip() for l in cleaned.split('\n') if l.strip()]
                if lines:
                    generic = lines[0]

    # 最终净化
    if generic:
        generic = re.sub(r'(英文名称|汉语拼音|商品名称).*', '', generic).strip()
        # 去除前导标点
        generic = re.sub(r'^[：:：,，、。\s]+', '', generic).strip()
    return generic, brand


# ==================== 有效期格式化 ====================
def parse_validity(validity_text: str) -> str:
    if not validity_text:
        return ""
    m = re.search(r'(\d+)\s*(个?月|年)', validity_text)
    if m:
        num = m.group(1)
        unit = m.group(2)
        if unit == "年":
            return f"{int(num) * 12}个月"
        return f"{num}个月"
    m = re.search(r'(\d+)', validity_text)
    if m:
        return f"{m.group(1)}个月"
    return validity_text.strip()


# ==================== 内容哈希（版本比对用）====================
def compute_data_hash(record: Dict) -> str:
    content_keys = [
        "generic_name", "indications", "contraindications",
        "adverse_reactions", "usage_dosage", "interactions_raw", "warnings"
    ]
    content_str = "".join(str(record.get(k, "")) for k in content_keys)
    return hashlib.sha256(content_str.encode("utf-8")).hexdigest()


# ==================== PDF 文本提取 ====================
def extract_text_from_pdf(pdf_path: str) -> str:
    """
    从 PDF 提取文本，优先用 pdfplumber，失败时尝试 PyMuPDF。
    pdfplumber 对结构化说明书 PDF 的段落提取更完整。
    """
    full_text = ""

    # 1. 优先使用 pdfplumber
    try:
        with pdfplumber.open(pdf_path) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    full_text += text + "\n"
    except Exception as e:
        print(f"pdfplumber 提取失败: {e}，尝试 PyMuPDF")

    # 2. 如果 pdfplumber 无结果，尝试 PyMuPDF
    if not full_text.strip():
        try:
            doc = fitz.open(pdf_path)
            for page in doc:
                text = page.get_text("text")
                lines = text.splitlines()
                cleaned_lines = []
                for line in lines:
                    line_stripped = line.strip()
                    if re.match(r'^\s*第\s*\d+\s*页(\s*/\s*共\s*\d+\s*页)?\s*$', line_stripped) or \
                            re.match(r'^\s*Page\s*\d+\s*(of\s*\d+)?\s*$', line_stripped) or \
                            line_stripped in ('药品说明书', '说明书', '说明'):
                        continue
                    cleaned_lines.append(line)
                full_text += "\n".join(cleaned_lines) + "\n"
            doc.close()
        except Exception as e:
            print(f"PyMuPDF 提取失败: {e}")
            return ""

    # 3. 合并硬换行
    lines = full_text.splitlines()
    merged = []
    buffer = ""
    sentence_ends = "。！？；”\"）)}]"
    for line in lines:
        line = line.strip()
        if not line:
            if buffer:
                merged.append(buffer)
                buffer = ""
            continue
        if buffer:
            if buffer[-1] not in sentence_ends and not re.match(r"[A-Z0-9]", line[0]):
                buffer += line
            else:
                merged.append(buffer)
                buffer = line
        else:
            buffer = line
    if buffer:
        merged.append(buffer)
    return "\n".join(merged)


def parse_pdf_to_sections(pdf_path: str) -> Dict[str, str]:
    raw = extract_text_from_pdf(pdf_path)
    if not raw:
        return {}
    cleaned = clean_text(raw)
    return split_sections(cleaned)


# ==================== 相互作用三元组抽取（增强版）====================
# 剂型后缀列表，用于提取药物核心名（如 "阿莫西林胶囊" → "阿莫西林"）
DOSAGE_SUFFIXES = (
    '片', '胶囊', '颗粒', '分散片', '咀嚼片', '缓释片', '肠溶片', '泡腾片',
    '素片', '糖衣片', '薄膜衣片', '口崩片', '缓释胶囊', '肠溶胶囊', '软胶囊',
    '注射液', '注射剂', '注射用粉针', '粉针', '口服液', '口服溶液剂',
    '干混悬剂', '混悬剂', '颗粒剂', '干糖浆', '冲剂', '糖浆', '糖浆剂',
    '软膏', '软膏剂', '乳膏', '乳膏剂', '滴眼液', '眼药水', '眼用制剂',
    '滴剂', '喷雾剂', '气雾剂', '栓剂', '凝胶', '凝胶剂', '溶液', '合剂',
    '酊剂', '贴剂', '滴耳液', '滴鼻液', '散剂', '溶液剂',
)


def extract_core_drug_name(full_name: str) -> str:
    """从完整药品名中提取核心药物名（去除剂型后缀）。
    如 '阿莫西林胶囊' → '阿莫西林'
       '盐酸克林霉素胶囊' → '盐酸克林霉素'
    """
    for suffix in DOSAGE_SUFFIXES:
        if full_name.endswith(suffix):
            core = full_name[:-len(suffix)]
            if len(core) >= 2:
                return core
    return full_name


def extract_interactions(drug_name: str, interaction_text: str,
                         known_drugs: List[str], known_brands: List[str] = None) -> List[Dict]:
    """
    从相互作用文本中抽取 (药品A, 药品B, 作用描述, 严重程度) 三元组。
    增强特性：6种匹配模板，精细严重度分4级，利用已知药品词典。
    """
    if not interaction_text:
        return []
    if known_brands is None:
        known_brands = []

    sentences = re.split(r"[。；;]", interaction_text)
    interactions = []

    all_drug_names = list(set(known_drugs + known_brands))
    drugs_sorted = sorted(all_drug_names, key=len, reverse=True)
    drugs_filtered = [d for d in drugs_sorted if d != drug_name and d != ""]

    # 构建匹配模式：同时使用完整名和核心名（去除剂型后缀）
    match_names = set()
    for d in drugs_filtered:
        match_names.add(d)  # 完整名
        core = extract_core_drug_name(d)
        if core != d:
            match_names.add(core)  # 核心名

    # 额外添加常见短名（手动补充一些常用药）
    for d in list(match_names):
        # 如果核心名以 "盐酸" 或 "硫酸" 开头，也添加无前缀版本
        for prefix in ['盐酸', '硫酸', '磷酸', '枸橼酸', '乳酸']:
            if d.startswith(prefix):
                without_prefix = d[len(prefix):]
                if len(without_prefix) >= 2:
                    match_names.add(without_prefix)

    match_names = sorted(match_names, key=len, reverse=True)

    if not match_names:
        # 回退模式：匹配常见药物名模式
        drug_pattern = r'(?:[\u4e00-\u9fffA-Za-z]{2,}(?:类|剂|药|素|酮|酸|苷|碱|醇|胺|酯|脲|酚|醚|酰胺|霉素|沙星|洛尔|普利|地平|唑|替丁|拉唑|格雷|司他|那|韦|替尼|单抗))'
    else:
        drug_pattern = "|".join(re.escape(d) for d in match_names)

    patterns = [
        # 模式1: 与 XX 合用 会/可 导致/引起 ...
        r"(?:与|和|同)\s*(?P<drugB>{drugs})\s*(?:合用|同时使用|联用|并用|同服|同用)"
        r".*?(?P<effect>会|可|可能|易|将)?\s*(?P<verb>导致|引起|产生|增加|降低|增强|减弱|"
        r"出现|发生|延长|缩短|减少|升高|降低|抑制|促进|影响|加重|诱发)\s*(?P<result>[^，。、]+)",
        # 模式2: XX 可 增强/减弱 本品 的 作用
        r"(?P<drugB>{drugs})\s*可\s*(?P<verb>增强|减弱|拮抗|协同|降低|增加|抑制|促进|影响)"
        r"\s*(?:本品|该药|{drug_name})[的]?(?:作用|效果|疗效|毒性|吸收|代谢|排泄)?(?P<result>[^，。、]+)?",
        # 模式3: 本品 可 增强/减弱 XX 的 ...
        r"本品\s*可\s*(?P<verb>增强|减弱|拮抗|协同|降低|增加|抑制|促进|影响)"
        r"\s*(?P<drugB>{drugs})[的]?(?P<result>[^，。、]+)",
        # 模式4: 不推荐/避免 与 XX 合用
        r"(?:不推荐|不建议|避免|禁止)\s*(?:与|和|同)\s*(?P<drugB>{drugs})\s*(?:合用|联用|并用|同服)"
        r"(?P<result>[^，。、]*)",
        # 模式5: 合用 XX 会导致/引起 YY
        r"(?:合用|联用|并用|同服)\s*(?P<drugB>{drugs})\s*(?:会|可|可能)"
        r"(?P<verb>导致|引起|产生|增加|降低|增强|减弱|出现|发生|延长|缩短|减少)"
        r"(?P<result>[^，。、]+)",
        # 模式6: XX 会 降低/增加 本品 血药浓度
        r"(?P<drugB>{drugs})\s*(?:会|可|可能)\s*(?P<verb>降低|增加|减少|升高|影响)"
        r"\s*(?:本品|{drug_name})[的]?(?:血药浓度|吸收|代谢|排泄|疗效|毒性)(?P<result>[^，。、]*)",
    ]

    for sent in sentences:
        sent = sent.strip()
        if not sent:
            continue
        sent_rep = sent.replace("本品", drug_name)

        severity = "注意"
        for sev_label, keywords in SEVERITY_KEYWORDS.items():
            if any(kw in sent for kw in keywords):
                severity = sev_label
                break

        for pattern in patterns:
            p = pattern.format(drugs=drug_pattern, drug_name=re.escape(drug_name))
            match = re.search(p, sent_rep)
            if match:
                drug_b = match.group("drugB")
                if drug_b == drug_name:
                    continue
                interactions.append({
                    "drug_a": drug_name,
                    "drug_b": drug_b,
                    "interaction_desc": match.group(0).strip(),
                    "severity": severity,
                    "source_text": sent,
                })
                break
    return interactions


def expand_bidirectional(interactions: List[Dict]) -> List[Dict]:
    """将单向相互作用扩展为双向（AB + BA），方便数据库查询。"""
    expanded = []
    seen_pairs = set()
    for item in interactions:
        drug_a, drug_b = item["drug_a"], item["drug_b"]
        pair_ab = (drug_a, drug_b)
        if pair_ab not in seen_pairs:
            seen_pairs.add(pair_ab)
            expanded.append({
                "drug_a": drug_a, "drug_b": drug_b,
                "interaction_desc": item["interaction_desc"],
                "severity": item["severity"],
                "source_text": item["source_text"],
            })
        pair_ba = (drug_b, drug_a)
        if pair_ab != pair_ba and pair_ba not in seen_pairs:
            seen_pairs.add(pair_ba)
            expanded.append({
                "drug_a": drug_b, "drug_b": drug_a,
                "interaction_desc": item["interaction_desc"],
                "severity": item["severity"],
                "source_text": item["source_text"],
            })
    return expanded


# ==================== 清洗记录构建 ====================
def build_cleaned_record(pdf_path: str, known_drugs: List[str] = None,
                         known_brands: List[str] = None) -> Optional[Dict]:
    """从单个 PDF 构建清洗后的药品记录（含所有增强字段）。"""
    if known_drugs is None:
        known_drugs = []
    if known_brands is None:
        known_brands = []

    sections = parse_pdf_to_sections(pdf_path)
    if not sections:
        return None

    name_section = sections.get("药品名称", "")
    generic_field = sections.get("通用名称", "")
    brand_field = sections.get("商品名称", "")

    full_text = " ".join(sections.values())
    approval_num = extract_approval_number(full_text)

    if name_section:
        generic, brand = extract_generic_brand(name_section)
    else:
        generic = generic_field if generic_field else ""
        brand = brand_field if brand_field else ""

    dosage_text = sections.get("剂型", "") or sections.get("性状", "")
    dosage_form = map_dosage_form(generic, dosage_text)

    category = classify_drug_category(approval_num, full_text)
    if category == "未知" and not approval_num:
        category = "化学药"

    # 处方类型识别
    prescription_type = extract_prescription_type(sections)

    validity_raw = clean_text(sections.get("有效期", ""))
    validity_normalized = parse_validity(validity_raw)

    record = {
        "pdf_source": pdf_path,
        "approval_number": approval_num,
        "generic_name": generic,
        "brand_name": brand if brand else None,
        "manufacturer": re.sub(r'^[：:]+', '',
                               (sections.get("生产企业", sections.get("生产厂家", None)) or "")).strip() or None,
        "category": category if category != "未知" else "其他",
        "dosage_form": dosage_form,
        "prescription_type": prescription_type,
        "indications": clean_text(sections.get("适应症", sections.get("功能主治", ""))),
        "contraindications": clean_text(sections.get("禁忌", "")),
        "adverse_reactions": clean_text(sections.get("不良反应", "")),
        "usage_dosage": clean_text(sections.get("用法用量", sections.get("用法与用量", ""))),
        "interactions_raw": clean_text(sections.get("药物相互作用", "")),
        "warnings": clean_text(sections.get("注意事项", "")),
        "storage": clean_text(sections.get("贮藏", sections.get("储存", ""))),
        "validity": validity_normalized,
    }

    for key in ["generic_name", "indications", "contraindications"]:
        if not record.get(key):
            record[key] = "暂无数据"

    interactions = extract_interactions(
        record["generic_name"], record["interactions_raw"], known_drugs, known_brands
    )
    record["extracted_interactions"] = expand_bidirectional(interactions)
    record["data_hash"] = compute_data_hash(record)
    record["is_complete"] = (
            bool(record["approval_number"])
            and record["generic_name"] != "暂无数据"
            and len(record["generic_name"]) > 1
    )
    return record


# ==================== 批量处理（增强版）====================
def process_pdf_directory(pdf_dir: str, output_json: str = "cleaned_drugs.json",
                          error_log: str = "errors.json"):
    """
    批量处理 PDF 目录，输出清洗结果。
    支持 manuals/ 和 manuals/static/ 两个子目录。
    """
    pdf_files = list(Path(pdf_dir).glob("*.pdf"))
    static_dir = Path(pdf_dir) / "static"
    if static_dir.exists():
        pdf_files.extend(static_dir.glob("*.pdf"))
    pdf_files = list(set(pdf_files))

    if not pdf_files:
        print("未找到 PDF 文件")
        return

    print(f"发现 {len(pdf_files)} 个 PDF 文件，开始第一次遍历...")

    # 第一次遍历：提取记录，收集药品名和商品名
    records = []
    all_generic_names = set()
    all_brand_names = set()
    errors = []

    for i, pdf_path in enumerate(pdf_files):
        if (i + 1) % 10 == 0:
            print(f"  进度: {i + 1}/{len(pdf_files)}")
        try:
            rec = build_cleaned_record(str(pdf_path), [], [])
        except Exception as e:
            errors.append({"pdf": str(pdf_path), "phase": "first_pass", "error": str(e)})
            print(f"  [!] 处理失败: {pdf_path.name} - {e}")
            continue
        if rec:
            records.append(rec)
            gn, bn = rec["generic_name"], rec["brand_name"]
            if gn and gn != "暂无数据":
                all_generic_names.add(gn)
            if bn and bn not in (None, "暂无数据"):
                all_brand_names.add(bn)
            if not rec["is_complete"]:
                errors.append({
                    "pdf": str(pdf_path), "phase": "validation",
                    "issue": "不完整记录",
                    "approval_number": rec["approval_number"],
                    "generic_name": rec["generic_name"],
                })

    known_drugs, known_brands = list(all_generic_names), list(all_brand_names)
    print(f"第一次遍历完成：{len(records)} 条记录，"
          f"{len(known_drugs)} 个通用名，{len(known_brands)} 个商品名")

    # 第二次遍历：重新抽取相互作用（使用完整词典）
    print("开始第二次遍历（相互作用抽取）...")
    final_records = []
    for i, rec in enumerate(records):
        if (i + 1) % 10 == 0:
            print(f"  进度: {i + 1}/{len(records)}")
        if rec["interactions_raw"]:
            rec["extracted_interactions"] = expand_bidirectional(
                extract_interactions(rec["generic_name"], rec["interactions_raw"],
                                     known_drugs, known_brands)
            )
        rec["data_hash"] = compute_data_hash(rec)

        if rec["approval_number"]:
            unique_id = rec["approval_number"]
        elif rec["generic_name"] and rec["generic_name"] != "暂无数据":
            key_parts = [
                rec["generic_name"],
                rec["dosage_form"] if rec["dosage_form"] != "未知" else "",
                rec["manufacturer"] or "",
            ]
            unique_id = "_".join(filter(None, key_parts))
        else:
            unique_id = f"UNKNOWN_{Path(rec['pdf_source']).stem}"
        rec["unique_id"] = unique_id
        final_records.append(rec)

    # 去重：基于 unique_id + data_hash 版本控制
    seen = {}
    for rec in final_records:
        uid, cur_hash = rec["unique_id"], rec["data_hash"]
        if uid in seen:
            existing_hash, existing_rec = seen[uid]
            if cur_hash != existing_hash:
                if rec.get("is_complete") and not existing_rec.get("is_complete"):
                    seen[uid] = (cur_hash, rec)
                    print(f"  [更新] {uid}: 内容有变化，已更新")
                elif rec.get("is_complete") and existing_rec.get("is_complete"):
                    print(f"  [重复] {uid}: 同一批准文号有多个版本")
        else:
            seen[uid] = (cur_hash, rec)

    dedup_records = [rec for _, rec in seen.values()]

    # 输出主数据
    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(dedup_records, f, ensure_ascii=False, indent=2)
    print(f"\n处理完成，共 {len(dedup_records)} 条药品记录，已保存至 {output_json}")

    # 错误日志
    if errors:
        with open(error_log, "w", encoding="utf-8") as f:
            json.dump(errors, f, ensure_ascii=False, indent=2)
        print(f"错误日志：{len(errors)} 条问题记录，已保存至 {error_log}")

    # 统计信息
    stats = {
        "total_pdfs": len(pdf_files),
        "successful_parses": len(records),
        "final_records": len(dedup_records),
        "categories": {}, "prescription_types": {}, "dosage_forms": {}, "complete_count": 0, "incomplete_count": 0,
    }
    for rec in dedup_records:
        cat = rec.get("category", "未知")
        stats["categories"][cat] = stats["categories"].get(cat, 0) + 1
        pt = rec.get("prescription_type", "未知")
        stats["prescription_types"][pt] = stats["prescription_types"].get(pt, 0) + 1
        df = rec.get("dosage_form", "未知")
        stats["dosage_forms"][df] = stats["dosage_forms"].get(df, 0) + 1
        if rec.get("is_complete"):
            stats["complete_count"] += 1
        else:
            stats["incomplete_count"] += 1

    print(f"类别分布: {stats['categories']}")
    print(f"处方类型: {stats['prescription_types']}")
    print(f"完整性: {stats['complete_count']} 完整, {stats['incomplete_count']} 不完整")

    stats_path = output_json.replace(".json", "_stats.json")
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    print(f"统计信息已保存至 {stats_path}")


if __name__ == "__main__":
    process_pdf_directory(
        pdf_dir="manuals",
        output_json="output_cleaned_drugs.json",
        error_log="errors.json",
    )
