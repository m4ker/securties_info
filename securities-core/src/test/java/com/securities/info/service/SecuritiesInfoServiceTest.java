package com.securities.info.service;

import com.securities.info.model.Market;
import com.securities.info.model.Period;
import com.securities.info.model.Quote;
import com.securities.info.model.Security;
import com.securities.info.model.HistoricalBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecuritiesInfoService 单元测试（集成测试，需网络）
 * 使用 @Test 标注的方法会直接调用真实 API，可在有网络的环境下运行
 */
class SecuritiesInfoServiceTest {

    private SecuritiesInfoService service;

    @BeforeEach
    void setUp() {
        service = new SecuritiesInfoService();
    }

    // -------------------------------------------------------------------------
    // 市场识别
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("识别美股代码")
    void testDetectMarket_US() {
        assertEquals(Market.US, SecuritiesInfoService.detectMarket("AAPL"));
        assertEquals(Market.US, SecuritiesInfoService.detectMarket("TSLA"));
        assertEquals(Market.US, SecuritiesInfoService.detectMarket("GOOGL"));
    }

    @Test
    @DisplayName("识别港股代码")
    void testDetectMarket_HK() {
        assertEquals(Market.HK, SecuritiesInfoService.detectMarket("0700.HK"));
        assertEquals(Market.HK, SecuritiesInfoService.detectMarket("9988.HK"));
        assertEquals(Market.HK, SecuritiesInfoService.detectMarket("0700"));
    }

    @Test
    @DisplayName("识别A股代码")
    void testDetectMarket_CNA() {
        assertEquals(Market.CN_A, SecuritiesInfoService.detectMarket("sh600519"));
        assertEquals(Market.CN_A, SecuritiesInfoService.detectMarket("sz000858"));
        assertEquals(Market.CN_A, SecuritiesInfoService.detectMarket("600519"));
        assertEquals(Market.CN_A, SecuritiesInfoService.detectMarket("000858"));
    }

    // -------------------------------------------------------------------------
    // 真实 API 测试（需网络）
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("搜索美股 - 使用本地缓存搜索")
    void testSearchUS() throws Exception {
        // 美股使用本地缓存搜索（从 Wikipedia 数据生成的缓存）
        List<Security> list = service.searchSecurities(Market.US, "Apple", 5);
        System.out.println("美股搜索结果(" + list.size() + "条): " + list);
        assertFalse(list.isEmpty(), "美股本地缓存有数据，搜索应返回结果");
    }

    @Test
    @DisplayName("搜索美股 - 按代码")
    void testSearchUS_bySymbol() throws Exception {
        // 美股使用本地缓存搜索
        List<Security> list = service.searchSecurities(Market.US, "TSLA", 5);
        System.out.println("美股代码搜索结果(" + list.size() + "条): " + list);
        assertFalse(list.isEmpty(), "美股本地缓存有 TSLA");
        assertEquals("TSLA", list.get(0).getSymbol());
    }

    @Test
    @DisplayName("搜索港股 - 按代码")
    void testSearchHK_bySymbol() throws Exception {
        // 搜索 0700.HK 能匹配缓存中的 00700.HK（去除前导零后匹配）
        List<Security> list = service.searchSecurities(Market.HK, "0700.HK", 5);
        System.out.println("港股代码搜索结果(" + list.size() + "条): " + list);
        assertFalse(list.isEmpty(), "港股搜索 0700.HK 应返回结果");
        // 缓存中是 00700.HK（5位），搜索 0700.HK（4位）应能匹配
        assertTrue(list.stream().anyMatch(s -> "00700.HK".equals(s.getSymbol())),
                "搜索 0700.HK 应包含腾讯 00700.HK");
        assertEquals(Market.HK, list.get(0).getMarket());
    }

    @Test
    @DisplayName("搜索港股 - 英文关键词")
    void testSearchHK_english() throws Exception {
        // 港股搜索使用本地缓存，英文名称搜索
        List<Security> list = service.searchSecurities(Market.HK, "TENCENT", 5);
        System.out.println("港股英文搜索结果: " + list);
        assertNotNull(list);
        assertFalse(list.isEmpty(), "港股英文搜索 TENCENT 应返回结果");
        assertTrue(list.stream().anyMatch(s -> "00700.HK".equals(s.getSymbol())),
                "搜索 TENCENT 应包含 00700.HK");
    }

    @Test
    @DisplayName("搜索A股 - 默认热门列表")
    void testSearchAShare_default() throws Exception {
        // 关键词为空时返回热门A股列表
        List<Security> list = service.searchSecurities(Market.CN_A, null, 10);
        System.out.println("A股热门(" + list.size() + "条): " + list);
        assertFalse(list.isEmpty(), "A股默认热门列表不应为空");
        assertEquals(10, list.size(), "应返回请求数量的热门股票");
        list.forEach(s -> {
            assertEquals(Market.CN_A, s.getMarket());
            assertNotNull(s.getSymbol());
            assertNotNull(s.getName());
            assertTrue(s.getSymbol().startsWith("sh") || s.getSymbol().startsWith("sz"),
                    "A股代码应有 sh/sz 前缀: " + s.getSymbol());
        });
    }

    @Test
    @DisplayName("搜索A股 - 按关键词")
    void testSearchAShare_byKeyword() throws Exception {
        List<Security> list = service.searchSecurities(Market.CN_A, "茅台", 5);
        System.out.println("A股关键词搜索(" + list.size() + "条): " + list);
        assertFalse(list.isEmpty(), "搜索 '茅台' 应返回结果");
        assertTrue(list.stream().anyMatch(s -> s.getSymbol().contains("600519")),
                "搜索茅台应包含 sh600519");
        list.forEach(s -> assertNotNull(s.getName(), "name 不应为空"));
    }

    @Test
    @DisplayName("获取美股行情 - AAPL")
    void testGetQuote_US() {
        Quote q = service.getQuote("AAPL");
        System.out.println("苹果行情: " + q);
        assertNotNull(q);
        assertNotNull(q.getPrice());
    }

    @Test
    @DisplayName("获取港股行情 - 腾讯")
    void testGetQuote_HK() {
        Quote q = service.getQuote("0700.HK");
        System.out.println("腾讯(港股)行情: " + q);
        assertNotNull(q);
    }

    @Test
    @DisplayName("获取A股行情 - 贵州茅台")
    void testGetQuote_AShare() {
        Quote q = service.getQuote("sh600519");
        System.out.println("贵州茅台行情: " + q);
        // A股行情依赖新浪接口（需携带 Referer），网络正常时应有数据
        if (q != null) {
            assertNotNull(q.getPrice());
            System.out.println("  价格: " + q.getPrice() + " 涨跌: " + q.getChange());
        } else {
            System.out.println("  [警告] 行情为空，可能新浪接口限制，请检查网络或 Referer 设置");
        }
    }

    @Test
    @DisplayName("获取美股历史数据 - 按周期")
    void testGetHistorical_US_Period() {
        List<HistoricalBar> bars = service.getHistoricalData("MSFT", Period.THREE_MONTHS);
        System.out.printf("微软(US) 3个月历史数据: %d 条%n", bars.size());
        assertFalse(bars.isEmpty());
        // 验证数据字段
        HistoricalBar last = bars.get(bars.size() - 1);
        assertNotNull(last.getClose());
        assertNotNull(last.getDate());
    }

    @Test
    @DisplayName("获取A股历史数据 - 按日期范围")
    void testGetHistorical_AShare_DateRange() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 3, 31);
        List<HistoricalBar> bars = service.getHistoricalData("sh600519", start, end);
        System.out.printf("贵州茅台(A股) 2024Q1历史数据: %d 条%n", bars.size());
        assertNotNull(bars);
    }
}
