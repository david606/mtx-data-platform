package com.mtx.rdp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlatformTest {

    @Test
    void exposesApprovedProjectIdentity() {
        assertEquals("MTX Data Platform", Platform.NAME);
        assertEquals("MTX RDP", Platform.SHORT_NAME);
        assertEquals("com.mtx.rdp", Platform.GROUP_ID);
    }
}
