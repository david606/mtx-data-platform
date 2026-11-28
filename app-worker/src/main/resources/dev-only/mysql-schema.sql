CREATE TABLE IF NOT EXISTS mtx_rdp_dev_only.dev_only_raw_message (
    raw_message_ref VARCHAR(64) NOT NULL,
    dev_only TINYINT NOT NULL,
    payload LONGBLOB NOT NULL,
    kafka_topic VARCHAR(255) NOT NULL,
    kafka_partition INT NOT NULL,
    kafka_offset BIGINT NOT NULL,
    received_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (raw_message_ref),
    UNIQUE KEY uq_dev_only_raw_position (kafka_topic, kafka_partition, kafka_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mtx_rdp_dev_only.dev_only_task (
    test_task_id VARCHAR(64) NOT NULL,
    raw_message_ref VARCHAR(64) NOT NULL,
    dev_only TINYINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    claimed_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    failure_reason VARCHAR(512) NULL,
    PRIMARY KEY (test_task_id),
    UNIQUE KEY uq_dev_only_task_raw (raw_message_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
