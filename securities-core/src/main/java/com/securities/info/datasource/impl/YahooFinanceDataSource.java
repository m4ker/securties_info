package com.securities.info.datasource.impl;

import com.google.gson.*;
import com.securities.info.datasource.HttpClientHelper;
import com.securities.info.datasource.MarketDataSource;
import com.securities.info.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Yahoo Finance 数据源（美股/港股）
 */
public class YahooFinanceDataSource implements MarketDataSource {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceDataSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Market market;
    private final Gson gson;

    public YahooFinanceDataSource(Market market) {
        this.market = market;
        this.gson = new Gson();
    }

    @Override
    public List<Security> searchSecurities(String keyword, int limit) throws IOException {
        // Yahoo Finance 不提供搜索 API，返回空列表
        // 实际使用需要从缓存或 Nasdaq 获取
        return List.of();
    }

    @Override
    public Security getSecurityInfo(String symbol) throws IOException {
        Quote quote = getQuote(symbol);
        if (quote != null) {
            return new Security(quote.getSymbol(), quote.getName(), quote.getMarket());
        }
        return null;
    }

    @Override
    public Quote getQuote(String symbol) throws IOException {
        String querySymbol = formatSymbol(symbol);
        String url = String.format(
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d",
            querySymbol
        );

        String json = HttpClientHelper.get(url);
        return parseQuoteResponse(json, symbol);
    }

    @Override
    public List<Quote> getQuotes(List<String> symbols) throws IOException {
        List<Quote> results = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                Quote quote = getQuote(symbol);
                if (quote != null) {
                    results.add(quote);
                }
            } catch (Exception e) {
                log.warn("获取行情失败: {}", symbol, e);
            }
        }
        return results;
    }

    @Override
    public List<HistoricalBar> getHistoricalData(String symbol, LocalDate startDate,
                                                  LocalDate endDate, AdjustmentType adjustment) throws IOException {
        String querySymbol = formatSymbol(symbol);
        long startTs = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long endTs = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();

        String url = String.format(
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
            querySymbol, startTs, endTs
        );

        String json = HttpClientHelper.get(url);
        return parseHistoricalResponse(json, symbol, adjustment);
    }

    @Override
    public List<HistoricalBar> getHistoricalData(String symbol, Period period,
                                                  AdjustmentType adjustment) throws IOException {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = period.getStartDate(endDate);
        return getHistoricalData(symbol, startDate, endDate, adjustment);
    }

    private String formatSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.trim();
        if (s.contains(".")) {
            return s.toUpperCase();
        }
        // 港股需要加 .HK 后缀（支持 4-5 位代码）
        if (market == Market.HK && s.matches("\\d{3,5}")) {
            return s + ".HK";
        }
        return s.toUpperCase();
    }

    private Quote parseQuoteResponse(String json, String originalSymbol) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject result = root.getAsJsonObject("chart")
                    .getAsJsonArray("result").get(0).getAsJsonObject();

            JsonObject meta = result.getAsJsonObject("meta");
            String symbol = meta.get("symbol").getAsString();
            String currency = getOrDefault(meta, "currency", "USD");
            String exchange = getOrDefault(meta, "exchangeName", "NMS");

            JsonObject quote = result.has("quote") ? result.getAsJsonObject("quote") : null;
            JsonObject metaRegularPrice = meta.has("regularMarketPrice")
                    ? meta : (quote != null ? quote : meta);

            BigDecimal price = getBigDecimal(meta, "regularMarketPrice");
            BigDecimal previousClose = getBigDecimal(quote, "previousClose");
            BigDecimal open = getBigDecimal(quote, "open");
            BigDecimal high = getBigDecimal(quote, "dayHigh");
            BigDecimal low = getBigDecimal(quote, "dayLow");
            Long volume = getLong(quote, "regularMarketVolume");

            BigDecimal change = null;
            BigDecimal changePercent = null;
            if (price != null && previousClose != null) {
                change = price.subtract(previousClose);
                changePercent = previousClose.compareTo(BigDecimal.ZERO) != 0
                        ? change.multiply(new BigDecimal("100")).divide(previousClose, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
            }

            ZoneId zone = ZoneId.of("America/New_York");
            LocalDateTime timestamp = meta.has("regularMarketTime")
                    ? Instant.ofEpochSecond(meta.get("regularMarketTime").getAsLong())
                        .atZone(zone).toLocalDateTime()
                    : LocalDateTime.now();

            return buildQuote(originalSymbol, symbol, currency, exchange, price,
                    change, changePercent, open, high, low, previousClose, volume, timestamp);

        } catch (Exception e) {
            log.warn("解析行情失败: {}", originalSymbol, e);
            return null;
        }
    }

    private List<HistoricalBar> parseHistoricalResponse(String json, String originalSymbol,
                                                           AdjustmentType adjustment) {
        List<HistoricalBar> bars = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject result = root.getAsJsonObject("chart")
                    .getAsJsonArray("result").get(0).getAsJsonObject();

            // 获取timestamp数组 - 必须是数组格式
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            if (timestamps == null || timestamps.size() == 0) {
                log.warn("没有历史数据: {}", originalSymbol);
                return bars;
            }

            // 从 indicators.quote[0] 获取 OHLCV 数据（Yahoo chart API 的正确位置）
            JsonObject quoteData = getQuoteData(result);
            if (quoteData == null) {
                log.warn("无法获取行情数据: {}", originalSymbol);
                return bars;
            }

            JsonArray openArr = getArrayFromObject(quoteData, "open");
            JsonArray highArr = getArrayFromObject(quoteData, "high");
            JsonArray lowArr = getArrayFromObject(quoteData, "low");
            JsonArray closeArr = getArrayFromObject(quoteData, "close");
            JsonArray volumeArr = getArrayFromObject(quoteData, "volume");

            // 处理adjclose
            JsonArray adjcloseArr = getAdjcloseArray(result);

            for (int i = 0; i < timestamps.size(); i++) {
                LocalDate date = Instant.ofEpochSecond(timestamps.get(i).getAsLong())
                        .atZone(ZoneId.systemDefault()).toLocalDate();

                HistoricalBar bar = new HistoricalBar();
                bar.setSymbol(originalSymbol);
                bar.setMarket(detectMarket(originalSymbol));
                bar.setDate(date);
                bar.setOpen(getArrayDecimal(openArr, i));
                bar.setHigh(getArrayDecimal(highArr, i));
                bar.setLow(getArrayDecimal(lowArr, i));
                // 使用 adjclose（前复权）或 close（不复权）
                bar.setClose(getArrayDecimal(adjcloseArr != null ? adjcloseArr : closeArr, i));
                bar.setVolume(getArrayLong(volumeArr, i));
                bar.setAdjustment(adjustment);
                bars.add(bar);
            }
        } catch (Exception e) {
            log.warn("解析历史数据失败: {}", originalSymbol, e);
        }
        return bars;
    }

    /**
     * 从 indicators.quote[0] 获取行情数据
     */
    private JsonObject getQuoteData(JsonObject result) {
        if (result.has("indicators")) {
            JsonObject indicators = result.getAsJsonObject("indicators");
            if (indicators.has("quote")) {
                JsonArray quotes = indicators.getAsJsonArray("quote");
                if (quotes != null && quotes.size() > 0) {
                    JsonElement quote = quotes.get(0);
                    if (quote.isJsonObject()) {
                        return quote.getAsJsonObject();
                    }
                }
            }
        }
        // 备用：如果直接在顶层
        return result;
    }

    /**
     * 从对象中获取数组
     */
    private JsonArray getArrayFromObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return null;
    }

    /**
     * 获取adjclose数组
     */
    private JsonArray getAdjcloseArray(JsonObject result) {
        if (!result.has("adjclose")) return null;
        JsonElement element = result.get("adjclose");
        if (element == null || element.isJsonNull()) return null;

        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        if (element.isJsonObject()) {
            JsonObject adjObj = element.getAsJsonObject();
            if (adjObj.has("adjclose") && adjObj.get("adjclose").isJsonArray()) {
                return adjObj.getAsJsonArray("adjclose");
            }
        }
        return null;
    }

    /**
     * 从JsonArray中获取BigDecimal
     */
    private BigDecimal getArrayDecimal(JsonArray arr, int index) {
        if (arr == null || index >= arr.size()) return null;
        JsonElement element = arr.get(index);
        if (element == null || element.isJsonNull()) return null;
        try {
            return new BigDecimal(element.getAsString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从JsonArray中获取Long
     */
    private Long getArrayLong(JsonArray arr, int index) {
        if (arr == null || index >= arr.size()) return null;
        JsonElement element = arr.get(index);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsLong();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Market detectMarket(String symbol) {
        String s = symbol.trim().toLowerCase();
        if (s.endsWith(".hk") || s.matches("\\d{4,5}")) {
            return Market.HK;
        }
        return Market.US;
    }

    private Quote buildQuote(String originalSymbol, String symbol, String currency,
                             String exchange, BigDecimal price, BigDecimal change,
                             BigDecimal changePercent, BigDecimal open, BigDecimal high,
                             BigDecimal low, BigDecimal previousClose, Long volume,
                             LocalDateTime timestamp) {
        Quote quote = new Quote();
        quote.setSymbol(originalSymbol);
        quote.setName(symbol);
        quote.setMarket(detectMarket(originalSymbol));
        quote.setCurrency(currency);
        quote.setExchange(exchange);
        quote.setPrice(price);
        quote.setChange(change);
        quote.setChangePercent(changePercent);
        quote.setOpen(open);
        quote.setHigh(high);
        quote.setLow(low);
        quote.setPreviousClose(previousClose);
        quote.setVolume(volume);
        quote.setTimestamp(timestamp);
        return quote;
    }

    private String getOrDefault(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) ? obj.get(key).getAsString() : defaultValue;
    }

    private BigDecimal getBigDecimal(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return new BigDecimal(obj.get(key).getAsString());
    }

    private BigDecimal getBigDecimal(JsonObject obj, int index, String key) {
        if (obj == null) return null;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr == null || index >= arr.size() || arr.get(index).isJsonNull()) return null;
        return new BigDecimal(arr.get(index).getAsString());
    }

    private Long getLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsLong();
    }

    private Long getLong(JsonObject obj, int index, String key) {
        if (obj == null) return null;
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr == null || index >= arr.size() || arr.get(index).isJsonNull()) return null;
        return arr.get(index).getAsLong();
    }
}
