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
import java.util.UUID;

/**
 * 一条 DEV_ONLY 测试链路。夹具标识不是正式来源事件身份，领取也不是正式执行权。
 * MySQL 插入成功后才提交 Kafka offset。Doris 未成功时任务记为 FAILED，不把 HTTP 请求当成写入成功。
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
            String rawRef = existing == null ? "raw" + runId : existing.rawRef();
            String taskId = existing == null ? "task" + runId : existing.taskId();
            if (existing == null) {
                mysql.insertRawAndTask(rawRef, taskId, received, sent.topic(), sent.partition(), sent.offset(), now);
            }
            receiver.commit(sent);
            if (!mysql.claim(taskId, Instant.now())) {
                return DevOnlyRunReport.failed(runId, "测试任务没有被本次领取");
            }
            try {
                doris.open();
                doris.applySchema();
                DorisStreamLoadWriter writer = new DorisStreamLoadWriter(
                        settings.dorisHost(), settings.dorisHttpPort(), settings.dorisUser(), settings.dorisPassword());
                DevOnlyExecutionResult result = new DevOnlySingleExecution().execute(
                        fixture(), DevOnlyFieldPassthrough::pass,
                        call -> write(writer, settings, runId, taskId, rawRef, call));
                return finish(mysql, doris, runId, taskId, rawRef, sent, mysqlVersion, charset, collation, result);
            } catch (RuntimeException exception) {
                mysql.markFailed(taskId, Instant.now(), exception.getMessage());
                throw exception;
            }
        } catch (RuntimeException exception) {
            String detail = exception.getMessage() == null ? "DEV_ONLY 链路失败" : exception.getMessage();
            Throwable cause = exception.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                detail = detail + ": " + cause.getMessage();
            }
            return DevOnlyRunReport.failed(runId, detail);
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

    private static DevOnlyRunReport finish(
            DevOnlyMysqlStore mysql,
            DevOnlyDorisQuery doris,
            String runId,
            String taskId,
            String rawRef,
            SentDevOnlyMessage sent,
            String mysqlVersion,
            String charset,
            String collation,
            DevOnlyExecutionResult result) {
        if (!(result instanceof DevOnlyExecutionResult.Completed completed)) {
            mysql.markFailed(taskId, Instant.now(), result.toString());
            return DevOnlyRunReport.failed(runId, "Pipeline 未完成: " + result);
        }
        mysql.markCompleted(taskId, Instant.now());
        DevOnlyMysqlStore.RawView raw = mysql.findRaw(rawRef);
        DevOnlyMysqlStore.TaskView task = mysql.findTask(taskId);
        DevOnlyDorisQuery.ResultRow row = doris.find(runId);
        boolean matched = raw != null && raw.devOnly() == 1
                && task != null && task.devOnly() == 1 && "COMPLETED".equals(task.status())
                && row != null && rawRef.equals(row.rawRef()) && taskId.equals(row.taskId())
                && "dev-only-contract".equals(row.contractVersionId())
                && "DEV_ONLY fixture field".equals(row.resultPayload())
                && ("1".equals(row.devOnly()) || "true".equalsIgnoreCase(row.devOnly()));
        if (!matched) {
            return DevOnlyRunReport.failed(runId, "Doris 或 MySQL 查询结果与本次夹具不一致");
        }
        return new DevOnlyRunReport(true, runId, mysqlVersion, charset, collation, sent.topic(), sent.partition(),
                sent.offset(), rawRef, taskId, task.status(), completed.contractVersionId(),
                completed.passthroughField(), row.devOnly(), "");
    }

    private static DevOnlyFixture fixture() {
        ContractFamilyId family = ContractFamilyId.of("dev-only-family");
        ExternalContractVersion version = ExternalContractVersion.declared("dev-only-v1");
        SnapshotContractVersionEntry entry = new SnapshotContractVersionEntry(
                "dev-only-snapshot", "dev-only-contract", family, version);
        return new DevOnlyFixture(true, "dev-only-snapshot", family, version, "DEV_ONLY fixture field", List.of(entry));
    }
}
