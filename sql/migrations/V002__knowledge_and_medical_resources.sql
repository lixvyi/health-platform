-- 健康百科与医疗资源重建迁移
-- 兼容 MySQL 5.7；不删除、不清空、不覆盖任何现有业务数据。
USE health_portal;

CREATE TABLE IF NOT EXISTS schema_migration (
    version       VARCHAR(32)  NOT NULL PRIMARY KEY,
    description   VARCHAR(255) NOT NULL,
    installed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移记录';

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing$$
CREATE PROCEDURE add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_index_if_missing$$
CREATE PROCEDURE add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 健康百科内容可信度与来源字段。
CALL add_column_if_missing('cms_content', 'source_name', "VARCHAR(128) NULL COMMENT '来源名称' AFTER source_url");
CALL add_column_if_missing('cms_content', 'source_publish_date', "DATE NULL COMMENT '来源发布日期' AFTER source_name");
CALL add_column_if_missing('cms_content', 'publisher', "VARCHAR(128) NULL COMMENT '发布机构' AFTER source_publish_date");
CALL add_column_if_missing('cms_content', 'last_review_time', "DATETIME NULL COMMENT '最近复核时间' AFTER publisher");
CALL add_column_if_missing('cms_content', 'target_audience', "VARCHAR(256) NULL COMMENT '适用人群' AFTER last_review_time");
CALL add_column_if_missing('cms_content', 'content_type', "VARCHAR(32) NOT NULL DEFAULT 'ARTICLE' COMMENT 'ARTICLE/DISEASE/DRUG/VACCINE/EPIDEMIC/TERM' AFTER target_audience");
CALL add_column_if_missing('cms_content', 'is_medical', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否医疗专业内容' AFTER content_type");
CALL add_column_if_missing('cms_content', 'has_emergency_warning', "TINYINT NOT NULL DEFAULT 0 COMMENT '是否含紧急就医警示' AFTER is_medical");
CALL add_column_if_missing('cms_content', 'contraindications', "TEXT NULL COMMENT '禁忌证' AFTER has_emergency_warning");
CALL add_column_if_missing('cms_content', 'adverse_reactions', "TEXT NULL COMMENT '不良反应' AFTER contraindications");
CALL add_column_if_missing('cms_content', 'verification_status', "VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/VERIFIED/OUTDATED' AFTER adverse_reactions");

CREATE TABLE IF NOT EXISTS knowledge_category (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id    BIGINT NOT NULL DEFAULT 0,
    name         VARCHAR(64) NOT NULL,
    code         VARCHAR(32) NOT NULL,
    icon         VARCHAR(32),
    sort_order   INT NOT NULL DEFAULT 0,
    status       TINYINT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_knowledge_category_code (code),
    KEY idx_knowledge_category_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康百科分类';

INSERT IGNORE INTO knowledge_category (name, code, icon, sort_order) VALUES
('疾病知识', 'DISEASE', '🏥', 1),
('药品说明', 'DRUG', '💊', 2),
('疫苗接种', 'VACCINE', '💉', 3),
('传染病防控', 'EPIDEMIC', '🦠', 4),
('健康科普', 'HEALTH_SCIENCE', '❤️', 5),
('医学术语标准', 'MEDICAL_TERM', '📋', 6);

CREATE TABLE IF NOT EXISTS content_category_rel (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_id   BIGINT NOT NULL,
    category_id  BIGINT NOT NULL,
    is_primary   TINYINT NOT NULL DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_content_category (content_id, category_id),
    KEY idx_content_category_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='百科内容与分类关系';

-- 数据资源池元数据与每次导入审计。
CREATE TABLE IF NOT EXISTS data_resource_dataset (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_code          VARCHAR(64) NOT NULL,
    dataset_name          VARCHAR(128) NOT NULL,
    dataset_type          VARCHAR(32) NOT NULL COMMENT 'CRAWL/FILE/CLEANED/DATABASE',
    source_name           VARCHAR(128),
    source_url            VARCHAR(512),
    source_file           VARCHAR(512),
    data_as_of_date       DATE,
    update_frequency      VARCHAR(32),
    record_count          BIGINT NOT NULL DEFAULT 0,
    duplicate_count       BIGINT NOT NULL DEFAULT 0,
    error_count           BIGINT NOT NULL DEFAULT 0,
    completeness_rate     DECIMAL(6,3),
    update_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_reason        VARCHAR(1024),
    last_collected_at     DATETIME,
    last_imported_at      DATETIME,
    last_verified_at      DATETIME,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dataset_code (dataset_code),
    KEY idx_dataset_type_status (dataset_type, update_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源池数据集元数据';

CREATE TABLE IF NOT EXISTS data_resource_import_run (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id      BIGINT NOT NULL,
    run_id          VARCHAR(64) NOT NULL,
    source_checksum VARCHAR(64),
    started_at      DATETIME NOT NULL,
    finished_at     DATETIME,
    status          VARCHAR(32) NOT NULL,
    scanned_count   BIGINT NOT NULL DEFAULT 0,
    inserted_count  BIGINT NOT NULL DEFAULT 0,
    updated_count   BIGINT NOT NULL DEFAULT 0,
    skipped_count   BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    error_count     BIGINT NOT NULL DEFAULT 0,
    error_file      VARCHAR(512),
    message         VARCHAR(1024),
    UNIQUE KEY uk_import_run_id (run_id),
    KEY idx_import_dataset_time (dataset_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源数据导入运行记录';

CREATE TABLE IF NOT EXISTS medical_hospital (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_record_no    VARCHAR(64),
    name                VARCHAR(256) NOT NULL,
    alias_name          VARCHAR(256),
    province            VARCHAR(64),
    city                VARCHAR(64),
    district            VARCHAR(64),
    address             VARCHAR(512),
    level               VARCHAR(64),
    type                VARCHAR(64),
    founded_year        VARCHAR(32),
    operation_mode      VARCHAR(32),
    is_insurance        TINYINT NOT NULL DEFAULT 0,
    bed_count           INT,
    annual_visits       BIGINT,
    medical_staff_count INT,
    departments         TEXT,
    phone               VARCHAR(128),
    email               VARCHAR(128),
    postal_code         VARCHAR(32),
    introduction        MEDIUMTEXT,
    website             VARCHAR(512),
    source_name         VARCHAR(128),
    source_file         VARCHAR(512),
    source_sheet        VARCHAR(128),
    source_row          INT,
    data_as_of_date     DATE,
    verified_at         DATETIME,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hospital_source_row (source_file(191), source_sheet, source_row),
    KEY idx_hospital_location (province, city, district),
    KEY idx_hospital_level_type (level, type),
    KEY idx_hospital_name (name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全国医院主数据';

-- 兼容旧版 medical_hospital 已存在的情况。
CALL add_column_if_missing('medical_hospital', 'source_record_no', "VARCHAR(64) NULL COMMENT '原始序号'");
CALL add_column_if_missing('medical_hospital', 'alias_name', "VARCHAR(256) NULL COMMENT '医院别名'");
CALL add_column_if_missing('medical_hospital', 'founded_year', "VARCHAR(32) NULL COMMENT '建院年份'");
CALL add_column_if_missing('medical_hospital', 'operation_mode', "VARCHAR(32) NULL COMMENT '经营方式'");
CALL add_column_if_missing('medical_hospital', 'bed_count', "INT NULL COMMENT '床位数'");
CALL add_column_if_missing('medical_hospital', 'annual_visits', "BIGINT NULL COMMENT '年门诊量'");
CALL add_column_if_missing('medical_hospital', 'medical_staff_count', "INT NULL COMMENT '医护人数'");
CALL add_column_if_missing('medical_hospital', 'departments', "TEXT NULL COMMENT '医院科室'");
CALL add_column_if_missing('medical_hospital', 'email', "VARCHAR(128) NULL COMMENT '邮箱'");
CALL add_column_if_missing('medical_hospital', 'postal_code', "VARCHAR(32) NULL COMMENT '邮编'");
CALL add_column_if_missing('medical_hospital', 'introduction', "MEDIUMTEXT NULL COMMENT '医院简介'");
CALL add_column_if_missing('medical_hospital', 'source_name', "VARCHAR(128) NULL COMMENT '来源名称'");
CALL add_column_if_missing('medical_hospital', 'source_file', "VARCHAR(512) NULL COMMENT '来源文件'");
CALL add_column_if_missing('medical_hospital', 'source_sheet', "VARCHAR(128) NULL COMMENT '来源工作表'");
CALL add_column_if_missing('medical_hospital', 'source_row', "INT NULL COMMENT '原始行号'");
CALL add_column_if_missing('medical_hospital', 'data_as_of_date', "DATE NULL COMMENT '数据截至日期'");
CALL add_column_if_missing('medical_hospital', 'verified_at', "DATETIME NULL COMMENT '核验时间'");
CALL add_column_if_missing('medical_hospital', 'verification_status', "VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '核验状态'");
CALL add_index_if_missing('medical_hospital', 'uk_hospital_source_row', "UNIQUE KEY `uk_hospital_source_row` (`source_file`(191), `source_sheet`, `source_row`)");

CREATE TABLE IF NOT EXISTS medical_public_tertiary_hospital (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade               VARCHAR(32) NOT NULL,
    province            VARCHAR(64) NOT NULL,
    hospital_name       VARCHAR(256) NOT NULL,
    source_name         VARCHAR(128) NOT NULL,
    source_file         VARCHAR(512) NOT NULL,
    source_sheet        VARCHAR(128) NOT NULL,
    source_row          INT NOT NULL,
    data_as_of_date     DATE,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tertiary_source_row (source_file(191), source_sheet, source_row),
    KEY idx_tertiary_grade_province (grade, province),
    KEY idx_tertiary_name (hospital_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全国三级公立综合医院等级名单';

-- 用户提供的“复旦医院排名.xlsx”是等级分档，不是数字名次或专科榜。
CREATE TABLE IF NOT EXISTS medical_hospital_grade (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    grade           VARCHAR(32) NOT NULL,
    hospital_name   VARCHAR(256) NOT NULL,
    source_name     VARCHAR(128) NOT NULL,
    source_file     VARCHAR(512) NOT NULL,
    source_sheet    VARCHAR(128) NOT NULL,
    source_row      INT NOT NULL,
    source_year     INT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_hospital_grade_source_row (source_file(191), source_sheet, source_row),
    KEY idx_hospital_grade (grade),
    KEY idx_hospital_grade_name (hospital_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医院等级分档名单';

-- 保留真正专科排行榜的结构；仅在获得真实专科、年份、名次数据后导入。
CREATE TABLE IF NOT EXISTS medical_specialty_ranking (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    specialty_name VARCHAR(128) NOT NULL,
    rank_year      INT NOT NULL,
    hospital_name  VARCHAR(256) NOT NULL,
    ranking        INT NOT NULL,
    score          DECIMAL(8,3),
    source_name    VARCHAR(128) NOT NULL,
    source_url     VARCHAR(512),
    source_file    VARCHAR(512),
    source_sheet   VARCHAR(128),
    source_row     INT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_specialty_rank (specialty_name, rank_year, ranking),
    KEY idx_specialty_hospital (hospital_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实专科排行榜';

-- 兼容旧版专科排名表；不删除旧 source 字段。
CALL add_column_if_missing('medical_specialty_ranking', 'source_name', "VARCHAR(128) NULL COMMENT '来源名称'");
CALL add_column_if_missing('medical_specialty_ranking', 'source_url', "VARCHAR(512) NULL COMMENT '来源URL'");
CALL add_column_if_missing('medical_specialty_ranking', 'source_file', "VARCHAR(512) NULL COMMENT '来源文件'");
CALL add_column_if_missing('medical_specialty_ranking', 'source_sheet', "VARCHAR(128) NULL COMMENT '来源工作表'");
CALL add_column_if_missing('medical_specialty_ranking', 'source_row', "INT NULL COMMENT '原始行号'");

CREATE TABLE IF NOT EXISTS medical_drug_catalog (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code  VARCHAR(64) NOT NULL COMMENT '药品分类代码',
    category_name  VARCHAR(256) NOT NULL COMMENT '药品分类',
    drug_number    VARCHAR(64) NOT NULL COMMENT '目录编号，按文本保存',
    drug_name      VARCHAR(512) NOT NULL COMMENT '药品名称',
    dosage_form    VARCHAR(256) COMMENT '剂型；原表没有时保持空值',
    insurance_type VARCHAR(32) COMMENT '甲/乙等原表字段',
    remark         TEXT,
    catalog_year   INT NOT NULL DEFAULT 2025,
    source_file    VARCHAR(512) NOT NULL,
    source_sheet   VARCHAR(128) NOT NULL,
    source_row     INT NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_drug_source_row (source_file(191), source_sheet, source_row),
    KEY idx_drug_number (drug_number),
    KEY idx_drug_category (category_code),
    KEY idx_drug_name (drug_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家医保药品目录';

-- 兼容此前按“通用名/医保类别”设计的旧药品表。
CALL add_column_if_missing('medical_drug_catalog', 'category_code', "VARCHAR(64) NULL COMMENT '药品分类代码'");
CALL add_column_if_missing('medical_drug_catalog', 'category_name', "VARCHAR(256) NULL COMMENT '药品分类'");
CALL add_column_if_missing('medical_drug_catalog', 'drug_number', "VARCHAR(64) NULL COMMENT '目录编号'");
CALL add_column_if_missing('medical_drug_catalog', 'source_file', "VARCHAR(512) NULL COMMENT '来源文件'");
CALL add_column_if_missing('medical_drug_catalog', 'source_sheet', "VARCHAR(128) NULL COMMENT '来源工作表'");
CALL add_column_if_missing('medical_drug_catalog', 'source_row', "INT NULL COMMENT '原始行号'");
CALL add_index_if_missing('medical_drug_catalog', 'uk_drug_source_row', "UNIQUE KEY `uk_drug_source_row` (`source_file`(191), `source_sheet`, `source_row`)");

CREATE TABLE IF NOT EXISTS medical_vaccine_schedule (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    preventable_disease VARCHAR(256),
    vaccine_name        VARCHAR(256) NOT NULL,
    route               VARCHAR(128),
    dose                VARCHAR(128),
    abbreviation        VARCHAR(64),
    age_schedule_json   TEXT NOT NULL COMMENT '按原表年龄列保存的 JSON',
    source_name         VARCHAR(256) NOT NULL,
    source_file         VARCHAR(512) NOT NULL,
    source_sheet        VARCHAR(128) NOT NULL,
    source_row          INT NOT NULL,
    source_year         INT,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vaccine_schedule_source_row (source_file(191), source_sheet, source_row),
    KEY idx_vaccine_schedule_name (vaccine_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家免疫规划儿童免疫程序';

CREATE TABLE IF NOT EXISTS medical_vaccine_hiv_guidance (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    vaccine_name               VARCHAR(256) NOT NULL,
    hiv_infected_symptomatic   VARCHAR(64),
    hiv_infected_asymptomatic  VARCHAR(64),
    hiv_unknown_symptomatic    VARCHAR(64),
    hiv_unknown_asymptomatic   VARCHAR(64),
    hiv_uninfected             VARCHAR(64),
    source_name                VARCHAR(256) NOT NULL,
    source_file                VARCHAR(512) NOT NULL,
    source_sheet               VARCHAR(128) NOT NULL,
    source_row                 INT NOT NULL,
    source_year                INT,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vaccine_hiv_source_row (source_file(191), source_sheet, source_row),
    KEY idx_vaccine_hiv_name (vaccine_name(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HIV感染母亲所生儿童疫苗接种建议';

CREATE TABLE IF NOT EXISTS user_feedback (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    feedback_type  VARCHAR(32) NOT NULL,
    target_type    VARCHAR(32) NOT NULL,
    target_id      BIGINT NOT NULL,
    target_name    VARCHAR(256),
    description    TEXT NOT NULL,
    contact_info   VARCHAR(128),
    status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    admin_remark   TEXT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at   DATETIME,
    processed_by   BIGINT,
    KEY idx_feedback_target (target_type, target_id),
    KEY idx_feedback_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息纠错反馈';

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

INSERT IGNORE INTO schema_migration (version, description)
VALUES ('V002', '健康百科分类、医疗资源、资源池元数据与反馈结构');
