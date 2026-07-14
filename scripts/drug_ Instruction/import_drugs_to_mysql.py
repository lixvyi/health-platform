#!/usr/bin/env python3

import json
import mysql.connector
import os
import re
from collections import defaultdict

# ==================== 数据库连接（从环境变量读取）====================
HEALTH_DB_CONFIG = {
    'host': os.environ.get('HEALTH_DB_HOST', 'localhost'),
    'port': int(os.environ.get('HEALTH_DB_PORT', 3306)),
    'user': os.environ.get('HEALTH_DB_USER', 'root'),
    'password': os.environ.get('HEALTH_DB_PASSWORD', ''),
    'database': os.environ.get('HEALTH_DB_NAME', 'health_portal'),
    'charset': 'utf8mb4',
}

if not HEALTH_DB_CONFIG['password']:
    print("❌ 错误：请设置环境变量 HEALTH_DB_PASSWORD")
    print("   Windows CMD: set HEALTH_DB_PASSWORD=yourpass && python import_drugs_to_mysql.py")
    print("   PowerShell:  $env:HEALTH_DB_PASSWORD='yourpass'; python import_drugs_to_mysql.py")
    exit(1)

# ==================== 路径配置 ====================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DRUG_FILE = os.environ.get(
    'HEALTH_DRUG_FILE',
    os.path.join(SCRIPT_DIR, 'output_cleaned_drugs.json'),
)
MAPPING_FILE = os.environ.get(
    'HEALTH_DRUG_MAPPING_FILE',
    os.path.join(SCRIPT_DIR, 'indication_symptom_mapping.json'),
)

# ==================== 加载数据 ====================
with open(DRUG_FILE, 'r', encoding='utf-8') as f:
    drugs = json.load(f)

with open(MAPPING_FILE, 'r', encoding='utf-8') as f:
    mapping = json.load(f)

print(f"药品记录: {len(drugs)}")
print(f"症状类型: {len(mapping['flat_mapping'])}")

# ==================== 映射常量 ====================
PRESCRIPTION_MAP = {
    '处方药': '处方药',
    'OTC': '非处方药',
    '未知': '未知',
}


def escape_sql(val):
    """转义 SQL 字符串中的特殊字符，遇 None 返回 NULL"""
    if val is None:
        return 'NULL'
    s = str(val)
    s = s.replace('\\', '\\\\')
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    s = s.replace('\n', '\\n')
    s = s.replace('\r', '\\r')
    s = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', '', s)
    if not s:
        return 'NULL'
    return f"'{s}'"


# ==================== 连接数据库并导入 ====================
def import_data():
    conn = mysql.connector.connect(**HEALTH_DB_CONFIG)
    cursor = conn.cursor()
    conn.start_transaction()

    try:
        # --- 清理旧数据 ---
        cursor.execute("DELETE FROM symptom_otc_map")
        cursor.execute("DELETE FROM drug_detail")
        cursor.execute("DELETE FROM drug_basic")
        print("已清空旧数据")

        # --- 1. 插入 drug_basic ---
        seen = set()
        unique_drugs = []
        unknown_counter = 0
        for d in drugs:
            an = d.get('approval_number')
            if an is None:
                unknown_counter += 1
                an = f"UNKNOWN_{unknown_counter}"
                d['approval_number'] = an
            an = str(an).strip()
            if not an:
                unknown_counter += 1
                an = f"UNKNOWN_{unknown_counter}"
                d['approval_number'] = an
            if an not in seen:
                seen.add(an)
                unique_drugs.append(d)

        print(f"去重后共 {len(unique_drugs)} 条唯一药品")

        insert_basic = []
        drug_id_map = {}
        for idx, d in enumerate(unique_drugs):
            an = d.get('approval_number', '') or ''
            gn = d.get('generic_name', '') or ''
            bn = d.get('brand_name')
            mfr = d.get('manufacturer')
            cat = d.get('category', '其他') or '其他'
            df = d.get('dosage_form')
            pt = d.get('prescription_type', '未知') or '未知'
            pt_mapped = PRESCRIPTION_MAP.get(pt, '未知')

            mfr_clean = mfr
            if mfr_clean:
                m = re.search(r'企业名称:([^\n]+)', str(mfr_clean))
                if m:
                    mfr_clean = m.group(1).strip()

            values = (
                escape_sql(an),
                escape_sql(gn),
                escape_sql(bn),
                escape_sql(mfr_clean),
                escape_sql(cat),
                escape_sql(df),
                escape_sql(pt_mapped),
            )
            insert_basic.append(f"({','.join(values)})")

        BATCH_SIZE = 100
        total_inserted = 0
        for i in range(0, len(insert_basic), BATCH_SIZE):
            batch = insert_basic[i:i + BATCH_SIZE]
            sql = f"""INSERT INTO drug_basic (approval_number, generic_name, brand_name, manufacturer, category, dosage_form, prescription_type) VALUES {','.join(batch)}"""
            cursor.execute(sql)
            total_inserted += len(batch)
        print(f"drug_basic 已插入: {total_inserted} 条")

        # 插入后查询实际 ID
        cursor.execute("SELECT id, approval_number FROM drug_basic")
        drug_id_map = {row[1]: row[0] for row in cursor.fetchall()}

        # --- 2. 插入 drug_detail ---
        insert_detail = []
        for d in unique_drugs:
            an = d.get('approval_number', '') or ''
            did = drug_id_map.get(an)
            if did is None:
                continue

            ind = d.get('indications', '') or ''
            contra = d.get('contraindications', '') or ''
            adverse = d.get('adverse_reactions', '') or ''
            dosage = d.get('usage_dosage', '') or ''
            warn = d.get('warnings', '') or ''
            inter = d.get('interactions_raw', '') or ''
            comp = d.get('composition') or ''
            storage = d.get('storage') or ''
            validity = d.get('validity') or ''

            frags_to_remove = ['部分。', '和', '的', '参考', '见', '等', '及', '与']
            for frag in frags_to_remove:
                ind = ind.replace(frag, '')
                contra = contra.replace(frag, '')
                adverse = adverse.replace(frag, '')
                dosage = dosage.replace(frag, '')
                warn = warn.replace(frag, '')
                inter = inter.replace(frag, '')

            values = (
                str(did),
                escape_sql(ind),
                escape_sql(contra),
                escape_sql(adverse),
                escape_sql(dosage),
                escape_sql(warn),
                escape_sql(inter),
                escape_sql(comp),
                escape_sql(storage),
                escape_sql(validity),
            )
            insert_detail.append(f"({','.join(values)})")

        for i in range(0, len(insert_detail), BATCH_SIZE):
            batch = insert_detail[i:i + BATCH_SIZE]
            sql = f"""INSERT INTO drug_detail (drug_id, indications, contraindications, adverse_reactions, usage_dosage, warnings, interactions_raw, composition, storage, validity) VALUES {','.join(batch)}"""
            cursor.execute(sql)
        print(f"drug_detail 已插入: {len(insert_detail)} 条")

        # --- 3. 插入 symptom_otc_map ---
        otc_drugs = {}
        for d in unique_drugs:
            an = d.get('approval_number', '') or ''
            pt = d.get('prescription_type', '') or ''
            did = drug_id_map.get(an)
            if pt in ('OTC', '非处方药', '甲类', '乙类') and did is not None:
                otc_drugs[an] = did

        print(f"OTC 药品数: {len(otc_drugs)}")

        approval_to_symptoms = defaultdict(list)
        for symptom_item in mapping['flat_mapping']:
            symptom = symptom_item['symptom']
            for an in symptom_item.get('approval_numbers', []):
                if an and an in otc_drugs:
                    approval_to_symptoms[an].append(symptom)

        otc_inserts = []
        for an, symptoms in sorted(approval_to_symptoms.items()):
            did = otc_drugs[an]
            for symptom in sorted(set(symptoms)):
                otc_inserts.append(f"({escape_sql(symptom)},{did},{escape_sql(f'OTC药品-{an}')})")

        for i in range(0, len(otc_inserts), BATCH_SIZE):
            batch = otc_inserts[i:i + BATCH_SIZE]
            sql = f"""INSERT INTO symptom_otc_map (symptom, drug_id, note) VALUES {','.join(batch)}"""
            cursor.execute(sql)
        print(f"symptom_otc_map 已插入: {len(otc_inserts)} 条")

        # --- 提交事务 ---
        conn.commit()
        print("\n✅ 所有数据已成功导入 health_portal 数据库！")

        # --- 统计 ---
        cursor.execute("SELECT COUNT(*) FROM drug_basic")
        basic_cnt = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM drug_detail")
        detail_cnt = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM symptom_otc_map")
        map_cnt = cursor.fetchone()[0]
        print(f"  drug_basic: {basic_cnt} 条")
        print(f"  drug_detail: {detail_cnt} 条")
        print(f"  symptom_otc_map: {map_cnt} 条")

        print(f"\n{'=' * 60}")
        print("OTC 药品症状映射摘要:")
        for an, symptoms in sorted(approval_to_symptoms.items()):
            drug_name = next((d.get('generic_name', '') for d in unique_drugs if d.get('approval_number', '') == an),
                             '未知')
            print(f"  【{drug_name}】({an}): {', '.join(symptoms[:5])}")
            if len(symptoms) > 5:
                print(f"    ... 共 {len(symptoms)} 种症状")

    except Exception as e:
        conn.rollback()
        print(f"\n❌ 导入失败，已回滚: {e}")
        raise
    finally:
        cursor.close()
        conn.close()


if __name__ == '__main__':
    import_data()
