package com.mtx.rdp.worker.devonly;

/**
 * 一次 DEV_ONLY 运行的可打印结果。不含口令。
 */
public record DevOnlyRunReport(
        boolean success,
        String devRunId,
        String mysqlVersion,
        String mysqlCharset,
        String mysqlCollation,
        String topic,
        int partition,
        long offset,
        String rawRef,
        String taskId,
        String taskStatus,
        String contractVersionId,
        String passthroughField,
        String dorisDevOnly,
        String failure) {

    public static DevOnlyRunReport failed(String devRunId, String failure) {
        return new DevOnlyRunReport(false, devRunId, "", "", "", "", -1, -1, "", "", "", "", "", "", failure);
    }

    public String toDisplayString() {
        return """
                success=%s
                dev_run_id=%s
                mysql_version=%s
                mysql_charset=%s
                mysql_collation=%s
                kafka_topic=%s
                kafka_partition=%s
                kafka_offset=%s
                raw_message_ref=%s
                test_task_id=%s
                task_status=%s
                contract_version_id=%s
                passthrough_field=%s
                doris_dev_only=%s
                failure=%s
                """.formatted(success, devRunId, mysqlVersion, mysqlCharset, mysqlCollation, topic, partition,
                offset, rawRef, taskId, taskStatus, contractVersionId, passthroughField, dorisDevOnly, failure);
    }
}
