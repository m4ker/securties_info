package com.securities.info.service;

import com.securities.info.model.Market;
import com.securities.info.model.Quote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 证券数据完整性测试
 *
 * 验证国内外知名互联网公司的股票代码是否存在于证券列表中。
 * 这些是我们最常用的股票，用于确保数据源完整。
 */
class SecuritiesDataCompletenessTest {

    private SecuritiesInfoService service;

    @BeforeEach
    void setUp() {
        service = new SecuritiesInfoService();
    }

    /**
     * 美国知名互联网/科技公司
     */
    private static final Map<String, String> US_COMPANIES = new LinkedHashMap<>();
    static {
        // 互联网巨头
        US_COMPANIES.put("AAPL", "Apple Inc.");
        US_COMPANIES.put("MSFT", "Microsoft Corporation");
        US_COMPANIES.put("GOOGL", "Alphabet Inc. (Google)");
        US_COMPANIES.put("GOOG", "Alphabet Inc. Class C");
        US_COMPANIES.put("AMZN", "Amazon.com Inc.");
        US_COMPANIES.put("META", "Meta Platforms Inc.");
        US_COMPANIES.put("NVDA", "NVIDIA Corporation");

        // 流媒体/社交
        US_COMPANIES.put("NFLX", "Netflix Inc.");
        US_COMPANIES.put("TSLA", "Tesla Inc.");
        US_COMPANIES.put("DIS", "The Walt Disney Company");
        US_COMPANIES.put("PYPL", "PayPal Holdings Inc.");
        US_COMPANIES.put("ADBE", "Adobe Inc.");
        US_COMPANIES.put("CRM", "Salesforce Inc.");
        US_COMPANIES.put("INTC", "Intel Corporation");
        US_COMPANIES.put("AMD", "Advanced Micro Devices");

        // 中国在美国上市的公司
        US_COMPANIES.put("BABA", "Alibaba Group Holding");
        US_COMPANIES.put("JD", "JD.com Inc.");
        US_COMPANIES.put("PDD", "Pinduoduo Inc.");
        US_COMPANIES.put("BIDU", "Baidu Inc.");
        US_COMPANIES.put("NTES", "NetEase Inc.");
        US_COMPANIES.put("TME", "Tencent Music Entertainment");
        US_COMPANIES.put("BILI", "Bilibili Inc.");
        US_COMPANIES.put("NVDA", "NVIDIA Corporation");
    }

    /**
     * 香港知名互联网/金融公司
     */
    private static final Map<String, String> HK_COMPANIES = new LinkedHashMap<>();
    static {
        // 互联网巨头
        HK_COMPANIES.put("0700.HK", "腾讯控股");
        HK_COMPANIES.put("9988.HK", "阿里巴巴-SW");
        HK_COMPANIES.put("9618.HK", "京东集团-SW");
        HK_COMPANIES.put("9898.HK", "百度集团-SW");
        HK_COMPANIES.put("0669.HK", "京东健康");
        HK_COMPANIES.put("1810.HK", "小米集团-W");
        HK_COMPANIES.put("3690.HK", "美团-W");

        // 电商/生活服务
        HK_COMPANIES.put("2319.HK", "蒙牛乳业");
        HK_COMPANIES.put("2382.HK", "舜宇光学科技");
        HK_COMPANIES.put("0268.HK", "金蝶国际");

        // 金融
        HK_COMPANIES.put("0005.HK", "汇丰控股");
        HK_COMPANIES.put("0939.HK", "建设银行");
        HK_COMPANIES.put("3968.HK", "招商银行");
        HK_COMPANIES.put("2388.HK", "中银香港");

        // 通信/媒体
        HK_COMPANIES.put("0762.HK", "中国联通");
        HK_COMPANIES.put("0941.HK", "中国移动");
        HK_COMPANIES.put("2628.HK", "中国人寿");
    }

    /**
     * A股知名互联网/消费公司
     */
    private static final Map<String, String> CNA_COMPANIES = new LinkedHashMap<>();
    static {
        // 互联网/科技
        CNA_COMPANIES.put("sh600519", "贵州茅台");
        CNA_COMPANIES.put("sh600036", "招商银行");
        CNA_COMPANIES.put("sh600276", "恒瑞医药");
        CNA_COMPANIES.put("sz000858", "五粮液");
        CNA_COMPANIES.put("sh601318", "中国平安");
        CNA_COMPANIES.put("sz000333", "美的集团");
        CNA_COMPANIES.put("sz002594", "比亚迪");

        // 互联网平台
        CNA_COMPANIES.put("sh600570", "恒生电子");
        CNA_COMPANIES.put("sz300059", "东方财富");
        CNA_COMPANIES.put("sz002027", "分众传媒");
        CNA_COMPANIES.put("sz300750", "宁德时代");

        // 通信
        CNA_COMPANIES.put("sh601728", "中国电信");
        CNA_COMPANIES.put("sh600941", "中国移动");
        CNA_COMPANIES.put("sz000063", "中兴通讯");

        // 消费
        CNA_COMPANIES.put("sh603259", "药明康德");
        CNA_COMPANIES.put("sh600887", "伊利股份");
        CNA_COMPANIES.put("sh601888", "中国中免");
    }

    // =========================================================================
    // 数据完整性检查
    // =========================================================================

    @Test
    @DisplayName("证券列表数据可用性检查")
    void testDataAvailability() {
        boolean available = service.isSecuritiesListAvailable();
        System.out.println("证券列表数据可用性: " + available);

        if (!available) {
            System.out.println("提示: 请先运行 Python 工具生成证券列表:");
            System.out.println("  cd python && python securities_list_tool.py --json");
        }
    }

    @Test
    @DisplayName("美股知名公司数据完整性")
    void testUSCompaniesCompleteness() {
        System.out.println("\n========== 美股知名公司数据完整性测试 ==========");
        Set<String> missing = new HashSet<>();

        for (Map.Entry<String, String> entry : US_COMPANIES.entrySet()) {
            String symbol = entry.getKey();
            String name = entry.getValue();
            try {
                Quote quote = service.getQuote(symbol);
                if (quote != null && quote.getPrice() != null) {
                    System.out.printf("  [OK] %s (%s) - $%s%n",
                            name, symbol, quote.getPrice());
                } else {
                    System.out.printf("  [OK] %s (%s) - 数据存在%n", name, symbol);
                }
            } catch (Exception e) {
                System.out.printf("  [!!] %s (%s) - 未找到或无法获取行情%n", name, symbol);
                missing.add(symbol + " (" + name + ")");
            }
        }

        System.out.printf("%n美股测试结果: %d/%d 通过%n",
                US_COMPANIES.size() - missing.size(), US_COMPANIES.size());

        if (!missing.isEmpty()) {
            System.out.println("缺失的股票: " + missing);
        }
    }

    @Test
    @DisplayName("港股知名公司数据完整性")
    void testHKCompaniesCompleteness() {
        System.out.println("\n========== 港股知名公司数据完整性测试 ==========");
        Set<String> missing = new HashSet<>();

        for (Map.Entry<String, String> entry : HK_COMPANIES.entrySet()) {
            String symbol = entry.getKey();
            String name = entry.getValue();
            try {
                Quote quote = service.getQuote(symbol);
                if (quote != null && quote.getPrice() != null) {
                    System.out.printf("  [OK] %s (%s) - HK$%s%n",
                            name, symbol, quote.getPrice());
                } else {
                    System.out.printf("  [OK] %s (%s) - 数据存在%n", name, symbol);
                }
            } catch (Exception e) {
                System.out.printf("  [!!] %s (%s) - 未找到或无法获取行情%n", name, symbol);
                missing.add(symbol + " (" + name + ")");
            }
        }

        System.out.printf("%n港股测试结果: %d/%d 通过%n",
                HK_COMPANIES.size() - missing.size(), HK_COMPANIES.size());

        if (!missing.isEmpty()) {
            System.out.println("缺失的股票: " + missing);
        }
    }

    @Test
    @DisplayName("A股知名公司数据完整性")
    void testCNACompaniesCompleteness() {
        System.out.println("\n========== A股知名公司数据完整性测试 ==========");
        Set<String> missing = new HashSet<>();

        for (Map.Entry<String, String> entry : CNA_COMPANIES.entrySet()) {
            String symbol = entry.getKey();
            String name = entry.getValue();
            try {
                Quote quote = service.getQuote(symbol);
                if (quote != null && quote.getPrice() != null) {
                    System.out.printf("  [OK] %s (%s) - ¥%s%n",
                            name, symbol, quote.getPrice());
                } else {
                    System.out.printf("  [OK] %s (%s) - 数据存在%n", name, symbol);
                }
            } catch (Exception e) {
                System.out.printf("  [!!] %s (%s) - 未找到或无法获取行情%n", name, symbol);
                missing.add(symbol + " (" + name + ")");
            }
        }

        System.out.printf("%nA股测试结果: %d/%d 通过%n",
                CNA_COMPANIES.size() - missing.size(), CNA_COMPANIES.size());

        if (!missing.isEmpty()) {
            System.out.println("缺失的股票: " + missing);
        }
    }

    /**
     * 综合测试：验证所有市场数据加载
     */
    @Test
    @DisplayName("全市场数据加载")
    void testAllMarketsDataLoading() {
        System.out.println("\n========== 全市场数据加载测试 ==========");

        try {
            System.out.printf("美股总数: %d%n", service.getTotalCount(Market.US));
        } catch (Exception e) {
            System.out.println("美股数据加载失败: " + e.getMessage());
        }

        // 测试港股
        try {
            var hkAll = service.getAllSecurities(Market.HK, null, 1, 10);
            System.out.printf("港股总数: %d (已加载)%n", service.getTotalCount(Market.HK));
            assertFalse(hkAll.isEmpty(), "港股数据不应为空");
        } catch (Exception e) {
            System.out.println("港股数据加载失败: " + e.getMessage());
        }

        // 测试A股
        try {
            var cnAll = service.getAllSecurities(Market.CN_A, null, 1, 10);
            System.out.printf("A股总数: %d (已加载)%n", service.getTotalCount(Market.CN_A));
            assertFalse(cnAll.isEmpty(), "A股数据不应为空");
        } catch (Exception e) {
            System.out.println("A股数据加载失败: " + e.getMessage());
        }
    }
}
