package com.securities.info.datasource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.securities.info.config.SecuritiesConfig;
import com.securities.info.model.Market;
import com.securities.info.model.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 证券列表加载器
 * 从 cache 目录加载各市场证券列表
 * 缓存文件: {cacheDir}/us_securities.json, {cacheDir}/hk_securities.json, {cacheDir}/cn_securities.json
 *
 * 缓存永久有效，不设置过期时间
 */
public class SecuritiesListLoader {

    private static final Logger log = LoggerFactory.getLogger(SecuritiesListLoader.class);

    // 内存缓存
    private final Map<Market, List<Security>> cache = new EnumMap<>(Market.class);

    // 缓存目录
    private final Path cacheDir;

    private final Gson gson;

    // 是否已初始化检查
    private volatile boolean initialized = false;

    public SecuritiesListLoader() {
        this(null);
    }

    public SecuritiesListLoader(String cacheDir) {
        this.gson = new Gson();
        this.cacheDir = resolveCacheDir(cacheDir);
        log.info("证券列表加载器初始化，缓存目录: {}", this.cacheDir);
    }

    /**
     * 初始化：检查缓存是否存在，如不存在则生成
     * 在项目启动或测试开始时调用
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        log.info("检查证券列表缓存...");
        checkAndGenerateCache();
        initialized = true;
    }

    /**
     * 检查并生成缓存
     */
    private void checkAndGenerateCache() {
        for (Market market : Market.values()) {
            String marketFile = getMarketFileName(market);
            Path cacheFile = cacheDir.resolve(marketFile + "_securities.json");

            if (!Files.exists(cacheFile)) {
                log.info("[{}] 缓存文件不存在，开始生成...", market);
                generateCacheForMarket(market);
            }
        }
    }

    /**
     * 为指定市场生成缓存
     * 使用自动保存模式 (fetchOnly=false)，分页失败时不保存
     */
    private void generateCacheForMarket(Market market) {
        try {
            Files.createDirectories(cacheDir);
            // fetchOnly=false: 自动保存，只有所有分页都成功才保存
            SecuritiesListUpdater updater = new SecuritiesListUpdater(cacheDir, false);
            List<Security> securities = updater.fetchByMarket(market);
            log.info("[{}] 缓存生成完成，共 {} 只证券", market, securities.size());
        } catch (Exception e) {
            log.error("[{}] 缓存生成失败", market, e);
        }
    }

    /**
     * 查找缓存目录
     */
    private Path resolveCacheDir(String customDir) {
        if (customDir != null && !customDir.isBlank()) {
            return Paths.get(customDir);
        }
        return SecuritiesConfig.getCacheDirPath();
    }

    /**
     * 获取指定市场的所有证券列表
     */
    public List<Security> getAllSecurities(Market market) {
        // 确保已初始化
        if (!initialized) {
            initialize();
        }

        // 检查内存缓存
        if (cache.containsKey(market)) {
            return cache.get(market);
        }

        // 从文件加载
        List<Security> securities = loadFromFile(market);
        cache.put(market, securities);
        return securities;
    }

    /**
     * 获取指定市场的所有证券列表，支持过滤
     */
    public List<Security> searchSecurities(Market market, String keyword) {
        List<Security> all = getAllSecurities(market);

        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(all);
        }

        String lowerKeyword = keyword.toLowerCase();
        // 标准化关键词：去除前导零（用于匹配 0700.HK 和 00700.HK）
        String normalizedKeyword = normalizeSymbol(lowerKeyword, market);
        
        return all.stream()
                .filter(s -> {
                    // 1. 精确匹配 symbol（包含匹配）
                    if (s.getSymbol() != null && s.getSymbol().toLowerCase().contains(lowerKeyword)) {
                        return true;
                    }
                    // 2. 标准化后匹配（去除前导零）
                    if (normalizedKeyword != null && s.getSymbol() != null) {
                        String normalizedSymbol = normalizeSymbol(s.getSymbol().toLowerCase(), market);
                        if (normalizedSymbol != null && normalizedSymbol.contains(normalizedKeyword)) {
                            return true;
                        }
                    }
                    // 3. 名称包含关键词
                    if (s.getName() != null && s.getName().toLowerCase().contains(lowerKeyword)) {
                        return true;
                    }
                    return false;
                })
                .toList();
    }

    /**
     * 标准化证券代码：去除前导零，便于匹配
     * 例如：00700.HK -> 700.hk, 0700.HK -> 700.hk
     */
    private String normalizeSymbol(String symbol, Market market) {
        if (symbol == null) return null;
        
        try {
            if (market == Market.HK) {
                // 港股：去除 .HK 后缀后去除前导零，再加回后缀
                if (symbol.endsWith(".hk")) {
                    String numPart = symbol.substring(0, symbol.length() - 3);
                    String normalized = String.valueOf(Integer.parseInt(numPart));
                    return normalized + ".hk";
                }
            } else if (market == Market.CN_A) {
                // A股：去除 sh/sz 前缀后去除前导零
                if (symbol.startsWith("sh") || symbol.startsWith("sz")) {
                    String prefix = symbol.substring(0, 2);
                    String numPart = symbol.substring(2);
                    String normalized = String.valueOf(Integer.parseInt(numPart));
                    return prefix + normalized;
                } else if (symbol.matches("\\d{6}")) {
                    // 纯数字 6 位码
                    return String.valueOf(Integer.parseInt(symbol));
                }
            }
        } catch (NumberFormatException e) {
            // 非标准格式，返回原始值
            return symbol;
        }
        return symbol;
    }

    /**
     * 按关键词搜索所有市场
     */
    public Map<Market, List<Security>> searchAllMarkets(String keyword) {
        Map<Market, List<Security>> result = new EnumMap<>(Market.class);
        for (Market market : Market.values()) {
            result.put(market, searchSecurities(market, keyword));
        }
        return result;
    }

    /**
     * 从文件加载市场数据
     */
    private List<Security> loadFromFile(Market market) {
        String marketFile = getMarketFileName(market);
        Path cacheFile = cacheDir.resolve(marketFile + "_securities.json");

        if (!Files.exists(cacheFile)) {
            log.warn("数据文件不存在: {}", cacheFile);
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<SecurityData>>(){}.getType();
            List<SecurityData> dataList = gson.fromJson(reader, listType);

            if (dataList == null || dataList.isEmpty()) {
                log.warn("数据文件为空: {}", cacheFile);
                return List.of();
            }

            List<Security> securities = new ArrayList<>(dataList.size());
            for (SecurityData data : dataList) {
                Security security = data.toSecurity(market);
                if (security != null) {
                    securities.add(security);
                }
            }

            log.info("[{}] 从文件加载 {} 只证券", market, securities.size());
            return securities;

        } catch (IOException e) {
            log.error("读取数据文件失败: {}", cacheFile, e);
            return List.of();
        }
    }

    /**
     * 获取市场对应的文件名
     */
    private String getMarketFileName(Market market) {
        if (market == Market.CN_A) {
            return "cn";
        }
        return market.getCode().toLowerCase();
    }

    /**
     * 手动刷新缓存
     */
    public synchronized void refresh() {
        log.info("刷新证券列表缓存...");
        cache.clear();
        initialized = false;
    }

    /**
     * 重新初始化（强制重新生成缓存）
     */
    public synchronized void reinitialize() {
        log.info("强制重新生成证券列表缓存...");
        cache.clear();
        initialized = false;
        initialize();
    }

    /**
     * 获取缓存统计
     */
    public Map<Market, Integer> getCacheStats() {
        Map<Market, Integer> stats = new EnumMap<>(Market.class);
        for (Market market : Market.values()) {
            List<Security> list = cache.get(market);
            if (list == null) {
                // 未加载的尝试加载
                list = getAllSecurities(market);
            }
            stats.put(market, list.size());
        }
        return stats;
    }

    /**
     * 获取缓存目录路径
     */
    public Path getCacheDir() {
        return cacheDir;
    }

    /**
     * 检查缓存目录是否存在
     */
    public boolean isCacheDirExists() {
        return Files.exists(cacheDir) && Files.isDirectory(cacheDir);
    }

    /**
     * JSON 数据结构
     */
    private static class SecurityData {
        String symbol;
        String name;
        String market;
        String exchange;
        String currency;
        String sector;
        String industry;

        Security toSecurity(Market defaultMarket) {
            if (symbol == null || symbol.isBlank()) {
                return null;
            }

            Market marketEnum = defaultMarket;
            if (market != null) {
                try {
                    marketEnum = Market.valueOf(market);
                } catch (IllegalArgumentException ignored) {}
            }

            return Security.builder()
                    .symbol(symbol)
                    .name(name)
                    .market(marketEnum)
                    .exchange(exchange)
                    .currency(currency)
                    .sector(sector)
                    .industry(industry)
                    .type("STOCK")
                    .active(true)
                    .build();
        }
    }
}
