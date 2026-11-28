package com.mtx.rdp.messaging.devonly;

public record SentDevOnlyMessage(String topic, int partition, long offset) {
}
