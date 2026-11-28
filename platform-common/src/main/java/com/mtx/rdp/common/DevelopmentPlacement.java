package com.mtx.rdp.common;

/**
 * 开发环境标识。只区分本机和公司开发环境，不表示生产，也不表示任务执行权。
 */
public enum DevelopmentPlacement {

    LOCAL("local", "LOCAL"),
    COMPANY("company", "COMPANY");

    private final String profile;
    private final String label;

    DevelopmentPlacement(String profile, String label) {
        this.profile = profile;
        this.label = label;
    }

    public String profile() {
        return profile;
    }

    public String label() {
        return label;
    }

    public static DevelopmentPlacement fromProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        for (DevelopmentPlacement placement : values()) {
            if (placement.profile.equals(profile)) {
                return placement;
            }
        }
        return null;
    }
}
