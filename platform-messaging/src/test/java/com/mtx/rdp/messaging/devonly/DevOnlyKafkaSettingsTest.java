package com.mtx.rdp.messaging.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DevOnlyKafkaSettingsTest {

    @Test
    void topicStaysOnTheDevOnlyName() {
        assertEquals("mtx.dev-only.raw-message", DevOnlyKafkaSettings.TOPIC);
        assertEquals("mtx.dev-only.raw-message-worker", DevOnlyKafkaSettings.GROUP_ID);
    }

    @Test
    void blankBootstrapIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DevOnlyKafkaSettings(" "));
    }
}
