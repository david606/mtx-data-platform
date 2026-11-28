package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevelopmentProfileFilesTest {

    @Test
    void adminAndWorkerUseTheSameProfileFiles() throws Exception {
        Path root = reactorRoot();
        assertEquals(
                Files.readString(root.resolve("app-admin/src/main/resources/application-company.yml")),
                Files.readString(root.resolve("app-worker/src/main/resources/application-company.yml")));
        assertEquals(
                Files.readString(root.resolve("app-admin/src/main/resources/application-local.yml")),
                Files.readString(root.resolve("app-worker/src/main/resources/application-local.yml")));
    }

    private static Path reactorRoot() throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("app-admin")) && Files.isDirectory(dir.resolve("platform-common"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 mtx-data-platform 根 POM");
    }
}
