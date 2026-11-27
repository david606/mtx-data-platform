package com.mtx.rdp.core.contract;

/**
 * 调用方已经读完的一条 snapshot 契约条目。本类型不读取存储。
 */
public record SnapshotContractVersionEntry(
        String snapshotId,
        String contractVersionId,
        ContractFamilyId contractFamilyId,
        ExternalContractVersion externalVersion) {

    public SnapshotContractVersionEntry {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("候选条目的 snapshotId 不能为空");
        }
        if (contractVersionId == null || contractVersionId.isBlank()) {
            throw new IllegalArgumentException("候选条目的 contractVersionId 不能为空");
        }
        if (contractFamilyId == null) {
            throw new IllegalArgumentException("候选条目的契约家族标识不能为空");
        }
        if (externalVersion == null) {
            throw new IllegalArgumentException("候选条目的外部版本号不能为空");
        }
    }
}
