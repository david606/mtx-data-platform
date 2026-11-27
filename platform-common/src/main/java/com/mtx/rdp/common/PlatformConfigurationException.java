package com.mtx.rdp.common;

/**
 * 启动配置不满足进程职责。调用方必须让进程失败，不能改写成已启动。
 */
public class PlatformConfigurationException extends RuntimeException {

    public PlatformConfigurationException(String message) {
        super(message);
    }

    public PlatformConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
