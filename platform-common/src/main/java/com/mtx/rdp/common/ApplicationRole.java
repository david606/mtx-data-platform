package com.mtx.rdp.common;

/**
 * 进程职责。只区分管理端和执行端，不表示任务执行权或业务权限。
 */
public enum ApplicationRole {

    ADMIN("admin"),
    WORKER("worker");

    private final String configValue;

    ApplicationRole(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static ApplicationRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformConfigurationException("mtx.rdp.role 不能为空");
        }
        for (ApplicationRole role : values()) {
            if (role.configValue.equals(value)) {
                return role;
            }
        }
        throw new PlatformConfigurationException("mtx.rdp.role 无法识别: " + value);
    }
}
