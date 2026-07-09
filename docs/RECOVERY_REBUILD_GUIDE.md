# 健康平台恢复重建说明

本文档用于从初始代码 + 外部导入数据文件恢复最后一次确认的项目状态。

## 原则

- 不编造医学数据。
- 不执行旧的 seed / sample / rebuild 脚本。
- 不删除、清空、覆盖已有业务数据。
- 所有外部表格导入必须保留来源文件、工作表、行号或官方 URL。
- 导入前先 dry-run，确认记录数和错误数，再执行正式入库。

## 已确认的数据源

外部导入文件放在：

```text
data/external-import/
├── hospitals/   全国医院数据库
├── insurance/   全国三级公立综合医院名单
├── rankings/    复旦医院分档名单
└── other/       2025医保药品目录、国家免疫规划疫苗表
```

当前导入器识别的数据集：

| 数据集 | 记录数 | 说明 |
| --- | ---: | --- |
| HOSPITAL_DIRECTORY_2024 | 50,599 | 全国医院名录，保留同名医院重复记录，不擅自合并 |
| PUBLIC_TERTIARY_HOSPITALS | 129 | 全国三级公立综合医院名单 |
| FUDAN_HOSPITAL_GRADES | 100 | 复旦医院分档名单，不冒充专科排名 |
| NATIONAL_DRUG_CATALOG_2025 | 3,441 | 2025医保药品目录，剂型缺失处保持为空 |
| VACCINE_SCHEDULE_2021 | 13 | 国家免疫规划儿童免疫程序 |
| VACCINE_HIV_GUIDANCE_2021 | 16 | HIV感染母亲所生儿童疫苗接种建议 |

## 恢复顺序

### 1. 后端/前端验证

```powershell
cd D:\health-platform\health-platform\health-portal-backend
mvn compile

cd D:\health-platform\health-platform\health-portal-frontend
npm run build
```

### 2. 只读审计

```powershell
cd D:\health-platform\health-platform
python scripts\health_resource_import.py
python scripts\health_resource_import.py --export
```

输出位置：

```text
data/processed/health-resources/
```

### 3. 非破坏性迁移 + 正式导入

推荐使用交互式脚本，不把密码写入项目文件：

```powershell
cd D:\health-platform\health-platform
powershell -ExecutionPolicy Bypass -File scripts\run_recovery_apply.ps1
```

脚本会按顺序执行：

1. `sql/migrations/V002__knowledge_and_medical_resources.sql`
2. `python scripts\health_resource_import.py` dry-run
3. `python scripts\health_resource_import.py --apply --export`

正式导入器是幂等的，基于来源文件/工作表/行号或唯一编码 upsert，不执行 `DELETE` / `TRUNCATE` / `DROP TABLE`。

## 已禁用的旧脚本

以下脚本只保留占位说明，不应再用于正式恢复：

- `scripts/seed_health_encyclopedia.py`
- `scripts/rebuild_encyclopedia_from_real_data.py`
- `scripts/import_latest_crawl.py`
- `scripts/fix_board_attribution.py`
- `scripts/import_icd10.py`
- `scripts/patch_db.py`
- `scripts/login_nhsa_codes.py`

## 页面模块

- 健康百科：`/knowledge`
  - 使用 `knowledge_category` + `content_category_rel` 作为分类关系。
  - 药品说明官方查询入口应作为文章内容呈现，不直接跳外链。
- 医疗资源：`/medical`
  - 医院查询、三级公立综合医院名单、复旦医院分档、真实专科排名预留、医保药品目录检索。
  - 医疗资源中不展示疫苗模块。
- 医院详情：`/medical/hospitals/:id`
- 数据资源池：`/data-pool`
  - 展示外部导入数据集、记录数、错误数和最近导入状态。

## 当前阻塞

如果执行数据库迁移时报 `Access denied`，说明本机 MySQL 密码与 `jdbc.properties` 不一致。
请使用 `scripts\run_recovery_apply.ps1` 输入正确密码，或先修正本地 `jdbc.properties`。
