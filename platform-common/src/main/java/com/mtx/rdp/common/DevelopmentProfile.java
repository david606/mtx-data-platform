package com.mtx.rdp.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时必须明确选择 company 或 local 之一。没有默认环境，也不回退到公司地址。
 */
public final class DevelopmentProfile {

    private DevelopmentProfile() {
    }

    public static DevelopmentPlacement requireSingle(List<String> activeProfiles) {
        if (activeProfiles == null) {
            throw new PlatformConfigurationException("激活的配置环境不能为 null");
        }
        List<DevelopmentPlacement> selected = new ArrayList<>();
        for (String profile : activeProfiles) {
            if (profile == null || profile.isBlank()) {
                throw new PlatformConfigurationException("激活的配置环境不能为空白");
            }
            DevelopmentPlacement placement = DevelopmentPlacement.fromProfile(profile);
            if (placement != null) {
                selected.add(placement);
            }
        }
        if (selected.isEmpty()) {
            throw new PlatformConfigurationException("未选择开发环境 company 或 local，拒绝启动");
        }
        if (selected.size() > 1) {
            throw new PlatformConfigurationException("不能同时激活 company 和 local");
        }
        return selected.getFirst();
    }
}
