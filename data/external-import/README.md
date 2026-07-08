# 外部导入数据目录

所有人工提供的 Excel / CSV 原始数据文件放在这里。不要直接改原始文件；导入器会读取原文件并输出标准化 JSON 到 `data/processed/health-resources/`。

## 目录约定

```text
external-import/
├── hospitals/   全国医院名录
├── insurance/   全国三级公立综合医院名单
├── rankings/    复旦医院分档/排名文件
└── other/       药品目录、疫苗表、其他补充数据
```

## 导入原则

- 先 dry-run：`python scripts/health_resource_import.py`
- 再导出审计结果：`python scripts/health_resource_import.py --export`
- 确认无误后再正式入库：`powershell -ExecutionPolicy Bypass -File scripts/run_recovery_apply.ps1`
- 不编造缺失字段；例如药品目录中没有剂型的记录，剂型保持为空。
- 不按医院名称去重；同名医院可能位于不同地区或属于不同记录。
