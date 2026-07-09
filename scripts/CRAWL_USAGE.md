# 新增健康百科数据源爬虫 - 使用说明

## 📦 已创建的爬虫脚本

### 1. 中国疾控中心传染病疫情通报爬虫
**文件**: `scripts/crawl_chinacdc_epidemic.py`  
**数据源**: https://www.chinacdc.cn/jksj/jksj01/  
**分类**: NEWS（新闻资讯）  
**功能**: 
- 采集月度法定传染病疫情通报
- 自动提取关键统计数据（发病数、死亡数、前5位病种等）
- 生成结构化摘要并写入CMS数据库

### 2. 中华医学会科普图文爬虫
**文件**: `scripts/crawl_cma_kepu.py`  
**数据源**: https://www.cma.org.cn/col/col4584/index.html  
**分类**: KNOWLEDGE（健康知识库）  
**功能**:
- 采集医学科普文章列表
- 提取文章摘要内容
- 支持关键词过滤和健康相关度判断

---

## 🔧 环境准备

### Python依赖安装
```bash
pip install requests beautifulsoup4 pymysql openpyxl
```

### 配置文件更新
已在 `scripts/crawl-sources.json` 中添加两个新数据源配置：
- `chinacdc_epidemic`: 传染病疫情通报
- `cma_kepu`: 中华医学会科普图文

---

## 🚀 运行方式

### 仅采集（不写入数据库）
```bash
# 采集传染病疫情数据
python scripts/crawl_chinacdc_epidemic.py

# 采集中华医学会科普文章
python scripts/crawl_cma_kepu.py
```

### 采集并写入CMS数据库
```bash
# 采集并导入传染病数据
python scripts/crawl_chinacdc_epidemic.py --import

# 采集并导入科普文章
python scripts/crawl_cma_kepu.py --cms
```

---

## 📁 输出文件位置

### 采集结果JSON
- 传染病数据: `data/crawl/epidemic/chinacdc_epidemic.json`
- 科普文章: `data/crawl/cma/cma_kepu.json`

### 后端资源镜像
- 传染病数据: `health-portal-backend/src/main/resources/data/crawl/epidemic/chinacdc_epidemic.json`
- 科普文章: `health-portal-backend/src/main/resources/data/crawl/cma/cma_kepu.json`

### 导入日志
- `data/crawl/epidemic/last-import.json`
- `data/crawl/cma/last-import.json`

---

## ⚙️ 合规特性

所有爬虫均遵循以下合规原则：

1. **限速访问**: 默认3秒/请求，避免对目标网站造成压力
2. **User-Agent标识**: 明确标识为 "HealthPortalBot/1.0 (CSU training; open data)"
3. **仅存摘要**: 只存储文章摘要和原文链接，不全文转载
4. **注明来源**: 每条记录都包含完整的数据来源归属信息
5. **去重机制**: 通过source_url字段避免重复采集
6. **公开数据**: 仅采集政府/官方机构公开发布的信息

---

## 📊 数据结构示例

### 传染病疫情数据
```json
{
  "title": "2026年5月全国传染病疫情概况",
  "url": "https://www.chinacdc.cn/jksj/jksj01/...",
  "pubDate": "2026-06-10",
  "summary": "2026年5月，全国共报告法定传染病1092528例，死亡1879人...",
  "stats": {
    "total_cases": 1092528,
    "total_deaths": 1879,
    "class_b_cases": 297309,
    "class_b_deaths": 1876,
    "top5_diseases": ["病毒性肝炎", "肺结核", "梅毒", "新型冠状病毒感染", "淋病"]
  }
}
```

### 科普文章数据
```json
{
  "title": "高血压患者的日常饮食建议",
  "url": "https://www.cma.org.cn/col/col4584/art/...",
  "pubDate": "2026-06-01",
  "summary": "高血压患者应控制钠盐摄入，多吃蔬菜水果..."
}
```

---

## 🔄 定时任务配置（可选）

### Windows PowerShell 定时执行
```powershell
# 每月1日采集传染病数据
Register-ScheduledTask -TaskName "CrawlEpidemicData" `
  -Action (New-ScheduledTaskAction -Execute "python" `
    -Argument "D:\health-platform\health-platform\scripts\crawl_chinacdc_epidemic.py --import") `
  -Trigger (New-ScheduledTaskTrigger -Monthly -DaysOfMonth 1 -At "02:00AM")

# 每周日采集科普文章
Register-ScheduledTask -TaskName "CrawlCMAKapu" `
  -Action (New-ScheduledTaskAction -Execute "python" `
    -Argument "D:\health-platform\health-platform\scripts\crawl_cma_kepu.py --cms") `
  -Trigger (New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At "03:00AM")
```

---

## ⚠️ 注意事项

1. **网络环境**: 部分政府网站可能需要国内IP访问
2. **反爬机制**: 如遇到403/412错误，可适当增加限速时间
3. **页面结构变化**: 如采集失败，需检查目标网站HTML结构是否变更
4. **数据库连接**: 确保MySQL服务已启动且jdbc.properties配置正确
5. **编码问题**: 所有文件使用UTF-8编码保存

---

## 🛠️ 故障排查

### 问题1: 无法连接到目标网站
```bash
# 测试网络连通性
curl -I https://www.chinacdc.cn/jksj/jksj01/
```

### 问题2: 数据库写入失败
```bash
# 检查数据库配置
cat health-portal-backend/src/main/resources/jdbc.properties
```

### 问题3: 采集结果为空
- 查看控制台输出的详细日志
- 检查 `data/crawl/*/last-import.json` 中的错误信息
- 可能需要调整CSS选择器或正则表达式

---

## 📈 后续扩展建议

### 待实现的数据源
1. **医保局ICD-10编码查询** - 需要逆向API接口
2. **国家药监局药品查询** - 建议使用Selenium模拟浏览器
3. **健康中国微博账号** - 建议改用微信公众号或官网替代

### 功能增强
- [ ] 添加邮件通知功能（采集成功/失败时发送通知）
- [ ] 支持增量采集（只采集新增内容）
- [ ] 数据统计面板（可视化展示采集情况）
- [ ] 异常监控告警（连续失败时自动报警）

---

## 📞 技术支持

如有问题，请检查：
1. Python版本 >= 3.8
2. 所有依赖包已正确安装
3. 网络连接正常
4. 数据库服务已启动

**最后更新**: 2026-07-07
