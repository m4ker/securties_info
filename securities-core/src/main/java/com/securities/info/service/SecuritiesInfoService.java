package com.securities.info.service;

import com.securities.info.config.SecuritiesConfig;
import com.securities.info.datasource.HistoricalDataCache;
import com.securities.info.datasource.MarketDataSource;
import com.securities.info.datasource.SecuritiesListLoader;
import com.securities.info.datasource.impl.SinaAShareDataSource;
import com.securities.info.datasource.impl.YahooFinanceDataSource;
import com.securities.info.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * 证券信息查询服务
 * 统一门面，内部路由到对应市场的数据源
 */
public class SecuritiesInfoService {

    private static final Logger log = LoggerFactory.getLogger(SecuritiesInfoService.class);

    private final Map<Market, MarketDataSource> dataSources;

    // 证券列表加载器（用于获取全市场证券列表）
    private final SecuritiesListLoader listLoader;

    // 历史数据缓存（有效期1天）
    private final HistoricalDataCache historyCache;

    public SecuritiesInfoService() {
        dataSources = new EnumMap<>(Market.class);
        dataSources.put(Market.US, new YahooFinanceDataSource(Market.US));
        dataSources.put(Market.HK, new YahooFinanceDataSource(Market.HK));
        dataSources.put(Market.CN_A, new SinaAShareDataSource());

        // 初始化列表加载器
        this.listLoader = new SecuritiesListLoader();
        this.historyCache = new HistoricalDataCache(SecuritiesConfig.getCacheDir());
    }

    public SecuritiesInfoService(SecuritiesListLoader listLoader) {
        dataSources = new EnumMap<>(Market.class);
        dataSources.put(Market.US, new YahooFinanceDataSource(Market.US));
        dataSources.put(Market.HK, new YahooFinanceDataSource(Market.HK));
        dataSources.put(Market.CN_A, new SinaAShareDataSource());
        this.listLoader = listLoader;
        this.historyCache = new HistoricalDataCache(SecuritiesConfig.getCacheDir());
    }

    /** 支持自定义数据源（测试/扩展用） */
    public SecuritiesInfoService(Map<Market, MarketDataSource> dataSources) {
        this.dataSources = new EnumMap<>(dataSources);
        this.listLoader = new SecuritiesListLoader();
        this.historyCache = new HistoricalDataCache(SecuritiesConfig.getCacheDir());
    }

    // =========================================================================
    // 证券列表查询
    // =========================================================================

    /**
     * 搜索指定市场的证券列表
     *
     * @param market  目标市场，传 null 则搜索所有市场
     * @param keyword 关键词（代码/名称），为空返回热门列表
     * @param limit   每个市场最大返回数
     */
    public List<Security> searchSecurities(Market market, String keyword, int limit) {
        List<Market> markets = market != null ? List.of(market) : List.of(Market.values());
        List<Security> result = new ArrayList<>();
        for (Market m : markets) {
            List<Security> found;
            // 使用 listLoader 进行本地搜索（从缓存列表中搜索）
            if (keyword == null || keyword.isBlank()) {
                // 空关键词：返回该市场的证券列表（A股返回前N个作为热门）
                found = listLoader.getAllSecurities(m);
            } else {
                found = listLoader.searchSecurities(m, keyword);
            }
            if (found != null && !found.isEmpty()) {
                result.addAll(found);
            }
        }
        // 限制返回数量
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    /**
     * 搜索所有市场
     */
    public List<Security> searchAllMarkets(String keyword, int limitPerMarket) {
        return searchSecurities(null, keyword, limitPerMarket);
    }

    // =========================================================================
    // 全市场证券列表（从缓存文件加载）
    // =========================================================================

    /**
     * 获取指定市场的所有证券列表
     * 数据来源于 cache 目录下的缓存文件
     *
     * @param market 目标市场
     * @return 该市场所有证券列表
     */
    public List<Security> getAllSecurities(Market market) {
        if (market == null) {
            return List.of();
        }
        return listLoader.getAllSecurities(market);
    }

    /**
     * 获取指定市场的证券列表（带关键词过滤）
     *
     * @param market  目标市场
     * @param keyword 关键词过滤（代码或名称），为 null 则返回全部
     * @return 证券列表
     */
    public List<Security> getAllSecurities(Market market, String keyword) {
        if (market == null) {
            return List.of();
        }
        return listLoader.searchSecurities(market, keyword);
    }

    /**
     * 获取指定市场的证券列表（带分页）
     *
     * @param market  目标市场
     * @param keyword 关键词过滤（代码或名称），为 null 则返回全部
     * @param page    页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 证券列表
     */
    public List<Security> getAllSecurities(Market market, String keyword, int page, int pageSize) {
        List<Security> all = getAllSecurities(market, keyword);
        int total = all.size();

        if (page < 1) page = 1;
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= total) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + pageSize, total);
        return all.subList(fromIndex, toIndex);
    }

    /**
     * 获取指定市场的证券总数
     */
    public int getTotalCount(Market market) {
        return listLoader.getAllSecurities(market).size();
    }

    /**
     * 获取全量证券列表（所有市场）
     * 数据来源：从缓存文件加载，如果缓存不存在则自动从网络获取
     */
    public Map<Market, List<Security>> getAllSecuritiesMap() {
        Map<Market, List<Security>> result = new EnumMap<>(Market.class);
        for (Market m : Market.values()) {
            // 使用 listLoader 获取列表（支持自动从网络获取）
            List<Security> list = listLoader.getAllSecurities(m);
            result.put(m, list);
        }
        return result;
    }

    /**
     * 获取指定市场的证券数量
     */
    public int getSecuritiesCount(Market market) {
        return getAllSecurities(market).size();
    }

    /**
     * 刷新证券列表缓存
     */
    public void refreshSecuritiesList() {
        listLoader.refresh();
        log.info("证券列表缓存已刷新");
    }

    /**
     * 检查缓存目录是否存在
     */
    public boolean isSecuritiesListAvailable() {
        return listLoader.isCacheDirExists();
    }

    /**
     * 获取缓存统计信息
     */
    public Map<Market, Integer> getSecuritiesListStats() {
        return listLoader.getCacheStats();
    }

    /**
     * 按证券代码获取基本信息（自动识别市场）
     */
    public Security getSecurityInfo(String symbol) {
        Market market = detectMarket(symbol);
        MarketDataSource ds = dataSources.get(market);
        if (ds == null) {
            log.warn("找不到市场数据源: {}", market);
            return null;
        }
        try {
            return ds.getSecurityInfo(symbol);
        } catch (IOException e) {
            log.error("获取证券信息失败: {}", symbol, e);
            return null;
        }
    }

    // =========================================================================
    // 实时行情查询
    // =========================================================================

    /**
     * 获取单只证券实时行情（自动识别市场）
     */
    public Quote getQuote(String symbol) {
        Market market = detectMarket(symbol);
        MarketDataSource ds = dataSources.get(market);
        if (ds == null) return null;
        try {
            return ds.getQuote(symbol);
        } catch (IOException e) {
            log.error("获取行情失败: {}", symbol, e);
            return null;
        }
    }

    /**
     * 批量获取行情（需指定市场，同一市场批量效率更高）
     */
    public List<Quote> getQuotes(Market market, List<String> symbols) {
        MarketDataSource ds = dataSources.get(market);
        if (ds == null) return List.of();
        try {
            return ds.getQuotes(symbols);
        } catch (IOException e) {
            log.error("[{}] 批量获取行情失败", market, e);
            return List.of();
        }
    }

    // =========================================================================
    // 历史数据查询
    // =========================================================================

    /**
     * 按日期范围查询历史日K数据（自动识别市场）
     * A股默认不复权，美股/港股默认前复权
     */
    public List<HistoricalBar> getHistoricalData(String symbol, LocalDate startDate, LocalDate endDate) {
        AdjustmentType defaultAdjustment = getDefaultAdjustment(symbol);
        return getHistoricalData(symbol, startDate, endDate, defaultAdjustment);
    }

    /**
     * 按日期范围查询历史日K数据（自动识别市场），指定复权类型
     * 结果会被缓存，有效期1天
     */
    public List<HistoricalBar> getHistoricalData(String symbol, LocalDate startDate, LocalDate endDate,
                                             AdjustmentType adjustment) {
        Market market = detectMarket(symbol);
        MarketDataSource ds = dataSources.get(market);
        if (ds == null) return List.of();

        // 生成缓存 key
        String cacheKey = historyCache.generateKey(symbol, startDate, endDate, adjustment);

        // 尝试从缓存获取
        List<HistoricalBar> cached = historyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，从数据源获取
        try {
            List<HistoricalBar> data = ds.getHistoricalData(symbol, startDate, endDate, adjustment);
            if (!data.isEmpty()) {
                historyCache.put(cacheKey, data);
            }
            return data;
        } catch (IOException e) {
            log.error("获取历史数据失败: {} [{} ~ {}] [{}]", symbol, startDate, endDate, adjustment, e);
            return List.of();
        }
    }

    /**
     * 按预设周期查询历史日K数据（自动识别市场）
     * A股默认不复权，美股/港股默认前复权
     */
    public List<HistoricalBar> getHistoricalData(String symbol, Period period) {
        AdjustmentType defaultAdjustment = getDefaultAdjustment(symbol);
        return getHistoricalData(symbol, period, defaultAdjustment);
    }

    /**
     * 按预设周期查询历史日K数据（自动识别市场），指定复权类型
     * 结果会被缓存，有效期1天
     */
    public List<HistoricalBar> getHistoricalData(String symbol, Period period, AdjustmentType adjustment) {
        Market market = detectMarket(symbol);
        MarketDataSource ds = dataSources.get(market);
        if (ds == null) return List.of();

        // 计算日期范围
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = period.getStartDate(endDate);

        // 生成缓存 key
        String cacheKey = historyCache.generateKey(symbol, startDate, endDate, adjustment);

        // 尝试从缓存获取
        List<HistoricalBar> cached = historyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，从数据源获取
        try {
            List<HistoricalBar> data = ds.getHistoricalData(symbol, period, adjustment);
            if (!data.isEmpty()) {
                historyCache.put(cacheKey, data);
            }
            return data;
        } catch (IOException e) {
            log.error("获取历史数据失败: {} [{}] [{}]", symbol, period, adjustment, e);
            return List.of();
        }
    }

    /**
     * 清除历史数据缓存
     */
    public void clearHistoricalCache() {
        historyCache.clear();
        log.info("历史数据缓存已清除");
    }

    /**
     * 清除过期历史数据缓存
     */
    public void cleanExpiredHistoryCache() {
        historyCache.cleanExpired();
        log.info("过期历史数据缓存已清除");
    }

    /**
     * 获取历史数据缓存统计
     */
    public Map<String, Object> getHistoricalCacheStats() {
        return historyCache.getStats();
    }

    // =========================================================================
    // 默认复权类型
    // =========================================================================

    /**
     * 根据市场返回默认复权类型
     * 所有市场默认前复权
     */
    private AdjustmentType getDefaultAdjustment(String symbol) {
        return AdjustmentType.FORWARD;
    }

    // =========================================================================
    // 市场识别
    // =========================================================================

    /**
     * 根据证券代码自动识别所属市场：
     *   - 纯数字 6 位以 6 开头 → A股沪市
     *   - 纯数字 6 位以 0/3 开头 → A股深市
     *   - sh/sz 前缀 → A股
     *   - 以 .HK 结尾，或 4-5 位纯数字 → 港股
     *   - 其他 → 美股
     */
    public static Market detectMarket(String symbol) {
        if (symbol == null) return Market.US;
        String s = symbol.trim().toLowerCase();

        // 新浪格式 A股
        if (s.startsWith("sh") || s.startsWith("sz")) return Market.CN_A;

        // 港股：.HK 后缀或纯数字 4-5 位
        if (s.endsWith(".hk")) return Market.HK;
        if (s.matches("\\d{4,5}")) return Market.HK;

        // A股：6位数字
        if (s.matches("\\d{6}")) return Market.CN_A;

        return Market.US;
    }
}
