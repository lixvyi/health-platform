# 数据库迁移说明

本目录存放版本化 SQL，不直接修改或替换 `sql/init.sql`。

## 当前迁移

- `V002__knowledge_and_medical_resources.sql`
  - 兼容 MySQL 5.7。
  - 不包含 `DROP TABLE`、`TRUNCATE`、`DELETE`。
  - 只创建缺失表，并通过 `information_schema` 为 `cms_content` 添加缺失字段。
  - 将三级公立医院、医院等级分档、真正的专科排名拆成语义不同的数据表。
  - 药品和疫苗记录保留来源文件、工作表和原始行号。

## 执行纪律

1. 先备份数据库。
2. 先在测试库执行并检查表结构。
3. 正式执行前记录 `SHOW TABLES` 与关键表行数。
4. 迁移只负责结构，不自动导入 Excel，也不修改已有内容归属。
5. 导入必须由后续幂等导入工具完成。

