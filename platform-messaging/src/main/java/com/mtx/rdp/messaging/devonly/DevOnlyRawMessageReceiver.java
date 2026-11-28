package com.mtx.rdp.messaging.devonly;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

/**
 * 按刚发送的位点读取一条 DEV_ONLY 字节。自动提交关闭。
 * 这是单条测试读取，不是正式消费组位点方案。
 */
public final class DevOnlyRawMessageReceiver implements AutoCloseable {

    private final KafkaConsumer<byte[], byte[]> consumer;

    public DevOnlyRawMessageReceiver(DevOnlyKafkaSettings settings) {
        this(open(settings));
    }

    DevOnlyRawMessageReceiver(KafkaConsumer<byte[], byte[]> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer 不能为空");
        }
        this.consumer = consumer;
    }

    public byte[] read(SentDevOnlyMessage sent, Duration timeout) {
        if (sent == null) {
            throw new IllegalArgumentException("发送结果不能为空");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("读取时限必须大于 0");
        }
        TopicPartition partition = new TopicPartition(sent.topic(), sent.partition());
        consumer.assign(List.of(partition));
        consumer.seek(partition, sent.offset());
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<byte[], byte[]> record : records) {
                if (record.offset() == sent.offset()) {
                    byte[] value = record.value();
                    return value == null ? new byte[0] : value.clone();
                }
            }
        }
        throw new IllegalStateException("在限定时间内没有读到刚发送的 DEV_ONLY 消息");
    }

    public void commit(SentDevOnlyMessage sent) {
        if (sent == null) {
            throw new IllegalArgumentException("发送结果不能为空");
        }
        TopicPartition partition = new TopicPartition(sent.topic(), sent.partition());
        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(sent.offset() + 1)));
    }

    @Override
    public void close() {
        consumer.close();
    }

    private static KafkaConsumer<byte[], byte[]> open(DevOnlyKafkaSettings settings) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, DevOnlyKafkaSettings.GROUP_ID);
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "mtx-dev-only-consumer");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}
