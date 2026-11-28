package com.mtx.rdp.core.contract;

import java.util.Objects;

/**
 * 内部契约版本标识
 * <pre>
 * 1. 可选的内部契约版本标识。
 * 2. 未提供该标识，与外部版本号为「未提供」，是两件不同的事。
 * </pre>
 */
public final class InternalContractVersionId {

    private static final InternalContractVersionId ABSENT = new InternalContractVersionId(null);

    private final String value;

    private InternalContractVersionId(String value) {
        this.value = value;
    }

    public static InternalContractVersionId absent() {
        return ABSENT;
    }

    public static InternalContractVersionId of(String contractVersionId) {
        if (contractVersionId == null || contractVersionId.isBlank()) {
            throw new IllegalArgumentException("内部 contractVersionId 已提供时不能为空");
        }
        return new InternalContractVersionId(contractVersionId);
    }

    public boolean provided() {
        return value != null;
    }

    public String value() {
        if (!provided()) {
            throw new IllegalStateException("未提供内部 contractVersionId");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InternalContractVersionId identifier)) {
            return false;
        }
        return Objects.equals(value, identifier.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
