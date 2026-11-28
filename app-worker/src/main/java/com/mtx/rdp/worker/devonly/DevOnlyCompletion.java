package com.mtx.rdp.worker.devonly;

import com.mtx.rdp.pipeline.devonly.DevOnlyExecutionResult;

/**
 * 在写入 Task 终态之前核对 Doris 和 MySQL。核对失败时不表示任务已经完成。
 */
final class DevOnlyCompletion {

    static final String CONTRACT = "dev-only-contract";
    static final String FIELD = "DEV_ONLY fixture field";

    private DevOnlyCompletion() {
    }

    static String mismatch(
        DevOnlyExecutionResult.Completed completed,
        DevOnlyDorisQuery.ResultRow row,
        DevOnlyMysqlStore.RawView raw,
        DevOnlyMysqlStore.TaskView task,
        String runId,
        String taskId,
        String rawRef) {
        if (row == null) {
            return "Doris 没有查到本次运行结果";
        }
        if (!runId.equals(row.devRunId())) {
            return "Doris 测试运行标识不一致";
        }
        if (!rawRef.equals(row.rawRef())) {
            return "Doris 原文引用不一致";
        }
        if (!taskId.equals(row.taskId())) {
            return "Doris 测试任务不一致";
        }
        if (!CONTRACT.equals(row.contractVersionId())) {
            return "Doris 契约版本不一致";
        }
        if (!devOnly(row.devOnly())) {
            return "Doris DEV_ONLY 标记不一致";
        }
        if (!FIELD.equals(row.resultPayload()) || !CONTRACT.equals(completed.contractVersionId())
            || !FIELD.equals(completed.passthroughField())) {
            return "Doris 结果字段不一致";
        }
        if (raw == null) {
            return "MySQL 原文不存在";
        }
        if (task == null) {
            return "MySQL 测试任务不存在";
        }
        if (raw.devOnly() != 1 || task.devOnly() != 1) {
            return "MySQL DEV_ONLY 标记不一致";
        }
        if (!rawRef.equals(raw.rawRef()) || !rawRef.equals(task.rawRef()) || !taskId.equals(task.taskId())) {
            return "MySQL 原文和任务没有关联";
        }
        if (!"CLAIMED".equals(task.status())) {
            return "MySQL 测试任务状态不符合预期";
        }
        return null;
    }

    private static boolean devOnly(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
