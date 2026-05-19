package com.securities.info.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 环境配置类
 *
 * 环境配置方式（优先级从高到低）：
 * 1. 系统属性: -Dsecurities.env=production
 * 2. 环境变量: SECURITIES_ENV=production
 * 3. 默认值: dev
 *
 * 支持的环境：dev, production
 */
public class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);

    private static final String PROP_ENV = "securities.env";
    private static final String ENV_ENV = "SECURITIES_ENV";
    private static final String DEFAULT_ENV = "dev";

    private static volatile String env;

    private EnvConfig() {}

    /**
     * 获取当前环境
     * @return dev 或 production
     */
    public static String getEnv() {
        if (env == null) {
            synchronized (EnvConfig.class) {
                if (env == null) {
                    env = resolveEnv();
                }
            }
        }
        return env;
    }

    /**
     * 是否为开发环境
     */
    public static boolean isDev() {
        return "dev".equals(getEnv());
    }

    /**
     * 是否为生产环境
     */
    public static boolean isProduction() {
        return "production".equals(getEnv());
    }

    /**
     * 获取项目根目录（通过 classpath 资源定位，失败则向上查找 pom.xml）
     */
    private static Path projectRoot;

    private static Path getProjectRoot() {
        if (projectRoot != null) {
            return projectRoot;
        }
        
        // 方式1：从 user.dir 向上查找 pom.xml（最可靠）
        Path current = Paths.get(System.getProperty("user.dir"));
        while (current != null && !Files.exists(current.resolve("pom.xml"))) {
            current = current.getParent();
        }
        
        if (current != null) {
            projectRoot = current;
            log.info("通过向上查找 pom.xml 定位项目根目录: {}", projectRoot.toAbsolutePath());
            return projectRoot;
        }
        
        // 方式2：尝试 classpath 定位（仅对开发模式有效，JAR 模式会失败）
        try {
            URL resource = EnvConfig.class.getClassLoader().getResource("securities-dev.yaml");
            if (resource != null && resource.getProtocol().equals("file")) {
                // 仅处理文件 URL（开发模式），JAR 模式跳过
                Path root = Paths.get(resource.toURI()).getParent();
                if (root != null && Files.exists(root.resolve("pom.xml"))) {
                    projectRoot = root;
                    log.info("通过 classpath 定位项目根目录: {}", projectRoot.toAbsolutePath());
                    return projectRoot;
                }
            }
        } catch (Exception e) {
            log.debug("通过 classpath 定位项目根目录失败", e);
        }
        
        // 回退到 user.dir
        projectRoot = Paths.get(System.getProperty("user.dir"));
        log.warn("未找到 pom.xml，使用 user.dir 作为项目根目录: {}", projectRoot.toAbsolutePath());
        return projectRoot;
    }

    /**
     * 获取配置文件路径
     * @return securities-{env}.yaml
     */
    public static Path getConfigFile() {
        return getProjectRoot().resolve("securities-" + getEnv() + ".yaml");
    }

    /**
     * 解析环境名称
     */
    private static String resolveEnv() {
        // 1. 系统属性
        String propValue = System.getProperty(PROP_ENV);
        if (propValue != null && !propValue.isBlank()) {
            String trimmed = propValue.trim().toLowerCase();
            if (isValidEnv(trimmed)) {
                log.info("环境配置 [系统属性]: {}", trimmed);
                return trimmed;
            } else {
                log.warn("无效的环境配置 [系统属性]: {}, 使用默认值: {}", propValue, DEFAULT_ENV);
            }
        }

        // 2. 环境变量
        String envValue = System.getenv(ENV_ENV);
        if (envValue != null && !envValue.isBlank()) {
            String trimmed = envValue.trim().toLowerCase();
            if (isValidEnv(trimmed)) {
                log.info("环境配置 [环境变量]: {}", trimmed);
                return trimmed;
            } else {
                log.warn("无效的环境配置 [环境变量]: {}, 使用默认值: {}", envValue, DEFAULT_ENV);
            }
        }

        // 3. 默认值
        log.info("环境配置 [默认值]: {}", DEFAULT_ENV);
        return DEFAULT_ENV;
    }

    /**
     * 验证环境名称是否有效
     */
    private static boolean isValidEnv(String envName) {
        return "dev".equals(envName) || "production".equals(envName);
    }

    /**
     * 加载 YAML 配置文件
     * @return 配置 Map，如果文件不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadConfig() {
        Path configFile = getConfigFile();

        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> config = yaml.load(is);
                log.info("加载配置文件: {} ({} 个配置项)", configFile.toAbsolutePath(), 
                    config != null ? config.size() : 0);
                return config;
            } catch (IOException e) {
                log.error("加载配置文件失败: {}", configFile, e);
            }
        } else {
            log.debug("配置文件不存在: {}，将使用其他配置方式", configFile.toAbsolutePath());
        }

        return null;
    }

    /**
     * 设置环境（用于测试）
     */
    public static void setEnv(String envName) {
        if (isValidEnv(envName)) {
            env = envName;
        } else {
            throw new IllegalArgumentException("无效的环境名称: " + envName + "，有效值: dev, production");
        }
    }

    /**
     * 重置配置（用于测试）
     */
    public static void reset() {
        env = null;
        projectRoot = null;
    }
}
