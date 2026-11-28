package com.mtx.rdp.connector.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FictionalMessageSourceTest {

    @Test
    void returnsCompleteFictionalBytesMarkedDevOnly() {
        FictionalMessageSource source = new FictionalMessageSource();
        byte[] expected = FictionalMessageSource.MESSAGE_TEXT.getBytes(StandardCharsets.UTF_8);

        byte[] first = source.message();
        byte[] second = source.message();

        assertArrayEquals(expected, first);
        assertArrayEquals(expected, second);
        String text = new String(first, StandardCharsets.UTF_8);
        assertTrue(text.contains("DEV_ONLY"));
        assertTrue(text.contains("虚构数据"));
    }

    @Test
    void callerMutationDoesNotChangeTheNextCompleteMessage() {
        FictionalMessageSource source = new FictionalMessageSource();
        byte[] expected = FictionalMessageSource.MESSAGE_TEXT.getBytes(StandardCharsets.UTF_8);

        byte[] first = source.message();
        first[0] = 0;

        assertArrayEquals(expected, source.message());
    }
}
