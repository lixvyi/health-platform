import requests, json
r = requests.get('http://localhost:5173/api/portal/contents', params={'categoryCode':'KNOWLEDGE','page':1,'size':5})
d = r.json()
print(f"Total: {d['data']['total']}")
for i in d['data']['records']:
    print(f"  {i['title']} | {i['author']}")

# Test keyword filter
print("\n--- Filter: '药品说明' ---")
r2 = requests.get('http://localhost:5173/api/portal/contents', params={'categoryCode':'KNOWLEDGE','keyword':'药品说明','page':1,'size':10})
d2 = r2.json()
print(f"Filtered total: {d2['data']['total']}")
for i in d2['data']['records']:
    print(f"  {i['title']}")
