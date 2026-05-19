package com.securities.info.datasource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.securities.info.config.SecuritiesConfig;
import com.securities.info.model.Market;
import com.securities.info.model.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 证券列表更新器
 * 从各市场公开接口获取完整证券列表
 *
 * 数据源：
 * - 美股: Nasdaq API
 * - 港股: 新浪财经批量接口
 * - A股:  新浪财经接口
 *
 * 支持两种模式：
 * 1. 自动模式（默认）：获取后自动保存到缓存文件
 * 2. 仅获取模式：fetchOnly=true 时，获取数据但不保存，由调用方决定何时保存
 */
public class SecuritiesListUpdater {

    private static final Logger log = LoggerFactory.getLogger(SecuritiesListUpdater.class);

    // 默认 User-Agent
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";

    // 缓存目录
    private final Path cacheDir;

    // 是否仅获取不保存
    private final boolean fetchOnly;

    // HTTP 请求间隔（毫秒）
    private static final long REQUEST_DELAY_MS = 200;

    // Gson 实例
    private final Gson gson;

    public SecuritiesListUpdater() {
        this(SecuritiesConfig.getCacheDirPath(), false);
    }

    /**
     * @param cacheDir   缓存目录
     * @param fetchOnly  true=仅获取不保存，false=获取后自动保存
     */
    public SecuritiesListUpdater(Path cacheDir, boolean fetchOnly) {
        this.cacheDir = cacheDir != null ? cacheDir : SecuritiesConfig.getCacheDirPath();
        this.fetchOnly = fetchOnly;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // 确保缓存目录存在
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException e) {
            log.warn("创建缓存目录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有市场的证券列表
     */
    public Map<Market, List<Security>> fetchAllMarkets() {
        Map<Market, List<Security>> result = new EnumMap<>(Market.class);

        System.out.println("=".repeat(50));
        System.out.println("证券列表更新工具 v1.0 (Java)");
        System.out.println("=".repeat(50));

        // 美股
        System.out.println("\n[1/3] 美股 (US)");
        result.put(Market.US, fetchUSStocks());

        // 港股
        System.out.println("\n[2/3] 港股 (HK)");
        result.put(Market.HK, fetchHKStocks());

        // A股
        System.out.println("\n[3/3] A股 (CN)");
        result.put(Market.CN_A, fetchCNStocks());

        // 汇总
        printSummary(result);

        return result;
    }

    /**
     * 获取指定市场的证券列表
     */
    public List<Security> fetchByMarket(Market market) {
        return switch (market) {
            case US -> fetchUSStocks();
            case HK -> fetchHKStocks();
            case CN_A -> fetchCNStocks();
        };
    }

    /**
     * 获取指定市场的证券列表（仅获取不保存）
     * 用于 CLI 工具：先获取数据，全部成功后再保存
     */
    public List<Security> fetchByMarketOnly(Market market) {
        // 创建仅获取模式的实例
        SecuritiesListUpdater fetcher = new SecuritiesListUpdater(cacheDir, true);
        return switch (market) {
            case US -> fetcher.fetchUSStocksOnly();
            case HK -> fetcher.fetchHKStocksOnly();
            case CN_A -> fetcher.fetchCNStocksOnly();
        };
    }

    /**
     * 保存市场缓存（由 CLI 工具调用）
     */
    public void saveMarketCache(Market market, List<Security> securities) {
        String marketFile = getMarketFileName(market);
        saveCache(marketFile, securities);
    }

    // =========================================================================
    // 公开接口
    // =========================================================================
    public void exportToJson(Path outputPath) {
        Map<Market, List<Security>> all = fetchAllMarkets();

        Map<String, Object> exportData = new LinkedHashMap<>();
        all.forEach((market, securities) ->
            exportData.put(market.getCode(), securities.stream()
                .map(this::toMap)
                .collect(Collectors.toList()))
        );

        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            gson.toJson(exportData, writer);
            System.out.println("\n数据已导出到: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("导出JSON失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        int count = 0;
        try {
            for (Path file : Files.list(cacheDir).collect(Collectors.toList())) {
                if (file.getFileName().toString().endsWith("_securities.json")) {
                    Files.deleteIfExists(file);
                    count++;
                }
            }
            System.out.println("已清除 " + count + " 个缓存文件");
        } catch (IOException e) {
            log.error("清除缓存失败", e);
        }
    }

    // =========================================================================
    // 美股获取 (Nasdaq API)
    // =========================================================================

    /**
     * 获取美股完整列表
     * 原子性：一个市场的所有分页都成功才保存，任一分页失败则不保存
     */
    public List<Security> fetchUSStocks() {
        // 先检查缓存（仅在非 fetchOnly 模式下）
        if (!fetchOnly) {
            List<Security> cached = loadCache("us");
            if (cached != null && !cached.isEmpty()) {
                System.out.println("  [命中缓存] 返回 " + cached.size() + " 只美股");
                return cached;
            }
        }

        System.out.println("  正在获取美股列表 (Nasdaq API)...");
        List<Security> securities = new ArrayList<>();
        Set<String> seenSymbols = new HashSet<>();

        // Nasdaq API URL 模板
        String baseUrl = "https://api.nasdaq.com/api/screener/stocks";

        // 获取交易所列表: NASDAQ, NYSE
        String[] exchanges = {"NASDAQ", "NYSE"};

        // 标记是否所有分页都成功
        boolean allSuccess = true;

        for (String exchange : exchanges) {
            System.out.println("    获取 " + exchange + "...");
            try {
                List<Security> exchangeStocks = fetchNasdaqExchange(baseUrl, exchange, seenSymbols);
                securities.addAll(exchangeStocks);
                System.out.println("      获取 " + exchangeStocks.size() + " 只");
                sleep();
            } catch (Exception e) {
                System.out.println("      [错误] 获取失败: " + e.getMessage());
                allSuccess = false;
                // 美股：任一交易所失败则不保存
                break;
            }
        }

        // 过滤普通股
        List<Security> commonStocks = securities.stream()
            .filter(this::isCommonStock)
            .collect(Collectors.toList());

        System.out.println("  过滤后共 " + commonStocks.size() + " 只美股");

        // 仅在所有分页都成功且非 fetchOnly 模式下才保存
        if (!fetchOnly && allSuccess) {
            saveCache("us", commonStocks);
        } else if (!allSuccess) {
            System.out.println("  [跳过保存] 部分数据获取失败，保留旧缓存");
        }
        return commonStocks;
    }

    /**
     * 获取美股（仅获取不保存）
     */
    private List<Security> fetchUSStocksOnly() {
        return fetchUSStocks();
    }

    private List<Security> fetchNasdaqExchange(String baseUrl, String exchange, Set<String> seen) throws IOException {
        List<Security> result = new ArrayList<>();
        int offset = 0;
        int limit = 5000;

        while (true) {
            String url = String.format("%s?tableonly=true&limit=%d&offset=%d&download=true&exchange=%s",
                    baseUrl, limit, offset, exchange);

            String json = HttpClientHelper.get(url, DEFAULT_UA, Map.of(
                "Origin", "https://www.nasdaq.com",
                "Referer", "https://www.nasdaq.com/"
            ));

            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) break;

            JsonArray rows = data.getAsJsonArray("rows");
            if (rows == null || rows.size() == 0) break;

            for (int i = 0; i < rows.size(); i++) {
                JsonObject row = rows.get(i).getAsJsonObject();
                String symbol = getString(row, "symbol");
                String name = getString(row, "name");

                if (symbol != null && !symbol.isBlank() && !seen.contains(symbol)) {
                    seen.add(symbol);
                    result.add(Security.builder()
                        .symbol(symbol)
                        .name(name)
                        .market(Market.US)
                        .exchange(exchange)
                        .currency("USD")
                        .sector(getString(row, "sector"))
                        .industry(getString(row, "industry"))
                        .build());
                }
            }

            if (rows.size() < limit) break;
            offset += limit;
            sleep();
        }

        return result;
    }

    // =========================================================================
    // 港股获取 (新浪财经批量接口)
    // =========================================================================

    /**
     * 获取港股完整列表
     * 原子性：一个市场的所有分页都成功才保存，任一分页失败则不保存
     */
    public List<Security> fetchHKStocks() {
        // 先检查缓存（仅在非 fetchOnly 模式下）
        if (!fetchOnly) {
            List<Security> cached = loadCache("hk");
            if (cached != null && !cached.isEmpty()) {
                System.out.println("  [命中缓存] 返回 " + cached.size() + " 只港股");
                return cached;
            }
        }

        System.out.println("  正在获取港股列表 (新浪财经批量接口)...");
        List<Security> securities = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 港股代码范围 00001-09999
        int totalCodes = 10000;
        int batchSize = 50;

        // 新浪港股接口的 Referer
        Map<String, String> headers = Map.of(
            "Referer", "https://finance.sina.com.cn/",
            "User-Agent", DEFAULT_UA
        );

        Pattern pattern = Pattern.compile("hq_str_hk(\\d+)=\"([^\"]+)\"");

        // 标记是否所有批次都成功
        boolean allSuccess = true;

        for (int start = 1; start <= totalCodes; start += batchSize) {
            try {
                // 构建代码列表
                StringBuilder codes = new StringBuilder();
                for (int i = start; i < start + batchSize && i <= totalCodes; i++) {
                    codes.append("hk").append(String.format("%05d", i)).append(",");
                }

                String url = "http://hq.sinajs.cn/list=" + codes;
                String response = HttpClientHelper.get(url, DEFAULT_UA, headers);

                // 新浪返回 GBK 编码，需要转换
                String content = new String(response.getBytes("GBK"), StandardCharsets.UTF_8);

                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    String code = matcher.group(1);
                    String data = matcher.group(2);
                    String[] parts = data.split(",");

                    if (parts.length >= 2) {
                        String name = parts[0];
                        String price = parts.length > 3 ? parts[3] : "";

                        // 过滤无效数据
                        if (name != null && !name.isBlank() && !price.isEmpty() && !"0".equals(price)) {
                            String fullCode = code + ".HK";
                            if (!seen.contains(fullCode)) {
                                seen.add(fullCode);
                                securities.add(Security.builder()
                                    .symbol(fullCode)
                                    .name(name)
                                    .market(Market.HK)
                                    .exchange("HKEX")
                                    .currency("HKD")
                                    .build());
                            }
                        }
                    }
                }

                // 显示进度
                if (start % 1000 == 0 || start + batchSize > totalCodes) {
                    System.out.println("    进度: " + Math.min(start + batchSize - 1, totalCodes)
                        + "/" + totalCodes + ", 获取 " + securities.size() + " 只");
                }

                sleep(100);

            } catch (Exception e) {
                // 单批失败：标记失败并停止
                System.out.println("    [错误] 第 " + start + " 批获取失败: " + e.getMessage());
                allSuccess = false;
                break;
            }
        }

        System.out.println("  共获取 " + securities.size() + " 只港股");

        // 仅在所有批次都成功且非 fetchOnly 模式下才保存
        if (!fetchOnly && allSuccess) {
            saveCache("hk", securities);
        } else if (!allSuccess) {
            System.out.println("  [跳过保存] 部分批次获取失败，保留旧缓存");
        }
        return securities;
    }

    /**
     * 获取港股（仅获取不保存）
     */
    private List<Security> fetchHKStocksOnly() {
        return fetchHKStocks();
    }

    // =========================================================================
    // A股获取 (新浪财经接口)
    // =========================================================================

    /**
     * 获取A股完整列表
     * 原子性：一个市场的所有分页都成功才保存，任一分页失败则不保存
     */
    public List<Security> fetchCNStocks() {
        // 先检查缓存（仅在非 fetchOnly 模式下）
        if (!fetchOnly) {
            List<Security> cached = loadCache("cn");
            if (cached != null && !cached.isEmpty()) {
                System.out.println("  [命中缓存] 返回 " + cached.size() + " 只A股");
                return cached;
            }
        }

        System.out.println("  正在获取A股列表 (新浪财经)...");
        List<Security> securities = new ArrayList<>();

        // 新浪财经 A股列表接口
        Map<String, String> markets = Map.of(
            "sh_a", "SSE",    // 沪市A股
            "sz_a", "SZSE"    // 深市A股
        );

        Map<String, String> headers = Map.of(
            "Referer", "https://finance.sina.com.cn/",
            "User-Agent", DEFAULT_UA
        );

        // 标记是否所有市场的所有页都成功
        boolean allSuccess = true;

        for (Map.Entry<String, String> entry : markets.entrySet()) {
            String marketFs = entry.getKey();
            String exchange = entry.getValue();
            int page = 1;
            int totalForMarket = 0;

            while (true) {
                try {
                    String url = String.format(
                        "http://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData?" +
                        "page=%d&num=100&sort=symbol&asc=1&node=%s&symbol=&_s_r_a=page",
                        page, marketFs
                    );

                    String json = HttpClientHelper.get(url, DEFAULT_UA, headers);
                    JsonArray items = gson.fromJson(json, JsonArray.class);

                    if (items == null || items.size() == 0) {
                        break;
                    }

                    for (int i = 0; i < items.size(); i++) {
                        JsonObject item = items.get(i).getAsJsonObject();
                        String code = getString(item, "symbol");
                        String name = getString(item, "name");

                        if (code != null && !code.isBlank()) {
                            securities.add(Security.builder()
                                .symbol(code)  // 已包含 sh/sz 前缀
                                .name(name)
                                .market(Market.CN_A)
                                .exchange(exchange)
                                .currency("CNY")
                                .build());
                        }
                    }

                    totalForMarket += items.size();
                    System.out.println("    " + exchange + ": " + totalForMarket + " 只");

                    // 新浪接口固定返回100条/页，少于100条说明没有更多数据
                    if (items.size() < 100) {
                        break;
                    }

                    page++;
                    sleep(200);

                } catch (Exception e) {
                    // 某页失败：标记失败并停止该市场的后续分页
                    System.out.println("    [错误] " + exchange + " 第" + page + "页获取失败: " + e.getMessage());
                    allSuccess = false;
                    break;
                }
            }

            // 如果该市场获取失败，跳过剩余市场
            if (!allSuccess) {
                break;
            }
        }

        // 去重
        Set<String> seen = new HashSet<>();
        List<Security> unique = securities.stream()
            .filter(s -> seen.add(s.getSymbol()))
            .collect(Collectors.toList());

        System.out.println("  去重后共 " + unique.size() + " 只A股");

        // 仅在所有分页都成功且非 fetchOnly 模式下才保存
        if (!fetchOnly && allSuccess) {
            saveCache("cn", unique);
        } else if (!allSuccess) {
            System.out.println("  [跳过保存] 部分分页获取失败，保留旧缓存");
        }
        return unique;
    }

    /**
     * 获取A股（仅获取不保存）
     */
    private List<Security> fetchCNStocksOnly() {
        return fetchCNStocks();
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private boolean isCommonStock(Security s) {
        if (s == null) return false;
        String name = s.getName();
        if (name == null) return true;  // 名称为空时保留

        String lowerName = name.toLowerCase();
        // 排除特殊类型
        String[] excludePatterns = {
            "warrant", "right", "unit", "preferred", "adr",
            "trust", "fund", "etf", "llc", "holdings",
            "class b"
        };

        for (String pattern : excludePatterns) {
            if (lowerName.contains(pattern)) {
                return false;
            }
        }
        return true;
    }

    private String getMarketFileName(Market market) {
        if (market == Market.CN_A) {
            return "cn";
        }
        return market.getCode().toLowerCase();
    }

    private List<Security> loadCache(String market) {
        // 路径: cache/us_securities.json
        Path cacheFile = cacheDir.resolve(market + "_securities.json");
        if (!Files.exists(cacheFile)) {
            return null;
        }

        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            // 与 saveCache 格式一致，直接解析为列表
            return gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<Security>>(){}.getType());
        } catch (Exception e) {
            log.warn("缓存读取失败: {}", e.getMessage());
            return null;
        }
    }

    private void saveCache(String market, List<Security> securities) {
        // 路径: cache/us_securities.json
        // 保存格式与 SecuritiesListLoader 兼容：直接保存证券列表数组
        Path cacheFile = cacheDir.resolve(market + "_securities.json");
        try {
            // 确保 securities 不为 null
            if (securities == null) {
                securities = List.of();
            }
            String json = gson.toJson(securities);
            Files.writeString(cacheFile, json, StandardCharsets.UTF_8);
            System.out.println("  缓存已保存: " + cacheFile + " (" + securities.size() + " 只)");
            log.info("缓存保存成功: {} ({} 只)", market, securities.size());
        } catch (IOException e) {
            log.error("缓存保存失败: {}", market, e);
            System.out.println("  [错误] 缓存保存失败: " + e.getMessage());
        }
    }

    private void sleep() {
        sleep(REQUEST_DELAY_MS);
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void printSummary(Map<Market, List<Security>> result) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("获取完成！");
        System.out.println("=".repeat(50));
        result.forEach((market, list) ->
            System.out.printf("  %s: %6d 只%n", market.getDisplayName(), list.size())
        );
        System.out.printf("  %s: %6d 只%n", "总计",
            result.values().stream().mapToInt(List::size).sum());
        System.out.println("=".repeat(50));
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return "";
        return obj.get(key).isJsonNull() ? "" : obj.get(key).getAsString();
    }

    private Map<String, Object> toMap(Security s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", s.getSymbol());
        map.put("name", s.getName());
        map.put("market", s.getMarket().getCode());
        map.put("exchange", s.getExchange());
        map.put("currency", s.getCurrency());
        map.put("sector", s.getSector() != null ? s.getSector() : "");
        map.put("industry", s.getIndustry() != null ? s.getIndustry() : "");
        return map;
    }

}
