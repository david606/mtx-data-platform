package com.mtx.rdp.messaging.devonly;

/**
 * DEV_ONLY 测试 topic 的连接参数。不表示正式业务 topic。
 */
public record DevOnlyKafkaSettings(String bootstrapServers) {

    public static final String TOPIC = "mtx.dev-only.raw-message";

    public static final String GROUP_ID = "mtx.dev-only.raw-message-worker";

    public DevOnlyKafkaSettings {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap 不能为空");
        }
    }
}
