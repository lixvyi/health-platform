"""使用真实token探测药品API"""
import requests
import json
import time

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
TOKEN = '24be6f30d7b14339bc60cd1e89fa4f7e'

# 先获取web-config看看能否得到更多信息
print("=" * 60)
print("Step 1: 获取web-config")
print("=" * 60)
config_url = f'https://apm.nhsa.gov.cn:8588/web-config?v=3.4.4-fix.2&_r={int(time.time()*1000)}&token={TOKEN}'
try:
    r = s.get(config_url, timeout=10)
    print(f"Status: {r.status_code}")
    print(f"Response: {r.text[:500]}")
except Exception as e:
    print(f"Error: {e}")

# 尝试带token的药品搜索
print("\n" + "=" * 60)
print("Step 2: 尝试带token的药品API")
print("=" * 60)

# 从用户提供的headers中提取的签名信息
ts = str(int(time.time()))
nonce = 'o5EptIQX'  # 用户提供的nonce

endpoints_params = [
    ('/sysDict/selectByKeys', {'keys': ['DRUG_TYPE', 'DRUG_FORM']}),
    ('/drug/search', {'keyword': '', 'pageNum': 1, 'pageSize': 5}),
    ('/drug/list', {'pageNum': 1, 'pageSize': 5}),
]

for ep, params in endpoints_params:
    url = BASE + ep
    # 尝试不同的token传递方式
    for token_method in ['header', 'param', 'body']:
        headers = dict(s.headers)
        body = params.copy()
        query_params = {}
        
        if token_method == 'header':
            headers['token'] = TOKEN
            headers['Authorization'] = f'Bearer {TOKEN}'
        elif token_method == 'param':
            query_params['token'] = TOKEN
        elif token_method == 'body':
            body['token'] = TOKEN
        
        try:
            r = s.post(url, json=body, params=query_params, headers=headers, timeout=10)
            resp = r.json() if r.text else {}
            code = resp.get('code', '?')
            msg = resp.get('message', '')[:80]
            if code != 600002:  # 不是token错误就有价值
                print(f"\n{ep} [{token_method}] -> code={code}, msg={msg}")
                if code == 0 or code == 200:
                    print(f"  DATA: {json.dumps(resp, ensure_ascii=False)[:300]}")
        except Exception as e:
            pass

# 尝试直接访问国家医保药品目录的公开数据
print("\n" + "=" * 60)
print("Step 3: 尝试其他可能的公开接口")
print("=" * 60)

public_urls = [
    'https://fuwu.nhsa.gov.cn/ebus/fuwu/api/pss/pw/drugDirectory/pageList',
    'https://fuwu.nhsa.gov.cn/ebus/fuwu/api/pss/pw/nationalDrug/pageList',
    'https://fuwu.nhsa.gov.cn/ebus/fuwu/api/pss/pw/drugInfo/pageList',
]

for url in public_urls:
    try:
        r = s.post(url, json={'pageNum': 1, 'pageSize': 2, 'token': TOKEN}, timeout=10)
        resp = r.json() if r.text else {}
        code = resp.get('code', '?')
        msg = resp.get('message', '')[:80]
        print(f"\n{url.split('/')[-1]} -> code={code}, msg={msg}")
        if code == 0 or code == 200:
            print(f"  SUCCESS: {json.dumps(resp, ensure_ascii=False)[:500]}")
    except Exception as e:
        print(f"\n{url.split('/')[-1]} -> ERROR: {e}")
