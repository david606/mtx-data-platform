package com.mtx.rdp.core.contract;

/**
 * 唯一性解析结果。失败只使用已有码 CONTRACT_VERSION_UNRESOLVED。
 */
public sealed interface ContractVersionUniquenessResult
        permits ContractVersionUniquenessResult.Resolved, ContractVersionUniquenessResult.Unresolved {

    String CONTRACT_VERSION_UNRESOLVED = "CONTRACT_VERSION_UNRESOLVED";

    static ContractVersionUniquenessResult resolved(String contractVersionId) {
        return new Resolved(contractVersionId);
    }

    static ContractVersionUniquenessResult unresolved() {
        return new Unresolved(CONTRACT_VERSION_UNRESOLVED);
    }

    record Resolved(String contractVersionId) implements ContractVersionUniquenessResult {

        public Resolved {
            if (contractVersionId == null || contractVersionId.isBlank()) {
                throw new IllegalArgumentException("确定的 contractVersionId 不能为空");
            }
        }
    }

    record Unresolved(String failureCode) implements ContractVersionUniquenessResult {

        public Unresolved {
            if (!CONTRACT_VERSION_UNRESOLVED.equals(failureCode)) {
                throw new IllegalArgumentException("唯一性解析的失败码只能是 CONTRACT_VERSION_UNRESOLVED");
            }
        }
    }
}
