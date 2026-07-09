-- 医疗资源板块数据库表
USE health_portal;

-- 医院信息表
CREATE TABLE IF NOT EXISTS `medical_hospital` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(256) NOT NULL COMMENT '医院名称',
    `province`    VARCHAR(64)  NOT NULL COMMENT '省份',
    `city`        VARCHAR(64)  NOT NULL COMMENT '城市',
    `district`    VARCHAR(64)  COMMENT '区县',
    `address`     VARCHAR(512) COMMENT '详细地址',
    `level`       VARCHAR(32)  COMMENT '医院等级（三级甲等/三级乙等/二级甲等等）',
    `type`        VARCHAR(64)  COMMENT '医院类型（综合医院/专科医院/中医医院等）',
    `phone`       VARCHAR(64)  COMMENT '联系电话',
    `website`     VARCHAR(256) COMMENT '官方网站',
    `is_insurance` TINYINT     DEFAULT 0 COMMENT '是否医保定点（1是 0否）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_province_city` (`province`, `city`),
    INDEX `idx_level` (`level`),
    INDEX `idx_name` (`name`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医院信息表';

-- 专科排名表（复旦版中国医院专科声誉排行榜）
CREATE TABLE IF NOT EXISTS `medical_specialty_ranking` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `specialty_name`  VARCHAR(128) NOT NULL COMMENT '专科名称',
    `rank_year`       INT          NOT NULL COMMENT '排名年份',
    `hospital_name`   VARCHAR(256) NOT NULL COMMENT '医院名称',
    `ranking`         INT          NOT NULL COMMENT '排名（1-10）',
    `score`           DECIMAL(5,2) COMMENT '评分',
    `source`          VARCHAR(256) COMMENT '数据来源',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_specialty_year` (`specialty_name`, `rank_year`),
    INDEX `idx_ranking` (`ranking`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专科排名表';

-- 医保定点机构表
CREATE TABLE IF NOT EXISTS `medical_insurance_institution` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`            VARCHAR(256) NOT NULL COMMENT '机构名称',
    `province`        VARCHAR(64)  NOT NULL COMMENT '省份',
    `city`            VARCHAR(64)  NOT NULL COMMENT '城市',
    `district`        VARCHAR(64)  COMMENT '区县',
    `address`         VARCHAR(512) COMMENT '详细地址',
    `type`            VARCHAR(64)  COMMENT '机构类型（医院/药店/诊所等）',
    `level`           VARCHAR(32)  COMMENT '机构等级',
    `insurance_code`  VARCHAR(64)  COMMENT '医保编码',
    `insurance_type`  VARCHAR(64)  COMMENT '医保类型（职工/居民）',
    `effective_date`  DATE         COMMENT '生效日期',
    `source_url`      VARCHAR(512) COMMENT '数据来源URL',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_province_city` (`province`, `city`),
    INDEX `idx_name` (`name`(191)),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医保定点机构表';
