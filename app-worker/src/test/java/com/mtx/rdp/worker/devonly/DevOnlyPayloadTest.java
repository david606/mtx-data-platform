package com.mtx.rdp.worker.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mtx.rdp.connector.simulation.FictionalMessageSource;
import java.nio.charset.StandardCharsets;
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
        assertThrows(IllegalArgumentException.class, () -> DevOnlyChainSettings.from(Map.of(
                DevOnlyChainSettings.MYSQL_USER, "root",
                DevOnlyChainSettings.MYSQL_PASSWORD, "secret",
                DevOnlyChainSettings.DORIS_USER, "dev_only_user",
                DevOnlyChainSettings.DORIS_PASSWORD, "secret")));
    }
}
