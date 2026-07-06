# -*- coding: utf-8 -*-
import re
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"}

for base in [
    "https://www.stats.gov.cn/sj/",
    "http://www.gov.cn/shuju/index.htm",
]:
    r = requests.get(base, headers=headers, timeout=20)
    r.encoding = r.apparent_encoding or "utf-8"
    soup = BeautifulSoup(r.text, "html.parser")
    files = []
    for a in soup.select("a[href]"):
        href = a.get("href", "")
        text = a.get_text(strip=True)
        if re.search(r"\.(csv|xls|xlsx|zip|rar)(\?|$)", href, re.I):
            files.append((text, urljoin(base, href)))
    print("===", base, "files:", len(files))
    for t, u in files[:15]:
        print(t[:40], u)
