CREATE DATABASE IF NOT EXISTS health_portal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE health_portal;
-- 1. 药品基本信息表
CREATE TABLE IF NOT EXISTS drug_basic
(
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    approval_number   VARCHAR(50)                                NOT NULL COMMENT '批准文号（国药准字H/Z/S...）',
    generic_name      VARCHAR(200)                               NOT NULL COMMENT '通用名称',
    brand_name        VARCHAR(200)                                        DEFAULT NULL COMMENT '商品名称',
    manufacturer      VARCHAR(500)                                        DEFAULT NULL COMMENT '生产企业',
    category          ENUM ('化学药','中成药','生物制品','其他') NOT NULL DEFAULT '其他' COMMENT '药品类别',
    dosage_form       VARCHAR(100)                                        DEFAULT NULL COMMENT '标准剂型',
    prescription_type ENUM ('处方药','非处方药','未知')          NOT NULL DEFAULT '未知' COMMENT '处方类型',
    atc_code          VARCHAR(20)                                         DEFAULT NULL COMMENT 'ATC编码',
    created_at        DATETIME                                   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME                                   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_approval (approval_number),
    INDEX idx_generic (generic_name),
    INDEX idx_category (category),
    INDEX idx_dosage (dosage_form),
    INDEX idx_prescription (prescription_type),
    FULLTEXT INDEX ft_name (generic_name, brand_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='药品基本信息表';

-- 2. 药品说明书详细表
CREATE TABLE IF NOT EXISTS drug_detail
(
    drug_id           BIGINT UNSIGNED   NOT NULL COMMENT '关联 drug_basic.id',
    indications       MEDIUMTEXT COMMENT '适应症',
    contraindications MEDIUMTEXT COMMENT '禁忌',
    adverse_reactions MEDIUMTEXT COMMENT '不良反应',
    usage_dosage      MEDIUMTEXT COMMENT '用法用量',
    warnings          MEDIUMTEXT COMMENT '注意事项',
    interactions_raw  MEDIUMTEXT COMMENT '药物相互作用原文',
    composition       MEDIUMTEXT COMMENT '成份原文（不解析）',
    storage           VARCHAR(500)               DEFAULT NULL COMMENT '贮藏',
    validity          VARCHAR(200)               DEFAULT NULL COMMENT '有效期',
    schema_version    SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '说明书版本号',
    created_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (drug_id),
    FULLTEXT INDEX ft_indications (indications),
    FULLTEXT INDEX ft_composition (composition),
    CONSTRAINT fk_detail_drug FOREIGN KEY (drug_id) REFERENCES drug_basic (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='药品说明书详细表';

