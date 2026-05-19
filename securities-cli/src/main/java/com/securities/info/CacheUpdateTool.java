package com.securities.info;

import com.securities.info.config.SecuritiesConfig;
import com.securities.info.datasource.SecuritiesListUpdater;
import com.securities.info.model.Market;
import com.securities.info.model.Security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 缓存更新工具
 *
 * 功能：
 * 1. 先获取数据到内存
 * 2. 只有在所有市场数据都成功获取后，才覆盖保存到缓存文件
 * 3. 任何一个市场失败，都不会覆盖旧缓存
 */
public class CacheUpdateTool {

    public static void main(String[] args) {
        CacheUpdateTool tool = new CacheUpdateTool();
        tool.run(args);
    }

    public void run(String[] args) {
        printBanner();

        // 解析参数
        String marketArg = args.length > 0 ? args[0].toLowerCase() : "all";
        Market targetMarket = parseMarket(marketArg);

        System.out.println("缓存目录: " + SecuritiesConfig.getCacheDir());
        System.out.println();

        // 更新缓存
        boolean success = updateCache(targetMarket);

        if (success) {
            System.out.println("\n✓ 缓存更新成功！");
            System.exit(0);
        } else {
            System.out.println("\n✗ 缓存更新失败，旧缓存未被修改。");
            System.exit(1);
        }
    }

    /**
     * 更新缓存
     * @param market 要更新的市场，null 表示所有市场
     * @return 是否全部成功
     */
    public boolean updateCache(Market market) {
        List<Market> markets = market != null ? List.of(market) : List.of(Market.values());

        System.out.println("开始更新缓存...");
        System.out.println("目标市场: " + (market != null ? market.getDisplayName() : "全部"));
        System.out.println();

        // 存储获取到的数据
        List<MarketData> results = new ArrayList<>();
        boolean allSuccess = true;

        // 逐个市场获取数据
        for (Market m : markets) {
            System.out.println("=".repeat(50));
            System.out.println("正在获取: " + m.getDisplayName());
            System.out.println("=".repeat(50));

            try {
                // 使用仅获取模式，不自动保存
                SecuritiesListUpdater updater = new SecuritiesListUpdater(SecuritiesConfig.getCacheDirPath(), true);
                List<Security> securities = updater.fetchByMarketOnly(m);

                if (securities.isEmpty()) {
                    System.out.println("  [警告] 获取数据为空！");
                    allSuccess = false;
                } else {
                    System.out.println("  获取成功: " + securities.size() + " 只");
                    results.add(new MarketData(m, securities));
                }

            } catch (Exception e) {
                System.out.println("  [错误] 获取失败: " + e.getMessage());
                allSuccess = false;
            }

            System.out.println();
        }

        // 所有市场都成功，才保存缓存
        if (allSuccess && !results.isEmpty()) {
            System.out.println("-".repeat(50));
            System.out.println("所有市场数据获取成功，开始保存缓存...");
            System.out.println("-".repeat(50));

            SecuritiesListUpdater saver = new SecuritiesListUpdater(SecuritiesConfig.getCacheDirPath(), true);
            for (MarketData data : results) {
                saver.saveMarketCache(data.market, data.securities);
            }

            return true;
        } else {
            System.out.println("-".repeat(50));
            System.out.println("部分市场获取失败，跳过保存操作。");
            System.out.println("旧缓存文件保持不变。");
            return false;
        }
    }

    private Market parseMarket(String arg) {
        return switch (arg) {
            case "us" -> Market.US;
            case "hk" -> Market.HK;
            case "cn", "a" -> Market.CN_A;
            default -> null; // 所有市场
        };
    }

    private void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║    证券列表缓存更新工具               ║");
        System.out.println("║    更新前会先获取数据到内存           ║");
        System.out.println("║    全部成功后才覆盖旧缓存             ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("用法: java -jar securities-cli.jar update [market]");
        System.out.println("  market: us | hk | cn | all (默认)");
        System.out.println();
    }

    private static class MarketData {
        final Market market;
        final List<Security> securities;

        MarketData(Market market, List<Security> securities) {
            this.market = market;
            this.securities = securities;
        }
    }
}
