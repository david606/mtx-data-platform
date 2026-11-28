package com.mtx.rdp.worker.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mtx.rdp.connector.simulation.FictionalMessageSource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DevOnlyPayloadTest {

    @Test
    void fictionalSourceIsMarked() {
        assertTrue(DevOnlyPayload.marked(new FictionalMessageSource().message()));
    }

    @Test
    void unmarkedBytesAreRejected() {
        assertFalse(DevOnlyPayload.marked("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingCredentialsAreNamed() {
        assertEquals(
                java.util.List.of(
                        DevOnlyChainSettings.MYSQL_USER,
                        DevOnlyChainSettings.MYSQL_PASSWORD,
                        DevOnlyChainSettings.DORIS_USER,
                        DevOnlyChainSettings.DORIS_PASSWORD),
                DevOnlyChainSettings.missingCredentialNames(Map.of()));
    }

    @Test
    void rootAccountIsRejected() {
        Map<String, String> environment = new HashMap<>();
        environment.put(DevOnlyChainSettings.MYSQL_USER, "root");
        environment.put(DevOnlyChainSettings.MYSQL_PASSWORD, "secret");
        environment.put(DevOnlyChainSettings.DORIS_USER, "dev_only_user");
        environment.put(DevOnlyChainSettings.DORIS_PASSWORD, "secret");
        environment.put("MTX_RDP_DEV_KAFKA_BOOTSTRAP", "127.0.0.1:9092");
        environment.put("MTX_RDP_DEV_MYSQL_HOST", "127.0.0.1");
        environment.put("MTX_RDP_DEV_MYSQL_PORT", "3306");
        environment.put("MTX_RDP_DEV_DORIS_HOST", "127.0.0.1");
        environment.put("MTX_RDP_DEV_DORIS_HTTP_PORT", "8030");
        environment.put("MTX_RDP_DEV_DORIS_QUERY_PORT", "9030");
        environment.put("MTX_RDP_DEV_DORIS_BE", "127.0.0.1:8040");
        assertThrows(IllegalArgumentException.class, () -> DevOnlyChainSettings.from(environment));
    }
}
