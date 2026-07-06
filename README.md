# 健康大数据应用创新研发中心门户系统

中南大学计算机学院软件工程专业 · 《软件工程案例分析与实践》实训项目（11号）

## 项目简介

典型门户 + CMS 系统，包含：门户首页、新闻中心、通知公告、应用中心、卫生政策、健康知识库、**数据资源目录**、关于我们、门户内容管理后台。

## 数据资源池与采集

- `/data-pool` — 统一数据资源池架构、互联网爬虫采集、开放数据文件池
- `/data` — 45 类开放数据图表与表格（国家统计局 25 + 上海 20）
- 采集脚本：`python scripts/crawl_open_data.py`（中国政府网 + 国家统计局官网 + 开放文件同步）
- 重新导入：`python scripts/import_nbs_excel.py d:/` · `python scripts/import_shanghai_csv.py`

## 技术栈

- 后端：Spring Boot 3.2、Spring Security、JWT、MyBatis-Plus、Redis、SpringAI
- 前端：Vue 3、Vite、Element Plus、ECharts
- 数据库：MySQL 8.0
- 部署：Docker Compose、Nginx

## 三人分工

| 成员 | 角色 | 职责 |
|------|------|------|
| A | 项目经理/后端 | Git、后端 API、SpringAI、架构文档 |
| B | 前端 | 公众门户 + 管理后台、Axure 原型 |
| C | 测试/DB | PDM、测试计划/用例、JUnit、JMeter |

## 快速启动

详见 **[START.md](START.md)**（缺什么、可选什么、一键命令）

### 本地开发（当前推荐）

```powershell
# 后端
cd health-portal-backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# 前端（新终端）
cd health-portal-frontend
npm install && npm run dev
```

访问 http://127.0.0.1:5173 · API 文档 http://127.0.0.1:8080/doc.html

### Docker + 大数据（需启动 Docker Desktop）

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.bigdata.yml --profile bigdata up -d --build
```

Spark UI http://localhost:8081 · MinIO http://localhost:9001

### 2. 启动后端（IDEA）
```bash
cd health-portal-backend
# IDEA 运行 HealthPortalApplication
# 数据库连接见 src/main/resources/jdbc.properties
```

API 文档：http://localhost:8080/doc.html

### 3. 启动前端

```bash
cd health-portal-frontend
npm install
npm run dev
```

访问：http://localhost:5173

### 默认账号

- 管理员：`admin` / `Admin@123`
- 编辑：`editor` / `Admin@123`

### AI 问答（可选）

设置环境变量后重启后端：

```bash
set AI_API_KEY=你的密钥
set AI_BASE_URL=https://api.deepseek.com
set AI_MODEL=deepseek-chat
```

## 目录结构

```
platform/
├── docs/                     # RUP 阶段文档
├── health-portal-backend/    # Spring Boot 后端
├── health-portal-frontend/   # Vue3 前端
├── docker/                   # Docker Compose + Nginx
├── sql/init.sql              # 数据库初始化
└── tests/jmeter/             # 压测脚本
```

## 答辩演示路径

1. 公众门户首页 → 数据资源目录 / 用户协议 → 新闻/公告/政策/知识库浏览
2. 管理后台登录 → 发布内容 → 前台刷新可见
3. 应用中心、关于我们
4. AI 健康知识问答
5. 数据看板 ECharts + Redis 缓存说明
6. Docker 部署架构
