package com.mtx.rdp.messaging.devonly;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * 发送 DEV_ONLY 原始字节。不改报文正文。
 */
public final class DevOnlyRawMessageSender implements AutoCloseable {

    private final String topic;
    private final Producer<byte[], byte[]> producer;

    public DevOnlyRawMessageSender(DevOnlyKafkaSettings settings) {
        this(DevOnlyKafkaSettings.TOPIC, open(settings));
    }

    DevOnlyRawMessageSender(String topic, Producer<byte[], byte[]> producer) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        if (producer == null) {
            throw new IllegalArgumentException("producer 不能为空");
        }
        this.topic = topic;
        this.producer = producer;
    }

    public SentDevOnlyMessage send(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("原始报文不能为空");
        }
        try {
            RecordMetadata metadata = producer
                    .send(new ProducerRecord<>(topic, null, payload.clone()))
                    .get(30, TimeUnit.SECONDS);
            return new SentDevOnlyMessage(metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送 DEV_ONLY 消息被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("发送 DEV_ONLY 消息失败", exception);
        }
    }

    @Override
    public void close() {
        producer.close();
    }

    private static Producer<byte[], byte[]> open(DevOnlyKafkaSettings settings) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, "mtx-dev-only-producer");
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return new KafkaProducer<>(properties);
    }
}
