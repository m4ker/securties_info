package com.securities.info;

import com.securities.info.model.*;
import com.securities.info.service.SecuritiesInfoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 证券信息查询系统 - 命令行入口
 */
public class Main {

    private static final SecuritiesInfoService service = new SecuritiesInfoService();
    private static final Scanner scanner = new Scanner(System.in, "UTF-8");

    public static void main(String[] args) {
        printBanner();

        if (args.length > 0) {
            handleCommand(String.join(" ", args));
            return;
        }

        // 交互模式
        while (true) {
            printMenu();
            System.out.print("请输入选项: ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("q") || input.equalsIgnoreCase("quit")) {
                System.out.println("再见！");
                break;
            }
            handleCommand(input);
        }
    }

    private static void handleCommand(String input) {
        switch (input) {
            case "1" -> search();
            case "2" -> getQuote();
            case "3" -> getHistory();
            case "4" -> listSecurities();
            case "q" -> System.exit(0);
            default -> System.out.println("无效选项，请重新输入。");
        }
    }

    private static void search() {
        System.out.println("\n=== 证券搜索 ===");
        System.out.print("请输入关键词: ");
        String keyword = scanner.nextLine().trim();

        System.out.print("市场 (us/hk/cn) [默认 us]: ");
        String mInput = scanner.nextLine().trim().toLowerCase();
        Market market = switch (mInput) {
            case "hk" -> Market.HK;
            case "cn" -> Market.CN_A;
            default -> Market.US;
        };

        System.out.println("\n查询中...");
        List<Security> list = service.searchSecurities(market, keyword, 10);

        if (list.isEmpty()) {
            System.out.println("未找到结果。");
        } else {
            System.out.printf("%-12s %-20s %-8s%n", "代码", "名称", "市场");
            System.out.println("-".repeat(45));
            for (Security s : list) {
                System.out.printf("%-12s %-20s %-8s%n",
                        s.getSymbol(), truncate(s.getName(), 20), s.getMarket().getDisplayName());
            }
        }
    }

    private static void getQuote() {
        System.out.println("\n=== 实时行情查询 ===");
        System.out.print("请输入证券代码（如 AAPL / 0700.HK / sh600519）: ");
        String symbol = scanner.nextLine().trim();
        if (symbol.isEmpty()) {
            System.out.println("代码不能为空！");
            return;
        }

        System.out.println("查询中...");
        Quote quote = service.getQuote(symbol);

        if (quote == null) {
            System.out.println("未找到行情数据。");
            return;
        }

        System.out.println();
        printQuote(quote);
    }

    private static void getHistory() {
        System.out.println("\n=== 历史数据查询 ===");
        System.out.print("请输入证券代码: ");
        String symbol = scanner.nextLine().trim();

        System.out.println("周期选项:");
        for (Period p : Period.values()) {
            System.out.printf("  %-8s %s%n", p.getCode(), p.getDisplayName());
        }
        System.out.print("请输入周期 [默认 1y]: ");
        String periodStr = scanner.nextLine().trim();
        Period period = parsePeriod(periodStr.isEmpty() ? "1y" : periodStr);

        System.out.println("查询中...");
        List<HistoricalBar> bars = service.getHistoricalData(symbol, period);

        if (bars.isEmpty()) {
            System.out.println("未找到历史数据。");
            return;
        }

        System.out.printf("%n共 %d 条数据（显示最近10条）:%n", bars.size());
        System.out.printf("%-12s %-10s %-10s %-10s %-10s%n", "日期", "开盘", "最高", "最低", "收盘");
        System.out.println("-".repeat(55));

        int show = Math.min(10, bars.size());
        for (int i = bars.size() - show; i < bars.size(); i++) {
            HistoricalBar b = bars.get(i);
            System.out.printf("%-12s %-10s %-10s %-10s %-10s%n",
                    b.getDate(), b.getOpen(), b.getHigh(), b.getLow(), b.getClose());
        }
    }

    private static void listSecurities() {
        System.out.println("\n=== 全量证券列表 ===");

        System.out.print("选择市场 (us/hk/cn) [默认 all]: ");
        String mInput = scanner.nextLine().trim().toLowerCase();

        System.out.println("\n获取中...");

        if (mInput.isEmpty() || mInput.equals("all")) {
            // 显示所有市场
            Map<Market, List<Security>> allSecurities = service.getAllSecuritiesMap();
            for (Map.Entry<Market, List<Security>> entry : allSecurities.entrySet()) {
                System.out.printf("%n【%s】共 %d 只:%n", entry.getKey().getDisplayName(), entry.getValue().size());
                if (entry.getValue().isEmpty()) {
                    System.out.println("  (暂无数据)");
                } else {
                    int show = Math.min(5, entry.getValue().size());
                    for (int i = 0; i < show; i++) {
                        Security s = entry.getValue().get(i);
                        System.out.printf("  %-12s %-20s%n", s.getSymbol(), truncate(s.getName(), 20));
                    }
                    if (entry.getValue().size() > 5) {
                        System.out.printf("  ... 还有 %d 只%n", entry.getValue().size() - 5);
                    }
                }
            }
        } else {
            Market market = switch (mInput) {
                case "us" -> Market.US;
                case "hk" -> Market.HK;
                case "cn" -> Market.CN_A;
                default -> Market.US;
            };

            List<Security> securities = service.getAllSecurities(market);
            System.out.printf("%n【%s】共 %d 只:%n", market.getDisplayName(), securities.size());

            if (securities.isEmpty()) {
                System.out.println("  (暂无数据)");
            } else {
                int show = Math.min(20, securities.size());
                System.out.printf("%-12s %-25s%n", "代码", "名称");
                System.out.println("-".repeat(40));
                for (int i = 0; i < show; i++) {
                    Security s = securities.get(i);
                    System.out.printf("%-12s %-25s%n", s.getSymbol(), truncate(s.getName(), 25));
                }
                if (securities.size() > show) {
                    System.out.printf("  ... 还有 %d 只%n", securities.size() - show);
                }
            }
        }
    }

    private static void printQuote(Quote q) {
        String changeSymbol = q.getChange() != null && q.getChange().signum() >= 0 ? "▲" : "▼";
        System.out.printf("┌─────────────────────────────────────┐%n");
        System.out.printf("│ [%s] %-10s %s%n", q.getMarket().getCode(), q.getSymbol(), q.getName());
        System.out.printf("├─────────────────────────────────────┤%n");
        System.out.printf("│ 最新价:  %-10s %s%n", q.getCurrency(), fmt(q.getPrice()));
        if (q.getChangePercent() != null) {
            System.out.printf("│ 涨跌:    %s %s (%.2f%%)%n", changeSymbol, fmt(q.getChange()), q.getChangePercent());
        }
        System.out.printf("│ 开盘:    %-10s  最高: %s%n", fmt(q.getOpen()), fmt(q.getHigh()));
        System.out.printf("│ 昨收:    %-10s  最低: %s%n", fmt(q.getPreviousClose()), fmt(q.getLow()));
        System.out.printf("│ 成交量:  %s%n", q.getVolume() != null ? q.getVolume() : "-");
        System.out.printf("└─────────────────────────────────────┘%n");
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║    证券信息查询系统 v2.0              ║");
        System.out.println("║    支持 美股 / 港股 / A股            ║");
        System.out.println("╚══════════════════════════════════════╝");
    }

    private static void printMenu() {
        System.out.println("\n--- 功能菜单 ---");
        System.out.println("  1. 证券搜索");
        System.out.println("  2. 实时行情查询");
        System.out.println("  3. 历史数据查询");
        System.out.println("  4. 全量证券列表");
        System.out.println("  q. 退出");
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static Period parsePeriod(String code) {
        for (Period p : Period.values()) {
            if (p.getCode().equalsIgnoreCase(code)) return p;
        }
        return Period.ONE_YEAR;
    }
}
