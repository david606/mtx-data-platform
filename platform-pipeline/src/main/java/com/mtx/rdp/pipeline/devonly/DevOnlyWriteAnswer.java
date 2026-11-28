package com.mtx.rdp.pipeline.devonly;

public record DevOnlyWriteAnswer(
        boolean success,
        String failureMessage,
        String streamStatus,
        long loadedRows,
        long filteredRows) {
}
