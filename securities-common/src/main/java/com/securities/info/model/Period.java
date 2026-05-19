package com.securities.info.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 历史数据查询周期
 */
public enum Period {
    ONE_WEEK("1w", "近1周", 7),
    ONE_MONTH("1m", "近1月", 30),
    THREE_MONTHS("3m", "近3月", 90),
    SIX_MONTHS("6m", "近6月", 180),
    ONE_YEAR("1y", "近1年", 365),
    TWO_YEARS("2y", "近2年", 730),
    FIVE_YEARS("5y", "近5年", 1825),
    TEN_YEARS("10y", "近10年", 3650);

    private final String code;
    private final String displayName;
    private final int days;

    Period(String code, String displayName, int days) {
        this.code = code;
        this.displayName = displayName;
        this.days = days;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getStartDate(LocalDate endDate) {
        return endDate.minusDays(days);
    }
}
