"""Import the tracked hospital resource JSON files into MySQL.

The importer is idempotent: records are matched by their source file, sheet,
and row, then inserted or updated. Schema V002 is applied automatically before
writing so a teammate can rebuild the medical resource tables from a checkout.
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import sys
from pathlib import Path
from typing import Any, Iterable

import pymysql

from apply_v002_migration import apply as apply_v002


PROJECT_ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = PROJECT_ROOT / "data" / "processed" / "health-resources"
DEFAULT_BATCH_SIZE = 500


DATASETS: dict[str, dict[str, Any]] = {
    "hospitals": {
        "file": RESOURCE_DIR / "HOSPITAL_DIRECTORY_2024.json",
        "table": "medical_hospital",
        "columns": {
            "source_record_no": "sourceRecordNo",
            "name": "name",
            "alias_name": "aliasName",
            "province": "province",
            "city": "city",
            "district": "district",
            "address": "address",
            "level": "level",
            "type": "type",
            "founded_year": "foundedYear",
            "operation_mode": "operationMode",
            "is_insurance": "isInsurance",
            "bed_count": "bedCount",
            "annual_visits": "annualVisits",
            "medical_staff_count": "medicalStaffCount",
            "departments": "departments",
            "phone": "phone",
            "email": "email",
            "postal_code": "postalCode",
            "introduction": "introduction",
            "source_name": "sourceName",
            "source_file": "sourceFile",
            "source_sheet": "sourceSheet",
            "source_row": "sourceRow",
        },
        "required": ("name", "sourceFile", "sourceSheet", "sourceRow"),
        "identity": ("source_file", "source_sheet", "source_row"),
    },
    "tertiary": {
        "file": RESOURCE_DIR / "PUBLIC_TERTIARY_HOSPITALS.json",
        "table": "medical_public_tertiary_hospital",
        "columns": {
            "grade": "grade",
            "province": "province",
            "hospital_name": "hospitalName",
            "source_name": "sourceName",
            "source_file": "sourceFile",
            "source_sheet": "sourceSheet",
            "source_row": "sourceRow",
        },
        "required": (
            "grade",
            "province",
            "hospitalName",
            "sourceName",
            "sourceFile",
            "sourceSheet",
            "sourceRow",
        ),
        "identity": ("source_file", "source_sheet", "source_row"),
    },
    "grades": {
        "file": RESOURCE_DIR / "FUDAN_HOSPITAL_GRADES.json",
        "table": "medical_hospital_grade",
        "columns": {
            "grade": "grade",
            "hospital_name": "hospitalName",
            "source_name": "sourceName",
            "source_file": "sourceFile",
            "source_sheet": "sourceSheet",
            "source_row": "sourceRow",
        },
        "required": (
            "grade",
            "hospitalName",
            "sourceName",
            "sourceFile",
            "sourceSheet",
            "sourceRow",
        ),
        "identity": ("source_file", "source_sheet", "source_row"),
    },
}


def load_records(config: dict[str, Any]) -> list[dict[str, Any]]:
    path: Path = config["file"]
    if not path.is_file():
        raise FileNotFoundError(f"Data file not found: {path}")
    with path.open("r", encoding="utf-8") as handle:
        records = json.load(handle)
    if not isinstance(records, list):
        raise ValueError(f"Expected a JSON array in {path}")
    return records


def normalize(value: Any) -> Any:
    if isinstance(value, str):
        value = value.strip()
        return value or None
    return value


def validate_records(
    dataset: str, records: list[dict[str, Any]], required: Iterable[str]
) -> None:
    problems: list[str] = []
    required_fields = tuple(required)
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            problems.append(f"record {index} is not an object")
            continue
        missing = [field for field in required_fields if normalize(record.get(field)) is None]
        if missing:
            problems.append(f"record {index} missing {', '.join(missing)}")
        if len(problems) >= 10:
            break
    if problems:
        detail = "\n  - ".join(problems)
        raise ValueError(f"Invalid {dataset} data:\n  - {detail}")


def connect(args: argparse.Namespace):
    password = args.password or os.getenv("HEALTH_DB_PASSWORD")
    if password is None:
        password = getpass.getpass("MySQL password: ")
    return pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )


def batches(rows: list[tuple[Any, ...]], size: int):
    for start in range(0, len(rows), size):
        yield rows[start : start + size]


def import_dataset(
    connection, dataset: str, config: dict[str, Any], records: list[dict[str, Any]], batch_size: int
) -> None:
    columns = tuple(config["columns"])
    source_keys = tuple(config["columns"].values())
    identity = set(config["identity"])
    update_columns = [column for column in columns if column not in identity]
    placeholders = ", ".join(["%s"] * len(columns))
    quoted_columns = ", ".join(f"`{column}`" for column in columns)
    updates = ", ".join(f"`{column}`=VALUES(`{column}`)" for column in update_columns)
    sql = (
        f"INSERT INTO `{config['table']}` ({quoted_columns}) VALUES ({placeholders}) "
        f"ON DUPLICATE KEY UPDATE {updates}"
    )
    rows = [tuple(normalize(record.get(key)) for key in source_keys) for record in records]
    try:
        with connection.cursor() as cursor:
            for chunk in batches(rows, batch_size):
                cursor.executemany(sql, chunk)
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    print(f"Imported {len(rows):,} {dataset} records into {config['table']}.")


def parse_datasets(value: str) -> list[str]:
    names = [name.strip().lower() for name in value.split(",") if name.strip()]
    if not names or "all" in names:
        return list(DATASETS)
    unknown = sorted(set(names) - set(DATASETS))
    if unknown:
        raise argparse.ArgumentTypeError(
            f"unknown dataset(s): {', '.join(unknown)}; choose all, hospitals, tertiary, grades"
        )
    return list(dict.fromkeys(names))


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--datasets",
        type=parse_datasets,
        default=list(DATASETS),
        help="Comma-separated: all, hospitals, tertiary, grades (default: all)",
    )
    parser.add_argument("--host", default=os.getenv("HEALTH_DB_HOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.getenv("HEALTH_DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("HEALTH_DB_USER", "root"))
    parser.add_argument("--password", help="Prefer HEALTH_DB_PASSWORD to avoid shell history")
    parser.add_argument("--database", default=os.getenv("HEALTH_DB_NAME", "health_portal"))
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate source files without connecting to or changing MySQL",
    )
    args = parser.parse_args(argv)
    if args.batch_size < 1:
        parser.error("--batch-size must be at least 1")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    loaded: dict[str, list[dict[str, Any]]] = {}
    for dataset in args.datasets:
        config = DATASETS[dataset]
        records = load_records(config)
        validate_records(dataset, records, config["required"])
        loaded[dataset] = records
        print(f"Validated {len(records):,} records from {config['file'].relative_to(PROJECT_ROOT)}.")

    if args.dry_run:
        print("Dry run complete; MySQL was not changed.")
        return 0

    connection = connect(args)
    try:
        with connection.cursor() as cursor:
            apply_v002(cursor)
        connection.commit()
        for dataset, records in loaded.items():
            import_dataset(connection, dataset, DATASETS[dataset], records, args.batch_size)
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
