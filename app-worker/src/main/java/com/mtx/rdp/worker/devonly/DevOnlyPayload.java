package com.mtx.rdp.worker.devonly;

import java.nio.charset.StandardCharsets;

/**
 * 只接受同时带有 DEV_ONLY 和「虚构数据」的测试字节。
 */
public final class DevOnlyPayload {

    private DevOnlyPayload() {
    }

    public static boolean marked(byte[] payload) {
        if (payload == null) {
            return false;
        }
        String text = new String(payload, StandardCharsets.UTF_8);
        return text.contains("DEV_ONLY") && text.contains("虚构数据");
    }
}
