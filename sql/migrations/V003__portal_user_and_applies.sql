-- 门户公众用户、数据/API 资源目录与申请审核

CREATE TABLE IF NOT EXISTS portal_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(128) NOT NULL,
    email           VARCHAR(128),
    phone           VARCHAR(32),
    real_name       VARCHAR(64),
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'USER/RESEARCHER',
    status          TINYINT      NOT NULL DEFAULT 1,
    organization    VARCHAR(256),
    org_type        VARCHAR(64)  COMMENT '高校/医院/企业/研究机构',
    research_direction VARCHAR(512),
    certify_status  VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED',
    certify_reason  TEXT,
    certify_remark  VARCHAR(512),
    certified_at    DATETIME,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portal_data_resource (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(256) NOT NULL,
    category         VARCHAR(64),
    source           VARCHAR(256),
    description      TEXT,
    data_type        VARCHAR(64),
    size_label       VARCHAR(64),
    permission_level VARCHAR(32)  NOT NULL DEFAULT 'STANDARD' COMMENT 'PUBLIC/STANDARD/RESEARCHER',
    open_data_id     VARCHAR(64),
    ref_content_id   BIGINT,
    status           TINYINT      NOT NULL DEFAULT 1,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portal_data_apply (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    resource_id   BIGINT       NOT NULL,
    project_name  VARCHAR(256),
    purpose       VARCHAR(512),
    reason        TEXT,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    review_remark VARCHAR(512),
    reviewed_by   BIGINT,
    reviewed_at   DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_resource (resource_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portal_api_service (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(256) NOT NULL,
    description      TEXT,
    method           VARCHAR(16)  NOT NULL DEFAULT 'GET',
    path             VARCHAR(256) NOT NULL,
    params_json      TEXT,
    response_example TEXT,
    permission_level VARCHAR(32)  NOT NULL DEFAULT 'STANDARD' COMMENT 'STANDARD/RESEARCHER',
    status           TINYINT      NOT NULL DEFAULT 1,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portal_api_apply (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    api_id        BIGINT       NOT NULL,
    project_name  VARCHAR(256),
    purpose       VARCHAR(512),
    reason        TEXT,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    review_remark VARCHAR(512),
    reviewed_by   BIGINT,
    reviewed_at   DATETIME,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_api (api_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始数据资源（关联开放数据目录）
INSERT INTO portal_data_resource (code, name, category, source, description, data_type, size_label, permission_level, open_data_id, sort_order) VALUES
('nbs-health-stats', '国家统计局年度卫生统计数据', '医疗数据', '国家统计局', '含医疗卫生机构、床位数、卫生人员等25类年度指标', '结构化', '约 15MB', 'STANDARD', 'nbs-1', 1),
('shanghai-medical', '上海市医疗机构开放数据集', '医疗数据', '上海市公共数据开放平台', '全市医疗机构名录、床位、科室等20类开放数据', 'CSV', '约 8MB', 'STANDARD', 'sh-1', 2),
('shanghai-population', '上海市人口与家庭健康数据', '人口数据', '上海市公共数据开放平台', '户籍人口、出生死亡、妇幼保健等统计', 'CSV', '约 5MB', 'RESEARCHER', 'sh-5', 3),
('env-air-quality', '环境与健康关联监测数据（模拟）', '环境数据', '研发中心数据池', '空气质量、温湿度与健康指标关联分析用脱敏样本', 'JSON', '约 120MB', 'RESEARCHER', NULL, 4),
('chronic-disease-sample', '慢病随访脱敏样本数据（模拟）', '医疗数据', '研发中心数据池', '用于慢病预测模型研究的脱敏结构化样本，非真实患者数据', 'CSV', '约 500MB', 'RESEARCHER', NULL, 5);

-- 初始 API 服务
INSERT INTO portal_api_service (code, name, description, method, path, params_json, response_example, permission_level, sort_order) VALUES
('health-indicator', '健康指标查询接口', '按地区与年份查询人口健康核心指标（模拟数据）', 'GET', '/api/mock/health/indicator',
 '{"city":"上海","year":2024}', '{"city":"上海","year":2024,"lifeExpectancy":84.2,"infantMortality":2.1}', 'STANDARD', 1),
('disease-trend', '疾病趋势统计接口', '查询指定疾病近几年的发病趋势统计（模拟）', 'GET', '/api/mock/health/disease-trend',
 '{"disease":"糖尿病","region":"全国"}', '{"disease":"糖尿病","trend":[{"year":2022,"count":1200},{"year":2023,"count":1350}]}', 'STANDARD', 2),
('env-health', '环境健康关联分析接口', '分析空气质量与健康风险指数的关联（模拟）', 'GET', '/api/mock/health/env-correlation',
 '{"city":"长沙","month":"2024-06"}', '{"city":"长沙","aqi":65,"healthRiskIndex":0.42}', 'RESEARCHER', 3),
('population-stats', '人口健康统计接口', '分年龄段人口健康统计摘要（模拟）', 'GET', '/api/mock/health/population',
 '{"province":"湖南"}', '{"province":"湖南","elderlyRatio":0.18,"chronicRate":0.26}', 'RESEARCHER', 4);
