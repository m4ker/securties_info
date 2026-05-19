package com.securities.info.model;

/**
 * 复权类型
 */
public enum AdjustmentType {
    NONE("不复权"),
    FORWARD("前复权"),
    BACKWARD("后复权");

    private final String displayName;

    AdjustmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
