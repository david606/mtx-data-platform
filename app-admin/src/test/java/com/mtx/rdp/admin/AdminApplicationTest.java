package com.mtx.rdp.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mtx.rdp.common.PlatformConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class AdminApplicationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void startsAdminApplication() {
        assertEquals(AdminApplication.APPLICATION_NAME, environment.getProperty("spring.application.name"));
        assertEquals("admin", environment.getProperty("mtx.rdp.role"));
        assertEquals(Status.UP, healthEndpoint.health().getStatus());
    }

    @Test
    void rejectsWorkerRoleOnAdminProcess() {
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                new SpringApplicationBuilder(AdminApplication.class)
                        .run("--mtx.rdp.role=worker"));

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
