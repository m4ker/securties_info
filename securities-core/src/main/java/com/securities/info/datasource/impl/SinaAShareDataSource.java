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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新浪财经 A股数据源
 */
public class SinaAShareDataSource implements MarketDataSource {

    private static final Logger log = LoggerFactory.getLogger(SinaAShareDataSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String SINA_REFERER = "https://finance.sina.com.cn/";

    private final Gson gson = new Gson();

    @Override
    public List<Security> searchSecurities(String keyword, int limit) throws IOException {
        // A股搜索需要从本地缓存获取
        return List.of();
    }

    @Override
    public Security getSecurityInfo(String symbol) throws IOException {
        Quote quote = getQuote(symbol);
        if (quote != null) {
            return new Security(quote.getSymbol(), quote.getName(), Market.CN_A);
        }
        return null;
    }

    @Override
    public Quote getQuote(String symbol) throws IOException {
        String querySymbol = formatSymbol(symbol);
        String url = "https://hq.sinajs.cn/list=" + querySymbol;

        String json = HttpClientHelper.get(url, DEFAULT_UA, Map.of("Referer", SINA_REFERER));
        return parseQuoteResponse(json, symbol);
    }

    @Override
    public List<Quote> getQuotes(List<String> symbols) throws IOException {
        String querySymbols = String.join(",",
                symbols.stream().map(this::formatSymbol).toArray(String[]::new));
        String url = "https://hq.sinajs.cn/list=" + querySymbols;

        String response = HttpClientHelper.get(url, DEFAULT_UA, Map.of("Referer", SINA_REFERER));
        return parseQuotesResponse(response, symbols);
    }

    @Override
    public List<HistoricalBar> getHistoricalData(String symbol, LocalDate startDate,
                                                 LocalDate endDate, AdjustmentType adjustment) throws IOException {
        String querySymbol = formatSymbol(symbol);
        String startStr = startDate.format(DATE_FORMAT);
        String endStr = endDate.format(DATE_FORMAT);

        // 使用东方财富 API
        // fqt: 0=不复权, 1=前复权, 2=后复权
        int fqt = switch (adjustment) {
            case FORWARD -> 1;
            case BACKWARD -> 2;
            default -> 0;
        };
        String secId = getSecId(symbol);
        if (secId == null) {
            throw new IOException("无效的A股代码格式: " + symbol);
        }
        String url = String.format(
            "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=%d&beg=%s&end=%s",
            secId, fqt, startStr.replace("-", ""), endStr.replace("-", "")
        );

        String json = HttpClientHelper.get(url, "https://finance.eastmoney.com");
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
        String s = symbol.trim().toLowerCase();
        if (s.startsWith("sh") || s.startsWith("sz")) {
            return s;
        }
        if (s.matches("\\d{6}")) {
            int code = Integer.parseInt(s);
            if (code >= 600000) return "sh" + s;
            return "sz" + s;
        }
        return s;
    }

    private String getSecId(String symbol) {
        String s = symbol.trim().toLowerCase();
        if (s.startsWith("sh") && s.length() > 2) {
            String code = s.substring(2);
            if (code.matches("\\d{6}")) {
                return "1." + code;
            }
        }
        if (s.startsWith("sz") && s.length() > 2) {
            String code = s.substring(2);
            if (code.matches("\\d{6}")) {
                return "0." + code;
            }
        }
        if (s.matches("\\d{6}")) {
            int code = Integer.parseInt(s);
            if (code >= 600000) return "1." + s;
            return "0." + s;
        }
        return null;  // 无效格式返回 null
    }

    private Quote parseQuoteResponse(String json, String originalSymbol) {
        try {
            // 新浪返回格式: var hq_str_sh600519="贵州茅台,1688.00,1699.00,1685.00,1699.00,1680.00,1685.00,1698.00,1688.00,3456,..."
            int start = json.indexOf("\"");
            int end = json.lastIndexOf("\"");
            if (start == -1 || end == -1) return null;

            String data = json.substring(start + 1, end);
            String[] fields = data.split(",");

            if (fields.length < 32) return null;

            Quote quote = new Quote();
            quote.setSymbol(originalSymbol);
            quote.setName(fields[0]);
            quote.setMarket(Market.CN_A);
            quote.setCurrency("CNY");
            quote.setOpen(parseBd(fields[1]));
            quote.setPreviousClose(parseBd(fields[2]));
            quote.setPrice(parseBd(fields[3]));
            quote.setHigh(parseBd(fields[4]));
            quote.setLow(parseBd(fields[5]));
            quote.setVolume(parseLong(fields[8]));
            quote.setTimestamp(LocalDateTime.now());
            quote.setExchange("SSE");

            if (quote.getPrice() != null && quote.getPreviousClose() != null) {
                BigDecimal change = quote.getPrice().subtract(quote.getPreviousClose());
                quote.setChange(change);
                if (quote.getPreviousClose().compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal changePercent = change.multiply(new BigDecimal("100"))
                            .divide(quote.getPreviousClose(), 2, java.math.RoundingMode.HALF_UP);
                    quote.setChangePercent(changePercent);
                }
            }

            return quote;
        } catch (Exception e) {
            log.warn("解析A股行情失败: {}", originalSymbol, e);
            return null;
        }
    }

    private List<Quote> parseQuotesResponse(String response, List<String> originalSymbols) {
        List<Quote> quotes = new ArrayList<>();
        String[] lines = response.split("\n");
        for (int i = 0; i < lines.length && i < originalSymbols.size(); i++) {
            Quote quote = parseQuoteResponse(lines[i], originalSymbols.get(i));
            if (quote != null) {
                quotes.add(quote);
            }
        }
        return quotes;
    }

    private List<HistoricalBar> parseHistoricalResponse(String json, String originalSymbol, AdjustmentType adjustment) {
        List<HistoricalBar> bars = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return bars;

            JsonArray klines = data.getAsJsonArray("klines");
            if (klines == null) return bars;

            for (JsonElement element : klines) {
                String line = element.getAsString();
                String[] fields = line.split(",");

                // 确保字段数量足够（日期,开,收,高,低,成交量 至少6个）
                if (fields.length < 6) {
                    log.warn("A股历史数据字段不足，跳过: {}", line);
                    continue;
                }

                HistoricalBar bar = new HistoricalBar();
                bar.setSymbol(originalSymbol);
                bar.setMarket(Market.CN_A);
                bar.setDate(LocalDate.parse(fields[0]));
                bar.setOpen(parseBd(fields[1]));
                bar.setClose(parseBd(fields[2]));
                bar.setHigh(parseBd(fields[3]));
                bar.setLow(parseBd(fields[4]));
                bar.setVolume(parseLong(fields[5]));
                // 使用请求的复权类型
                bar.setAdjustment(adjustment != null ? adjustment : AdjustmentType.NONE);
                bars.add(bar);
            }
        } catch (Exception e) {
            log.warn("解析A股历史数据失败: {}", originalSymbol, e);
        }
        return bars;
    }

    private BigDecimal parseBd(String s) {
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
