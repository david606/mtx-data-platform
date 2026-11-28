package com.mtx.rdp.core.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定快照内的契约版本唯一性
 * <pre>
 * 1. 在已经固定的 snapshot 内定位唯一 contractVersionId。
 * 2. 不选择快照，不访问存储，也不做家族身份、contractId、外部版本号后置一致性或正文哈希校验。
 * </pre>
 */
public final class ContractVersionUniqueness {

    private ContractVersionUniqueness() {
    }

    public static ContractVersionUniquenessResult resolve(ContractVersionUniquenessQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("唯一性解析输入不能为空");
        }
        if (query.internalContractVersionId().provided()) {
            return locateProvidedId(query);
        }
        return countByFamilyAndExternalVersion(query);
    }

    private static ContractVersionUniquenessResult locateProvidedId(ContractVersionUniquenessQuery query) {
        String providedId = query.internalContractVersionId().value();
        for (SnapshotContractVersionEntry entry : query.entries()) {
            if (providedId.equals(entry.contractVersionId())) {
                return ContractVersionUniquenessResult.resolved(entry.contractVersionId());
            }
        }
        return ContractVersionUniquenessResult.unresolved();
    }

    private static ContractVersionUniquenessResult countByFamilyAndExternalVersion(
            ContractVersionUniquenessQuery query) {
        List<SnapshotContractVersionEntry> matches = new ArrayList<>();
        for (SnapshotContractVersionEntry entry : query.entries()) {
            if (entry.contractFamilyId().equals(query.contractFamilyId())
                    && entry.externalVersion().equals(query.messageExternalVersion())) {
                matches.add(entry);
            }
        }
        if (matches.size() != 1) {
            return ContractVersionUniquenessResult.unresolved();
        }
        return ContractVersionUniquenessResult.resolved(matches.getFirst().contractVersionId());
    }
}
