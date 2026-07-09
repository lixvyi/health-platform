"""Apply V002 schema migration in a MySQL-5.7-friendly, idempotent way.

This script only creates tables, adds missing columns, and adds missing indexes.
It never drops, truncates, or deletes business data.
"""

from __future__ import annotations

import argparse
import os
import sys
from typing import Iterable

import pymysql


def connect(args: argparse.Namespace):
    password = args.password if args.password is not None else os.getenv("HEALTH_DB_PASSWORD")
    if password is None:
        raise SystemExit("Missing database password. Use --password or HEALTH_DB_PASSWORD.")
    return pymysql.connect(
        host=args.host,
        user=args.user,
        password=password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )


def table_exists(cur, table: str) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s",
        (table,),
    )
    return cur.fetchone()[0] > 0


def column_exists(cur, table: str, column: str) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.COLUMNS "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND COLUMN_NAME=%s",
        (table, column),
    )
    return cur.fetchone()[0] > 0


def index_exists(cur, table: str, index_name: str) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND INDEX_NAME=%s",
        (table, index_name),
    )
    return cur.fetchone()[0] > 0


def add_column(cur, table: str, column: str, definition: str) -> None:
    if table_exists(cur, table) and not column_exists(cur, table, column):
        cur.execute(f"ALTER TABLE `{table}` ADD COLUMN `{column}` {definition}")
        print(f"added column {table}.{column}")


def add_index(cur, table: str, index_name: str, definition: str) -> None:
    if table_exists(cur, table) and not index_exists(cur, table, index_name):
        cur.execute(f"ALTER TABLE `{table}` ADD {definition}")
        print(f"added index {table}.{index_name}")


def execute_all(cur, statements: Iterable[str]) -> None:
    for statement in statements:
        sql = statement.strip()
        if sql:
            cur.execute(sql)


def apply(cur) -> None:
    execute_all(cur, [
        """
        CREATE TABLE IF NOT EXISTS schema_migration (
            version VARCHAR(32) NOT NULL PRIMARY KEY,
            description VARCHAR(255) NOT NULL,
            installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS knowledge_category (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            category_code VARCHAR(64) NOT NULL,
            category_name VARCHAR(64) NOT NULL,
            description VARCHAR(512),
            display_order INT NOT NULL DEFAULT 0,
            external_url VARCHAR(512),
            status TINYINT NOT NULL DEFAULT 1,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_knowledge_category_code (category_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS content_category_rel (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            content_id BIGINT NOT NULL,
            category_code VARCHAR(64) NOT NULL,
            source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_content_category (content_id, category_code),
            KEY idx_rel_category (category_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS data_resource_dataset (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            dataset_code VARCHAR(64) NOT NULL,
            dataset_name VARCHAR(128) NOT NULL,
            dataset_type VARCHAR(64) NOT NULL DEFAULT 'DATABASE',
            source_name VARCHAR(128),
            source_url VARCHAR(512),
            source_file VARCHAR(512),
            data_as_of_date DATE,
            update_frequency VARCHAR(32),
            record_count BIGINT NOT NULL DEFAULT 0,
            duplicate_count BIGINT NOT NULL DEFAULT 0,
            error_count BIGINT NOT NULL DEFAULT 0,
            completeness_rate DECIMAL(6,3),
            update_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
            failure_reason VARCHAR(1024),
            last_collected_at DATETIME,
            last_imported_at DATETIME,
            last_verified_at DATETIME,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_dataset_code (dataset_code),
            KEY idx_dataset_type_status (dataset_type, update_status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS data_resource_import_run (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            dataset_id BIGINT NOT NULL,
            run_id VARCHAR(64) NOT NULL,
            source_checksum VARCHAR(64),
            started_at DATETIME NOT NULL,
            finished_at DATETIME,
            status VARCHAR(32) NOT NULL,
            scanned_count BIGINT NOT NULL DEFAULT 0,
            inserted_count BIGINT NOT NULL DEFAULT 0,
            updated_count BIGINT NOT NULL DEFAULT 0,
            skipped_count BIGINT NOT NULL DEFAULT 0,
            duplicate_count BIGINT NOT NULL DEFAULT 0,
            error_count BIGINT NOT NULL DEFAULT 0,
            error_file VARCHAR(512),
            message VARCHAR(1024),
            UNIQUE KEY uk_import_run_id (run_id),
            KEY idx_import_dataset_time (dataset_id, started_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
    ])

    cms_columns = [
        ("source_name", "VARCHAR(128) NULL AFTER source_url"),
        ("source_publish_date", "DATE NULL AFTER source_name"),
        ("publisher", "VARCHAR(128) NULL AFTER source_publish_date"),
        ("last_review_time", "DATETIME NULL AFTER publisher"),
        ("target_audience", "VARCHAR(256) NULL AFTER last_review_time"),
        ("content_type", "VARCHAR(32) NOT NULL DEFAULT 'ARTICLE' AFTER target_audience"),
        ("is_medical", "TINYINT NOT NULL DEFAULT 0 AFTER content_type"),
        ("has_emergency_warning", "TINYINT NOT NULL DEFAULT 0 AFTER is_medical"),
        ("contraindications", "TEXT NULL AFTER has_emergency_warning"),
        ("adverse_reactions", "TEXT NULL AFTER contraindications"),
        ("verification_status", "VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER adverse_reactions"),
    ]
    for column, definition in cms_columns:
        add_column(cur, "cms_content", column, definition)
    add_index(cur, "cms_content", "idx_cms_source_name", "KEY idx_cms_source_name (source_name)")
    add_index(cur, "cms_content", "idx_cms_verification_status", "KEY idx_cms_verification_status (verification_status)")

    execute_all(cur, [
        """
        CREATE TABLE IF NOT EXISTS medical_hospital (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            source_record_no VARCHAR(64),
            name VARCHAR(256) NOT NULL,
            alias_name VARCHAR(256),
            province VARCHAR(64),
            city VARCHAR(64),
            district VARCHAR(64),
            address VARCHAR(512),
            level VARCHAR(64),
            type VARCHAR(128),
            founded_year VARCHAR(64),
            operation_mode VARCHAR(64),
            is_insurance TINYINT,
            bed_count INT,
            annual_visits BIGINT,
            medical_staff_count INT,
            departments TEXT,
            phone VARCHAR(128),
            email VARCHAR(128),
            postal_code VARCHAR(32),
            introduction TEXT,
            website VARCHAR(256),
            source_name VARCHAR(128),
            source_file VARCHAR(512),
            source_sheet VARCHAR(128),
            source_row INT,
            data_as_of_date DATE,
            verified_at DATETIME,
            verification_status VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_hospital_source_row (source_file(191), source_sheet, source_row),
            KEY idx_hospital_region (province, city, district),
            KEY idx_hospital_level (level),
            KEY idx_hospital_name (name(191))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_public_tertiary_hospital (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            grade VARCHAR(32),
            province VARCHAR(64),
            hospital_name VARCHAR(256) NOT NULL,
            source_name VARCHAR(128),
            source_file VARCHAR(512),
            source_sheet VARCHAR(128),
            source_row INT,
            data_as_of_date DATE,
            verification_status VARCHAR(32) NOT NULL DEFAULT 'IMPORTED',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_public_tertiary_source_row (source_file(191), source_sheet, source_row),
            KEY idx_public_tertiary_region (province),
            KEY idx_public_tertiary_grade (grade),
            KEY idx_public_tertiary_name (hospital_name(191))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_hospital_grade (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            grade VARCHAR(32) NOT NULL,
            hospital_name VARCHAR(256) NOT NULL,
            source_name VARCHAR(128),
            source_file VARCHAR(512),
            source_sheet VARCHAR(128),
            source_row INT,
            source_year INT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_hospital_grade_source_row (source_file(191), source_sheet, source_row),
            KEY idx_hospital_grade (grade),
            KEY idx_hospital_grade_name (hospital_name(191))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_specialty_ranking (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            specialty_name VARCHAR(128) NOT NULL,
            rank_year INT NOT NULL,
            hospital_name VARCHAR(256) NOT NULL,
            ranking INT NOT NULL,
            score DECIMAL(8,3),
            source_name VARCHAR(128),
            source_url VARCHAR(512),
            source_file VARCHAR(512),
            source_sheet VARCHAR(128),
            source_row INT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_specialty_rank (specialty_name, rank_year, ranking),
            KEY idx_specialty_year (specialty_name, rank_year)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_drug_catalog (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            category_code VARCHAR(64),
            category_name VARCHAR(256),
            drug_number VARCHAR(64),
            drug_name VARCHAR(256) NOT NULL,
            dosage_form VARCHAR(256),
            insurance_type VARCHAR(64),
            remark TEXT,
            catalog_year INT,
            source_file VARCHAR(512),
            source_sheet VARCHAR(128),
            source_row INT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_drug_source_row (source_file(191), source_sheet, source_row),
            KEY idx_drug_name (drug_name(191)),
            KEY idx_drug_number (drug_number),
            KEY idx_drug_category (category_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_vaccine_schedule (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            preventable_disease VARCHAR(256) NOT NULL,
            vaccine_name VARCHAR(256) NOT NULL,
            route VARCHAR(128),
            dose VARCHAR(128),
            abbreviation VARCHAR(128),
            age_schedule_json TEXT,
            source_name VARCHAR(256) NOT NULL,
            source_file VARCHAR(512) NOT NULL,
            source_sheet VARCHAR(128) NOT NULL,
            source_row INT NOT NULL,
            source_year INT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_vaccine_schedule_source_row (source_file(191), source_sheet, source_row),
            KEY idx_vaccine_schedule_name (vaccine_name(191))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS medical_vaccine_hiv_guidance (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            vaccine_name VARCHAR(256) NOT NULL,
            hiv_infected_symptomatic VARCHAR(64),
            hiv_infected_asymptomatic VARCHAR(64),
            hiv_unknown_symptomatic VARCHAR(64),
            hiv_unknown_asymptomatic VARCHAR(64),
            hiv_uninfected VARCHAR(64),
            source_name VARCHAR(256) NOT NULL,
            source_file VARCHAR(512) NOT NULL,
            source_sheet VARCHAR(128) NOT NULL,
            source_row INT NOT NULL,
            source_year INT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY uk_vaccine_hiv_source_row (source_file(191), source_sheet, source_row),
            KEY idx_vaccine_hiv_name (vaccine_name(191))
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS user_feedback (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            feedback_type VARCHAR(32) NOT NULL,
            target_type VARCHAR(32) NOT NULL,
            target_id BIGINT NOT NULL,
            target_name VARCHAR(256),
            description TEXT NOT NULL,
            contact_info VARCHAR(128),
            status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
            admin_remark TEXT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            processed_at DATETIME,
            processed_by BIGINT,
            KEY idx_feedback_target (target_type, target_id),
            KEY idx_feedback_status_time (status, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
    ])

    hospital_columns = [
        ("source_record_no", "VARCHAR(64) NULL"),
        ("alias_name", "VARCHAR(256) NULL"),
        ("district", "VARCHAR(64) NULL"),
        ("founded_year", "VARCHAR(64) NULL"),
        ("operation_mode", "VARCHAR(64) NULL"),
        ("is_insurance", "TINYINT NULL"),
        ("bed_count", "INT NULL"),
        ("annual_visits", "BIGINT NULL"),
        ("medical_staff_count", "INT NULL"),
        ("departments", "TEXT NULL"),
        ("email", "VARCHAR(128) NULL"),
        ("postal_code", "VARCHAR(32) NULL"),
        ("introduction", "TEXT NULL"),
        ("website", "VARCHAR(256) NULL"),
        ("source_name", "VARCHAR(128) NULL"),
        ("source_file", "VARCHAR(512) NULL"),
        ("source_sheet", "VARCHAR(128) NULL"),
        ("source_row", "INT NULL"),
        ("data_as_of_date", "DATE NULL"),
        ("verified_at", "DATETIME NULL"),
        ("verification_status", "VARCHAR(32) NOT NULL DEFAULT 'IMPORTED'"),
        ("created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
        ("updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
    ]
    for column, definition in hospital_columns:
        add_column(cur, "medical_hospital", column, definition)
    add_index(cur, "medical_hospital", "uk_hospital_source_row", "UNIQUE KEY uk_hospital_source_row (source_file(191), source_sheet, source_row)")
    add_index(cur, "medical_hospital", "idx_hospital_region", "KEY idx_hospital_region (province, city, district)")
    add_index(cur, "medical_hospital", "idx_hospital_level", "KEY idx_hospital_level (level)")
    add_index(cur, "medical_hospital", "idx_hospital_name", "KEY idx_hospital_name (name(191))")

    # Existing early medical tables may have been created before the final schema.
    public_tertiary_columns = [
        ("grade", "VARCHAR(32) NULL"),
        ("province", "VARCHAR(64) NULL"),
        ("hospital_name", "VARCHAR(256) NULL"),
        ("source_name", "VARCHAR(128) NULL"),
        ("source_file", "VARCHAR(512) NULL"),
        ("source_sheet", "VARCHAR(128) NULL"),
        ("source_row", "INT NULL"),
        ("data_as_of_date", "DATE NULL"),
        ("verification_status", "VARCHAR(32) NOT NULL DEFAULT 'IMPORTED'"),
        ("created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
        ("updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
    ]
    for column, definition in public_tertiary_columns:
        add_column(cur, "medical_public_tertiary_hospital", column, definition)
    add_index(cur, "medical_public_tertiary_hospital", "uk_public_tertiary_source_row", "UNIQUE KEY uk_public_tertiary_source_row (source_file(191), source_sheet, source_row)")

    grade_columns = [
        ("grade", "VARCHAR(32) NULL"),
        ("hospital_name", "VARCHAR(256) NULL"),
        ("source_name", "VARCHAR(128) NULL"),
        ("source_file", "VARCHAR(512) NULL"),
        ("source_sheet", "VARCHAR(128) NULL"),
        ("source_row", "INT NULL"),
        ("source_year", "INT NULL"),
        ("created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
    ]
    for column, definition in grade_columns:
        add_column(cur, "medical_hospital_grade", column, definition)
    add_index(cur, "medical_hospital_grade", "uk_hospital_grade_source_row", "UNIQUE KEY uk_hospital_grade_source_row (source_file(191), source_sheet, source_row)")

    drug_columns = [
        ("category_code", "VARCHAR(64) NULL"),
        ("category_name", "VARCHAR(256) NULL"),
        ("drug_number", "VARCHAR(64) NULL"),
        ("drug_name", "VARCHAR(256) NULL"),
        ("dosage_form", "VARCHAR(256) NULL"),
        ("insurance_type", "VARCHAR(64) NULL"),
        ("remark", "TEXT NULL"),
        ("catalog_year", "INT NULL"),
        ("source_file", "VARCHAR(512) NULL"),
        ("source_sheet", "VARCHAR(128) NULL"),
        ("source_row", "INT NULL"),
        ("created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"),
        ("updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"),
    ]
    for column, definition in drug_columns:
        add_column(cur, "medical_drug_catalog", column, definition)
    add_index(cur, "medical_drug_catalog", "uk_drug_source_row", "UNIQUE KEY uk_drug_source_row (source_file(191), source_sheet, source_row)")

    cur.execute(
        """
        INSERT INTO knowledge_category (category_code, category_name, description, display_order, status)
        VALUES
        ('DISEASE', '疾病知识', '疾病科普、诊疗知识与权威医学说明', 10, 1),
        ('DRUG', '药品说明', '药品目录、说明书查询入口与权威药品信息', 20, 1),
        ('VACCINE', '疫苗接种', '国家免疫规划疫苗程序与接种建议', 30, 1),
        ('EPIDEMIC', '传染病防控', '传染病防控知识与疾控公开信息', 40, 1),
        ('HEALTH_POPULARIZATION', '健康科普', '健康生活方式、环境健康、公共卫生科普', 50, 1),
        ('MEDICAL_TERMS', '医学术语标准', '疾病编码、医保编码和医学术语标准', 60, 1)
        ON DUPLICATE KEY UPDATE
            category_name=VALUES(category_name),
            description=VALUES(description),
            display_order=VALUES(display_order),
            status=VALUES(status)
        """
    )
    cur.execute(
        """
        INSERT INTO schema_migration (version, description)
        VALUES ('V002', 'Knowledge categories, medical resources, data resource pool metadata')
        ON DUPLICATE KEY UPDATE description=VALUES(description)
        """
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.getenv("HEALTH_DB_HOST", "localhost"))
    parser.add_argument("--user", default=os.getenv("HEALTH_DB_USER", "root"))
    parser.add_argument("--password")
    parser.add_argument("--database", default=os.getenv("HEALTH_DB_NAME", "health_portal"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    conn = connect(args)
    try:
        with conn.cursor() as cur:
            apply(cur)
        conn.commit()
        print("V002 migration applied.")
        return 0
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
