package com.mtx.rdp.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mtx.rdp.common.PlatformConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("company")
@TestPropertySource(properties = {
        "mtx.rdp.dev.mysql.username=dev_only_user",
        "mtx.rdp.dev.mysql.password=invalid-example",
        "mtx.rdp.dev.doris.username=dev_only_user",
        "mtx.rdp.dev.doris.password=invalid-example"
})
class WorkerApplicationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private WorkerDevInfrastructureProperties devInfrastructure;

    @Test
    void startsWorkerApplication() {
        assertEquals(WorkerApplication.APPLICATION_NAME, environment.getProperty("spring.application.name"));
        assertEquals("worker", environment.getProperty("mtx.rdp.role"));
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
        assertEquals("COMPANY", devInfrastructure.getPlacement());
        assertEquals("192.168.1.50", devInfrastructure.getMysql().getHost());
        assertEquals(3306, devInfrastructure.getMysql().getPort());
        assertEquals("192.168.1.51:9092", devInfrastructure.getKafka().getBootstrapServers());
        assertEquals("192.168.1.50:8040,192.168.1.51:8040", devInfrastructure.getDoris().getBeAllowlist());
        assertFalse(devInfrastructure.publicDescription().contains("invalid-example"));
        assertFalse(devInfrastructure.publicDescription().contains("dev_only_user"));
    }

    @Test
    void rejectsAdminRoleOnWorkerProcess() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(WorkerApplication.class)
                    .run("--mtx.rdp.role=admin")) {
                context.stop();
            }
        });

        assertInstanceOf(PlatformConfigurationException.class, rootCause(failure));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
