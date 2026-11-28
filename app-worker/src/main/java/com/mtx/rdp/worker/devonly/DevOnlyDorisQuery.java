package com.mtx.rdp.worker.devonly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Doris 测试库的建表和查询。写入走 HTTP Stream Load，不在这里完成。
 */
public final class DevOnlyDorisQuery implements AutoCloseable {

    private final DevOnlyChainSettings settings;
    private Connection connection;

    public DevOnlyDorisQuery(DevOnlyChainSettings settings) {
        this.settings = settings;
    }

    public void open() {
        String url = "jdbc:mysql://" + settings.dorisHost() + ":" + settings.dorisQueryPort()
            + "/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectionTimeZone=UTC";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Properties properties = new Properties();
            properties.setProperty("user", settings.dorisUser());
            properties.setProperty("password", settings.dorisPassword());
            connection = DriverManager.getConnection(url, properties);
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("连接 Doris 查询端口失败", exception);
        }
    }

    public void applySchema() {
        String database = DevOnlyDatabaseName.require(settings.dorisDatabase());
        String script = read("dev-only/doris-schema.sql").replace("__DEV_ONLY_DATABASE__", database);
        try (Statement statement = connection.createStatement()) {
            for (String command : script.split(";")) {
                String sql = command.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("执行 DEV_ONLY Doris 脚本失败", exception);
        }
    }

    public ResultRow find(String devRunId) {
        String database = DevOnlyDatabaseName.require(settings.dorisDatabase());
        String sql = """
            SELECT dev_run_id, dev_only, test_task_id, raw_message_ref, contract_version_id, result_payload
            FROM %s.dev_only_result
            WHERE dev_run_id = ?
            """.formatted(database);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, devRunId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ResultRow(rows.getString(1), rows.getString(2), rows.getString(3),
                    rows.getString(4), rows.getString(5), rows.getString(6));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询 DEV_ONLY Doris 结果失败", exception);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new IllegalStateException("关闭 Doris 连接失败", exception);
            }
        }
    }

    private static String read(String resource) {
        try (var input = DevOnlyDorisQuery.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("缺少脚本 " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取脚本失败", exception);
        }
    }

    public record ResultRow(
        String devRunId,
        String devOnly,
        String taskId,
        String rawRef,
        String contractVersionId,
        String resultPayload) {
    }
}
