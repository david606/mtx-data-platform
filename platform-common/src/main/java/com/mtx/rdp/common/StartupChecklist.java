package com.mtx.rdp.common;

import java.util.List;

/**
 * 核对进程名、职责和开发环境。必须明确选择 company 或 local，不默认成公司环境。
 */
public final class StartupChecklist {

    private StartupChecklist() {
    }

    public static StartupReport check(
            String applicationName,
            String expectedApplicationName,
            String roleValue,
            ApplicationRole expectedRole,
            List<String> activeProfiles) {
        if (applicationName == null || applicationName.isBlank()) {
            throw new PlatformConfigurationException("spring.application.name 不能为空");
        }
        if (expectedApplicationName == null || expectedApplicationName.isBlank()) {
            throw new PlatformConfigurationException("进程期望的应用名不能为空");
        }
        if (!applicationName.equals(expectedApplicationName)) {
            throw new PlatformConfigurationException(
                    "spring.application.name 与进程职责不一致: " + applicationName);
        }
        if (expectedRole == null) {
            throw new PlatformConfigurationException("进程期望的职责不能为空");
        }
        ApplicationRole actualRole = ApplicationRole.parse(roleValue);
        if (actualRole != expectedRole) {
            throw new PlatformConfigurationException(
                    "mtx.rdp.role 与进程职责不一致: " + actualRole.configValue());
        }
        List<String> profiles = copyProfiles(activeProfiles);
        DevelopmentPlacement placement = DevelopmentProfile.requireSingle(profiles);
        return new StartupReport(applicationName, actualRole, profiles, placement.label());
    }

    private static List<String> copyProfiles(List<String> activeProfiles) {
        if (activeProfiles == null) {
            throw new PlatformConfigurationException("激活的配置环境不能为 null");
        }
        for (String profile : activeProfiles) {
            if (profile == null || profile.isBlank()) {
                throw new PlatformConfigurationException("激活的配置环境不能为空白");
            }
        }
        return List.copyOf(activeProfiles);
    }
}
