package com.mtx.rdp.worker.devonly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 开发测试连接参数。口令只来自调用方给出的环境，不写死在代码里。
 */
public record DevOnlyChainSettings(
        String kafkaBootstrap,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUser,
        String mysqlPassword,
        String dorisHost,
        int dorisHttpPort,
        int dorisQueryPort,
        String dorisDatabase,
        String dorisUser,
        String dorisPassword) {

    public static final String MYSQL_USER = "MTX_RDP_DEV_MYSQL_USER";
    public static final String MYSQL_PASSWORD = "MTX_RDP_DEV_MYSQL_PASSWORD";
    public static final String DORIS_USER = "MTX_RDP_DEV_DORIS_USER";
    public static final String DORIS_PASSWORD = "MTX_RDP_DEV_DORIS_PASSWORD";

    public DevOnlyChainSettings {
        rejectRoot(mysqlUser, "MySQL");
        rejectRoot(dorisUser, "Doris");
        if (mysqlPassword == null || mysqlPassword.isBlank() || dorisPassword == null || dorisPassword.isBlank()) {
            throw new IllegalArgumentException("MySQL 和 Doris 密码不能为空");
        }
    }

    public static List<String> missingCredentialNames(Map<String, String> environment) {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, environment, MYSQL_USER);
        addIfBlank(missing, environment, MYSQL_PASSWORD);
        addIfBlank(missing, environment, DORIS_USER);
        addIfBlank(missing, environment, DORIS_PASSWORD);
        return List.copyOf(missing);
    }

    public static DevOnlyChainSettings from(Map<String, String> environment) {
        List<String> missing = missingCredentialNames(environment);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少环境变量: " + String.join(", ", missing));
        }
        return new DevOnlyChainSettings(
                value(environment, "MTX_RDP_DEV_KAFKA_BOOTSTRAP", "192.168.1.51:9092"),
                value(environment, "MTX_RDP_DEV_MYSQL_HOST", "192.168.1.50"),
                integer(environment, "MTX_RDP_DEV_MYSQL_PORT", 3306),
                value(environment, "MTX_RDP_DEV_MYSQL_DATABASE", "mtx_rdp_dev_only"),
                environment.get(MYSQL_USER),
                environment.get(MYSQL_PASSWORD),
                value(environment, "MTX_RDP_DEV_DORIS_HOST", "192.168.1.50"),
                integer(environment, "MTX_RDP_DEV_DORIS_HTTP_PORT", 8030),
                integer(environment, "MTX_RDP_DEV_DORIS_QUERY_PORT", 9030),
                value(environment, "MTX_RDP_DEV_DORIS_DATABASE", "mtx_rdp_dev_only"),
                environment.get(DORIS_USER),
                environment.get(DORIS_PASSWORD));
    }

    private static void addIfBlank(List<String> missing, Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int integer(Map<String, String> environment, String name, int fallback) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private static void rejectRoot(String username, String product) {
        if (username == null || username.isBlank() || "root".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException(product + " 应用账号不能为空，也不能使用 root");
        }
    }
}
