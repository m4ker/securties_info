package com.securities.info.datasource;

import com.securities.info.model.AdjustmentType;
import com.securities.info.model.HistoricalBar;
import com.securities.info.model.Market;
import org.junit.jupiter.api.*;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HistoricalDataCache 单元测试
 */
@DisplayName("历史数据缓存测试")
class HistoricalDataCacheTest {

    private HistoricalDataCache cache;
    private Path testCacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // 使用临时目录作为测试缓存目录
        testCacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "cache-test-" + UUID.randomUUID());
        cache = new HistoricalDataCache(testCacheDir.toString());
    }

    @AfterEach
    void tearDown() {
        // 清理测试缓存
        if (cache != null) {
            cache.clear();
        }
        // 删除临时目录
        try {
            if (Files.exists(testCacheDir)) {
                Files.walk(testCacheDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // generateKey 测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateKey - 生成正确的缓存key")
    void testGenerateKey() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        assertEquals("AAPL_2024-01-01_2024-03-31_FORWARD", key);
    }

    @Test
    @DisplayName("generateKey - 小写symbol自动转为大写")
    void testGenerateKey_LowerCaseSymbol() {
        String key = cache.generateKey("aapl", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.NONE);
        assertEquals("AAPL_2024-01-01_2024-03-31_NONE", key);
    }

    @Test
    @DisplayName("generateKey - 不同复权类型生成不同key")
    void testGenerateKey_DifferentAdjustmentTypes() {
        String key1 = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        String key2 = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.NONE);
        assertNotEquals(key1, key2);
    }

    // -------------------------------------------------------------------------
    // put & get 基本功能测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("put & get - 基本存取功能")
    void testPutAndGet() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        List<HistoricalBar> bars = createTestBars("AAPL", 5);

        cache.put(key, bars);
        List<HistoricalBar> cached = cache.get(key);

        assertNotNull(cached);
        assertEquals(5, cached.size());
        assertEquals("AAPL", cached.get(0).getSymbol());
        // close = price + 1.00 = 151.00
        assertEquals(new BigDecimal("151.00"), cached.get(0).getClose());
    }

    @Test
    @DisplayName("put & get - 多次put覆盖同一key")
    void testPutOverwrite() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);

        List<HistoricalBar> bars1 = createTestBars("AAPL", 5);
        cache.put(key, bars1);

        List<HistoricalBar> bars2 = createTestBars("AAPL", 10);
        cache.put(key, bars2);

        List<HistoricalBar> cached = cache.get(key);
        assertEquals(10, cached.size());
    }

    @Test
    @DisplayName("put - null数据不应缓存")
    void testPutNullData() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        cache.put(key, null);

        List<HistoricalBar> cached = cache.get(key);
        assertNull(cached);
    }

    @Test
    @DisplayName("put - 空列表不应缓存")
    void testPutEmptyList() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        cache.put(key, Collections.emptyList());

        List<HistoricalBar> cached = cache.get(key);
        assertNull(cached);
    }

    @Test
    @DisplayName("get - 未缓存的key返回null")
    void testGet_NotFound() {
        String key = cache.generateKey("UNKNOWN", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        List<HistoricalBar> cached = cache.get(key);
        assertNull(cached);
    }

    // -------------------------------------------------------------------------
    // 内存缓存测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("内存缓存 - 首次get后数据被缓存")
    void testMemoryCache_Hit() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        List<HistoricalBar> bars = createTestBars("AAPL", 5);

        // 模拟数据从文件加载到内存（先put写入，再新建cache实例模拟重启）
        cache.put(key, bars);

        // 新建缓存实例，模拟从文件恢复
        HistoricalDataCache newCache = new HistoricalDataCache(testCacheDir.toString());
        List<HistoricalBar> cached = newCache.get(key);

        assertNotNull(cached);
        assertEquals(5, cached.size());
    }

    // -------------------------------------------------------------------------
    // clear 测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("clear - 清除所有缓存")
    void testClear() {
        String key1 = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        String key2 = cache.generateKey("GOOGL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);

        cache.put(key1, createTestBars("AAPL", 5));
        cache.put(key2, createTestBars("GOOGL", 5));

        assertNotNull(cache.get(key1));
        assertNotNull(cache.get(key2));

        cache.clear();

        assertNull(cache.get(key1));
        assertNull(cache.get(key2));
    }

    // -------------------------------------------------------------------------
    // cleanExpired 测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cleanExpired - 删除过期缓存文件")
    void testCleanExpired() throws Exception {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        List<HistoricalBar> bars = createTestBars("AAPL", 5);

        // 写入缓存
        cache.put(key, bars);

        // 获取缓存文件路径 - 注意：文件在 history 子目录中
        String safeFileName = key.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
        Path cacheFile = testCacheDir.resolve("history").resolve(safeFileName);
        assertTrue(Files.exists(cacheFile), "缓存文件应该存在: " + cacheFile);

        // 修改文件时间为25小时前（超过24小时过期阈值）
        long oldTime = System.currentTimeMillis() - (25L * 60 * 60 * 1000);
        Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(oldTime));

        // 用反射清空内存缓存（不删除文件）
        java.lang.reflect.Field field = HistoricalDataCache.class.getDeclaredField("memoryCache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, ?> memoryCache = 
            (java.util.concurrent.ConcurrentHashMap<String, ?>) field.get(cache);
        memoryCache.clear();

        // 调用 cleanExpired
        cache.cleanExpired();

        // 验证过期文件已被删除
        assertFalse(Files.exists(cacheFile), "过期的缓存文件应该被删除");
    }

    @Test
    @DisplayName("cleanExpired - 未过期缓存文件保留")
    void testCleanExpired_NotExpired() throws Exception {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        List<HistoricalBar> bars = createTestBars("AAPL", 5);

        cache.put(key, bars);

        cache.cleanExpired();

        assertNotNull(cache.get(key));
    }

    // -------------------------------------------------------------------------
    // getStats 测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStats - 返回正确的统计信息")
    void testGetStats() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        cache.put(key, createTestBars("AAPL", 5));

        Map<String, Object> stats = cache.getStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("memoryCacheSize"));
        assertTrue(stats.containsKey("fileCacheCount"));
        assertTrue(stats.containsKey("cacheDir"));
        assertEquals(1, stats.get("memoryCacheSize"));
    }

    @Test
    @DisplayName("getStats - 初始状态为空")
    void testGetStats_InitialState() {
        Map<String, Object> stats = cache.getStats();

        assertNotNull(stats);
        assertEquals(0, stats.get("memoryCacheSize"));
        // Files.list().count() 返回 long 类型
        assertEquals(0L, stats.get("fileCacheCount"));
    }

    // -------------------------------------------------------------------------
    // 数据完整性测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("数据完整性 - 所有字段正确序列化/反序列化")
    void testDataIntegrity() {
        String key = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);

        List<HistoricalBar> original = new ArrayList<>();
        original.add(HistoricalBar.builder()
                .symbol("AAPL")
                .market(Market.US)
                .date(LocalDate.of(2024, 1, 2))
                .open(new BigDecimal("185.50"))
                .high(new BigDecimal("188.75"))
                .low(new BigDecimal("184.25"))
                .close(new BigDecimal("187.50"))
                .adjustment(AdjustmentType.FORWARD)
                .volume(50000000L)
                .build());

        cache.put(key, original);

        // 新建cache实例模拟重启后读取
        HistoricalDataCache newCache = new HistoricalDataCache(testCacheDir.toString());
        List<HistoricalBar> restored = newCache.get(key);

        assertNotNull(restored);
        assertEquals(1, restored.size());

        HistoricalBar bar = restored.get(0);
        assertEquals("AAPL", bar.getSymbol());
        assertEquals(Market.US, bar.getMarket());
        assertEquals(LocalDate.of(2024, 1, 2), bar.getDate());
        assertEquals(new BigDecimal("185.50"), bar.getOpen());
        assertEquals(new BigDecimal("188.75"), bar.getHigh());
        assertEquals(new BigDecimal("184.25"), bar.getLow());
        assertEquals(new BigDecimal("187.50"), bar.getClose());
        assertEquals(AdjustmentType.FORWARD, bar.getAdjustment());
        assertEquals(50000000L, bar.getVolume());
    }

    // -------------------------------------------------------------------------
    // 多市场数据测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("多市场 - 不同市场的数据独立缓存")
    void testMultiMarket() {
        String keyUS = cache.generateKey("AAPL", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        String keyHK = cache.generateKey("0700.HK", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);
        String keyCN = cache.generateKey("sh600519", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), AdjustmentType.FORWARD);

        cache.put(keyUS, createTestBars("AAPL", 5));
        cache.put(keyHK, createTestBars("0700.HK", 5));
        cache.put(keyCN, createTestBars("sh600519", 5));

        assertNotNull(cache.get(keyUS));
        assertNotNull(cache.get(keyHK));
        assertNotNull(cache.get(keyCN));

        Map<String, Object> stats = cache.getStats();
        assertEquals(3, stats.get("memoryCacheSize"));
    }

    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------

    private List<HistoricalBar> createTestBars(String symbol, int count) {
        List<HistoricalBar> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        BigDecimal price = new BigDecimal("150.00");

        for (int i = 0; i < count; i++) {
            bars.add(HistoricalBar.builder()
                    .symbol(symbol)
                    .market(symbol.contains(".HK") ? Market.HK : (symbol.startsWith("sh") || symbol.startsWith("sz") ? Market.CN_A : Market.US))
                    .date(date.plusDays(i))
                    .open(price)
                    .high(price.add(new BigDecimal("2.00")))
                    .low(price.subtract(new BigDecimal("1.00")))
                    .close(price.add(new BigDecimal("1.00")))
                    .adjustment(AdjustmentType.FORWARD)
                    .volume(1000000L + i * 10000)
                    .build());
        }
        return bars;
    }
}
