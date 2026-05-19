package com.securities.info.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 证券基本信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Security implements Serializable {
    private static final long serialVersionUID = 1L;

    private String symbol;
    private String name;
    private Market market;
    private String exchange;
    private String currency;
    private String sector;
    private String industry;
    private String type;
    private boolean active;

    // 常用构造器
    public Security(String symbol, String name, Market market) {
        this.symbol = symbol;
        this.name = name;
        this.market = market;
    }
}
