package com.mtx.rdp.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mtx.rdp.common.PlatformConfigurationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

class WorkerEnvironmentProfileTest {

    @Test
    void missingProfileFailsAndDoesNotUseCompany() {
        PlatformConfigurationException exception = rootConfigurationFailure("--spring.profiles.active=");
        assertEquals("未选择开发环境 company 或 local，拒绝启动", exception.getMessage());
        assertFalse(exception.getMessage().contains("192.168.1.50"));
    }

    @Test
    void missingPasswordFails() {
        PlatformConfigurationException exception = rootConfigurationFailure(
                "--spring.profiles.active=company",
                "--mtx.rdp.dev.mysql.username=dev_only_user",
                "--mtx.rdp.dev.mysql.password=",
                "--mtx.rdp.dev.doris.username=dev_only_user",
                "--mtx.rdp.dev.doris.password=invalid-example");
        assertEquals("MySQL 密码不能为空", exception.getMessage());
    }

    private static PlatformConfigurationException rootConfigurationFailure(String... args) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(WorkerApplication.class)
                    .run(args)) {
                context.stop();
            }
        });
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        assertInstanceOf(PlatformConfigurationException.class, root);
        return (PlatformConfigurationException) root;
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    @TestPropertySource(properties = {
            "mtx.rdp.dev.mysql.username=dev_only_user",
            "mtx.rdp.dev.mysql.password=invalid-example",
            "mtx.rdp.dev.doris.username=dev_only_user",
            "mtx.rdp.dev.doris.password=invalid-example"
    })
    class LocalProfile {

        @Autowired
        private WorkerDevInfrastructureProperties devInfrastructure;

        @Test
        void bindsLoopbackAndNotCompanyBe() {
            assertEquals("LOCAL", devInfrastructure.getPlacement());
            assertEquals("127.0.0.1", devInfrastructure.getMysql().getHost());
            assertEquals(3306, devInfrastructure.getMysql().getPort());
            assertEquals("mtx_rdp_dev_only", devInfrastructure.getMysql().getDatabase());
            assertEquals("127.0.0.1:9092", devInfrastructure.getKafka().getBootstrapServers());
            assertEquals(9030, devInfrastructure.getDoris().getFeSqlPort());
            assertEquals(8030, devInfrastructure.getDoris().getFeHttpPort());
            assertEquals("mtx_rdp_dev_only", devInfrastructure.getDoris().getDatabase());
            assertEquals("127.0.0.1:8040", devInfrastructure.getDoris().getBeAllowlist());
            assertFalse(devInfrastructure.getDoris().getBeAllowlist().contains("192.168.1."));
            assertFalse(devInfrastructure.publicDescription().contains("invalid-example"));
        }
    }
}
