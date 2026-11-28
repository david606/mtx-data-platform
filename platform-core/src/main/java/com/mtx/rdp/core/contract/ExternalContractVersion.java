package com.mtx.rdp.core.contract;

import java.util.Objects;

/**
 * 外部契约版本号
 * <pre>
 * 1. 报文或版本行上的外部版本号。
 * 2. 「未提供」是单独的领域值，不是 Java null、空白，也不是某个实际版本号字符串。
 * </pre>
 */
public final class ExternalContractVersion {

    private static final ExternalContractVersion NOT_PROVIDED = new ExternalContractVersion(null);

    private final String declaredVersion;

    private ExternalContractVersion(String declaredVersion) {
        this.declaredVersion = declaredVersion;
    }

    public static ExternalContractVersion notProvided() {
        return NOT_PROVIDED;
    }

    public static ExternalContractVersion declared(String declaredVersion) {
        if (declaredVersion == null || declaredVersion.isBlank() || "未提供".equals(declaredVersion)) {
            throw new IllegalArgumentException("明确外部版本号不能为空，也不能写成未提供");
        }
        return new ExternalContractVersion(declaredVersion);
    }

    public boolean isNotProvided() {
        return declaredVersion == null;
    }

    public String declaredVersion() {
        if (isNotProvided()) {
            throw new IllegalStateException("未提供的外部版本号没有明确版本字符串");
        }
        return declaredVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExternalContractVersion version)) {
            return false;
        }
        return Objects.equals(declaredVersion, version.declaredVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(declaredVersion);
    }
}
