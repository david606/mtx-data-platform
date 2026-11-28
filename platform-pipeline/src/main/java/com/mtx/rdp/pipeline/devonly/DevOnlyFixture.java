package com.mtx.rdp.pipeline.devonly;

import com.mtx.rdp.core.contract.ContractFamilyId;
import com.mtx.rdp.core.contract.ExternalContractVersion;
import com.mtx.rdp.core.contract.SnapshotContractVersionEntry;
import java.util.List;

/**
 * 本轮测试夹具。snapshot 由夹具给出，不是当前最新快照。
 */
public record DevOnlyFixture(
        boolean devOnly,
        String snapshotId,
        ContractFamilyId contractFamilyId,
        ExternalContractVersion externalVersion,
        String fieldText,
        List<SnapshotContractVersionEntry> entries) {

    public DevOnlyFixture {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
