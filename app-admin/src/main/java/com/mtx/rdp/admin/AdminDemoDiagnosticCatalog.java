package com.mtx.rdp.admin;

import java.util.List;
import java.util.Objects;

/**
 * Admin 自己的 DEV_ONLY 静态演示清单。
 * 内容不是 Worker 的实际执行结果，也不是正式业务结果或正式审计。
 * 本类不提供运行时输出入口，不表示已经获得 DEV_ONLY 运行许可。
 * A11-2 的启动输出和 A13 的生产隔离都不在这里实现。
 */
public final class AdminDemoDiagnosticCatalog {

    public static final String DEV_ONLY = "DEV_ONLY";
    public static final String DEMO_DATA = "演示数据";
    public static final String INFORMAL_RESULT = "非正式结果";
    public static final String SUCCESS = "成功";
    public static final String FAILURE = "失败";
    public static final String NOT_CONFIGURED = "未配置";
    public static final String BLOCKED = "阻断";

    private static final List<Entry> ENTRIES = List.of(
            entry("Admin 演示阶段：虚构成功。这不是 Worker 执行结果。", SUCCESS),
            entry("Admin 演示阶段：虚构失败。这不是 Worker 执行结果。", FAILURE),
            entry("Admin 演示阶段：虚构未配置。这不是正式错误码，也不是 Worker 执行结果。", NOT_CONFIGURED),
            entry("Admin 演示阶段：虚构阻断。这不是正式任务状态，也不是 Worker 执行结果。", BLOCKED));

    private AdminDemoDiagnosticCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static Entry entry(String stage, String status) {
        return new Entry(stage, status, DEV_ONLY, DEMO_DATA, INFORMAL_RESULT);
    }

    /**
     * 一条 Admin 演示记录。阶段说明只描述这份虚构数据，状态也只属于演示，不表示正式结果。
     */
    public record Entry(
            String stage,
            String status,
            String devOnlyMark,
            String demoDataMark,
            String informalResultMark) {

        public Entry {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(status, "status");
            if (stage.isBlank()) {
                throw new IllegalArgumentException("演示阶段说明不能为空");
            }
            if (!DEV_ONLY.equals(devOnlyMark)
                    || !DEMO_DATA.equals(demoDataMark)
                    || !INFORMAL_RESULT.equals(informalResultMark)) {
                throw new IllegalArgumentException("演示记录必须同时带有 DEV_ONLY、演示数据和非正式结果");
            }
        }
    }
}
