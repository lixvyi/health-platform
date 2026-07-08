import requests
import json

print("=== 测试后端API ===\n")

# 测试KNOWLEDGE分类的内容
url = "http://localhost:8080/api/cms/content"
params = {
    "categoryCode": "KNOWLEDGE",
    "page": 1,
    "size": 10
}

try:
    response = requests.get(url, params=params)
    print(f"✓ API响应状态码: {response.status_code}")
    
    if response.status_code == 200:
        data = response.json()
        total = data.get('totalElements', 0)
        content = data.get('content', [])
        
        print(f"✓ 总记录数: {total}")
        print(f"✓ 当前页返回: {len(content)} 条\n")
        
        print("=== 最新5条健康知识 ===")
        for i, item in enumerate(content[:5], 1):
            title = item.get('title', '无标题')
            source = item.get('source', '未知来源')
            created = item.get('createdAt', '')[:10] if item.get('createdAt') else ''
            print(f"{i}. {title}")
            print(f"   来源: {source} | 创建时间: {created}")
            print()
        
        # 检查是否有ICD-10相关数据
        icd10_items = [item for item in content if 'ICD' in item.get('title', '') or '疾病编码' in item.get('title', '')]
        if icd10_items:
            print(f"\n✓ 找到 {len(icd10_items)} 条ICD-10相关数据:")
            for item in icd10_items[:3]:
                print(f"  - {item.get('title')}")
        else:
            print("\n⚠ 未找到ICD-10相关数据（可能需要导入）")
            
    else:
        print(f"✗ API返回错误: {response.text}")
        
except Exception as e:
    print(f"✗ 请求失败: {e}")
    print("请确保后端服务正在运行")
