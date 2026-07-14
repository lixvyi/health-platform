# 医疗资源数据导入指南

医院查询使用 MySQL 数据表，不会在运行时直接读取 JSON。项目已经跟踪三份可重建数据：

| 数据集 | JSON 文件 | 目标表 |
| --- | --- | --- |
| 全国医院目录 | `data/processed/health-resources/HOSPITAL_DIRECTORY_2024.json` | `medical_hospital` |
| 三级公立医院 | `data/processed/health-resources/PUBLIC_TERTIARY_HOSPITALS.json` | `medical_public_tertiary_hospital` |
| 医院等级分档 | `data/processed/health-resources/FUDAN_HOSPITAL_GRADES.json` | `medical_hospital_grade` |

## 环境准备

确保 MySQL 中已经创建 `health_portal` 数据库，并安装 Python 依赖：

```powershell
pip install pymysql
```

推荐通过环境变量提供密码，避免密码进入命令历史：

```powershell
$env:HEALTH_DB_PASSWORD = "你的 MySQL 密码"
```

## 一键导入

在项目根目录运行：

```powershell
python scripts/import_medical_resources.py
```

脚本会先以幂等方式应用 V002 表结构，再批量导入全部三类数据。重复执行不会重复插入，而会按照来源文件、工作表和原始行号更新现有记录。

只导入指定数据集：

```powershell
python scripts/import_medical_resources.py --datasets hospitals
python scripts/import_medical_resources.py --datasets tertiary,grades
```

旧的医院目录命令仍然可用：

```powershell
python scripts/import_hospitals.py
```

## 导入前校验

仅检查文件是否存在、JSON 格式和必填字段，不连接或修改数据库：

```powershell
python scripts/import_medical_resources.py --dry-run
```

## 自定义数据库连接

默认连接 `localhost:3306`、用户 `root`、数据库 `health_portal`。可以使用环境变量：

```powershell
$env:HEALTH_DB_HOST = "localhost"
$env:HEALTH_DB_PORT = "3306"
$env:HEALTH_DB_USER = "root"
$env:HEALTH_DB_PASSWORD = "你的 MySQL 密码"
$env:HEALTH_DB_NAME = "health_portal"
python scripts/import_medical_resources.py
```

也可以使用 `--host`、`--port`、`--user`、`--password`、`--database` 参数覆盖。

## 数据量参考

- 全国医院目录：50,599 条
- 三级公立医院：129 条
- 医院等级分档：100 条

导入完成后，启动后端即可通过医疗资源接口查询这些数据。

# 医疗资源数据导入指南

医院查询使用 MySQL 数据表，不会在运行时直接读取 JSON。项目已经跟踪三份可重建数据：

| 数据集    | JSON 文件                                                          | 目标表                                |
|--------|------------------------------------------------------------------|------------------------------------|
| 全国医院目录 | `data/processed/health-resources/HOSPITAL_DIRECTORY_2024.json`   | `medical_hospital`                 |
| 三级公立医院 | `data/processed/health-resources/PUBLIC_TERTIARY_HOSPITALS.json` | `medical_public_tertiary_hospital` |
| 医院等级分档 | `data/processed/health-resources/FUDAN_HOSPITAL_GRADES.json`     | `medical_hospital_grade`           |

## 环境准备

确保 MySQL 中已经创建 `health_portal` 数据库，并安装 Python 依赖：

```powershell
pip install pymysql
```

推荐通过环境变量提供密码，避免密码进入命令历史：

```powershell
$env:HEALTH_DB_PASSWORD = "你的 MySQL 密码"
```

## 一键导入

在项目根目录运行：

```powershell
python scripts/import_medical_resources.py
```

脚本会先以幂等方式应用 V002 表结构，再批量导入全部三类数据。重复执行不会重复插入，而会按照来源文件、工作表和原始行号更新现有记录。

只导入指定数据集：

```powershell
python scripts/import_medical_resources.py --datasets hospitals
python scripts/import_medical_resources.py --datasets tertiary,grades
```

旧的医院目录命令仍然可用：

```powershell
python scripts/import_hospitals.py
```

## 导入前校验

仅检查文件是否存在、JSON 格式和必填字段，不连接或修改数据库：

```powershell
python scripts/import_medical_resources.py --dry-run
```

## 自定义数据库连接

默认连接 `localhost:3306`、用户 `root`、数据库 `health_portal`。可以使用环境变量：

```powershell
$env:HEALTH_DB_HOST = "localhost"
$env:HEALTH_DB_PORT = "3306"
$env:HEALTH_DB_USER = "root"
$env:HEALTH_DB_PASSWORD = "你的 MySQL 密码"
$env:HEALTH_DB_NAME = "health_portal"
python scripts/import_medical_resources.py
```

也可以使用 `--host`、`--port`、`--user`、`--password`、`--database` 参数覆盖。

## 数据量参考

- 全国医院目录：50,599 条
- 三级公立医院：129 条
- 医院等级分档：100 条

导入完成后，启动后端即可通过医疗资源接口查询这些数据。
