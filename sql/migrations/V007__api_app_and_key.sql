-- ============================================================
-- V007: 外部 API 调用方 AppKey 鉴权体系
-- ============================================================
CREATE DATABASE IF NOT EXISTS health_portal DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE health_portal;

-- 1. 应用凭证表（每个调用方=一个应用）
CREATE TABLE IF NOT EXISTS api_app
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_name     VARCHAR(100) NOT NULL COMMENT '应用名称',
    app_key      VARCHAR(64)  NOT NULL UNIQUE COMMENT 'AppKey（公钥，请求时传递）',
    app_secret   VARCHAR(128) NOT NULL COMMENT 'AppSecret（仅服务端存储，不对外返回）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    owner        VARCHAR(100)          DEFAULT NULL COMMENT '申请人/负责人',
    email        VARCHAR(128)          DEFAULT NULL COMMENT '联系邮箱',
    organization VARCHAR(256)          DEFAULT NULL COMMENT '所属机构',
    description  VARCHAR(512)          DEFAULT NULL COMMENT '应用描述',
    ip_whitelist VARCHAR(1000)         DEFAULT NULL COMMENT 'IP白名单，逗号分隔；空=不限制',
    daily_quota  INT          NOT NULL DEFAULT 10000 COMMENT '每日调用上限',
    qps_limit    INT          NOT NULL DEFAULT 10 COMMENT '每秒调用上限（单机）',
    tier         VARCHAR(32)  NOT NULL DEFAULT 'FREE' COMMENT '套餐等级：FREE/STANDARD/PRO',
    approved_by  BIGINT                DEFAULT NULL COMMENT '审批人（关联 sys_user.id）',
    approved_at  DATETIME              DEFAULT NULL COMMENT '审批时间',
    expire_at    DATETIME              DEFAULT NULL COMMENT '密钥过期时间，null=永不过期',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_app_key (app_key),
    INDEX idx_status (status),
    INDEX idx_owner (owner)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='外部API应用凭证';

-- 2. API 调用日志表
CREATE TABLE IF NOT EXISTS api_app_usage_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_key     VARCHAR(32)  NOT NULL COMMENT '调用方 AppKey',
    api_path    VARCHAR(256) NOT NULL COMMENT '请求路径',
    api_method  VARCHAR(16)  NOT NULL COMMENT 'HTTP方法',
    ip          VARCHAR(64)  NOT NULL COMMENT '客户端IP',
    status_code INT          NOT NULL DEFAULT 200 COMMENT 'HTTP状态码',
    latency_ms  INT          NOT NULL DEFAULT 0 COMMENT '响应耗时(毫秒)',
    request_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',

    INDEX idx_app_key (app_key),
    INDEX idx_request_at (request_at),
    INDEX idx_app_key_time (app_key, request_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='API调用日志';

-- 3. 默认插入一个测试用应用（用于开发和调试）
INSERT IGNORE INTO api_app (app_name, app_key, app_secret, status, owner, email, description, daily_quota, qps_limit,
                            tier)
VALUES ('开发调试用', 'dev-test-key', 'dev-test-secret', 1, '系统管理员', 'admin@health-portal.local',
        '本地开发调试用AppKey', 999999, 999, 'PRO');
INSERT IGNORE INTO portal_api_service (code, name, description, method, path, params_json, response_example,
                                       permission_level, sort_order)
VALUES ('hospitals', '全国医院目录查询',
        '查询全国 5 万+ 医院的详细信息，支持按省份、城市、关键词过滤。数据来源：2024年最新全国医院数据库。',
        'GET', '/api/external/hospitals',
        '{"province":"湖南省","city":"长沙市","keyword":"人民","page":1,"size":10}',
        '{"total":50599,"page":1,"size":10,"records":[{"province":"湖南省","name":"示例医院"}]}',
        'STANDARD', 5),

       ('tertiary-hospitals', '三级公立综合医院名单',
        '查询全国三级公立综合医院等级名单（A++ / A+ / A 等），支持按省份和等级筛选。',
        'GET', '/api/external/tertiary-hospitals',
        '{"province":"上海市","grade":"A++","page":1,"size":20}',
        '{"total":1163,"page":1,"size":20,"records":[{"province":"上海市","grade":"A++","hospitalName":"复旦大学附属中山医院"}]}',
        'STANDARD',
        6),

       ('hospital-grades',
        '复旦医院等级分档',
        '按 A++++ / A+++ / A++ 等等级分组展示全国顶级医院排名（复旦版）。',
        'GET',
        '/api/external/hospital-grades',
        '{}',
        '{"A++++":[{"grade":"A++++","hospitalName":"北京协和医院"}],"A+++":[]}',
        'STANDARD',
        7),

       ('drug-catalog',
        '国家药品目录查询',
        '查询国家基本医疗保险药品目录（2025版），含 4.4 万+ 条药品信息，支持按名称、剂型、分类搜索。',
        'GET',
        '/api/external/drug-catalog',
        '{"drugName":"阿莫西林","categoryCode":"XJ01","page":1,"size":20}',
        '{"total":44735,"page":1,"size":20,"records":[{"drugName":"阿莫西林","categoryCode":"XJ01"}]}',
        'STANDARD', 8);
