package com.securities.info.controller;

import com.securities.info.model.*;
import com.securities.info.service.SecuritiesInfoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 证券信息 REST Controller
 */
@RestController
@RequestMapping("/api/securities")
public class SecuritiesController {

    private final SecuritiesInfoService service;

    public SecuritiesController() {
        this.service = new SecuritiesInfoService();
    }

    // =========================================================================
    // 搜索 API
    // =========================================================================

    /**
     * 搜索证券
     *
     * GET /api/securities/search?market=US&keyword=Apple&limit=10
     */
    @GetMapping("/search")
    public List<Security> search(
            @RequestParam(required = false) Market market,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {

        return service.searchSecurities(market, keyword, limit);
    }

    // =========================================================================
    // 全量证券列表 API
    // =========================================================================

    /**
     * 获取指定市场的全量证券列表
     *
     * GET /api/securities/list?market=US
     */
    @GetMapping("/list")
    public List<Security> getAllSecurities(@RequestParam Market market) {
        return service.getAllSecurities(market);
    }

    /**
     * 获取所有市场的全量证券列表
     *
     * GET /api/securities/list/all
     */
    @GetMapping("/list/all")
    public Map<Market, List<Security>> getAllSecuritiesMap() {
        return service.getAllSecuritiesMap();
    }

    /**
     * 获取指定市场的证券数量
     *
     * GET /api/securities/count?market=US
     */
    @GetMapping("/count")
    public int getSecuritiesCount(@RequestParam Market market) {
        return service.getSecuritiesCount(market);
    }

    // =========================================================================
    // 行情 API
    // =========================================================================

    /**
     * 获取实时行情
     *
     * GET /api/securities/quote?symbol=AAPL
     */
    @GetMapping("/quote")
    public Quote getQuote(@RequestParam String symbol) {
        return service.getQuote(symbol);
    }

    /**
     * 批量获取行情
     *
     * GET /api/securities/quotes?market=US&symbols=AAPL,TSLA,GOOGL
     */
    @GetMapping("/quotes")
    public List<Quote> getQuotes(
            @RequestParam Market market,
            @RequestParam String symbols) {

        List<String> symbolList = List.of(symbols.split(","));
        return service.getQuotes(market, symbolList);
    }

    // =========================================================================
    // 历史数据 API
    // =========================================================================

    /**
     * 获取历史数据（按日期范围）
     *
     * GET /api/securities/history?symbol=AAPL&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/history")
    public List<HistoricalBar> getHistory(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "FORWARD") AdjustmentType adjustment) {

        return service.getHistoricalData(symbol, startDate, endDate, adjustment);
    }

    /**
     * 获取历史数据（按周期）
     *
     * GET /api/securities/history/period?symbol=AAPL&period=ONE_YEAR
     */
    @GetMapping("/history/period")
    public List<HistoricalBar> getHistoryByPeriod(
            @RequestParam String symbol,
            @RequestParam Period period,
            @RequestParam(defaultValue = "FORWARD") AdjustmentType adjustment) {

        return service.getHistoricalData(symbol, period, adjustment);
    }

    // =========================================================================
    // 辅助 API
    // =========================================================================

    /**
     * 获取支持的市场列表
     */
    @GetMapping("/markets")
    public Market[] getMarkets() {
        return Market.values();
    }

    /**
     * 获取支持的周期列表
     */
    @GetMapping("/periods")
    public Period[] getPeriods() {
        return Period.values();
    }
}
