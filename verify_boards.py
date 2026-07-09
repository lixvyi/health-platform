#!/usr/bin/env python3
import requests

boards = ['疾病知识', '药品说明', '疫苗接种', '传染病防控', '健康科普', '医学术语标准']

print("=" * 70)
print("前端API验证 - 各板块数据")
print("=" * 70)

for board in boards:
    r = requests.get('http://localhost:8080/api/cms/content', params={
        'categoryCode': 'KNOWLEDGE',
        'keyword': board,
        'pageNum': 1,
        'pageSize': 5
    })
    data = r.json()
    total = data['data']['total']
    status = "✅" if total > 0 else "⚠️ 无数据"
    print(f"\n{status} {board}: {total} 条")
    if total > 0:
        for item in data['data']['records'][:3]:
            print(f"   - {item['title'][:40]}... | {item['author']}")
