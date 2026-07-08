"""探测国家医保服务平台药品目录API"""
import requests
import json
import time
import hashlib
import hmac

s = requests.Session()
s.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0',
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    'channel': 'web',
    'Referer': 'https://fuwu.nhsa.gov.cn/nationalHallSt/',
    'Origin': 'https://fuwu.nhsa.gov.cn',
})

BASE = 'https://fuwu.nhsa.gov.cn/ebus/fuwu/api/pss/pw'

# 已知的端点
endpoints = [
    '/sysDict/selectByKeys',
    '/drug/search',
    '/drug/list', 
    '/drug/query',
    '/drugDirectory/search',
    '/drugDirectory/list',
    '/national/drug/search',
    '/national/drug/list',
]

print("=" * 60)
print("探测药品目录API端点")
print("=" * 60)

for ep in endpoints:
    url = BASE + ep
    ts = str(int(time.time()))
    nonce = 'test1234'
    
    # 尝试不带签名
    try:
        r = s.post(url, json={'pageNum': 1, 'pageSize': 2}, timeout=10)
        print(f"\n{ep}")
        print(f"  Status: {r.status_code}")
        text = r.text[:300] if r.text else '(empty)'
        print(f"  Response: {text}")
    except Exception as e:
        print(f"\n{ep} -> ERROR: {e}")

# 也试试GET请求
print("\n" + "=" * 60)
print("尝试GET请求")
print("=" * 60)
for ep in ['/sysDict/selectByKeys']:
    url = BASE + ep
    try:
        r = s.get(url, params={'keys': 'drug'}, timeout=10)
        print(f"\nGET {ep}")
        print(f"  Status: {r.status_code}")
        text = r.text[:500] if r.text else '(empty)'
        print(f"  Response: {text}")
    except Exception as e:
        print(f"\nGET {ep} -> ERROR: {e}")
