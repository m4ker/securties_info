package com.securities.info.sdk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.securities.info.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 证券查询 SDK 客户端
 *
 * <p>使用方法:</p>
 * <pre>{@code
 * SecuritiesClient client = new SecuritiesClient("http://localhost:8080");
 *
 * // 搜索证券
 * List<Security> stocks = client.search("AAPL", 10);
 *
 * // 获取行情
 * Quote quote = client.getQuote("AAPL");
 *
 * // 获取历史数据
 * List<HistoricalBar> history = client.getHistory("AAPL", Period.ONE_YEAR);
 * }</pre>
 */
public class SecuritiesClient {

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public SecuritiesClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    // =========================================================================
    // 搜索 API
    // =========================================================================

    /**
     * 搜索证券
     *
     * @param market 市场（US/HK/CN），null 表示所有市场
     * @param keyword 关键词（代码/名称）
     * @param limit 返回数量限制
     * @return 证券列表
     */
    public List<Security> search(Market market, String keyword, int limit) throws IOException {
        String url = buildUrl("/api/securities/search",
                "market", market != null ? market.name() : null,
                "keyword", keyword,
                "limit", String.valueOf(limit));
        return executeGet(url, new TypeToken<List<Security>>(){}.getType());
    }

    /**
     * 搜索证券（所有市场）
     */
    public List<Security> search(String keyword, int limit) throws IOException {
        return search(null, keyword, limit);
    }

    // =========================================================================
    // 全量证券列表 API
    // =========================================================================

    /**
     * 获取指定市场的全量证券列表
     *
     * @param market 市场
     * @return 该市场的所有证券列表
     */
    public List<Security> getAllSecurities(Market market) throws IOException {
        String url = buildUrl("/api/securities/list", "market", market.name());
        return executeGet(url, new TypeToken<List<Security>>(){}.getType());
    }

    /**
     * 获取所有市场的全量证券列表
     *
     * @return 各市场的证券列表 Map
     */
    public Map<Market, List<Security>> getAllSecurities() throws IOException {
        String url = baseUrl + "/api/securities/list/all";
        Type type = new TypeToken<Map<Market, List<Security>>>(){}.getType();
        return executeGet(url, type);
    }

    /**
     * 获取指定市场的证券数量
     *
     * @param market 市场
     * @return 证券数量
     */
    public int getSecuritiesCount(Market market) throws IOException {
        String url = buildUrl("/api/securities/count", "market", market.name());
        String result = executeRawGet(url);
        return Integer.parseInt(result.trim());
    }

    private String executeRawGet(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "text/plain")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 请求失败: " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    // =========================================================================
    // 行情 API
    // =========================================================================

    /**
     * 获取实时行情
     */
    public Quote getQuote(String symbol) throws IOException {
        String url = buildUrl("/api/securities/quote", "symbol", symbol);
        return executeGet(url, Quote.class);
    }

    /**
     * 批量获取行情
     */
    public List<Quote> getQuotes(Market market, List<String> symbols) throws IOException {
        String symbolsStr = String.join(",", symbols);
        String url = buildUrl("/api/securities/quotes",
                "market", market.name(),
                "symbols", symbolsStr);
        return executeGet(url, new TypeToken<List<Quote>>(){}.getType());
    }

    // =========================================================================
    // 历史数据 API
    // =========================================================================

    /**
     * 获取历史数据（按周期）
     */
    public List<HistoricalBar> getHistory(String symbol, Period period) throws IOException {
        return getHistory(symbol, period, AdjustmentType.FORWARD);
    }

    /**
     * 获取历史数据（按周期，指定复权）
     */
    public List<HistoricalBar> getHistory(String symbol, Period period, AdjustmentType adjustment) throws IOException {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = period.getStartDate(endDate);
        return getHistory(symbol, startDate, endDate, adjustment);
    }

    /**
     * 获取历史数据（按日期范围）
     */
    public List<HistoricalBar> getHistory(String symbol, LocalDate startDate, LocalDate endDate) throws IOException {
        return getHistory(symbol, startDate, endDate, AdjustmentType.FORWARD);
    }

    /**
     * 获取历史数据（按日期范围，指定复权）
     */
    public List<HistoricalBar> getHistory(String symbol, LocalDate startDate,
                                          LocalDate endDate, AdjustmentType adjustment) throws IOException {
        String url = buildUrl("/api/securities/history",
                "symbol", symbol,
                "startDate", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "endDate", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "adjustment", adjustment.name());
        return executeGet(url, new TypeToken<List<HistoricalBar>>(){}.getType());
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    private String buildUrl(String path, String... params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        boolean first = true;
        for (int i = 0; i < params.length; i += 2) {
            String key = params[i];
            String value = params[i + 1];
            if (value != null && !value.isEmpty()) {
                url.append(first ? "?" : "&")
                   .append(key).append("=")
                   .append(value);
                first = false;
            }
        }
        return url.toString();
    }

    private <T> T executeGet(String url, Type type) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP 请求失败: " + response.code() + " - " + url);
            }
            String body = response.body() != null ? response.body().string() : "";
            return gson.fromJson(body, type);
        }
    }

    /**
     * 关闭客户端（释放资源）
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
