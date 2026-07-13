package com.csu.health.portal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PortalSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portal_user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL UNIQUE,
                    password VARCHAR(128) NOT NULL,
                    email VARCHAR(128), phone VARCHAR(32), real_name VARCHAR(64),
                    role VARCHAR(32) NOT NULL DEFAULT 'USER', status TINYINT NOT NULL DEFAULT 1,
                    organization VARCHAR(256), org_type VARCHAR(64), research_direction VARCHAR(512),
                    certify_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    certify_reason TEXT, certify_remark VARCHAR(512), certified_at DATETIME,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portal_data_resource (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(256) NOT NULL,
                    category VARCHAR(64), source VARCHAR(256), description TEXT,
                    data_type VARCHAR(64), size_label VARCHAR(64),
                    permission_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
                    open_data_id VARCHAR(64), ref_content_id BIGINT,
                    status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portal_data_apply (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL, resource_id BIGINT NOT NULL,
                    project_name VARCHAR(256), purpose VARCHAR(512), reason TEXT,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    review_remark VARCHAR(512), reviewed_by BIGINT, reviewed_at DATETIME,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portal_api_service (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(256) NOT NULL,
                    description TEXT, method VARCHAR(16) NOT NULL DEFAULT 'GET',
                    path VARCHAR(256) NOT NULL, params_json TEXT, response_example TEXT,
                    permission_level VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
                    status TINYINT NOT NULL DEFAULT 1, sort_order INT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portal_api_apply (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL, api_id BIGINT NOT NULL,
                    project_name VARCHAR(256), purpose VARCHAR(512), reason TEXT,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    review_remark VARCHAR(512), reviewed_by BIGINT, reviewed_at DATETIME,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            seedApisIfEmpty();
            log.info("Portal user schema ready");
        } catch (Exception e) {
            log.warn("Portal schema init skipped: {}", e.getMessage());
        }
    }

    private void seedApisIfEmpty() {
        Long apiCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portal_api_service", Long.class);
        if (apiCount != null && apiCount == 0) {
            jdbcTemplate.update("""
                INSERT INTO portal_api_service (code,name,description,method,path,params_json,response_example,permission_level,sort_order) VALUES
                ('health-indicator','健康指标查询接口','按地区与年份查询人口健康核心指标（模拟）','GET','/api/mock/health/indicator',
                 '{"city":"上海","year":2024}','{"city":"上海","year":2024,"lifeExpectancy":84.2}', 'STANDARD',1),
                ('disease-trend','疾病趋势统计接口','查询指定疾病近几年的发病趋势（模拟）','GET','/api/mock/health/disease-trend',
                 '{"disease":"糖尿病","region":"全国"}','{"disease":"糖尿病","trend":[{"year":2023,"count":1200}]}', 'STANDARD',2),
                ('env-health','环境健康关联分析接口','分析空气质量与健康风险指数关联（模拟）','GET','/api/mock/health/env-correlation',
                 '{"city":"长沙","month":"2024-06"}','{"city":"长沙","aqi":65,"healthRiskIndex":0.42}', 'RESEARCHER',3),
                ('population-stats','人口健康统计接口','分年龄段人口健康统计摘要（模拟）','GET','/api/mock/health/population',
                 '{"province":"湖南"}','{"province":"湖南","elderlyRatio":0.18}', 'RESEARCHER',4)
                """);
        }
    }
}
