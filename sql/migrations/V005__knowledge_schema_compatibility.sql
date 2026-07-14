-- 健康百科分类兼容迁移（MySQL 5.7）
-- 可重复执行；保留旧版分类和内容关联数据，同时补齐当前代码使用的字段。
USE health_portal;

CREATE TABLE IF NOT EXISTS schema_migration (
    version VARCHAR(32) NOT NULL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库迁移记录';

DELIMITER $$

DROP PROCEDURE IF EXISTS v005_rename_column_if_needed$$
CREATE PROCEDURE v005_rename_column_if_needed(
    IN p_table VARCHAR(64), IN p_old VARCHAR(64), IN p_new VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_old
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_new
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` CHANGE COLUMN `', p_old, '` `', p_new, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_add_column_if_missing$$
CREATE PROCEDURE v005_add_column_if_missing(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_add_index_if_missing$$
CREATE PROCEDURE v005_add_index_if_missing(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_reconcile_legacy_text_column$$
CREATE PROCEDURE v005_reconcile_legacy_text_column(
    IN p_old VARCHAR(64), IN p_new VARCHAR(64), IN p_nullable_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_category' AND COLUMN_NAME = p_old
    ) AND EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_category' AND COLUMN_NAME = p_new
    ) THEN
        SET @ddl = CONCAT(
            'UPDATE knowledge_category SET `', p_new, '` = `', p_old,
            '` WHERE `', p_new, '` IS NULL OR `', p_new, '` = '''''
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        IF EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_category'
              AND COLUMN_NAME = p_old AND IS_NULLABLE = 'NO'
        ) THEN
            SET @ddl = CONCAT('ALTER TABLE knowledge_category MODIFY COLUMN `', p_old, '` ', p_nullable_definition);
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_reconcile_legacy_sort_order$$
CREATE PROCEDURE v005_reconcile_legacy_sort_order()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_category' AND COLUMN_NAME = 'display_order'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'knowledge_category' AND COLUMN_NAME = 'sort_order'
    ) THEN
        UPDATE knowledge_category
        SET sort_order = display_order
        WHERE sort_order = 0 AND display_order <> 0;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_migrate_legacy_relations$$
CREATE PROCEDURE v005_migrate_legacy_relations()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_category_rel' AND COLUMN_NAME = 'category_code'
    ) THEN
        INSERT INTO knowledge_category (parent_id, name, code, sort_order, status, created_at, updated_at)
        SELECT 0, r.category_code, r.category_code, 999, 1, NOW(), NOW()
        FROM (SELECT DISTINCT category_code FROM content_category_rel WHERE category_code IS NOT NULL) r
        LEFT JOIN knowledge_category kc ON kc.code = r.category_code
        WHERE kc.id IS NULL;

        UPDATE content_category_rel r
        JOIN knowledge_category kc ON kc.code = r.category_code
        SET r.category_id = kc.id
        WHERE r.category_id IS NULL;

        IF EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_category_rel'
              AND COLUMN_NAME = 'category_code' AND IS_NULLABLE = 'NO'
        ) THEN
            ALTER TABLE content_category_rel MODIFY COLUMN category_code VARCHAR(64) NULL;
        END IF;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_finalize_category_id$$
CREATE PROCEDURE v005_finalize_category_id()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_category_rel'
          AND COLUMN_NAME = 'category_id' AND IS_NULLABLE = 'YES'
    ) AND NOT EXISTS (SELECT 1 FROM content_category_rel WHERE category_id IS NULL LIMIT 1) THEN
        ALTER TABLE content_category_rel MODIFY COLUMN category_id BIGINT NOT NULL;
    END IF;
END$$

DROP PROCEDURE IF EXISTS v005_update_legacy_relation_code$$
CREATE PROCEDURE v005_update_legacy_relation_code(IN p_old VARCHAR(64), IN p_new VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_category_rel' AND COLUMN_NAME = 'category_code'
    ) THEN
        SET @ddl = CONCAT(
            'UPDATE content_category_rel SET category_code = ', QUOTE(p_new),
            ' WHERE category_code = ', QUOTE(p_old)
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康百科分类';

CALL v005_rename_column_if_needed('knowledge_category', 'category_code', 'code', 'VARCHAR(64) NOT NULL');
CALL v005_rename_column_if_needed('knowledge_category', 'category_name', 'name', 'VARCHAR(64) NOT NULL');
CALL v005_rename_column_if_needed('knowledge_category', 'display_order', 'sort_order', 'INT NOT NULL DEFAULT 0');
CALL v005_reconcile_legacy_text_column('category_code', 'code', 'VARCHAR(64) NULL');
CALL v005_reconcile_legacy_text_column('category_name', 'name', 'VARCHAR(64) NULL');
CALL v005_reconcile_legacy_sort_order();
CALL v005_add_column_if_missing('knowledge_category', 'parent_id', 'BIGINT NOT NULL DEFAULT 0 AFTER id');
CALL v005_add_column_if_missing('knowledge_category', 'icon', 'VARCHAR(32) NULL AFTER code');
CALL v005_add_column_if_missing('knowledge_category', 'updated_at',
    'DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at');
CALL v005_add_index_if_missing('knowledge_category', 'idx_knowledge_category_parent',
    'KEY idx_knowledge_category_parent (parent_id, sort_order)');

CREATE TABLE IF NOT EXISTS content_category_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_content_category (content_id, category_id),
    KEY idx_content_category_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='百科内容与分类关系';

CALL v005_add_column_if_missing('content_category_rel', 'category_id', 'BIGINT NULL AFTER content_id');
CALL v005_add_column_if_missing('content_category_rel', 'is_primary', 'TINYINT NOT NULL DEFAULT 0 AFTER category_id');
CALL v005_migrate_legacy_relations();

INSERT INTO knowledge_category (parent_id, name, code, icon, sort_order, status) VALUES
(0, '疾病知识', 'DISEASE', '🏥', 1, 1),
(0, '药品说明', 'DRUG', '💊', 2, 1),
(0, '疫苗接种', 'VACCINE', '💉', 3, 1),
(0, '传染病防控', 'EPIDEMIC', '🦠', 4, 1),
(0, '健康科普', 'HEALTH_SCIENCE', '❤️', 5, 1),
(0, '医学术语标准', 'MEDICAL_TERM', '📋', 6, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), icon = VALUES(icon), sort_order = VALUES(sort_order), status = 1;

SET @health_legacy_id = (SELECT id FROM knowledge_category WHERE code = 'HEALTH_POPULARIZATION' LIMIT 1);
SET @health_id = (SELECT id FROM knowledge_category WHERE code = 'HEALTH_SCIENCE' LIMIT 1);
DELETE legacy_rel FROM content_category_rel legacy_rel
JOIN content_category_rel canonical_rel
  ON canonical_rel.content_id = legacy_rel.content_id AND canonical_rel.category_id = @health_id
WHERE legacy_rel.category_id = @health_legacy_id;
DELETE legacy_code_rel FROM content_category_rel legacy_code_rel
JOIN content_category_rel canonical_code_rel
  ON canonical_code_rel.content_id = legacy_code_rel.content_id
 AND canonical_code_rel.category_code = 'HEALTH_SCIENCE'
WHERE legacy_code_rel.category_code = 'HEALTH_POPULARIZATION';
UPDATE content_category_rel SET category_id = @health_id WHERE category_id = @health_legacy_id;
CALL v005_update_legacy_relation_code('HEALTH_POPULARIZATION', 'HEALTH_SCIENCE');
DELETE FROM knowledge_category WHERE id = @health_legacy_id;

SET @term_legacy_id = (SELECT id FROM knowledge_category WHERE code = 'MEDICAL_TERMS' LIMIT 1);
SET @term_id = (SELECT id FROM knowledge_category WHERE code = 'MEDICAL_TERM' LIMIT 1);
DELETE legacy_rel FROM content_category_rel legacy_rel
JOIN content_category_rel canonical_rel
  ON canonical_rel.content_id = legacy_rel.content_id AND canonical_rel.category_id = @term_id
WHERE legacy_rel.category_id = @term_legacy_id;
DELETE legacy_code_rel FROM content_category_rel legacy_code_rel
JOIN content_category_rel canonical_code_rel
  ON canonical_code_rel.content_id = legacy_code_rel.content_id
 AND canonical_code_rel.category_code = 'MEDICAL_TERM'
WHERE legacy_code_rel.category_code = 'MEDICAL_TERMS';
UPDATE content_category_rel SET category_id = @term_id WHERE category_id = @term_legacy_id;
CALL v005_update_legacy_relation_code('MEDICAL_TERMS', 'MEDICAL_TERM');
DELETE FROM knowledge_category WHERE id = @term_legacy_id;

CALL v005_finalize_category_id();
CALL v005_add_index_if_missing('content_category_rel', 'idx_content_category_category',
    'KEY idx_content_category_category (category_id)');
CALL v005_add_index_if_missing('content_category_rel', 'uk_content_category_id',
    'UNIQUE KEY uk_content_category_id (content_id, category_id)');

INSERT INTO schema_migration (version, description)
VALUES ('V005', '健康百科分类新旧数据库结构兼容')
ON DUPLICATE KEY UPDATE description = VALUES(description);

DROP PROCEDURE IF EXISTS v005_rename_column_if_needed;
DROP PROCEDURE IF EXISTS v005_add_column_if_missing;
DROP PROCEDURE IF EXISTS v005_add_index_if_missing;
DROP PROCEDURE IF EXISTS v005_reconcile_legacy_text_column;
DROP PROCEDURE IF EXISTS v005_reconcile_legacy_sort_order;
DROP PROCEDURE IF EXISTS v005_migrate_legacy_relations;
DROP PROCEDURE IF EXISTS v005_finalize_category_id;
DROP PROCEDURE IF EXISTS v005_update_legacy_relation_code;
