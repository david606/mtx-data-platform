package com.mtx.rdp.worker.devonly;

import com.mtx.rdp.connector.simulation.FictionalMessageSource;
import com.mtx.rdp.core.contract.ContractFamilyId;
import com.mtx.rdp.core.contract.ExternalContractVersion;
import com.mtx.rdp.core.contract.SnapshotContractVersionEntry;
import com.mtx.rdp.messaging.devonly.DevOnlyKafkaSettings;
import com.mtx.rdp.messaging.devonly.DevOnlyRawMessageReceiver;
import com.mtx.rdp.messaging.devonly.DevOnlyRawMessageSender;
import com.mtx.rdp.messaging.devonly.SentDevOnlyMessage;
import com.mtx.rdp.pipeline.devonly.DevOnlyExecutionResult;
import com.mtx.rdp.pipeline.devonly.DevOnlyFixture;
import com.mtx.rdp.pipeline.devonly.DevOnlySingleExecution;
import com.mtx.rdp.pipeline.devonly.DevOnlyWriteAnswer;
import com.mtx.rdp.transform.devonly.DevOnlyFieldPassthrough;
import com.mtx.rdp.writer.devonly.DorisStreamLoadResult;
import com.mtx.rdp.writer.devonly.DorisStreamLoadWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DEV_ONLY 测试链路
 * <pre>
 * 1. 一条 DEV_ONLY 测试链路。夹具标识不是正式来源事件身份，领取也不是正式执行权。
 * 2. MySQL 插入成功后才提交 Kafka offset。Doris 或核对失败时任务保持未完成，并记为 FAILED。
 * 3. 显式重新执行只接受 FAILED 的 DEV_ONLY 测试任务，不重新消费 Kafka，也不是正式恢复。
 * </pre>
 */
public final class DevOnlyChainRunner {

    private static final DateTimeFormatter DORIS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    public DevOnlyRunReport run(DevOnlyChainSettings settings) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        byte[] payload = new FictionalMessageSource().message();
        if (!DevOnlyPayload.marked(payload)) {
            return DevOnlyRunReport.failed(runId, "虚构来源缺少 DEV_ONLY 标记");
        }
        DevOnlyKafkaSettings kafka = new DevOnlyKafkaSettings(settings.kafkaBootstrap());
        try (DevOnlyRawMessageSender sender = new DevOnlyRawMessageSender(kafka);
                DevOnlyRawMessageReceiver receiver = new DevOnlyRawMessageReceiver(kafka);
                DevOnlyMysqlStore mysql = new DevOnlyMysqlStore(settings);
                DevOnlyDorisQuery doris = new DevOnlyDorisQuery(settings)) {
            SentDevOnlyMessage sent = sender.send(payload);
            byte[] received = receiver.read(sent, Duration.ofSeconds(20));
            if (!Arrays.equals(payload, received) || !DevOnlyPayload.marked(received)) {
                return DevOnlyRunReport.failed(runId, "消费到的字节与虚构报文不一致，未提交 offset");
            }
            mysql.open();
            String mysqlVersion = mysql.serverVersion();
            String charset = mysql.serverCharset();
            String collation = mysql.serverCollation();
            mysql.applySchema();
            Instant now = Instant.now();
            DevOnlyMysqlStore.ExistingRow existing = mysql.findByPosition(sent.topic(), sent.partition(), sent.offset());
            if (existing != null && !"READY".equals(existing.status())) {
                return DevOnlyRunReport.failed(runId, "已有 DEV_ONLY 测试任务状态为 " + existing.status() + "，本次运行不自动重试");
            }
            String rawRef = existing == null ? "raw" + runId : existing.rawRef();
            String taskId = existing == null ? "task" + runId : existing.taskId();
            if (existing == null) {
                mysql.insertRawAndTask(rawRef, taskId, received, sent.topic(), sent.partition(), sent.offset(), now);
            }
            receiver.commit(sent);
            if (!mysql.claim(taskId, Instant.now())) {
                return DevOnlyRunReport.failed(runId, "测试任务没有被本次领取");
            }
            mysql.beginRun(runId, taskId, Instant.now());
            return executeClaimed(settings, mysql, doris, runId, taskId, rawRef, sent, mysqlVersion, charset, collation);
        } catch (RuntimeException exception) {
            return DevOnlyRunReport.failed(runId, detail(exception));
        }
    }

    public DevOnlyRunReport rerunFailed(DevOnlyChainSettings settings, String taskId) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        if (taskId == null || !taskId.matches("[A-Za-z0-9]{1,64}")) {
            return DevOnlyRunReport.failed(runId, "测试任务标识无效");
        }
        try (DevOnlyMysqlStore mysql = new DevOnlyMysqlStore(settings);
                DevOnlyDorisQuery doris = new DevOnlyDorisQuery(settings)) {
            mysql.open();
            String mysqlVersion = mysql.serverVersion();
            String charset = mysql.serverCharset();
            String collation = mysql.serverCollation();
            mysql.applySchema();
            DevOnlyRunReport rejected = beginRerun(mysql, mysql.findTask(taskId), runId);
            if (rejected != null) {
                return rejected;
            }
            DevOnlyMysqlStore.TaskView task = mysql.findTask(taskId);
            DevOnlyMysqlStore.RawView raw = task == null ? null : mysql.findRaw(task.rawRef());
            if (raw == null || raw.devOnly() != 1) {
                mysql.markFailed(taskId, runId, Instant.now(), "MySQL 原文不存在");
                return DevOnlyRunReport.failed(runId, "MySQL 原文不存在");
            }
            SentDevOnlyMessage sent = new SentDevOnlyMessage(raw.topic(), raw.partition(), raw.offset());
            return executeClaimed(settings, mysql, doris, runId, taskId, raw.rawRef(), sent, mysqlVersion, charset, collation);
        } catch (RuntimeException exception) {
            return DevOnlyRunReport.failed(runId, detail(exception));
        }
    }

    static DevOnlyRunReport beginRerun(DevOnlyTaskBook tasks, DevOnlyMysqlStore.TaskView task, String runId) {
        String rejection = DevOnlyRetryGuard.rejection(task);
        if (rejection != null) {
            return DevOnlyRunReport.failed(runId, rejection);
        }
        if (!tasks.reopenFailed(task.taskId(), runId, Instant.now())) {
            return DevOnlyRunReport.failed(runId, "FAILED 测试任务没有进入本次重新执行");
        }
        return null;
    }

    private DevOnlyRunReport executeClaimed(
            DevOnlyChainSettings settings,
            DevOnlyMysqlStore mysql,
            DevOnlyDorisQuery doris,
            String runId,
            String taskId,
            String rawRef,
            SentDevOnlyMessage sent,
            String mysqlVersion,
            String charset,
            String collation) {
        try {
            doris.open();
            doris.applySchema();
            DorisStreamLoadWriter writer = new DorisStreamLoadWriter(
                    settings.dorisHost(), settings.dorisHttpPort(), settings.dorisUser(), settings.dorisPassword(),
                    Set.copyOf(settings.dorisBeTargets()));
            DevOnlyExecutionResult result = new DevOnlySingleExecution().execute(
                    fixture(), DevOnlyFieldPassthrough::pass,
                    call -> write(writer, settings, runId, taskId, rawRef, call));
            return finishAfterWrite(mysql, doris::find, runId, taskId, rawRef, sent, mysqlVersion, charset, collation, result);
        } catch (RuntimeException exception) {
            failIfClaimed(mysql, taskId, runId, exception.getMessage() == null ? "DEV_ONLY 链路失败" : exception.getMessage());
            throw exception;
        }
    }

    private static DevOnlyWriteAnswer write(
            DorisStreamLoadWriter writer,
            DevOnlyChainSettings settings,
            String runId,
            String taskId,
            String rawRef,
            com.mtx.rdp.pipeline.devonly.DevOnlyWriteCall call) {
        String json = "{"
                + "\"dev_run_id\":\"" + runId + "\","
                + "\"dev_only\":true,"
                + "\"test_task_id\":\"" + taskId + "\","
                + "\"raw_message_ref\":\"" + rawRef + "\","
                + "\"contract_version_id\":\"" + call.contractVersionId() + "\","
                + "\"result_payload\":\"" + call.passthroughField() + "\","
                + "\"processed_at\":\"" + DORIS_TIME.format(Instant.now()) + "\"}";
        DorisStreamLoadResult loaded = writer.load(
                settings.dorisDatabase(), "dev_only_result", "devonly" + runId, json.getBytes(StandardCharsets.UTF_8));
        return new DevOnlyWriteAnswer(loaded.success(), loaded.failureMessage(), loaded.streamStatus(),
                loaded.loadedRows(), loaded.filteredRows());
    }

    static DevOnlyRunReport finishAfterWrite(
            DevOnlyTaskBook mysql,
            DevOnlyResultLookup doris,
            String runId,
            String taskId,
            String rawRef,
            SentDevOnlyMessage sent,
            String mysqlVersion,
            String charset,
            String collation,
            DevOnlyExecutionResult result) {
        if (!(result instanceof DevOnlyExecutionResult.Completed completed)) {
            failIfClaimed(mysql, taskId, runId, "Pipeline 未完成: " + result);
            return DevOnlyRunReport.failed(runId, "Pipeline 未完成: " + result);
        }
        DevOnlyDorisQuery.ResultRow row;
        try {
            row = doris.find(runId);
        } catch (RuntimeException exception) {
            failIfClaimed(mysql, taskId, runId, "查询 Doris 失败");
            return DevOnlyRunReport.failed(runId, "查询 Doris 失败");
        }
        DevOnlyMysqlStore.RawView raw = mysql.findRaw(rawRef);
        DevOnlyMysqlStore.TaskView task = mysql.findTask(taskId);
        String mismatch = DevOnlyCompletion.mismatch(completed, row, raw, task, runId, taskId, rawRef);
        if (mismatch != null) {
            failIfClaimed(mysql, taskId, runId, mismatch);
            return DevOnlyRunReport.failed(runId, mismatch);
        }
        mysql.markCompleted(taskId, runId, Instant.now());
        return new DevOnlyRunReport(true, runId, mysqlVersion, charset, collation, sent.topic(), sent.partition(),
                sent.offset(), rawRef, taskId, "COMPLETED", completed.contractVersionId(),
                completed.passthroughField(), row.devOnly(), "");
    }

    private static void failIfClaimed(DevOnlyTaskBook mysql, String taskId, String runId, String reason) {
        DevOnlyMysqlStore.TaskView task = mysql.findTask(taskId);
        if (task != null && task.devOnly() == 1 && "CLAIMED".equals(task.status())) {
            mysql.markFailed(taskId, runId, Instant.now(), reason);
        }
    }

    private static String detail(RuntimeException exception) {
        String message = exception.getMessage() == null ? "DEV_ONLY 链路失败" : exception.getMessage();
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return message + ": " + cause.getMessage();
        }
        return message;
    }

    private static DevOnlyFixture fixture() {
        ContractFamilyId family = ContractFamilyId.of("dev-only-family");
        ExternalContractVersion version = ExternalContractVersion.declared("dev-only-v1");
        SnapshotContractVersionEntry entry = new SnapshotContractVersionEntry(
                "dev-only-snapshot", "dev-only-contract", family, version);
        return new DevOnlyFixture(true, "dev-only-snapshot", family, version, "DEV_ONLY fixture field", List.of(entry));
    }
}
