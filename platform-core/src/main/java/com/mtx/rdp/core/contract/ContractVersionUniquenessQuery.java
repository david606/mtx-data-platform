package com.mtx.rdp.core.contract;

import java.util.List;

/**
 * 唯一性解析的全部输入。候选集合必须已经是该 snapshot 读完后的契约条目，不能混入其他 snapshot。
 */
public record ContractVersionUniquenessQuery(
        String snapshotId,
        ExternalContractVersion messageExternalVersion,
        InternalContractVersionId internalContractVersionId,
        ContractFamilyId contractFamilyId,
        List<SnapshotContractVersionEntry> entries) {

    public ContractVersionUniquenessQuery {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("已固定的 snapshotId 不能为空");
        }
        if (messageExternalVersion == null) {
            throw new IllegalArgumentException("报文外部版本号不能为空");
        }
        if (internalContractVersionId == null) {
            throw new IllegalArgumentException("内部 contractVersionId 必须明确为已提供或未提供");
        }
        if (contractFamilyId == null) {
            throw new IllegalArgumentException("调用方指定的契约家族不能为空");
        }
        if (entries == null) {
            throw new IllegalArgumentException("候选条目集合不能为空引用");
        }
        List<SnapshotContractVersionEntry> copied = List.copyOf(entries);
        for (SnapshotContractVersionEntry entry : copied) {
            if (!snapshotId.equals(entry.snapshotId())) {
                throw new IllegalArgumentException("候选条目的 snapshotId 与已固定 snapshotId 不一致");
            }
        }
        entries = copied;
    }
}
