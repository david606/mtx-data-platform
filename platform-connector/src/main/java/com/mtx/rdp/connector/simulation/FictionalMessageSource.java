package com.mtx.rdp.connector.simulation;

import java.nio.charset.StandardCharsets;

/**
 * 虚构报文
 * <pre>
 * 1. 生成一份标明虚构的完整报文。不连接业务平台，不读取外部配置，也不创建 Task 或 Pipeline。
 * 2. 类型出现在 classpath 上不等于获得运行时使用许可。
 * </pre>
 */
public final class FictionalMessageSource {

    static final String MESSAGE_TEXT = """
            DEV_ONLY
            虚构数据
            本报文只存在于当前进程的测试输入中，不是业务平台数据。
            """;

    private static final byte[] MESSAGE_BYTES = MESSAGE_TEXT.getBytes(StandardCharsets.UTF_8);

    public byte[] message() {
        return MESSAGE_BYTES.clone();
    }
}
