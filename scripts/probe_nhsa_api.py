#!/usr/bin/env python3
"""探测国家医保服务平台的API接口"""
import requests
import json

s = requests.Session()
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Content-Type': 'application/json',
})

# ===== 药品目录 API 探测 =====
print("=" * 60)
print("药品目录 API 探测")
print("=" * 60)

drug_urls = [
    ('POST', 'https://fuwu.nhsa.gov.cn/nationalHallSt/prod-api/drug/search'),
    ('POST', 'https://fuwu.nhsa.gov.cn/prod-api/drug/search'),
    ('GET',  'https://fuwu.nhsa.gov.cn/nationalHallSt/prod-api/drug/list'),
    ('POST', 'https://fuwu.nhsa.gov.cn/nationalHallSt/api/drug/query'),
    ('POST', 'https://fuwu.nhsa.gov.cn/nationalHallSt/prod-api/national/drug/search'),
    ('POST', 'https://fuwu.nhsa.gov.cn/nationalHallSt/prod-api/search/drug'),
]

for method, url in drug_urls:
    try:
        if method == 'POST':
            r = s.post(url, json={'pageNum': 1, 'pageSize': 2}, timeout=10)
        else:
            r = s.get(url, params={'pageNum': 1, 'pageSize': 2}, timeout=10)
        status = r.status_code
        body = r.text[:200] if r.text else '(empty)'
        print(f"  {method} {url}")
        print(f"    -> {status}: {body}")
    except Exception as e:
        print(f"  {method} {url}")
        print(f"    -> ERROR: {e}")

# ===== ICD-10 医学术语 API 探测 =====
print("\n" + "=" * 60)
print("ICD-10 医学术语 API 探测")
print("=" * 60)

icd_urls = [
    ('GET',  'https://code.nhsa.gov.cn/jbzd/api/wester/list'),
    ('POST', 'https://code.nhsa.gov.cn/jbzd/api/wester/search'),
    ('GET',  'https://code.nhsa.gov.cn/jbzd/api/wester/all'),
    ('POST', 'https://code.nhsa.gov.cn/jbzd/public/api/wester/list'),
    ('GET',  'https://code.nhsa.gov.cn/jbzd/public/api/wester/tree'),
    ('POST', 'https://code.nhsa.gov.cn/jbzd/api/disease/search'),
]

for method, url in icd_urls:
    try:
        if method == 'POST':
            r = s.post(url, json={'pageNum': 1, 'pageSize': 2}, timeout=10)
        else:
            r = s.get(url, params={'pageNum': 1, 'pageSize': 2}, timeout=10)
        status = r.status_code
        body = r.text[:200] if r.text else '(empty)'
        print(f"  {method} {url}")
        print(f"    -> {status}: {body}")
    except Exception as e:
        print(f"  {method} {url}")
        print(f"    -> ERROR: {e}")
