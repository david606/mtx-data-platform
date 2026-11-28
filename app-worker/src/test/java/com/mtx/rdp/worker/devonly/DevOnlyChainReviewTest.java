package com.mtx.rdp.worker.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mtx.rdp.messaging.devonly.SentDevOnlyMessage;
import com.mtx.rdp.pipeline.devonly.DevOnlyExecutionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevOnlyChainReviewTest {

    @Test
    void matchedDorisAndMysqlCompletesTheClaimedTask() {
        MemoryBook book = claimedBook();
        DevOnlyRunReport report = finish(book, lookup(matchingRow()), completedResult());
        assertTrue(report.success());
        assertEquals("COMPLETED", report.taskStatus());
        assertEquals("COMPLETED", book.status);
        assertEquals(1, book.completed);
        assertEquals(0, book.failed);
    }

    @Test
    void dorisWriteFailureDoesNotComplete() {
        MemoryBook book = claimedBook();
        DevOnlyRunReport report = finish(book, lookup(matchingRow()), new DevOnlyExecutionResult.WriteFailed("写入失败"));
        assertFalse(report.success());
        assertEquals("FAILED", book.status);
        assertEquals(0, book.completed);
    }

    @Test
    void dorisQueryFailureDoesNotComplete() {
        MemoryBook book = claimedBook();
        DevOnlyRunReport report = finish(book, ignored -> {
            throw new IllegalStateException("查询 DEV_ONLY Doris 结果失败");
        }, completedResult());
        assertFalse(report.success());
        assertEquals("查询 Doris 失败", report.failure());
        assertEquals("FAILED", book.status);
        assertEquals(0, book.completed);
    }

    @Test
    void missingDorisRowDoesNotComplete() {
        assertMismatch(null, "Doris 没有查到本次运行结果");
    }

    @Test
    void wrongDorisRawRefDoesNotComplete() {
        assertMismatch(row("other-raw", TASK, DevOnlyCompletion.CONTRACT, "1"), "Doris 原文引用不一致");
    }

    @Test
    void wrongDorisTaskIdDoesNotComplete() {
        assertMismatch(row(RAW, "other-task", DevOnlyCompletion.CONTRACT, "1"), "Doris 测试任务不一致");
    }

    @Test
    void wrongDorisContractDoesNotComplete() {
        assertMismatch(row(RAW, TASK, "other-contract", "1"), "Doris 契约版本不一致");
    }

    @Test
    void wrongDorisDevOnlyMarkerDoesNotComplete() {
        assertMismatch(row(RAW, TASK, DevOnlyCompletion.CONTRACT, "0"), "Doris DEV_ONLY 标记不一致");
    }

    @Test
    void missingMysqlRawDoesNotComplete() {
        MemoryBook book = claimedBook();
        book.rawPresent = false;
        DevOnlyRunReport report = finish(book, lookup(matchingRow()), completedResult());
        assertEquals("MySQL 原文不存在", report.failure());
        assertEquals("FAILED", book.status);
        assertEquals(0, book.completed);
    }

    @Test
    void unexpectedMysqlTaskStatusDoesNotComplete() {
        MemoryBook book = claimedBook();
        book.status = "FAILED";
        book.failureReason = "earlier";
        DevOnlyRunReport report = finish(book, lookup(matchingRow()), completedResult());
        assertEquals("MySQL 测试任务状态不符合预期", report.failure());
        assertEquals("FAILED", book.status);
        assertEquals("earlier", book.failureReason);
        assertEquals(0, book.completed);
        assertEquals(0, book.failed);
    }

    @Test
    void failedDevOnlyTaskCanReenterWithoutErasingPriorFailure() {
        MemoryBook book = claimedBook();
        book.status = "FAILED";
        book.failureReason = "first";
        book.runs.add(new MemoryBook.Run("run-old", "first", null));
        DevOnlyRunReport rejected = DevOnlyChainRunner.beginRerun(book, book.view(), "run-new");
        assertNull(rejected);
        assertEquals("CLAIMED", book.status);
        assertEquals("first", book.runs.get(0).failure);
        assertEquals("first", book.runs.get(1).prior);
        book.markFailed(TASK, "run-new", Instant.now(), "second");
        assertEquals("FAILED", book.status);
        assertEquals("second", book.failureReason);
        assertEquals("first", book.runs.get(0).failure);
        assertEquals("first", book.runs.get(1).prior);
        assertEquals(0, book.completed);
    }

    @Test
    void completedTaskCannotBeReexecuted() {
        MemoryBook book = claimedBook();
        book.status = "COMPLETED";
        DevOnlyRunReport report = DevOnlyChainRunner.beginRerun(book, book.view(), "run-again");
        assertFalse(report.success());
        assertEquals("COMPLETED 测试任务不能重复执行", report.failure());
        assertEquals("COMPLETED", book.status);
        assertEquals(0, book.reopened);
        assertEquals(0, book.completed);
    }

    @Test
    void taskWithoutDevOnlyMarkerCannotReenter() {
        MemoryBook book = claimedBook();
        book.status = "FAILED";
        book.devOnly = 0;
        DevOnlyRunReport report = DevOnlyChainRunner.beginRerun(book, book.view(), "run-other");
        assertEquals("只允许重新执行 DEV_ONLY 测试任务", report.failure());
        assertEquals("FAILED", book.status);
        assertEquals(0, book.reopened);
    }

    @Test
    void defaultDatabaseIsTheDevOnlyName() {
        DevOnlyChainSettings settings = DevOnlyChainSettings.from(credentials());
        assertEquals(DevOnlyDatabaseName.NAME, settings.mysqlDatabase());
        assertEquals(DevOnlyDatabaseName.NAME, settings.dorisDatabase());
        assertEquals(2, settings.dorisBeTargets().size());
    }

    @Test
    void illegalDatabaseNamesAreRejected() {
        for (String name : List.of("production", "mysql", "mtx_rdp_dev_only;drop", "mtx_rdp_dev_only.other")) {
            Map<String, String> environment = credentials();
            environment.put("MTX_RDP_DEV_MYSQL_DATABASE", name);
            assertThrows(IllegalArgumentException.class, () -> DevOnlyChainSettings.from(environment));
        }
        assertEquals(DevOnlyDatabaseName.NAME, DevOnlyDatabaseName.require(DevOnlyDatabaseName.NAME));
    }

    private static void assertMismatch(DevOnlyDorisQuery.ResultRow row, String expected) {
        MemoryBook book = claimedBook();
        DevOnlyRunReport report = finish(book, lookup(row), completedResult());
        assertEquals(expected, report.failure());
        assertEquals("FAILED", book.status);
        assertEquals(0, book.completed);
    }

    private static DevOnlyRunReport finish(
            MemoryBook book, DevOnlyResultLookup lookup, DevOnlyExecutionResult result) {
        return DevOnlyChainRunner.finishAfterWrite(book, lookup, RUN, TASK, RAW,
                new SentDevOnlyMessage("mtx.dev-only.raw-message", 0, 3),
                "8.0.4-rc-log", "utf8mb4", "utf8mb4_0900_ai_ci", result);
    }

    private static DevOnlyResultLookup lookup(DevOnlyDorisQuery.ResultRow row) {
        return ignored -> row;
    }

    private static DevOnlyExecutionResult.Completed completedResult() {
        return new DevOnlyExecutionResult.Completed(DevOnlyCompletion.CONTRACT, DevOnlyCompletion.FIELD);
    }

    private static DevOnlyDorisQuery.ResultRow matchingRow() {
        return row(RAW, TASK, DevOnlyCompletion.CONTRACT, "1");
    }

    private static DevOnlyDorisQuery.ResultRow row(String rawRef, String taskId, String contract, String devOnly) {
        return new DevOnlyDorisQuery.ResultRow(RUN, devOnly, taskId, rawRef, contract, DevOnlyCompletion.FIELD);
    }

    private static MemoryBook claimedBook() {
        MemoryBook book = new MemoryBook();
        book.status = "CLAIMED";
        return book;
    }

    private static Map<String, String> credentials() {
        Map<String, String> environment = new HashMap<>();
        environment.put(DevOnlyChainSettings.MYSQL_USER, "dev_only_user");
        environment.put(DevOnlyChainSettings.MYSQL_PASSWORD, "secret");
        environment.put(DevOnlyChainSettings.DORIS_USER, "dev_only_user");
        environment.put(DevOnlyChainSettings.DORIS_PASSWORD, "secret");
        return environment;
    }

    private static final String RUN = "run-1";
    private static final String TASK = "task-1";
    private static final String RAW = "raw-1";

    private static final class MemoryBook implements DevOnlyTaskBook {
        private String status;
        private int devOnly = 1;
        private String failureReason;
        private boolean rawPresent = true;
        private int completed;
        private int failed;
        private int reopened;
        private final List<Run> runs = new ArrayList<>();

        @Override
        public boolean reopenFailed(String taskId, String newRunId, Instant claimedAt) {
            if (devOnly != 1 || !"FAILED".equals(status)) {
                return false;
            }
            runs.add(new Run(newRunId, null, failureReason));
            status = "CLAIMED";
            reopened++;
            return true;
        }

        @Override
        public void markCompleted(String taskId, String runId, Instant finishedAt) {
            if (!"CLAIMED".equals(status)) {
                throw new IllegalStateException("not claimed");
            }
            status = "COMPLETED";
            completed++;
        }

        @Override
        public void markFailed(String taskId, String runId, Instant finishedAt, String reason) {
            if (!"CLAIMED".equals(status)) {
                throw new IllegalStateException("not claimed");
            }
            status = "FAILED";
            failureReason = reason;
            failed++;
        }

        @Override
        public DevOnlyMysqlStore.TaskView findTask(String taskId) {
            return view();
        }

        @Override
        public DevOnlyMysqlStore.RawView findRaw(String rawRef) {
            if (!rawPresent) {
                return null;
            }
            return new DevOnlyMysqlStore.RawView(RAW, devOnly, "mtx.dev-only.raw-message", 0, 3);
        }

        private DevOnlyMysqlStore.TaskView view() {
            return new DevOnlyMysqlStore.TaskView(TASK, RAW, devOnly, status, failureReason);
        }

        private record Run(String id, String failure, String prior) {
        }
    }
}
