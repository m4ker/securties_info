package com.securities.info.datasource;

import com.securities.info.model.*;

import java.io.IOException;
import java.util.List;

/**
 * 数据源统一接口
 */
public interface MarketDataSource {

    /**
     * 搜索证券
     */
    List<Security> searchSecurities(String keyword, int limit) throws IOException;

    /**
     * 获取证券基本信息
     */
    Security getSecurityInfo(String symbol) throws IOException;

    /**
     * 获取实时行情
     */
    Quote getQuote(String symbol) throws IOException;

    /**
     * 批量获取行情
     */
    List<Quote> getQuotes(List<String> symbols) throws IOException;

    /**
     * 获取历史数据（按日期范围）
     */
    List<HistoricalBar> getHistoricalData(String symbol, java.time.LocalDate startDate,
                                          java.time.LocalDate endDate, AdjustmentType adjustment) throws IOException;

    /**
     * 获取历史数据（按周期）
     */
    List<HistoricalBar> getHistoricalData(String symbol, Period period, AdjustmentType adjustment) throws IOException;
}
