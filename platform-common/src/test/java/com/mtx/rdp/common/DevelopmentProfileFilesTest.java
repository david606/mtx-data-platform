package com.mtx.rdp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void gitIgnoresSecretEnvFilesButNotTheExample() throws Exception {
        Path root = reactorRoot();
        assertEquals(0, git(root, "check-ignore", "-q", "--no-index", ".env"));
        assertEquals(0, git(root, "check-ignore", "-q", "--no-index", ".env.company"));
        assertEquals(0, git(root, "check-ignore", "-q", "--no-index", ".env.local"));
        assertEquals(0, git(root, "check-ignore", "-q", "--no-index", ".env.backup"));
        assertNotEquals(0, git(root, "check-ignore", "-q", "--no-index", ".env.example"));
        assertNotEquals(0, git(root, "ls-files", "--error-unmatch", ".env"));
    }

    private static int git(Path root, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).start();
        process.getInputStream().readAllBytes();
        process.getErrorStream().readAllBytes();
        return process.waitFor();
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
