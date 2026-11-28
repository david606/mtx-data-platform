package com.mtx.rdp.pipeline.devonly;

/**
 * 单次 DEV_ONLY 执行的结果。不是正式任务终态。
 */
public sealed interface DevOnlyExecutionResult {

    record RejectedNotDevOnly() implements DevOnlyExecutionResult {
    }

    record NotExecuted(String reason) implements DevOnlyExecutionResult {
    }

    record Unresolved(String failureCode) implements DevOnlyExecutionResult {
    }

    record WriteFailed(String message) implements DevOnlyExecutionResult {
    }

    record Completed(String contractVersionId, String passthroughField) implements DevOnlyExecutionResult {
    }
}
