package com.securities.info.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 证券相关配置类
 *
 * 缓存目录配置优先级（从高到低）：
 * 1. 系统属性: -Dsecurities.cache.dir=/path/to/cache
 * 2. 环境变量: SECURITIES_CACHE_DIR=/path/to/cache
 * 3. 配置文件: securities-{env}.yaml 中的 cache.dir
 * 4. 环境默认值:
 *    - dev: D:\Projects\HelloCodeBuddy\securities-cache
 *    - production: {user.home}/securities-cache
 */
public class SecuritiesConfig {

    private static final Logger log = LoggerFactory.getLogger(SecuritiesConfig.class);

    // 配置键名
    private static final String PROP_CACHE_DIR = "securities.cache.dir";
    private static final String ENV_CACHE_DIR = "SECURITIES_CACHE_DIR";

    // 环境默认值
    private static final String DEV_CACHE_DIR = "D:\\Projects\\securities_info\\securities-cache";
    private static final String PROD_CACHE_DIR = "~/.securities-cache";

    // 缓存
    private static volatile String cacheDir;
    private static volatile Map<String, Object> configMap;

    private SecuritiesConfig() {}

    /**
     * 获取缓存目录路径
     * @return 缓存目录绝对路径
     */
    public static String getCacheDir() {
        if (cacheDir == null) {
            synchronized (SecuritiesConfig.class) {
                if (cacheDir == null) {
                    cacheDir = resolveCacheDir();
                }
            }
        }
        return cacheDir;
    }

    /**
     * 获取缓存目录 Path 对象
     */
    public static Path getCacheDirPath() {
        String dir = getCacheDir();
        // 处理 ~ 路径
        if (dir.startsWith("~")) {
            String home = System.getProperty("user.home");
            dir = home + File.separator + dir.substring(1);
        }
        return Paths.get(dir);
    }

    /**
     * 获取证券列表缓存文件路径
     */
    public static Path getSecuritiesListCacheFile(String market) {
        return getCacheDirPath().resolve(market + "_securities.json");
    }

    /**
     * 获取历史数据缓存目录
     */
    public static Path getHistoryCacheDir() {
        return getCacheDirPath().resolve("history");
    }

    /**
     * 解析缓存目录
     */
    private static String resolveCacheDir() {
        // 1. 系统属性
        String propValue = System.getProperty(PROP_CACHE_DIR);
        if (propValue != null && !propValue.isBlank()) {
            String resolved = resolvePath(propValue.trim());
            log.info("缓存目录 [系统属性]: {}", resolved);
            return resolved;
        }

        // 2. 环境变量
        String envValue = System.getenv(ENV_CACHE_DIR);
        if (envValue != null && !envValue.isBlank()) {
            String resolved = resolvePath(envValue.trim());
            log.info("缓存目录 [环境变量]: {}", resolved);
            return resolved;
        }

        // 3. YAML 配置文件
        Map<String, Object> config = getConfigMap();
        if (config != null) {
            Object cacheObj = config.get("cache");
            if (cacheObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cacheConfig = (Map<String, Object>) cacheObj;
                String configValue = (String) cacheConfig.get("dir");
                if (configValue != null && !configValue.isBlank()) {
                    String resolved = resolvePath(configValue.trim());
                    log.info("缓存目录 [配置文件]: {}", resolved);
                    return resolved;
                }
            }
        }

        // 4. 环境默认值
        String defaultDir = EnvConfig.isDev() ? DEV_CACHE_DIR : PROD_CACHE_DIR;
        String resolved = resolvePath(defaultDir);
        log.info("缓存目录 [环境默认值]: {}", resolved);
        return resolved;
    }

    /**
     * 解析路径中的变量
     * 支持 ${user.home} 等变量替换
     */
    private static String resolvePath(String path) {
        if (path == null) return path;

        String resolved = path;
        // 替换 ${user.home}
        if (resolved.contains("${user.home}")) {
            String home = System.getProperty("user.home");
            resolved = resolved.replace("${user.home}", home);
        }
        return resolved;
    }

    /**
     * 获取配置文件内容（带缓存）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getConfigMap() {
        if (configMap == null) {
            synchronized (SecuritiesConfig.class) {
                if (configMap == null) {
                    configMap = EnvConfig.loadConfig();
                }
            }
        }
        return configMap;
    }

    /**
     * 设置缓存目录（用于测试）
     */
    public static void setCacheDir(String dir) {
        cacheDir = dir;
    }

    /**
     * 设置配置属性（用于测试）
     */
    public static void setConfigMap(Map<String, Object> map) {
        configMap = map;
    }

    /**
     * 重置配置（用于测试）
     */
    public static void reset() {
        cacheDir = null;
        configMap = null;
        EnvConfig.reset();
    }
}
