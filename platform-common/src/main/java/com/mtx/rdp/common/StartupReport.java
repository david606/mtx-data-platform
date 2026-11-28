package com.mtx.rdp.common;

import java.util.List;

/**
 * 一次启动校验的结果。环境标签是 LOCAL 或 COMPANY，不表示生产环境。
 */
public record StartupReport(
        String applicationName,
        ApplicationRole role,
        List<String> activeProfiles,
        String environmentLabel) {
}
