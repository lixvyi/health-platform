# -*- coding: utf-8 -*-
import re
import requests
from bs4 import BeautifulSoup

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

# 1. 中国政府网数据栏目（200 OK，最安全）
r = requests.get("http://www.gov.cn/shuju/index.htm", headers=headers, timeout=20)
r.encoding = r.apparent_encoding or "utf-8"
soup = BeautifulSoup(r.text, "html.parser")
links = []
for a in soup.select("a[href]"):
    href = a.get("href", "")
    text = a.get_text(strip=True)
    if text and ("数据" in text or "统计" in text or "GDP" in text or "CPI" in text):
        if href.startswith("/"):
            href = "http://www.gov.cn" + href
        links.append((text[:40], href))
print("gov.cn shuju links:", len(links))
for t, h in links[:8]:
    print(" ", t, h)

# 2. 国家统计局 easyquery 导出 JSON（官方查询接口，非内网）
params = {
    "m": "QueryData",
    "dbcode": "hgnd",
    "rowcode": "zb",
    "colcode": "sj",
    "wds": '[{"wdcode":"zb","valuecode":"A0301"}]',
    "dfwds": "[]",
}
r2 = requests.get("https://data.stats.gov.cn/easyquery.htm", params=params, headers={
    **headers,
    "Referer": "https://data.stats.gov.cn/easyquery.htm?cn=C01",
}, timeout=20)
print("stats easyquery", r2.status_code, r2.text[:200])
