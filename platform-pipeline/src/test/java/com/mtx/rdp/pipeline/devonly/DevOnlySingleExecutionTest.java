package com.mtx.rdp.pipeline.devonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mtx.rdp.core.contract.ContractFamilyId;
import com.mtx.rdp.core.contract.ContractVersionUniquenessResult;
import com.mtx.rdp.core.contract.ExternalContractVersion;
import com.mtx.rdp.core.contract.SnapshotContractVersionEntry;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DevOnlySingleExecutionTest {

    private final DevOnlySingleExecution execution = new DevOnlySingleExecution();

    @Test
    void uniqueFixturePassesTheFieldAndCompletes() {
        AtomicBoolean wrote = new AtomicBoolean();
        DevOnlyExecutionResult result = execution.execute(fixture(true, true), text -> text, call -> {
            wrote.set(true);
            assertEquals("dev-only-contract", call.contractVersionId());
            assertEquals("DEV_ONLY fixture field", call.passthroughField());
            return new DevOnlyWriteAnswer(true, "", "Success", 1, 0);
        });
        assertTrue(wrote.get());
        DevOnlyExecutionResult.Completed completed = assertInstanceOf(DevOnlyExecutionResult.Completed.class, result);
        assertEquals("DEV_ONLY fixture field", completed.passthroughField());
    }

    @Test
    void missingSnapshotDoesNotResolveOrWrite() {
        AtomicBoolean wrote = new AtomicBoolean();
        DevOnlyExecutionResult result = execution.execute(fixture(true, false), text -> text, call -> {
            wrote.set(true);
            return new DevOnlyWriteAnswer(true, "", "Success", 1, 0);
        });
        assertInstanceOf(DevOnlyExecutionResult.NotExecuted.class, result);
        assertFalse(wrote.get());
    }

    @Test
    void unresolvedDoesNotWrite() {
        ContractFamilyId family = ContractFamilyId.of("dev-only-family");
        ExternalContractVersion version = ExternalContractVersion.declared("dev-only-v1");
        SnapshotContractVersionEntry first = entry(family, version, "one");
        SnapshotContractVersionEntry second = entry(family, version, "two");
        DevOnlyFixture fixture = new DevOnlyFixture(
                true, "dev-only-snapshot", family, version, "DEV_ONLY fixture field", List.of(first, second));
        DevOnlyExecutionResult result = execution.execute(fixture, text -> text, call -> {
            throw new AssertionError("未唯一解析时不能写");
        });
        DevOnlyExecutionResult.Unresolved unresolved = assertInstanceOf(DevOnlyExecutionResult.Unresolved.class, result);
        assertEquals(ContractVersionUniquenessResult.CONTRACT_VERSION_UNRESOLVED, unresolved.failureCode());
    }

    @Test
    void unmarkedFixtureIsRejected() {
        DevOnlyExecutionResult result = execution.execute(fixture(false, true), text -> text,
                call -> new DevOnlyWriteAnswer(true, "", "Success", 1, 0));
        assertInstanceOf(DevOnlyExecutionResult.RejectedNotDevOnly.class, result);
    }

    private static DevOnlyFixture fixture(boolean devOnly, boolean withSnapshot) {
        ContractFamilyId family = ContractFamilyId.of("dev-only-family");
        ExternalContractVersion version = ExternalContractVersion.declared("dev-only-v1");
        if (!withSnapshot) {
            return new DevOnlyFixture(devOnly, null, family, version, "DEV_ONLY fixture field", List.of());
        }
        return new DevOnlyFixture(devOnly, "dev-only-snapshot", family, version, "DEV_ONLY fixture field",
                List.of(entry(family, version, "dev-only-contract")));
    }

    private static SnapshotContractVersionEntry entry(
            ContractFamilyId family, ExternalContractVersion version, String contractVersionId) {
        return new SnapshotContractVersionEntry("dev-only-snapshot", contractVersionId, family, version);
    }
}
