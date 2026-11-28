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
    void localRejectsCompanyAddress() {
        DevInfrastructure infrastructure = company();
        infrastructure.setPlacement("LOCAL");

        assertThrows(PlatformConfigurationException.class,
                () -> infrastructure.requireFor(DevelopmentPlacement.LOCAL));
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
}
