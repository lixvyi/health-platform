import os
import re
import requests
import time
from selenium import webdriver
from selenium.common.exceptions import TimeoutException, NoSuchElementException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

# ----- 用户配置 -----
START_PAGE = 1  # 起始页码（包含）
END_PAGE = 207  # 结束页码（包含），设置为 -1 表示下载所有页

chrome_driver_path = "chromedriver.exe"  # 修改为你的路径
output_dir = "manuals"  # 下载目录

# ----- 初始化 -----
service = Service(executable_path=chrome_driver_path)
options = Options()
options.add_argument("--disable-blink-features=AutomationControlled")
options.add_experimental_option("excludeSwitches", ["enable-automation"])
options.add_experimental_option('useAutomationExtension', False)
# options.add_argument("--headless")

driver = webdriver.Chrome(service=service, options=options)
driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {
    "source": "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
})

list_url = "https://www.cde.org.cn/yzxpj/listpage/baccb6ea4350170164a8141548c32f2e"
driver.get(list_url)
print("页面加载中...")

# ----- 等待第一页数据加载 -----
MAX_WAIT = 30
found = False
for i in range(MAX_WAIT):
    links_count = driver.execute_script("""
        return document.querySelectorAll('a[onclick*="downloadInstructions"]').length;
    """)
    if links_count > 0:
        print(f"检测到 {links_count} 个下载链接，数据已加载。")
        found = True
        break
    time.sleep(1)
    if i % 5 == 0:
        print(f"等待数据... {i + 1}s")

if not found:
    print("数据加载超时，尝试手动触发...")
    try:
        driver.execute_script("defaultObj.methods.initEvaluateTable();")
        time.sleep(5)
        links_count = driver.execute_script("""
            return document.querySelectorAll('a[onclick*="downloadInstructions"]').length;
        """)
        if links_count > 0:
            print(f"手动触发后检测到 {links_count} 个下载链接")
            found = True
        else:
            print("仍无数据，退出。")
            driver.quit()
            exit()
    except:
        driver.quit()
        exit()

# ----- 获取总页数（从分页控件） -----
total_pages = driver.execute_script("""
    var pageInfo = document.querySelector('.layui-laypage-total, .page-total, .total-pages');
    if (pageInfo) {
        var text = pageInfo.textContent.trim();
        var match = text.match(/共(\\d+)页/);
        if (match) return parseInt(match[1]);
    }
    // 如果没有总页数信息，返回 -1 表示未知
    return -1;
""")
print(f"总页数: {total_pages if total_pages > 0 else '未知'}")

if END_PAGE == -1:
    if total_pages > 0:
        END_PAGE = total_pages
    else:
        print("无法获取总页数，请手动指定 END_PAGE。")
        driver.quit()
        exit()

# 校验页码范围
if START_PAGE < 1:
    START_PAGE = 1
if END_PAGE > total_pages and total_pages > 0:
    END_PAGE = total_pages
print(f"将下载第 {START_PAGE} 页 到 第 {END_PAGE} 页")

# ----- 翻页到起始页（如果 START_PAGE > 1） -----
current_page = 1
if START_PAGE > 1:
    # 先尝试使用输入框跳转（如果存在）
    jump_success = driver.execute_script("""
        var input = document.querySelector('.layui-laypage-skip input, .page-skip input, input[name="pageNum"]');
        var btn = document.querySelector('.layui-laypage-skip button, .page-skip button, .jump-btn');
        if (input && btn) {
            input.value = arguments[0];
            btn.click();
            return true;
        }
        return false;
    """, START_PAGE)

    if jump_success:
        print(f"通过输入框跳转到第 {START_PAGE} 页")
        time.sleep(3)
        current_page = START_PAGE
    else:
        # 没有输入框，逐页点击到目标页
        print(f"无输入框，从第1页逐页翻到第 {START_PAGE} 页...")
        while current_page < START_PAGE:
            # 点击下一页
            clicked = driver.execute_script("""
                var nextBtn = document.querySelector('a.layui-laypage-next:not(.layui-disabled)');
                if (!nextBtn) {
                    // 尝试文本“下一页”
                    var candidates = document.querySelectorAll('a');
                    for (var i=0; i<candidates.length; i++) {
                        if (candidates[i].textContent.trim() === '下一页' && !candidates[i].classList.contains('layui-disabled')) {
                            nextBtn = candidates[i];
                            break;
                        }
                    }
                }
                if (nextBtn) {
                    nextBtn.click();
                    return true;
                }
                return false;
            """)
            if not clicked:
                print("无法点击下一页，可能已到末尾。")
                break
            current_page += 1
            time.sleep(2)  # 等待加载
            # 等待新页面的下载链接出现
            wait_for_links = 10
            while wait_for_links > 0:
                new_links = driver.execute_script("""
                    return document.querySelectorAll('a[onclick*="downloadInstructions"]').length;
                """)
                if new_links > 0:
                    break
                time.sleep(0.5)
                wait_for_links -= 0.5
        print(f"当前已到第 {current_page} 页")


# ----- 提取当前页数据并下载 -----
def extract_page_records():
    onclick_list = driver.execute_script("""
        var links = document.querySelectorAll('a[onclick*="downloadInstructions"]');
        var results = [];
        for (var i = 0; i < links.length; i++) {
            results.push(links[i].getAttribute('onclick'));
        }
        return results;
    """)
    records = []
    pattern = re.compile(
        r"downloadInstructions\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]\s*\)")
    for onclick in onclick_list:
        match = pattern.search(onclick)
        if match:
            records.append({
                'nidCODE': match.group(1),
                'acceptid': match.group(2),
                'drgnamecn': match.group(3)
            })
    return records


# ----- 下载函数（复用Session）-----
cookies = {c['name']: c['value'] for c in driver.get_cookies()}
session = requests.Session()
session.cookies.update(cookies)
session.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
    "Referer": list_url,
    "Accept": "application/json, text/javascript, */*; q=0.01",
    "X-Requested-With": "XMLHttpRequest"
})


def download_manual(record, save_dir=output_dir):
    nid = record['nidCODE']
    acceptid = record['acceptid']
    drugname = record['drgnamecn']
    download_url = f"https://www.cde.org.cn/yzxpj/yzxpjNoticeDownload?nidCODE={nid}&xxgk=sm"
    try:
        resp = session.get(download_url, stream=True, timeout=30)
        resp.raise_for_status()
        cd = resp.headers.get('content-disposition', '')
        if 'filename=' in cd:
            filename = cd.split('filename=')[1].strip('"')
        else:
            filename = f"{acceptid}_{drugname}_说明书.pdf"
        for ch in r'\/:*?"<>|':
            filename = filename.replace(ch, '_')
        os.makedirs(save_dir, exist_ok=True)
        filepath = os.path.join(save_dir, filename)
        with open(filepath, 'wb') as f:
            for chunk in resp.iter_content(8192):
                if chunk:
                    f.write(chunk)
        print(f"下载成功: {filepath}")
    except Exception as e:
        print(f"下载失败 [{nid}]: {e}")


# ----- 主循环：从 current_page 到 END_PAGE -----
while current_page <= END_PAGE:
    print(f"\n正在处理第 {current_page} 页...")
    records = extract_page_records()
    if not records:
        print("当前页无数据，停止。")
        break
    print(f"提取 {len(records)} 条记录，开始下载...")
    for idx, rec in enumerate(records, 1):
        print(f"  [{idx}/{len(records)}] 下载 {rec['acceptid']}")
        download_manual(rec)
        time.sleep(1)

    if current_page == END_PAGE:
        break

    # 翻到下一页
    clicked = driver.execute_script("""
        var nextBtn = document.querySelector('a.layui-laypage-next:not(.layui-disabled)');
        if (!nextBtn) {
            var candidates = document.querySelectorAll('a');
            for (var i=0; i<candidates.length; i++) {
                if (candidates[i].textContent.trim() === '下一页' && !candidates[i].classList.contains('layui-disabled')) {
                    nextBtn = candidates[i];
                    break;
                }
            }
        }
        if (nextBtn) {
            nextBtn.click();
            return true;
        }
        return false;
    """)
    if not clicked:
        print("无法翻到下一页，停止。")
        break
    current_page += 1
    time.sleep(3)
    # 等待新数据
    wait_start = time.time()
    while time.time() - wait_start < 10:
        new_links = driver.execute_script("""
            return document.querySelectorAll('a[onclick*="downloadInstructions"]').length;
        """)
        if new_links > 0:
            break
        time.sleep(0.5)

driver.quit()
print("\n所有指定页附件下载完成！")
