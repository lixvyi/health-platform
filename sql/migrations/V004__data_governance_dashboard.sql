CREATE TABLE IF NOT EXISTS data_governance_issue (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_code  VARCHAR(64) NOT NULL,
    source_name   VARCHAR(128),
    issue_type    VARCHAR(32) NOT NULL,
    description   VARCHAR(512) NOT NULL,
    detected_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    handler_note  VARCHAR(512),
    handled_by    VARCHAR(64),
    handled_at    DATETIME,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_governance_dataset_issue (dataset_code, issue_type),
    KEY idx_governance_status_time (status, detected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据治理异常人工处理记录';

INSERT IGNORE INTO data_governance_issue
    (dataset_code, source_name, issue_type, description, status)
SELECT dataset_code, source_name, 'IMPORT_ERROR',
       CONCAT('最近导入记录到 ', error_count, ' 条错误或跳过数据，请核对错误明细。'), 'PENDING'
FROM data_resource_dataset
WHERE error_count > 0;

INSERT IGNORE INTO data_governance_issue
    (dataset_code, source_name, issue_type, description, status)
SELECT dataset_code, source_name, 'DUPLICATE_DATA',
       CONCAT('检测到 ', duplicate_count, ' 条重复数据，需要确认去重规则。'), 'PENDING'
FROM data_resource_dataset
WHERE duplicate_count > 0;

INSERT IGNORE INTO data_governance_issue
    (dataset_code, source_name, issue_type, description, status)
SELECT dataset_code, source_name, 'MISSING_FIELD',
       CONCAT('字段完整度为 ', ROUND(completeness_rate * 100, 1), '%，低于治理阈值。'), 'PENDING'
FROM data_resource_dataset
WHERE completeness_rate IS NOT NULL AND completeness_rate < 0.98;

INSERT IGNORE INTO data_governance_issue
    (dataset_code, source_name, issue_type, description, status)
SELECT dataset_code, source_name, 'COLLECTION_FAILED',
       COALESCE(failure_reason, '最近一次采集或导入失败。'), 'PENDING'
FROM data_resource_dataset
WHERE update_status = 'FAILED';
