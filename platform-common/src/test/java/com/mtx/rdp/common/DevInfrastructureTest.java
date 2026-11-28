package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DevInfrastructureTest {

    @Test
    void descriptionOmitsAccountsAndPasswords() {
        DevInfrastructure infrastructure = company();
        String description = infrastructure.publicDescription();

        assertFalse(description.contains("dev_only_user"));
        assertFalse(description.contains("invalid-example"));
        assertFalse(description.contains("jdbc:"));
        assertEquals("COMPANY", infrastructure.getPlacement());
    }

    @Test
    void localAcceptsOnlyLoopback() {
        DevInfrastructure infrastructure = local();

        infrastructure.requireFor(DevelopmentPlacement.LOCAL);
        infrastructure.getMysql().setHost("192.168.1.50");

        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.LOCAL));
        assertEquals("MySQL 地址不在当前开发环境白名单内", exception.getMessage());
    }

    @Test
    void companyMysqlAcceptsOnlyTheListedAddress() {
        DevInfrastructure infrastructure = company();
        infrastructure.getMysql().setHost("192.168.1.51");

        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("MySQL 地址不在当前开发环境白名单内", exception.getMessage());
    }

    @Test
    void companyKafkaAcceptsOnlyTheListedAddress() {
        DevInfrastructure infrastructure = company();
        infrastructure.getKafka().setBootstrapServers("192.168.1.50:9092");

        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("Kafka 地址不在当前开发环境白名单内", exception.getMessage());
    }

    @Test
    void companyDorisFeAcceptsOnlyTheListedAddress() {
        DevInfrastructure infrastructure = company();
        infrastructure.getDoris().setFeSqlHost("192.168.1.51");

        PlatformConfigurationException sqlException = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("Doris FE SQL 地址不在当前开发环境白名单内", sqlException.getMessage());

        infrastructure.getDoris().setFeSqlHost("192.168.1.50");
        infrastructure.getDoris().setFeHttpPort(9030);
        PlatformConfigurationException httpException = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("Doris FE HTTP 地址不在当前开发环境白名单内", httpException.getMessage());
    }

    @Test
    void companyDorisBeAcceptsOnlyTheListedAddresses() {
        DevInfrastructure infrastructure = company();
        infrastructure.getDoris().setBeAllowlist("192.168.1.51:8040,192.168.1.50:8040");
        infrastructure.requireFor(DevelopmentPlacement.COMPANY);

        infrastructure.getDoris().setBeAllowlist("192.168.1.50:8040,10.0.0.8:8040");
        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("Doris BE 地址不在当前开发环境白名单内", exception.getMessage());
    }

    @Test
    void unknownAddressIsRejected() {
        DevInfrastructure infrastructure = company();
        infrastructure.getMysql().setHost("10.1.2.3");

        assertThrows(PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
    }

    @Test
    void rootAccountIsRejected() {
        DevInfrastructure infrastructure = company();
        infrastructure.getMysql().setUsername("root");

        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));
        assertEquals("MySQL 账号不能为空，也不能使用 root", exception.getMessage());
        assertFalse(exception.getMessage().contains("invalid-example"));
    }

    @Test
    void blankPasswordFailsWithoutEchoingIt() {
        DevInfrastructure infrastructure = company();
        infrastructure.getMysql().setPassword("   ");

        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.COMPANY));

        assertEquals("MySQL 密码不能为空", exception.getMessage());
        assertFalse(exception.getMessage().contains("invalid-example"));
    }

    private static DevInfrastructure company() {
        DevInfrastructure infrastructure = new DevInfrastructure();
        infrastructure.setPlacement("COMPANY");
        infrastructure.setScope(DevInfrastructure.SCOPE);
        DevInfrastructure.Mysql mysql = infrastructure.getMysql();
        mysql.setHost("192.168.1.50");
        mysql.setPort(3306);
        mysql.setDatabase("mtx_rdp_dev_only");
        mysql.setUsername("dev_only_user");
        mysql.setPassword("invalid-example");
        mysql.setConnectTimeout(Duration.ofSeconds(5));
        mysql.getPool().setMaximumSize(2);
        mysql.getPool().setMinimumIdle(0);
        DevInfrastructure.Kafka kafka = infrastructure.getKafka();
        kafka.setBootstrapServers("192.168.1.51:9092");
        kafka.setConsumerGroup("");
        kafka.setSecurityProtocol("PLAINTEXT");
        kafka.getProducer().setAcks("all");
        kafka.getConsumer().setEnableAutoCommit(false);
        DevInfrastructure.Doris doris = infrastructure.getDoris();
        doris.setFeSqlHost("192.168.1.50");
        doris.setFeSqlPort(9030);
        doris.setFeHttpHost("192.168.1.50");
        doris.setFeHttpPort(8030);
        doris.setBeAllowlist("192.168.1.50:8040,192.168.1.51:8040");
        doris.setDatabase("mtx_rdp_dev_only");
        doris.setUsername("dev_only_user");
        doris.setPassword("invalid-example");
        doris.setStreamLoadTimeout(Duration.ofSeconds(30));
        infrastructure.requireFor(DevelopmentPlacement.COMPANY);
        return infrastructure;
    }

    private static DevInfrastructure local() {
        DevInfrastructure infrastructure = company();
        infrastructure.setPlacement("LOCAL");
        infrastructure.getMysql().setHost("127.0.0.1");
        infrastructure.getKafka().setBootstrapServers("127.0.0.1:9092");
        infrastructure.getDoris().setFeSqlHost("127.0.0.1");
        infrastructure.getDoris().setFeHttpHost("127.0.0.1");
        infrastructure.getDoris().setBeAllowlist("127.0.0.1:8040");
        infrastructure.requireFor(DevelopmentPlacement.LOCAL);
        return infrastructure;
    }
}
