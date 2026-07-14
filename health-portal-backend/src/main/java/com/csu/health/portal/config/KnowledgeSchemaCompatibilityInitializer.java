package com.csu.health.portal.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Upgrades legacy knowledge-category tables in place before portal requests are served.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSchemaCompatibilityInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        try {
            ensureMigrationTable();
            ensureKnowledgeCategoryTable();
            ensureContentCategoryRelationTable();
            migrateLegacyRelations();
            seedCanonicalCategories();
            mergeCategoryAlias("HEALTH_POPULARIZATION", "HEALTH_SCIENCE", "健康科普", "❤️", 5);
            mergeCategoryAlias("MEDICAL_TERMS", "MEDICAL_TERM", "医学术语标准", "📋", 6);
            ensureRelationIndexes();
            markInstalled();
            logSchemaSummary();
        } catch (Exception e) {
            log.error("Knowledge schema compatibility migration failed", e);
            throw new IllegalStateException("健康百科数据库结构升级失败: " + e.getMessage(), e);
        }
    }

    private void ensureMigrationTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS schema_migration (
                    version VARCHAR(32) NOT NULL PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移记录'
                """);
    }

    private void ensureKnowledgeCategoryTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_category (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    parent_id BIGINT NOT NULL DEFAULT 0,
                    name VARCHAR(64) NOT NULL,
                    code VARCHAR(64) NOT NULL,
                    icon VARCHAR(32),
                    sort_order INT NOT NULL DEFAULT 0,
                    status TINYINT NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_knowledge_category_code (code),
                    KEY idx_knowledge_category_parent (parent_id, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康百科分类'
                """);

        renameColumnIfNeeded("knowledge_category", "category_code", "code", "VARCHAR(64) NOT NULL");
        renameColumnIfNeeded("knowledge_category", "category_name", "name", "VARCHAR(64) NOT NULL");
        renameColumnIfNeeded("knowledge_category", "display_order", "sort_order", "INT NOT NULL DEFAULT 0");
        reconcileLegacyTextColumn("category_code", "code", "VARCHAR(64) NULL");
        reconcileLegacyTextColumn("category_name", "name", "VARCHAR(64) NULL");
        if (columnExists("knowledge_category", "display_order")
                && columnExists("knowledge_category", "sort_order")) {
            jdbcTemplate.update("""
                    UPDATE knowledge_category
                    SET sort_order = display_order
                    WHERE sort_order = 0 AND display_order <> 0
                    """);
        }
        addColumnIfMissing("knowledge_category", "parent_id", "BIGINT NOT NULL DEFAULT 0 AFTER id");
        addColumnIfMissing("knowledge_category", "icon", "VARCHAR(32) NULL AFTER code");
        addColumnIfMissing("knowledge_category", "updated_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
        addIndexIfMissing("knowledge_category", "idx_knowledge_category_parent",
                "KEY idx_knowledge_category_parent (parent_id, sort_order)");
    }

    private void ensureContentCategoryRelationTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS content_category_rel (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    content_id BIGINT NOT NULL,
                    category_id BIGINT NOT NULL,
                    is_primary TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_content_category (content_id, category_id),
                    KEY idx_content_category_category (category_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='百科内容与分类关系'
                """);

        addColumnIfMissing("content_category_rel", "category_id", "BIGINT NULL AFTER content_id");
        addColumnIfMissing("content_category_rel", "is_primary", "TINYINT NOT NULL DEFAULT 0 AFTER category_id");
    }

    private void migrateLegacyRelations() {
        if (!columnExists("content_category_rel", "category_code")) {
            return;
        }

        // Preserve any custom legacy category before converting its relation to an id.
        jdbcTemplate.update("""
                INSERT INTO knowledge_category (parent_id, name, code, sort_order, status, created_at, updated_at)
                SELECT 0, r.category_code, r.category_code, 999, 1, NOW(), NOW()
                FROM (SELECT DISTINCT category_code FROM content_category_rel WHERE category_code IS NOT NULL) r
                LEFT JOIN knowledge_category kc ON kc.code = r.category_code
                WHERE kc.id IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE content_category_rel r
                JOIN knowledge_category kc ON kc.code = r.category_code
                SET r.category_id = kc.id
                WHERE r.category_id IS NULL
                """);

        Long unresolved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_category_rel WHERE category_id IS NULL", Long.class);
        if (unresolved != null && unresolved == 0 && columnIsNullable("content_category_rel", "category_id")) {
            jdbcTemplate.execute("ALTER TABLE content_category_rel MODIFY COLUMN category_id BIGINT NOT NULL");
        } else {
            if (unresolved != null && unresolved > 0) {
                log.warn("Knowledge relation migration left {} unresolved rows", unresolved);
            }
        }

        // Keep the legacy code for older import scripts, but allow new code to insert by category_id only.
        if (!columnIsNullable("content_category_rel", "category_code")) {
            jdbcTemplate.execute("ALTER TABLE content_category_rel MODIFY COLUMN category_code VARCHAR(64) NULL");
        }
    }

    private void seedCanonicalCategories() {
        upsertCategory("疾病知识", "DISEASE", "🏥", 1);
        upsertCategory("药品说明", "DRUG", "💊", 2);
        upsertCategory("疫苗接种", "VACCINE", "💉", 3);
        upsertCategory("传染病防控", "EPIDEMIC", "🦠", 4);
        upsertCategory("健康科普", "HEALTH_SCIENCE", "❤️", 5);
        upsertCategory("医学术语标准", "MEDICAL_TERM", "📋", 6);
    }

    private void upsertCategory(String name, String code, String icon, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_category (parent_id, name, code, icon, sort_order, status)
                VALUES (0, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name), icon = VALUES(icon), sort_order = VALUES(sort_order), status = 1
                """, name, code, icon, sortOrder);
    }

    private void mergeCategoryAlias(String legacyCode, String canonicalCode,
                                    String name, String icon, int sortOrder) {
        Long legacyId = categoryId(legacyCode);
        Long canonicalId = categoryId(canonicalCode);
        if (legacyId == null || canonicalId == null || legacyId.equals(canonicalId)) {
            return;
        }

        jdbcTemplate.update("""
                DELETE legacy_rel
                FROM content_category_rel legacy_rel
                JOIN content_category_rel canonical_rel
                  ON canonical_rel.content_id = legacy_rel.content_id
                 AND canonical_rel.category_id = ?
                WHERE legacy_rel.category_id = ?
                """, canonicalId, legacyId);
        jdbcTemplate.update(
                "UPDATE content_category_rel SET category_id = ? WHERE category_id = ?",
                canonicalId, legacyId);
        if (columnExists("content_category_rel", "category_code")) {
            jdbcTemplate.update(
                    "UPDATE content_category_rel SET category_code = ? WHERE category_code = ?",
                    canonicalCode, legacyCode);
        }
        jdbcTemplate.update("DELETE FROM knowledge_category WHERE id = ?", legacyId);
        jdbcTemplate.update("""
                UPDATE knowledge_category
                SET name = ?, icon = ?, sort_order = ?, status = 1
                WHERE id = ?
                """, name, icon, sortOrder, canonicalId);
    }

    private void ensureRelationIndexes() {
        addIndexIfMissing("content_category_rel", "idx_content_category_category",
                "KEY idx_content_category_category (category_id)");
        addIndexIfMissing("content_category_rel", "uk_content_category_id",
                "UNIQUE KEY uk_content_category_id (content_id, category_id)");
    }

    private void markInstalled() {
        jdbcTemplate.update("""
                INSERT INTO schema_migration (version, description)
                VALUES ('V005', '健康百科分类新旧数据库结构兼容')
                ON DUPLICATE KEY UPDATE description = VALUES(description)
                """);
    }

    private void logSchemaSummary() {
        Long categories = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_category WHERE status = 1", Long.class);
        Long relations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_category_rel WHERE category_id IS NOT NULL", Long.class);
        log.info("Knowledge schema ready: categories={}, relations={}", categories, relations);
    }

    private Long categoryId(String code) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM knowledge_category WHERE code = ? LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void renameColumnIfNeeded(String table, String legacyColumn,
                                      String canonicalColumn, String definition) {
        if (columnExists(table, legacyColumn) && !columnExists(table, canonicalColumn)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " CHANGE COLUMN " + legacyColumn + " "
                    + canonicalColumn + " " + definition);
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void reconcileLegacyTextColumn(String legacyColumn, String canonicalColumn,
                                           String nullableDefinition) {
        if (!columnExists("knowledge_category", legacyColumn)
                || !columnExists("knowledge_category", canonicalColumn)) {
            return;
        }

        // 处理重复的 category_code：相同值的多行中，保留 id 最小的行，其余追加 _id 后缀
        jdbcTemplate.update("UPDATE knowledge_category kc1 "
                + "INNER JOIN knowledge_category kc2 ON kc1." + legacyColumn + " = kc2." + legacyColumn + " "
                + "SET kc1." + canonicalColumn + " = CONCAT(kc1." + legacyColumn + ", '_', kc1.id) "
                + "WHERE kc1.id > kc2.id "
                + "AND (kc1." + canonicalColumn + " IS NULL OR kc1." + canonicalColumn + " = '') "
                + "AND kc1." + legacyColumn + " IS NOT NULL AND kc1." + legacyColumn + " != ''");

        // 处理 category_code 与已有的 code 值冲突
        jdbcTemplate.update("UPDATE knowledge_category kc1 "
                + "INNER JOIN knowledge_category kc2 ON kc1." + legacyColumn + " = kc2." + canonicalColumn + " "
                + "SET kc1." + canonicalColumn + " = CONCAT(kc1." + legacyColumn + ", '_', kc1.id) "
                + "WHERE (kc1." + canonicalColumn + " IS NULL OR kc1." + canonicalColumn + " = '') "
                + "AND kc1.id != kc2.id");

        // 剩余无冲突的行直接复制
        jdbcTemplate.update("UPDATE knowledge_category SET " + canonicalColumn + " = " + legacyColumn
                + " WHERE " + canonicalColumn + " IS NULL OR " + canonicalColumn + " = ''");

        if (!columnIsNullable("knowledge_category", legacyColumn)) {
            jdbcTemplate.execute("ALTER TABLE knowledge_category MODIFY COLUMN "
                    + legacyColumn + " " + nullableDefinition);
        }
    }

    private void addIndexIfMissing(String table, String index, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, table, index);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + definition);
        }
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean columnIsNullable(String table, String column) {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, String.class, table, column);
        return "YES".equalsIgnoreCase(nullable);
    }
}
