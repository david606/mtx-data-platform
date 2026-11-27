package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StartupChecklistTest {

    @Test
    void reportsUnnamedEnvironmentWhenNoProfileIsActive() {
        StartupReport report = StartupChecklist.check(
                "mtx-rdp-admin",
                "mtx-rdp-admin",
                "admin",
                ApplicationRole.ADMIN,
                List.of());

        assertEquals(ApplicationRole.ADMIN, report.role());
        assertEquals(StartupChecklist.UNNAMED_ENVIRONMENT, report.environmentLabel());
        assertEquals(List.of(), report.activeProfiles());
    }

    @Test
    void reportsActiveProfilesWithoutChoosingADeployment() {
        StartupReport report = StartupChecklist.check(
                "mtx-rdp-worker",
                "mtx-rdp-worker",
                "worker",
                ApplicationRole.WORKER,
                List.of("local", "debug"));

        assertEquals("local,debug", report.environmentLabel());
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
