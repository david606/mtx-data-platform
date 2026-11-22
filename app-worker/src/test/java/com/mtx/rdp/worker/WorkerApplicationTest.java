package com.mtx.rdp.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class WorkerApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void startsWorkerApplication() {
        assertEquals("mtx-rdp-worker", environment.getProperty("spring.application.name"));
    }
}
