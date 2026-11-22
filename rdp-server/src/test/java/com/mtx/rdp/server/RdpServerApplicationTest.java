package com.mtx.rdp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RdpServerApplicationTest {

    @Autowired
    private RdpServerApplication application;

    @Test
    void contextLoadsAndUsesSharedPlatformName() {
        assertEquals("MTX Data Platform", application.platformName());
    }
}
