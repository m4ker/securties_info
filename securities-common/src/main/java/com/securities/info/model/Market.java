package com.securities.info.model;

/**
 * 市场枚举
 */
public enum Market {
    US("US", "美股"),
    HK("HK", "港股"),
    CN_A("CN", "A股");

    private final String code;
    private final String displayName;

    Market(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }
}
