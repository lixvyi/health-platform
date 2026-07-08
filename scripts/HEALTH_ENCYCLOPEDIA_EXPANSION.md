# 健康百科扩展说明（已废弃）

这份旧说明文档曾经描述过会自动创建示例 ICD-10 / 健康百科数据的流程。

当前恢复后的项目规则已经调整：

- 不允许编造或手写医学数据作为正式内容；
- 不允许在没有官方来源、来源文件、来源行号的情况下导入健康百科数据；
- 外部 Excel/JSON 数据应先经过 dry-run 审计，再用 `scripts/health_resource_import.py` 或新的专用导入器入库；
- 药品、疫苗、医院、排名等数据必须保留来源文件、来源工作表、来源行号或官方 URL。

如需恢复健康百科数据，请以 `sql/migrations/V002__knowledge_and_medical_resources.sql`
和 `scripts/health_resource_import.py` 为准。
