package com.securities.info.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日K线数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalBar implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbol;
    private Market market;
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private AdjustmentType adjustment;
    private Long volume;
}
