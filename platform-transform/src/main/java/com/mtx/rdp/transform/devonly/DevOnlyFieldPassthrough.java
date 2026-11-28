package com.mtx.rdp.transform.devonly;

/**
 * DEV_ONLY 原字段直通。不解释金额，不分配规则版本。
 */
public final class DevOnlyFieldPassthrough {

    private DevOnlyFieldPassthrough() {
    }

    public static String pass(String fieldText) {
        if (fieldText == null || fieldText.isBlank()) {
            throw new IllegalArgumentException("直通字段不能为空");
        }
        return fieldText;
    }
}
