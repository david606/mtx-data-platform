package com.mtx.rdp.worker.devonly;

import java.time.Instant;

/**
 * DEV_ONLY 测试任务状态
 * <pre>
 * 1. DEV_ONLY 测试任务的状态变化。COMPLETED 只能从已领取写入。
 * 2. 重新进入只允许 FAILED 且带 DEV_ONLY 标记的测试任务，并保留原先的失败说明。
 * </pre>
 */
interface DevOnlyTaskBook {

    boolean reopenFailed(String taskId, String newRunId, Instant claimedAt);

    void markCompleted(String taskId, String runId, Instant finishedAt);

    void markFailed(String taskId, String runId, Instant finishedAt, String reason);

    DevOnlyMysqlStore.TaskView findTask(String taskId);

    DevOnlyMysqlStore.RawView findRaw(String rawRef);
}
