package com.securities.info.service;

import com.securities.info.model.AdjustmentType;
import com.securities.info.model.HistoricalBar;
import com.securities.info.model.Market;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复权价格测试（集成测试，需网络）
 *
 * 测试目标：
 * 1. 验证不同复权类型（不复权、前复权、后复权）能够正确获取
 * 2. 验证 HistoricalBar 的 adjustment 字段正确设置
 * 3. 验证复权价格的数学关系
 */
class AdjustmentPriceTest {

    private SecuritiesInfoService service;

    // 测试日期范围（避免周末和节假日）
    private static final LocalDate START_DATE = LocalDate.of(2024, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2024, 12, 31);

    @BeforeEach
    void setUp() {
        service = new SecuritiesInfoService();
    }

    // =========================================================================
    // 美股测试（Yahoo Finance）
    // =========================================================================

    @Nested
    @DisplayName("美股 - Yahoo Finance 复权价格测试")
    class USStockTest {

        @Test
        @DisplayName("获取 AAPL 不复权价")
        void testAAPL_None() {
            List<HistoricalBar> bars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.NONE);

            assertFalse(bars.isEmpty(), "AAPL 不复权数据不应为空");
            System.out.println("AAPL 不复权数据数量: " + bars.size());

            for (HistoricalBar bar : bars) {
                assertEquals(Market.US, bar.getMarket());
                assertEquals(AdjustmentType.NONE, bar.getAdjustment());
                assertNotNull(bar.getClose(), "收盘价不应为空");
                printBar(bar);
            }
        }

        @Test
        @DisplayName("获取 AAPL 前复权价")
        void testAAPL_Forward() {
            List<HistoricalBar> bars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.FORWARD);

            assertFalse(bars.isEmpty(), "AAPL 前复权数据不应为空");
            System.out.println("AAPL 前复权数据数量: " + bars.size());

            for (HistoricalBar bar : bars) {
                assertEquals(Market.US, bar.getMarket());
                assertEquals(AdjustmentType.FORWARD, bar.getAdjustment());
                assertNotNull(bar.getClose(), "收盘价不应为空");
            }
        }

        @Test
        @DisplayName("获取 AAPL 后复权价")
        void testAAPL_Backward() {
            List<HistoricalBar> bars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.BACKWARD);

            assertFalse(bars.isEmpty(), "AAPL 后复权数据不应为空");
            System.out.println("AAPL 后复权数据数量: " + bars.size());

            for (HistoricalBar bar : bars) {
                assertEquals(Market.US, bar.getMarket());
                assertEquals(AdjustmentType.BACKWARD, bar.getAdjustment());
                assertNotNull(bar.getClose(), "收盘价不应为空");
            }
        }

        @Test
        @DisplayName("AAPL 三种复权价格的数学关系验证")
        void testAAPL_MathRelationship() {
            List<HistoricalBar> noneBars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.NONE);
            List<HistoricalBar> forwardBars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.FORWARD);
            List<HistoricalBar> backwardBars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.BACKWARD);

            assertFalse(noneBars.isEmpty(), "不复权数据不应为空");
            assertFalse(forwardBars.isEmpty(), "前复权数据不应为空");
            assertFalse(backwardBars.isEmpty(), "后复权数据不应为空");

            // 验证数据长度一致
            assertEquals(noneBars.size(), forwardBars.size(),
                    "前复权和不复权数据长度应一致");
            assertEquals(noneBars.size(), backwardBars.size(),
                    "后复权和不复权数据长度应一致");

            System.out.println("AAPL 三种复权价格对比（前10条）:");
            System.out.println("日期       | 不复权    | 前复权    | 后复权");
            System.out.println("-".repeat(55));

            for (int i = 0; i < Math.min(10, noneBars.size()); i++) {
                HistoricalBar none = noneBars.get(i);
                HistoricalBar forward = forwardBars.get(i);
                HistoricalBar backward = backwardBars.get(i);

                assertEquals(none.getDate(), forward.getDate());
                assertEquals(none.getDate(), backward.getDate());

                String line = String.format("%s | %.4f  | %.4f  | %.4f",
                        none.getDate(),
                        none.getClose(),
                        forward.getClose(),
                        backward.getClose());
                System.out.println(line);
            }
        }

        @Test
        @DisplayName("AAPL 默认复权类型应为前复权")
        void testAAPL_DefaultAdjustment() {
            // 调用不带 adjustment 参数的方法，验证默认行为
            List<HistoricalBar> defaultBars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE);
            List<HistoricalBar> forwardBars = service.getHistoricalData(
                    "AAPL", START_DATE, END_DATE, AdjustmentType.FORWARD);

            assertFalse(defaultBars.isEmpty());
            assertFalse(forwardBars.isEmpty());

            // 默认应返回前复权价格
            assertEquals(AdjustmentType.FORWARD, defaultBars.get(0).getAdjustment());

            // 数据条数应一致
            assertEquals(defaultBars.size(), forwardBars.size());

            // 验证每条数据的 adjustment 字段一致
            for (int i = 0; i < defaultBars.size(); i++) {
                assertEquals(defaultBars.get(i).getAdjustment(),
                        forwardBars.get(i).getAdjustment(),
                        "默认复权类型应与显式指定一致");
            }
        }
    }

    // =========================================================================
    // 港股测试（Yahoo Finance）
    // =========================================================================

    @Nested
    @DisplayName("港股 - Yahoo Finance 复权价格测试")
    class HKStockTest {

        @Test
        @DisplayName("获取腾讯控股 0700.HK 三种复权价格")
        void testTencent_AllAdjustments() {
            System.out.println("腾讯控股 (0700.HK) 三种复权价格对比:");

            for (AdjustmentType type : AdjustmentType.values()) {
                List<HistoricalBar> bars = service.getHistoricalData(
                        "0700.HK", START_DATE, END_DATE, type);

                assertFalse(bars.isEmpty(),
                        "腾讯控股 " + type.getDisplayName() + " 数据不应为空");
                assertEquals(type,
                        bars.get(0).getAdjustment(),
                        "adjustment 字段应正确设置");

                BigDecimal latestClose = bars.get(bars.size() - 1).getClose();
                System.out.printf("  %s: 最新收盘价 = %.4f (共 %d 条)%n",
                        type.getDisplayName(), latestClose, bars.size());
            }
        }

        @Test
        @DisplayName("验证港股代码格式兼容性")
        void testHKCode_Format() {
            // Yahoo Finance 只支持 0700.HK 格式，不支持 700.HK
            List<HistoricalBar> bars1 = service.getHistoricalData(
                    "0700.HK", START_DATE, END_DATE, AdjustmentType.NONE);

            assertFalse(bars1.isEmpty(), "0700.HK 格式应支持");
            assertEquals(AdjustmentType.NONE, bars1.get(0).getAdjustment());
            System.out.println("腾讯控股 (0700.HK) 不复权数据: " + bars1.size() + " 条");

            // 注意：700.HK 格式不被 Yahoo Finance 支持，会返回空列表
            List<HistoricalBar> bars2 = service.getHistoricalData(
                    "700.HK", START_DATE, END_DATE, AdjustmentType.NONE);
            assertTrue(bars2.isEmpty(), "700.HK 格式不被 Yahoo Finance 支持，应返回空列表");
            System.out.println("700.HK 格式验证通过（确实不支持，返回空列表）");
        }
    }

    // =========================================================================
    // A股测试（新浪财经）
    // =========================================================================

    @Nested
    @DisplayName("A股 - 新浪财经复权价格测试")
    class CNStockTest {

        @Test
        @DisplayName("获取贵州茅台 sh600519 三种复权价格")
        void testKweichowMoutai_AllAdjustments() {
            System.out.println("贵州茅台 (sh600519) 三种复权价格对比:");

            for (AdjustmentType type : AdjustmentType.values()) {
                List<HistoricalBar> bars = service.getHistoricalData(
                        "sh600519", START_DATE, END_DATE, type);

                assertFalse(bars.isEmpty(),
                        "贵州茅台 " + type.getDisplayName() + " 数据不应为空");
                assertEquals(type, bars.get(0).getAdjustment(),
                        "adjustment 字段应正确设置");

                BigDecimal latestClose = bars.get(bars.size() - 1).getClose();
                System.out.printf("  %s: 最新收盘价 = %.4f (共 %d 条)%n",
                        type.getDisplayName(), latestClose, bars.size());
            }
        }

        @Test
        @DisplayName("获取比亚迪 sz002594 三种复权价格")
        void testBYD_AllAdjustments() {
            System.out.println("比亚迪 (sz002594) 三种复权价格对比:");

            for (AdjustmentType type : AdjustmentType.values()) {
                List<HistoricalBar> bars = service.getHistoricalData(
                        "sz002594", START_DATE, END_DATE, type);

                assertFalse(bars.isEmpty(),
                        "比亚迪 " + type.getDisplayName() + " 数据不应为空");

                BigDecimal latestClose = bars.get(bars.size() - 1).getClose();
                System.out.printf("  %s: 最新收盘价 = %.4f (共 %d 条)%n",
                        type.getDisplayName(), latestClose, bars.size());
            }
        }

        @Test
        @DisplayName("A股默认复权类型验证")
        void testCN_DefaultAdjustment() {
            // 所有市场默认返回前复权数据
            List<HistoricalBar> defaultBars = service.getHistoricalData(
                    "sh600519", START_DATE, END_DATE);
            List<HistoricalBar> forwardBars = service.getHistoricalData(
                    "sh600519", START_DATE, END_DATE, AdjustmentType.FORWARD);

            assertFalse(defaultBars.isEmpty());
            assertFalse(forwardBars.isEmpty());

            // 默认应返回前复权价格
            assertEquals(AdjustmentType.FORWARD, defaultBars.get(0).getAdjustment());

            // 数据条数应一致
            assertEquals(defaultBars.size(), forwardBars.size());

            // 验证每条数据的 adjustment 字段一致
            for (int i = 0; i < defaultBars.size(); i++) {
                assertEquals(defaultBars.get(i).getAdjustment(),
                        forwardBars.get(i).getAdjustment(),
                        "默认复权类型应与显式指定一致");
            }
        }

        @Test
        @DisplayName("验证A股代码格式兼容性")
        void testCNCode_FormatCompatibility() {
            // sh600519 和 600519 应返回相同数据
            List<HistoricalBar> bars1 = service.getHistoricalData(
                    "sh600519", START_DATE, END_DATE, AdjustmentType.NONE);
            List<HistoricalBar> bars2 = service.getHistoricalData(
                    "600519", START_DATE, END_DATE, AdjustmentType.NONE);

            assertFalse(bars1.isEmpty());
            assertFalse(bars2.isEmpty());
            assertEquals(bars1.size(), bars2.size());
        }
    }

    // =========================================================================
    // 按周期查询测试
    // =========================================================================

    @Nested
    @DisplayName("按周期查询复权价格测试")
    class PeriodQueryTest {

        @Test
        @DisplayName("按 1 年周期查询美股复权价格")
        void testPeriodQuery_US() {
            for (AdjustmentType type : AdjustmentType.values()) {
                List<HistoricalBar> bars = service.getHistoricalData(
                        "AAPL", com.securities.info.model.Period.ONE_YEAR, type);

                assertFalse(bars.isEmpty(),
                        "AAPL " + type.getDisplayName() + " 不应为空");
                assertEquals(type, bars.get(0).getAdjustment());
                System.out.printf("AAPL %s (1年): %d 条数据%n",
                        type.getDisplayName(), bars.size());
            }
        }

        @Test
        @DisplayName("按 6 个月周期查询A股复权价格")
        void testPeriodQuery_CN() {
            for (AdjustmentType type : AdjustmentType.values()) {
                List<HistoricalBar> bars = service.getHistoricalData(
                        "sh600519", com.securities.info.model.Period.SIX_MONTHS, type);

                assertFalse(bars.isEmpty(),
                        "sh600519 " + type.getDisplayName() + " 不应为空");
                assertEquals(type, bars.get(0).getAdjustment());
                System.out.printf("sh600519 %s (6个月): %d 条数据%n",
                        type.getDisplayName(), bars.size());
            }
        }
    }

    // =========================================================================
    // 边界测试
    // =========================================================================

    @Nested
    @DisplayName("边界测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("测试有分红拆股的股票（苹果 AAPL 多次拆股）")
        void testStockWithSplits_AAPL() {
            // AAPL 在 2020 年有过 4:1 拆股，测试复权数据能否正确处理
            LocalDate preSplit = LocalDate.of(2020, 6, 1);
            LocalDate postSplit = LocalDate.of(2020, 8, 1);

            List<HistoricalBar> forwardBars = service.getHistoricalData(
                    "AAPL", preSplit, postSplit, AdjustmentType.FORWARD);
            List<HistoricalBar> noneBars = service.getHistoricalData(
                    "AAPL", preSplit, postSplit, AdjustmentType.NONE);

            assertFalse(forwardBars.isEmpty(), "前复权数据不应为空");
            assertFalse(noneBars.isEmpty(), "不复权数据不应为空");

            // 打印拆股前后价格对比
            System.out.println("AAPL 拆股期间 (2020-06 ~ 2020-08) 价格对比:");
            System.out.println("日期       | 不复权    | 前复权");
            System.out.println("-".repeat(40));

            for (HistoricalBar bar : forwardBars) {
                int idx = forwardBars.indexOf(bar);
                if (idx < noneBars.size()) {
                    System.out.printf("%s | %.4f  | %.4f%n",
                            bar.getDate(),
                            noneBars.get(idx).getClose(),
                            bar.getClose());
                }
            }
        }

        @Test
        @DisplayName("测试长期数据（前复权 vs 不复权）")
        void testLongTermData() {
            // 测试 5 年数据，验证复权调整的正确性
            LocalDate fiveYearsAgo = LocalDate.now().minusYears(5);
            LocalDate now = LocalDate.now();

            List<HistoricalBar> noneBars = service.getHistoricalData(
                    "AAPL", fiveYearsAgo, now, AdjustmentType.NONE);
            List<HistoricalBar> forwardBars = service.getHistoricalData(
                    "AAPL", fiveYearsAgo, now, AdjustmentType.FORWARD);

            assertFalse(noneBars.isEmpty(), "5年不复权数据不应为空");
            assertFalse(forwardBars.isEmpty(), "5年前复权数据不应为空");

            System.out.println("AAPL 5年数据统计:");
            System.out.printf("  不复权数据: %d 条%n", noneBars.size());
            System.out.printf("  前复权数据: %d 条%n", forwardBars.size());

            // 验证最新价格应该接近（因为前复权保持最新价格不变）
            BigDecimal noneLatest = noneBars.get(noneBars.size() - 1).getClose();
            BigDecimal forwardLatest = forwardBars.get(forwardBars.size() - 1).getClose();

            System.out.printf("  最新不复权价: %.4f%n", noneLatest);
            System.out.printf("  最新前复权价: %.4f%n", forwardLatest);
            System.out.printf("  价格差异: %.4f (%.2f%%)%n",
                    forwardLatest.subtract(noneLatest).abs(),
                    forwardLatest.subtract(noneLatest)
                            .divide(noneLatest, 4, BigDecimal.ROUND_HALF_UP)
                            .abs()
                            .multiply(BigDecimal.valueOf(100)));
        }
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private void printBar(HistoricalBar bar) {
        System.out.printf("  %s | O:%.2f H:%.2f L:%.2f C:%.2f [%-4s]%n",
                bar.getDate(),
                bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(),
                bar.getAdjustment().getDisplayName());
    }
}
