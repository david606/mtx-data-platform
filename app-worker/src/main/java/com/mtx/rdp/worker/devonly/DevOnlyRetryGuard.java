package com.mtx.rdp.worker.devonly;

/**
 * DEV_ONLY 失败任务能否再次进入本次测试。不是正式 Worker 恢复、接管或 fencing。
 */
final class DevOnlyRetryGuard {

    private DevOnlyRetryGuard() {
    }

    static String rejection(DevOnlyMysqlStore.TaskView task) {
        if (task == null) {
            return "测试任务不存在";
        }
        if (task.devOnly() != 1) {
            return "只允许重新执行 DEV_ONLY 测试任务";
        }
        if ("COMPLETED".equals(task.status())) {
            return "COMPLETED 测试任务不能重复执行";
        }
        if (!"FAILED".equals(task.status())) {
            return "只允许从 FAILED 重新进入本次测试执行";
        }
        return null;
    }
}
