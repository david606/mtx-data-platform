package com.mtx.rdp.worker.devonly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;

/**
 * DEV_ONLY 测试表访问。同一 Kafka 位点不插入第二行。这不是正式 Create-or-Get。
 */
public final class DevOnlyMysqlStore implements AutoCloseable {

    private final DevOnlyChainSettings settings;
    private Connection connection;

    public DevOnlyMysqlStore(DevOnlyChainSettings settings) {
        this.settings = settings;
    }

    public void open() {
        connection = connect("jdbc:mysql://" + settings.mysqlHost() + ":" + settings.mysqlPort()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectionTimeZone=UTC",
                settings.mysqlUser(), settings.mysqlPassword());
    }

    public String serverVersion() {
        return queryString("SELECT VERSION()");
    }

    public String serverCharset() {
        return queryString("SHOW VARIABLES LIKE 'character_set_server'");
    }

    public String serverCollation() {
        return queryString("SHOW VARIABLES LIKE 'collation_server'");
    }

    public void applySchema() {
        executeScript("dev-only/mysql-schema.sql");
    }

    public ExistingRow findByPosition(String topic, int partition, long offset) {
        String sql = """
                SELECT raw_message.raw_message_ref, task.test_task_id, task.status
                FROM mtx_rdp_dev_only.dev_only_raw_message raw_message
                JOIN mtx_rdp_dev_only.dev_only_task task
                  ON task.raw_message_ref = raw_message.raw_message_ref
                WHERE raw_message.kafka_topic = ? AND raw_message.kafka_partition = ?
                  AND raw_message.kafka_offset = ? AND raw_message.dev_only = 1 AND task.dev_only = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, topic);
            statement.setInt(2, partition);
            statement.setLong(3, offset);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ExistingRow(rows.getString(1), rows.getString(2), rows.getString(3));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取已有 DEV_ONLY 测试行失败", exception);
        }
    }

    public void insertRawAndTask(
            String rawRef, String taskId, byte[] payload, String topic, int partition, long offset, Instant receivedAt) {
        if (!DevOnlyPayload.marked(payload)) {
            throw new IllegalArgumentException("无 DEV_ONLY 标记，不写入测试表");
        }
        try {
            connection.setAutoCommit(false);
            insertRaw(rawRef, payload, topic, partition, offset, receivedAt);
            insertTask(taskId, rawRef, receivedAt);
            connection.commit();
        } catch (SQLException exception) {
            rollback();
            throw new IllegalStateException("写入 DEV_ONLY 原文和测试任务失败", exception);
        } finally {
            enableAutoCommit();
        }
    }

    public boolean claim(String taskId, Instant claimedAt) {
        String sql = """
                UPDATE mtx_rdp_dev_only.dev_only_task
                SET status = 'CLAIMED', claimed_at = ?
                WHERE test_task_id = ? AND dev_only = 1 AND status = 'READY'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(claimedAt));
            statement.setString(2, taskId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("领取 DEV_ONLY 测试任务失败", exception);
        }
    }

    public void markCompleted(String taskId, Instant finishedAt) {
        updateFinish(taskId, "COMPLETED", finishedAt, null);
    }

    public void markFailed(String taskId, Instant finishedAt, String reason) {
        updateFinish(taskId, "FAILED", finishedAt, limit(reason));
    }

    public TaskView findTask(String taskId) {
        String sql = """
                SELECT test_task_id, raw_message_ref, dev_only, status, failure_reason
                FROM mtx_rdp_dev_only.dev_only_task
                WHERE test_task_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new TaskView(rows.getString(1), rows.getString(2), rows.getInt(3),
                        rows.getString(4), rows.getString(5));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询 DEV_ONLY 测试任务失败", exception);
        }
    }

    public RawView findRaw(String rawRef) {
        String sql = """
                SELECT raw_message_ref, dev_only, kafka_topic, kafka_partition, kafka_offset
                FROM mtx_rdp_dev_only.dev_only_raw_message
                WHERE raw_message_ref = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rawRef);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new RawView(rows.getString(1), rows.getInt(2), rows.getString(3),
                        rows.getInt(4), rows.getLong(5));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询 DEV_ONLY 原文失败", exception);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new IllegalStateException("关闭 MySQL 连接失败", exception);
            }
        }
    }

    private void insertRaw(
            String rawRef, byte[] payload, String topic, int partition, long offset, Instant receivedAt)
            throws SQLException {
        String sql = """
                INSERT INTO mtx_rdp_dev_only.dev_only_raw_message
                (raw_message_ref, dev_only, payload, kafka_topic, kafka_partition, kafka_offset, received_at, created_at)
                VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rawRef);
            statement.setBytes(2, payload);
            statement.setString(3, topic);
            statement.setInt(4, partition);
            statement.setLong(5, offset);
            statement.setTimestamp(6, Timestamp.from(receivedAt));
            statement.setTimestamp(7, Timestamp.from(receivedAt));
            statement.executeUpdate();
        }
    }

    private void insertTask(String taskId, String rawRef, Instant createdAt) throws SQLException {
        String sql = """
                INSERT INTO mtx_rdp_dev_only.dev_only_task
                (test_task_id, raw_message_ref, dev_only, status, created_at)
                VALUES (?, ?, 1, 'READY', ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId);
            statement.setString(2, rawRef);
            statement.setTimestamp(3, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }

    private void updateFinish(String taskId, String status, Instant finishedAt, String reason) {
        String sql = """
                UPDATE mtx_rdp_dev_only.dev_only_task
                SET status = ?, finished_at = ?, failure_reason = ?
                WHERE test_task_id = ? AND dev_only = 1 AND status = 'CLAIMED'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.from(finishedAt));
            statement.setString(3, reason);
            statement.setString(4, taskId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("DEV_ONLY 测试任务不在已领取状态，不能写成 " + status);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("更新 DEV_ONLY 测试任务状态失败", exception);
        }
    }

    private void executeScript(String resource) {
        String script = read(resource);
        try (Statement statement = connection.createStatement()) {
            for (String command : script.split(";")) {
                String sql = stripComments(command).trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("执行 DEV_ONLY MySQL 脚本失败", exception);
        }
    }

    private String queryString(String sql) {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            if (!rows.next()) {
                return "";
            }
            if (rows.getMetaData().getColumnCount() == 1) {
                return rows.getString(1);
            }
            return rows.getString(1) + "=" + rows.getString(2);
        } catch (SQLException exception) {
            throw new IllegalStateException("读取 MySQL 服务器信息失败", exception);
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            throw new IllegalStateException("回滚 DEV_ONLY 写入失败", exception);
        }
    }

    private void enableAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            throw new IllegalStateException("恢复 MySQL 自动提交失败", exception);
        }
    }

    private static Connection connect(String url, String user, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Properties properties = new Properties();
            properties.setProperty("user", user);
            properties.setProperty("password", password);
            return DriverManager.getConnection(url, properties);
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("连接 MySQL 失败", exception);
        }
    }

    private static String read(String resource) {
        try (var input = DevOnlyMysqlStore.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("缺少脚本 " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取脚本失败", exception);
        }
    }

    private static String stripComments(String command) {
        StringBuilder sql = new StringBuilder();
        for (String line : command.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        return sql.toString();
    }

    private static String limit(String reason) {
        if (reason == null || reason.isBlank()) {
            return "未说明";
        }
        String compact = reason.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }

    public record ExistingRow(String rawRef, String taskId, String status) {
    }

    public record TaskView(String taskId, String rawRef, int devOnly, String status, String failureReason) {
    }

    public record RawView(String rawRef, int devOnly, String topic, int partition, long offset) {
    }
}
