package com.mtx.rdp.messaging.devonly;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class DevOnlyKafkaLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "MTX_RDP_DEV_KAFKA_LIVE", matches = "true")
    void bytesRoundTripWithoutChange() {
        String bootstrap = System.getenv().getOrDefault("MTX_RDP_DEV_KAFKA_BOOTSTRAP", "192.168.1.51:9092");
        DevOnlyKafkaSettings settings = new DevOnlyKafkaSettings(bootstrap);
        byte[] payload = "DEV_ONLY\n虚构数据\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (DevOnlyRawMessageSender sender = new DevOnlyRawMessageSender(settings);
                DevOnlyRawMessageReceiver receiver = new DevOnlyRawMessageReceiver(settings)) {
            SentDevOnlyMessage sent = sender.send(payload);
            byte[] received = receiver.read(sent, Duration.ofSeconds(20));
            assertArrayEquals(payload, received);
            receiver.commit(sent);
        }
    }
}
