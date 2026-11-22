package com.mtx.rdp.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class AdminApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void startsAdminApplication() {
        assertEquals("mtx-rdp-admin", environment.getProperty("spring.application.name"));
    }
}
