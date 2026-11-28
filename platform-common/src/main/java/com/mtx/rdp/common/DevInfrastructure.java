package com.mtx.rdp.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 开发环境里的服务地址和凭据。不建立连接。描述文本不包含账号、密码或 JDBC URL。
 */
public class DevInfrastructure {

    public static final String SCOPE = "DEV_ONLY";
    private static final String LOOPBACK = "127.0.0.1";

    private String placement;
    private String scope;
    private Mysql mysql = new Mysql();
    private Kafka kafka = new Kafka();
    private Doris doris = new Doris();

    public void requireFor(DevelopmentPlacement expected) {
        if (expected == null) {
            throw new PlatformConfigurationException("开发环境不能为空");
        }
        if (!expected.label().equals(placement)) {
            throw new PlatformConfigurationException("配置中的环境标识与激活的 profile 不一致");
        }
        if (!SCOPE.equals(scope)) {
            throw new PlatformConfigurationException("开发连接范围必须是 DEV_ONLY");
        }
        requireService(mysql, "MySQL", expected);
        requireKafka(expected);
        requireDoris(expected);
    }

    public String publicDescription() {
        return "placement=" + placement
                + " scope=" + scope
                + " mysql=" + mysql.host + ":" + mysql.port + "/" + mysql.database
                + " kafka=" + kafka.bootstrapServers
                + " dorisFeSql=" + doris.feSqlHost + ":" + doris.feSqlPort
                + " dorisFeHttp=" + doris.feHttpHost + ":" + doris.feHttpPort
                + " dorisDatabase=" + doris.database
                + " dorisBe=" + doris.beAllowlist;
    }

    private static void requireService(Mysql mysql, String name, DevelopmentPlacement expected) {
        requireHost(name, mysql.host, expected);
        requirePort(name, mysql.port);
        requireText(name + " 数据库名不能为空", mysql.database);
        requireAccount(name, mysql.username, mysql.password);
        requireDuration(name + " 连接超时", mysql.connectTimeout);
        if (mysql.pool.maximumSize < 1 || mysql.pool.minimumIdle < 0
                || mysql.pool.minimumIdle > mysql.pool.maximumSize) {
            throw new PlatformConfigurationException(name + " 连接池参数无效");
        }
    }

    private void requireKafka(DevelopmentPlacement expected) {
        requireText("Kafka bootstrap 不能为空", kafka.bootstrapServers);
        HostPort bootstrap = hostPort(kafka.bootstrapServers, "Kafka bootstrap");
        requireAddress(bootstrap.host(), expected, "Kafka");
        requireText("Kafka 安全协议不能为空", kafka.securityProtocol);
        requireText("Kafka producer acks 不能为空", kafka.producer.acks);
        if (kafka.consumerGroup == null) {
            throw new PlatformConfigurationException("Kafka consumer group 配置入口不能缺省");
        }
    }

    private void requireDoris(DevelopmentPlacement expected) {
        requireHost("Doris FE SQL", doris.feSqlHost, expected);
        requirePort("Doris FE SQL", doris.feSqlPort);
        requireHost("Doris FE HTTP", doris.feHttpHost, expected);
        requirePort("Doris FE HTTP", doris.feHttpPort);
        requireText("Doris 数据库名不能为空", doris.database);
        requireAccount("Doris", doris.username, doris.password);
        requireDuration("Doris Stream Load 超时", doris.streamLoadTimeout);
        List<HostPort> targets = beTargets(doris.beAllowlist);
        if (targets.isEmpty()) {
            throw new PlatformConfigurationException("Doris BE 允许列表不能为空");
        }
        for (HostPort target : targets) {
            requireAddress(target.host(), expected, "Doris BE");
        }
    }

    private static void requireHost(String name, String host, DevelopmentPlacement expected) {
        requireText(name + " 主机不能为空", host);
        requireAddress(host, expected, name);
    }

    private static void requireAddress(String host, DevelopmentPlacement expected, String name) {
        boolean loopback = LOOPBACK.equals(host);
        if (expected == DevelopmentPlacement.LOCAL && !loopback) {
            throw new PlatformConfigurationException(name + " 在 local 环境必须使用 127.0.0.1");
        }
        if (expected == DevelopmentPlacement.COMPANY && loopback) {
            throw new PlatformConfigurationException(name + " 在 company 环境不能使用 127.0.0.1");
        }
    }

    private static void requirePort(String name, int port) {
        if (port < 1 || port > 65535) {
            throw new PlatformConfigurationException(name + " 端口无效");
        }
    }

    private static void requireAccount(String name, String username, String password) {
        if (username == null || username.isBlank() || "root".equalsIgnoreCase(username)) {
            throw new PlatformConfigurationException(name + " 账号不能为空，也不能使用 root");
        }
        if (password == null || password.isBlank()) {
            throw new PlatformConfigurationException(name + " 密码不能为空");
        }
    }

    private static void requireDuration(String name, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new PlatformConfigurationException(name + " 无效");
        }
    }

    private static void requireText(String message, String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformConfigurationException(message);
        }
    }

    private static HostPort hostPort(String value, String name) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new PlatformConfigurationException(name + " 无效");
        }
        int port;
        try {
            port = Integer.parseInt(value.substring(colon + 1));
        } catch (NumberFormatException exception) {
            throw new PlatformConfigurationException(name + " 无效");
        }
        requirePort(name, port);
        return new HostPort(value.substring(0, colon), port);
    }

    private static List<HostPort> beTargets(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<HostPort> targets = new ArrayList<>();
        for (String item : raw.split(",")) {
            String token = item.trim();
            if (!token.isEmpty()) {
                targets.add(hostPort(token, "Doris BE"));
            }
        }
        return targets;
    }

    public String getPlacement() {
        return placement;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Mysql getMysql() {
        return mysql;
    }

    public void setMysql(Mysql mysql) {
        this.mysql = mysql;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Doris getDoris() {
        return doris;
    }

    public void setDoris(Doris doris) {
        this.doris = doris;
    }

    public static final class Mysql {
        private String host;
        private int port;
        private String database;
        private String username;
        private String password;
        private Duration connectTimeout;
        private Pool pool = new Pool();

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Pool getPool() {
            return pool;
        }

        public void setPool(Pool pool) {
            this.pool = pool;
        }
    }

    public static final class Pool {
        private int maximumSize;
        private int minimumIdle;

        public int getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }
    }

    public static final class Kafka {
        private String bootstrapServers;
        private String consumerGroup;
        private String securityProtocol;
        private Producer producer = new Producer();
        private Consumer consumer = new Consumer();

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getSecurityProtocol() {
            return securityProtocol;
        }

        public void setSecurityProtocol(String securityProtocol) {
            this.securityProtocol = securityProtocol;
        }

        public Producer getProducer() {
            return producer;
        }

        public void setProducer(Producer producer) {
            this.producer = producer;
        }

        public Consumer getConsumer() {
            return consumer;
        }

        public void setConsumer(Consumer consumer) {
            this.consumer = consumer;
        }
    }

    public static final class Producer {
        private String acks;

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }
    }

    public static final class Consumer {
        private boolean enableAutoCommit;

        public boolean isEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }
    }

    public static final class Doris {
        private String feSqlHost;
        private int feSqlPort;
        private String feHttpHost;
        private int feHttpPort;
        private String beAllowlist;
        private String database;
        private String username;
        private String password;
        private Duration streamLoadTimeout;

        public String getFeSqlHost() {
            return feSqlHost;
        }

        public void setFeSqlHost(String feSqlHost) {
            this.feSqlHost = feSqlHost;
        }

        public int getFeSqlPort() {
            return feSqlPort;
        }

        public void setFeSqlPort(int feSqlPort) {
            this.feSqlPort = feSqlPort;
        }

        public String getFeHttpHost() {
            return feHttpHost;
        }

        public void setFeHttpHost(String feHttpHost) {
            this.feHttpHost = feHttpHost;
        }

        public int getFeHttpPort() {
            return feHttpPort;
        }

        public void setFeHttpPort(int feHttpPort) {
            this.feHttpPort = feHttpPort;
        }

        public String getBeAllowlist() {
            return beAllowlist;
        }

        public void setBeAllowlist(String beAllowlist) {
            this.beAllowlist = beAllowlist;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Duration getStreamLoadTimeout() {
            return streamLoadTimeout;
        }

        public void setStreamLoadTimeout(Duration streamLoadTimeout) {
            this.streamLoadTimeout = streamLoadTimeout;
        }
    }

    private record HostPort(String host, int port) {
    }
}
