CREATE TABLE IF NOT EXISTS __DEV_ONLY_DATABASE__.dev_only_result (
    dev_run_id VARCHAR(64) NOT NULL,
    dev_only BOOLEAN NOT NULL,
    test_task_id VARCHAR(64) NOT NULL,
    raw_message_ref VARCHAR(64) NOT NULL,
    contract_version_id VARCHAR(128) NOT NULL,
    result_payload STRING NOT NULL,
    processed_at DATETIME NOT NULL
)
DUPLICATE KEY(dev_run_id)
DISTRIBUTED BY HASH(dev_run_id) BUCKETS 1
PROPERTIES ("replication_num" = "2");
