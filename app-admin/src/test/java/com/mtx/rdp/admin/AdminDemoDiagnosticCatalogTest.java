package com.mtx.rdp.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mtx.rdp.admin.AdminDemoDiagnosticCatalog.Entry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A11-1：直接检查静态演示清单的文本。不启动应用，也不证明运行时已经输出。
 * A11-2 和 A13 不在本测试范围内。
 */
class AdminDemoDiagnosticCatalogTest {

    @Test
    void a11StaticCatalogContainsFourDemoStatusesAndThreeMarksOnEveryEntry() {
        List<Entry> entries = AdminDemoDiagnosticCatalog.entries();

        assertFalse(entries.isEmpty());
        assertEquals(
                List.of(
                        AdminDemoDiagnosticCatalog.SUCCESS,
                        AdminDemoDiagnosticCatalog.FAILURE,
                        AdminDemoDiagnosticCatalog.NOT_CONFIGURED,
                        AdminDemoDiagnosticCatalog.BLOCKED),
                entries.stream().map(Entry::status).toList());
        for (Entry entry : entries) {
            assertEquals(AdminDemoDiagnosticCatalog.DEV_ONLY, entry.devOnlyMark());
            assertEquals(AdminDemoDiagnosticCatalog.DEMO_DATA, entry.demoDataMark());
            assertEquals(AdminDemoDiagnosticCatalog.INFORMAL_RESULT, entry.informalResultMark());
            assertTrue(entry.stage().contains("Admin 演示阶段"));
            assertTrue(entry.stage().contains("不是 Worker 执行结果"));
        }
    }

    @Test
    void a11ReturnedCatalogCannotBeModifiedByTheCaller() {
        List<Entry> entries = AdminDemoDiagnosticCatalog.entries();
        Entry extra = new Entry(
                "Admin 演示阶段：调用方追加。这不是 Worker 执行结果。",
                AdminDemoDiagnosticCatalog.SUCCESS,
                AdminDemoDiagnosticCatalog.DEV_ONLY,
                AdminDemoDiagnosticCatalog.DEMO_DATA,
                AdminDemoDiagnosticCatalog.INFORMAL_RESULT);

        assertThrows(UnsupportedOperationException.class, () -> entries.add(extra));
        assertEquals(4, AdminDemoDiagnosticCatalog.entries().size());
    }
}
