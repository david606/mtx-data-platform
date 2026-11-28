package com.mtx.rdp.writer.devonly;

/**
 * Doris Stream Load 的判定结果。HTTP 成功不等于写入成功。
 */
public record DorisStreamLoadResult(
        boolean success,
        int httpStatus,
        String streamStatus,
        long loadedRows,
        long filteredRows,
        String failureMessage) {

    public static DorisStreamLoadResult failure(int httpStatus, String streamStatus, String failureMessage) {
        return new DorisStreamLoadResult(false, httpStatus, streamStatus, 0, 0, failureMessage);
    }
}
