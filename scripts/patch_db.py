import pymysql

conn = pymysql.connect(host='localhost', user='root', password='060508', database='health_portal', charset='utf8mb4')
cur = conn.cursor()
cur.execute("UPDATE cms_app SET name=%s, link_url=%s, description=%s, sort_order=%s WHERE id=%s",
            ('国家统计数据库', 'https://data.stats.gov.cn', '国家统计局官方数据', 1, 1))
cur.execute("UPDATE cms_app SET name=%s, link_url=%s, description=%s, sort_order=%s WHERE id=%s",
            ('上海公共数据开放', '/data?tab=shanghai', '20类医疗机构等CSV', 2, 2))
cur.execute("UPDATE cms_app SET name=%s, link_url=%s, description=%s, sort_order=%s WHERE id=%s",
            ('统一数据资源池', '/data-pool', '爬虫+开放数据+Spark ETL', 3, 3))
cur.execute("UPDATE cms_site_config SET config_value=%s WHERE config_key='home_intro'",
            ('依托政务数据共享与开放数据平台，结合互联网合规采集，构建健康大数据统一资源池',))
conn.commit()
conn.close()
print('DB updated OK')
