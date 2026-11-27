package com.mtx.rdp.core.contract;

import java.util.Objects;

/**
 * 调用方已经给出的契约家族标识。本类型只做相等比较，不校验家族五段身份或 contractId。
 */
public final class ContractFamilyId {

    private final String value;

    private ContractFamilyId(String value) {
        this.value = value;
    }

    public static ContractFamilyId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("契约家族标识不能为空");
        }
        return new ContractFamilyId(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContractFamilyId familyId)) {
            return false;
        }
        return value.equals(familyId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
