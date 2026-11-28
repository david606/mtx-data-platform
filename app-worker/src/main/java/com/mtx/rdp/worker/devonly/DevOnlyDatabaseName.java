package com.mtx.rdp.worker.devonly;

/**
 * DEV_ONLY 数据库标识符。只接受固定名称，避免把配置拼进 SQL。
 */
public final class DevOnlyDatabaseName {

    public static final String NAME = "mtx_rdp_dev_only";

    private DevOnlyDatabaseName() {
    }

    public static String require(String database) {
        if (!NAME.equals(database)) {
            throw new IllegalArgumentException("只允许 DEV_ONLY 数据库 " + NAME);
        }
        return NAME;
    }
}
