package com.mtx.rdp.common;

import java.util.List;

/**
 * 一次启动校验的结果。环境标签只反映已激活的 Spring profile，不选择部署环境。
 */
public record StartupReport(
        String applicationName,
        ApplicationRole role,
        List<String> activeProfiles,
        String environmentLabel) {
}
