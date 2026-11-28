package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StartupChecklistTest {

    @Test
    void rejectsStartupWhenNoDevelopmentProfileIsActive() {
        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-admin",
                        "mtx-rdp-admin",
                        "admin",
                        ApplicationRole.ADMIN,
                        List.of()));

        assertEquals("未选择开发环境 company 或 local，拒绝启动", exception.getMessage());
    }

    @Test
    void reportsLocalPlacementWithoutUsingCompany() {
        StartupReport report = StartupChecklist.check(
                "mtx-rdp-worker",
                "mtx-rdp-worker",
                "worker",
                ApplicationRole.WORKER,
                List.of("local", "debug"));

        assertEquals("LOCAL", report.environmentLabel());
    }

    @Test
    void rejectsCompanyAndLocalTogether() {
        assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-admin",
                        "mtx-rdp-admin",
                        "admin",
                        ApplicationRole.ADMIN,
                        List.of("company", "local")));
    }

    @Test
    void rejectsBlankApplicationName() {
        PlatformConfigurationException exception = assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(" ", "mtx-rdp-admin", "admin", ApplicationRole.ADMIN, List.of()));

        assertEquals("spring.application.name 不能为空", exception.getMessage());
    }

    @Test
    void rejectsApplicationNameThatDoesNotMatchTheProcess() {
        assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-worker",
                        "mtx-rdp-admin",
                        "admin",
                        ApplicationRole.ADMIN,
                        List.of()));
    }

    @Test
    void rejectsUnknownRole() {
        assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-admin",
                        "mtx-rdp-admin",
                        "pipeline",
                        ApplicationRole.ADMIN,
                        List.of()));
    }

    @Test
    void rejectsRoleThatBelongsToTheOtherProcess() {
        assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-admin",
                        "mtx-rdp-admin",
                        "worker",
                        ApplicationRole.ADMIN,
                        List.of()));
    }

    @Test
    void rejectsNullProfiles() {
        assertThrows(
                PlatformConfigurationException.class,
                () -> StartupChecklist.check(
                        "mtx-rdp-admin",
                        "mtx-rdp-admin",
                        "admin",
                        ApplicationRole.ADMIN,
                        null));
    }
}
