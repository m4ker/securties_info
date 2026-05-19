package com.securities.info.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时行情快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbol;
    private String name;
    private String exchange;
    private Market market;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal previousClose;
    private Long volume;
    private BigDecimal turnover;
    private LocalDateTime timestamp;
    private String currency;
}
