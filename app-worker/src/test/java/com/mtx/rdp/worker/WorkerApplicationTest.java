package com.mtx.rdp.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mtx.rdp.common.PlatformConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootTest
class WorkerApplicationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void startsWorkerApplication() {
        assertEquals(WorkerApplication.APPLICATION_NAME, environment.getProperty("spring.application.name"));
        assertEquals("worker", environment.getProperty("mtx.rdp.role"));
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
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
