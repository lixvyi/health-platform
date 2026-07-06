# 本地启动清单（无需全部具备即可运行核心功能）

## 必需（你已有）
- [x] JDK 21
- [x] Maven（`d:\platform\tools\apache-maven-3.9.9`）
- [x] MySQL 8，`root` / `060508`，库 `health_portal`
- [x] Node.js + npm（前端）
- [x] Python 3 + `pip install requests beautifulsoup4 openpyxl pandas`

## 可选
- [ ] **Docker Desktop** — 未启动时无法用 docker-compose；本地 MySQL 方式仍可跑
- [ ] **Redis** — `local` profile 已禁用 Redis，不装也能跑
- [ ] **AI_API_KEY** — 不配置则 AI 问答为演示模式（不影响其他功能）

## 数据文件（你已提供）
- [x] `d:\年度数据 (*.xlsx)` ×25 → 国家统计局
- [x] 上海 CSV ×22 → 已导入 20 类

## 尚未提供 / 不需要你提供
- Hadoop 集群账号 — 不需要；用 MinIO+Spark Docker 扩展层替代演示
- 政务 API Key — 不需要；用开放下载+合规爬虫
- 上海平台 API AppKey — 不需要；已用 CSV 文件

## 一键本地启动

```powershell
# 1. 数据库（若未初始化）
python -c "import pymysql; c=pymysql.connect(host='localhost',user='root',password='060508'); c.cursor().execute('CREATE DATABASE IF NOT EXISTS health_portal'); c.close()"

# 2. ETL + 爬虫（可选刷新数据）
python d:\platform\scripts\crawl_open_data.py
python d:\platform\scripts\spark_etl_batch.py

# 3. 后端
cd d:\platform\health-portal-backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# 4. 前端（新终端）
cd d:\platform\health-portal-frontend
npm install
npm run dev
```

## Docker 全栈（需先启动 Docker Desktop）

```powershell
cd d:\platform\docker
docker compose -f docker-compose.yml -f docker-compose.bigdata.yml --profile bigdata up -d --build
cd ..\health-portal-frontend && npm run build
```

访问：http://localhost （Nginx）| Spark UI http://localhost:8081 | MinIO http://localhost:9001

## 默认账号
- 管理后台：admin / Admin@123
