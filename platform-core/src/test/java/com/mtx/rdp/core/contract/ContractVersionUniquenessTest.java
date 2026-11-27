package com.mtx.rdp.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContractVersionUniquenessTest {

    private static final String SNAPSHOT = "snapshot-1";
    private static final ContractFamilyId FAMILY = ContractFamilyId.of("family-a");
    private static final ContractFamilyId OTHER_FAMILY = ContractFamilyId.of("family-b");

    @Test
    void t01ReturnsProvidedIdWhenOtherFamiliesAlsoExist() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.of("cv-target"),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                List.of(
                        entry("cv-other", OTHER_FAMILY, ExternalContractVersion.declared("2.0")),
                        entry("cv-target", FAMILY, ExternalContractVersion.declared("1.0")))));

        assertEquals(new ContractVersionUniquenessResult.Resolved("cv-target"), result);
    }

    @Test
    void t02DoesNotFallBackToExternalVersionWhenProvidedIdIsMissing() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.of("cv-missing"),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                List.of(entry("cv-other", FAMILY, ExternalContractVersion.declared("2.0")))));

        assertUnresolved(result);
    }

    @Test
    void t03ReturnsProvidedIdWhenEntryFamilyDiffers() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.of("cv-target"),
                ExternalContractVersion.declared("1.0"),
                FAMILY,
                List.of(entry("cv-target", OTHER_FAMILY, ExternalContractVersion.declared("1.0")))));

        assertEquals(new ContractVersionUniquenessResult.Resolved("cv-target"), result);
    }

    @Test
    void t04ReturnsProvidedIdWhenExternalVersionDiffers() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.of("cv-target"),
                ExternalContractVersion.notProvided(),
                FAMILY,
                List.of(entry("cv-target", FAMILY, ExternalContractVersion.declared("9.9")))));

        assertEquals(new ContractVersionUniquenessResult.Resolved("cv-target"), result);
    }

    @Test
    void t05CountsExactlyOneDeclaredVersionInsideTheSpecifiedFamily() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                List.of(
                        entry("cv-other-family", OTHER_FAMILY, ExternalContractVersion.declared("2.0")),
                        entry("cv-match", FAMILY, ExternalContractVersion.declared("2.0")),
                        entry("cv-other-version", FAMILY, ExternalContractVersion.declared("3.0")))));

        assertEquals(new ContractVersionUniquenessResult.Resolved("cv-match"), result);
    }

    @Test
    void t06CountsExactlyOneNotProvidedVersionAndDoesNotTreatItAsAWildcard() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.notProvided(),
                FAMILY,
                List.of(
                        entry("cv-declared", FAMILY, ExternalContractVersion.declared("1.0")),
                        entry("cv-not-provided", FAMILY, ExternalContractVersion.notProvided()),
                        entry("cv-other-family", OTHER_FAMILY, ExternalContractVersion.notProvided()))));

        assertEquals(new ContractVersionUniquenessResult.Resolved("cv-not-provided"), result);
    }

    @Test
    void t07ReturnsUnresolvedWhenNoEntryMatches() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.declared("missing"),
                FAMILY,
                List.of(entry("cv-present", FAMILY, ExternalContractVersion.declared("1.0")))));

        assertUnresolved(result);
    }

    @Test
    void t08ReturnsUnresolvedWhenSeveralEntriesMatchAndDoesNotPickOne() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                List.of(
                        entry("cv-newer", FAMILY, ExternalContractVersion.declared("2.0")),
                        entry("cv-older", FAMILY, ExternalContractVersion.declared("2.0")))));

        assertUnresolved(result);
    }

    @Test
    void t09ReturnsUnresolvedWhenSeveralNotProvidedEntriesExist() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.notProvided(),
                FAMILY,
                List.of(
                        entry("cv-first", FAMILY, ExternalContractVersion.notProvided()),
                        entry("cv-second", FAMILY, ExternalContractVersion.notProvided()))));

        assertUnresolved(result);
    }

    @Test
    void t10RejectsEntriesFromAnotherSnapshotAsAParameterError() {
        SnapshotContractVersionEntry foreign = new SnapshotContractVersionEntry(
                "snapshot-2",
                "cv-foreign",
                FAMILY,
                ExternalContractVersion.declared("2.0"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> query(
                        InternalContractVersionId.absent(),
                        ExternalContractVersion.declared("2.0"),
                        FAMILY,
                        List.of(foreign)));

        assertEquals("候选条目的 snapshotId 与已固定 snapshotId 不一致", exception.getMessage());
    }

    @Test
    void t11RepeatingTheSameInputReturnsTheSameResult() {
        List<SnapshotContractVersionEntry> entries = List.of(
                entry("cv-match", FAMILY, ExternalContractVersion.declared("2.0")),
                entry("cv-other", OTHER_FAMILY, ExternalContractVersion.declared("2.0")));
        ContractVersionUniquenessQuery firstQuery = query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                entries);
        ContractVersionUniquenessQuery secondQuery = query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.declared("2.0"),
                FAMILY,
                entries);

        assertEquals(
                ContractVersionUniqueness.resolve(firstQuery),
                ContractVersionUniqueness.resolve(secondQuery));
        assertEquals(
                ContractVersionUniqueness.resolve(firstQuery),
                ContractVersionUniqueness.resolve(firstQuery));
    }

    @Test
    void t12CompletedEmptyEntryListIsUnresolved() {
        ContractVersionUniquenessResult result = ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.of("cv-any"),
                ExternalContractVersion.declared("1.0"),
                FAMILY,
                List.of()));

        assertUnresolved(result);
        assertUnresolved(ContractVersionUniqueness.resolve(query(
                InternalContractVersionId.absent(),
                ExternalContractVersion.notProvided(),
                FAMILY,
                List.of())));
    }

    @Test
    void notProvidedExternalVersionIsDistinctFromADeclaredVersionString() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalContractVersion.declared("未提供"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalContractVersion.declared(" "));
        assertThrows(IllegalArgumentException.class, () -> InternalContractVersionId.of(" "));
    }

    private static void assertUnresolved(ContractVersionUniquenessResult result) {
        ContractVersionUniquenessResult.Unresolved unresolved = assertInstanceOf(
                ContractVersionUniquenessResult.Unresolved.class,
                result);
        assertEquals(ContractVersionUniquenessResult.CONTRACT_VERSION_UNRESOLVED, unresolved.failureCode());
    }

    private static ContractVersionUniquenessQuery query(
            InternalContractVersionId internalId,
            ExternalContractVersion messageVersion,
            ContractFamilyId familyId,
            List<SnapshotContractVersionEntry> entries) {
        return new ContractVersionUniquenessQuery(SNAPSHOT, messageVersion, internalId, familyId, entries);
    }

    private static SnapshotContractVersionEntry entry(
            String contractVersionId,
            ContractFamilyId familyId,
            ExternalContractVersion externalVersion) {
        return new SnapshotContractVersionEntry(SNAPSHOT, contractVersionId, familyId, externalVersion);
    }
}
