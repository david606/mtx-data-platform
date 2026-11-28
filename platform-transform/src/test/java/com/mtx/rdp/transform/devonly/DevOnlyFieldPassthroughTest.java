package com.mtx.rdp.transform.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DevOnlyFieldPassthroughTest {

    @Test
    void returnsTheSameFieldText() {
        assertEquals("DEV_ONLY fixture field", DevOnlyFieldPassthrough.pass("DEV_ONLY fixture field"));
    }

    @Test
    void blankFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DevOnlyFieldPassthrough.pass(" "));
    }
}
