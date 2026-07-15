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
        // 清理旧的模拟数据（如果存在）
        jdbcTemplate.update("DELETE FROM portal_api_service WHERE code IN ('health-indicator','disease-trend','env-health','population-stats')");

        // 检查真实 API 是否已存在
        Long realApiCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM portal_api_service WHERE code IN ('hospitals','tertiary-hospitals','hospital-grades','drug-catalog')", Long.class);
        if (realApiCount != null && realApiCount == 0) {
            jdbcTemplate.update("""
                INSERT INTO portal_api_service (code,name,description,method,path,params_json,response_example,permission_level,sort_order) VALUES
                    ('hospitals','全国医院目录查询','查询全国5万+医院的详细信息，支持按省份、城市、关键词过滤','GET','/api/external/hospitals',
                     '{"province":"湖南省","city":"长沙市","keyword":"人民","page":1,"size":10}',
                     '{"total":50599,"page":1,"size":10,"records":[{"province":"湖南省","name":"示例医院"}]}', 'STANDARD',1),
                    ('tertiary-hospitals','三级公立综合医院名单','查询全国三级公立综合医院等级名单，支持按省份和等级筛选','GET','/api/external/tertiary-hospitals',
                     '{"province":"上海市","grade":"A++","page":1,"size":20}',
                     '{"total":1163,"page":1,"size":20,"records":[{"province":"上海市","grade":"A++","hospitalName":"复旦大学附属中山医院"}]}', 'STANDARD',2),
                    ('hospital-grades','复旦医院等级分档','按等级分组展示全国顶级医院排名（复旦版）','GET','/api/external/hospital-grades',
                     '{}','{"A++++":[{"grade":"A++++","hospitalName":"北京协和医院"}],"A+++":[]}', 'STANDARD',3),
                    ('drug-catalog','国家药品目录查询','查询国家医保药品目录（2025版），含4.4万+条药品信息','GET','/api/external/drug-catalog',
                     '{"drugName":"阿莫西林","categoryCode":"XJ01","page":1,"size":20}',
                     '{"total":44735,"page":1,"size":20,"records":[{"drugName":"阿莫西林","categoryCode":"XJ01"}]}', 'STANDARD',4)
                """);
            log.info("Seeded real API definitions");
        }
    }
}
