import requests
import json

print("=== 测试后端API（带认证） ===\n")

# 1. 先登录获取token
login_url = "http://localhost:8080/api/auth/login"
login_data = {
    "username": "admin",
    "password": "admin123"
}

try:
    print("1. 正在登录...")
    login_response = requests.post(login_url, json=login_data)
    print(f"   登录状态码: {login_response.status_code}")
    
    if login_response.status_code == 200:
        token = login_response.json().get('token')
        print(f"   ✓ 登录成功，获取到token\n")
        
        # 2. 使用token访问API
        headers = {
            "Authorization": f"Bearer {token}"
        }
        
        url = "http://localhost:8080/api/cms/content"
        params = {
            "categoryCode": "KNOWLEDGE",
            "page": 1,
            "size": 10
        }
        
        print("2. 正在获取健康知识数据...")
        response = requests.get(url, params=params, headers=headers)
        print(f"   API响应状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            total = data.get('totalElements', 0)
            content = data.get('content', [])
            
            print(f"   ✓ 总记录数: {total}")
            print(f"   ✓ 当前页返回: {len(content)} 条\n")
            
            print("=== 最新5条健康知识 ===")
            for i, item in enumerate(content[:5], 1):
                title = item.get('title', '无标题')
                source = item.get('source', '未知来源')
                created = item.get('createdAt', '')[:10] if item.get('createdAt') else ''
                print(f"{i}. {title}")
                print(f"   来源: {source} | 创建时间: {created}")
                print()
            
            # 检查是否有ICD-10相关数据
            icd10_items = [item for item in content if 'ICD' in item.get('title', '') or '疾病编码' in item.get('title', '') or '高血压' in item.get('title', '') or '糖尿病' in item.get('title', '')]
            if icd10_items:
                print(f"\n✓ 找到 {len(icd10_items)} 条ICD-10/疾病相关数据:")
                for item in icd10_items[:5]:
                    print(f"  - {item.get('title')} (来源: {item.get('source', 'N/A')})")
            else:
                print("\n⚠ 未找到ICD-10相关数据")
                
        else:
            print(f"   ✗ API返回错误: {response.text}")
    else:
        print(f"   ✗ 登录失败: {login_response.text}")
        print("   尝试使用默认密码...")
        
except Exception as e:
    print(f"✗ 请求失败: {e}")
    import traceback
    traceback.print_exc()
