package com.securities.info.datasource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.securities.info.model.AdjustmentType;
import com.securities.info.model.HistoricalBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 历史数据缓存
 * 缓存目录: cache/history/
 * 缓存有效期: 1天
 */
public class HistoricalDataCache {

    private static final Logger log = LoggerFactory.getLogger(HistoricalDataCache.class);

    /** 默认缓存目录 */
    private static final String DEFAULT_CACHE_DIR = "cache";

    /** 历史数据子目录 */
    private static final String HISTORY_SUBDIR = "history";

    /** 缓存有效期（1天） */
    private static final long CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000;

    /** 缓存目录 */
    private final Path cacheDir;

    /** 内存缓存 */
    private final ConcurrentHashMap<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    private final Gson gson;

    public HistoricalDataCache() {
        this(DEFAULT_CACHE_DIR);
    }

    public HistoricalDataCache(String baseCacheDir) {
        this.cacheDir = Paths.get(baseCacheDir, HISTORY_SUBDIR);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalAdapter())
                .registerTypeAdapter(BigDecimal.class, new BigDecimalAdapter())
                .create();
        initCacheDir();
    }

    /**
     * 初始化缓存目录
     */
    private void initCacheDir() {
        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                log.info("创建历史数据缓存目录: {}", cacheDir);
            }
        } catch (IOException e) {
            log.error("创建缓存目录失败: {}", cacheDir, e);
        }
    }

    /**
     * 生成缓存 key
     */
    public String generateKey(String symbol, LocalDate startDate, LocalDate endDate, AdjustmentType adjustment) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return String.format("%s_%s_%s_%s",
                symbol.toUpperCase(),
                startDate.format(formatter),
                endDate.format(formatter),
                adjustment.name());
    }

    /**
     * 生成缓存文件路径
     */
    private Path getCacheFile(String key) {
        // 使用 key 作为文件名，将特殊字符替换为下划线
        String safeFileName = key.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
        return cacheDir.resolve(safeFileName);
    }

    /**
     * 获取缓存数据
     */
    public List<HistoricalBar> get(String key) {
        // 1. 检查内存缓存
        CacheEntry memoryEntry = memoryCache.get(key);
        if (memoryEntry != null && !memoryEntry.isExpired()) {
            log.debug("[缓存命中] key={}", key);
            return memoryEntry.data;
        }

        // 2. 检查文件缓存
        Path cacheFile = getCacheFile(key);
        if (Files.exists(cacheFile)) {
            try {
                long fileAge = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
                if (fileAge < CACHE_EXPIRY_MS) {
                    // 文件缓存未过期，加载到内存
                    List<HistoricalBar> data = loadFromFile(cacheFile);
                    if (data != null) {
                        memoryCache.put(key, new CacheEntry(data));
                        log.debug("[文件缓存命中] key={}", key);
                        return data;
                    }
                } else {
                    log.debug("[文件缓存已过期] key={}, age={}h", key, fileAge / (60 * 60 * 1000));
                    // 删除过期文件
                    Files.deleteIfExists(cacheFile);
                }
            } catch (IOException e) {
                log.warn("读取缓存文件失败: {}", cacheFile, e);
            }
        }

        return null;
    }

    /**
     * 保存缓存数据
     */
    public void put(String key, List<HistoricalBar> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        // 1. 保存到内存缓存
        memoryCache.put(key, new CacheEntry(data));

        // 2. 保存到文件缓存
        Path cacheFile = getCacheFile(key);
        try {
            saveToFile(cacheFile, data);
            log.debug("[缓存写入] key={}, size={}", key, data.size());
        } catch (IOException e) {
            log.warn("写入缓存文件失败: {}", cacheFile, e);
        }
    }

    /**
     * 从文件加载缓存
     */
    private List<HistoricalBar> loadFromFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CachedData cached = gson.fromJson(reader, CachedData.class);
            return cached != null ? cached.toHistoricalBars() : null;
        } catch (IOException e) {
            log.error("读取缓存文件失败: {}", file, e);
            return null;
        }
    }

    /**
     * 保存数据到文件
     */
    private void saveToFile(Path file, List<HistoricalBar> data) throws IOException {
        CachedData cached = new CachedData(data);
        String json = gson.toJson(cached);
        Files.writeString(file, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 清除过期缓存
     */
    public void cleanExpired() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir, "*.json")) {
            for (Path file : stream) {
                try {
                    long fileAge = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
                    if (fileAge >= CACHE_EXPIRY_MS) {
                        Files.delete(file);
                        log.info("删除过期缓存: {}", file.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("处理缓存文件失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("遍历缓存目录失败: {}", cacheDir, e);
        }
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        memoryCache.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir, "*.json")) {
            for (Path file : stream) {
                Files.delete(file);
            }
        } catch (IOException e) {
            log.error("清除缓存失败: {}", cacheDir, e);
        }
        log.info("已清除所有历史数据缓存");
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("memoryCacheSize", memoryCache.size());
        try {
            long fileCount = Files.list(cacheDir).count();
            stats.put("fileCacheCount", fileCount);
        } catch (IOException e) {
            stats.put("fileCacheCount", -1);
        }
        stats.put("cacheDir", cacheDir.toString());
        return stats;
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final List<HistoricalBar> data;
        final long createTime;

        CacheEntry(List<HistoricalBar> data) {
            this.data = data;
            this.createTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createTime > CACHE_EXPIRY_MS;
        }
    }

    /**
     * 缓存数据结构（用于 JSON 序列化）
     */
    private static class CachedData {
        List<CachedBar> bars;
        long timestamp;

        CachedData() {}

        CachedData(List<HistoricalBar> bars) {
            this.bars = new ArrayList<>();
            for (HistoricalBar bar : bars) {
                this.bars.add(new CachedBar(bar));
            }
            this.timestamp = System.currentTimeMillis();
        }

        List<HistoricalBar> toHistoricalBars() {
            List<HistoricalBar> result = new ArrayList<>();
            for (CachedBar cb : bars) {
                result.add(cb.toHistoricalBar());
            }
            return result;
        }
    }

    /**
     * 缓存的 K 线数据
     */
    private static class CachedBar {
        String symbol;
        String market;
        String date;
        String open;
        String high;
        String low;
        String close;
        String adjustment;
        Long volume;

        CachedBar() {}

        CachedBar(HistoricalBar bar) {
            this.symbol = bar.getSymbol();
            this.market = bar.getMarket().name();
            this.date = bar.getDate().toString();
            this.open = bar.getOpen() != null ? bar.getOpen().toPlainString() : null;
            this.high = bar.getHigh() != null ? bar.getHigh().toPlainString() : null;
            this.low = bar.getLow() != null ? bar.getLow().toPlainString() : null;
            this.close = bar.getClose() != null ? bar.getClose().toPlainString() : null;
            this.adjustment = bar.getAdjustment().name();
            this.volume = bar.getVolume();
        }

        HistoricalBar toHistoricalBar() {
            HistoricalBar.HistoricalBarBuilder builder = HistoricalBar.builder()
                    .symbol(symbol)
                    .market(com.securities.info.model.Market.valueOf(market))
                    .date(LocalDate.parse(date))
                    .adjustment(AdjustmentType.valueOf(adjustment))
                    .volume(volume);
            if (open != null) builder.open(new BigDecimal(open));
            if (high != null) builder.high(new BigDecimal(high));
            if (low != null) builder.low(new BigDecimal(low));
            if (close != null) builder.close(new BigDecimal(close));
            return builder.build();
        }
    }

    /**
     * BigDecimal 适配器
     */
    private static class BigDecimalAdapter extends com.google.gson.TypeAdapter<BigDecimal> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, BigDecimal value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toPlainString());
            }
        }

        @Override
        public BigDecimal read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return new BigDecimal(in.nextString());
        }
    }

    /**
     * LocalDate 适配器
     */
    private static class LocalAdapter extends com.google.gson.TypeAdapter<LocalDate> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public LocalDate read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDate.parse(in.nextString());
        }
    }
}
