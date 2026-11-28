package com.mtx.rdp.worker.devonly;

import java.util.List;
import java.util.Map;

/**
 * 手动运行一条 DEV_ONLY 链路。不由 Worker 默认启动调用。
 */
public final class DevOnlyChainMain {

    private DevOnlyChainMain() {
    }

    public static void main(String[] args) {
        Map<String, String> environment = System.getenv();
        List<String> missing = DevOnlyChainSettings.missingCredentialNames(environment);
        if (!missing.isEmpty()) {
            System.err.println("缺少环境变量: " + String.join(", ", missing));
            System.exit(2);
        }
        DevOnlyRunReport report = new DevOnlyChainRunner().run(DevOnlyChainSettings.from(environment));
        System.out.print(report.toDisplayString());
        if (!report.success()) {
            System.exit(1);
        }
    }
}
