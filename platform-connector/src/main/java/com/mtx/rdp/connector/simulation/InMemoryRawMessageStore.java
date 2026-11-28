package com.mtx.rdp.connector.simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 只在当前对象生命周期内保存完整原文。关闭后数据不可恢复。
 * 不写文件、数据库或其他外部介质，不承担 IR-15 的保留、审计或恢复。
 * 引用只对这个对象有效，不是跨进程标识。类型在 classpath 上不等于运行时使用许可。
 */
public final class InMemoryRawMessageStore {

    private final Object monitor = new Object();
    private final Map<String, byte[]> messages = new HashMap<>();
    private long nextReference = 1;
    private boolean closed;

    public String put(byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        byte[] stored = raw.clone();
        synchronized (monitor) {
            if (closed) {
                throw new IllegalStateException("进程内原文存储已关闭，不能写入");
            }
            String reference = "mem-" + nextReference;
            nextReference++;
            messages.put(reference, stored);
            return reference;
        }
    }

    public Read read(String reference) {
        synchronized (monitor) {
            if (closed) {
                return new Unavailable();
            }
            byte[] stored = messages.get(reference);
            if (stored == null) {
                return new NotFound();
            }
            return new Found(stored);
        }
    }

    public void close() {
        synchronized (monitor) {
            closed = true;
            messages.clear();
        }
    }

    /**
     * 一次读取的三种结果。FOUND 带独立副本，NOT_FOUND 表示存储仍可用但没有该引用，UNAVAILABLE 表示已经关闭。
     */
    public sealed interface Read permits Found, NotFound, Unavailable {
    }

    public record Found(byte[] body) implements Read {

        public Found {
            Objects.requireNonNull(body, "body");
            body = body.clone();
        }
    }

    public record NotFound() implements Read {
    }

    public record Unavailable() implements Read {
    }
}
