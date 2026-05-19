# 证券投资管理系统 - 信息查询模块

## 项目简介

支持查询**美股（US）、港股（HK）、A股（CN）**的证券信息：
- 证券搜索
- 实时行情
- 历史日K线数据

## 项目结构

```
securities-parent/
├── securities-common/     # 公共模块：数据模型、枚举
├── securities-core/       # 核心模块：业务逻辑、数据源
├── securities-sdk/        # SDK 模块：客户端库（可独立发布）
├── securities-api/        # REST API 模块（Spring Boot）
├── securities-cli/        # 命令行模块
└── pom.xml                # 父 POM
```

## 技术栈

| 组件 | 说明 |
|------|------|
| Java 17 | 主语言 |
| Maven | 构建工具 |
| OkHttp 4.12 | HTTP 客户端 |
| Gson 2.10 | JSON 解析 |
| Spring Boot 3.2 | REST API |
| Lombok | 简化代码 |

## 快速开始

### 构建项目

```bash
# 构建所有模块
mvn clean package -DskipTests

# 仅构建指定模块
mvn clean package -DskipTests -pl securities-cli
mvn clean package -DskipTests -pl securities-api
```

### 运行 CLI 应用

```bash
cd securities-cli
java -jar target/securities-cli.jar
```

### 更新缓存

```bash
# 更新所有市场缓存（美股、港股、A股）
java -cp securities-cli.jar com.securities.info.CacheUpdateTool

# 只更新港股
java -cp securities-cli.jar com.securities.info.CacheUpdateTool hk

# 只更新美股
java -cp securities-cli.jar com.securities.info.CacheUpdateTool us

# 只更新A股
java -cp securities-cli.jar com.securities.info.CacheUpdateTool cn
```

**缓存更新机制**：
1. **分页级原子性**：获取每个市场时，必须所有分页都成功才保存，任一分页失败则不保存该市场
2. **市场级原子性（CLI 工具）**：所有市场都成功获取后，才一次性覆盖保存到缓存文件；任一市场失败，都不覆盖旧缓存

### 启动 REST API

```bash
cd securities-api
java -jar target/securities-api.jar
# 服务地址: http://localhost:8080
```

### 环境配置

系统支持分环境配置，通过 `securities.env` 参数指定当前环境：

| 配置方式 | 优先级 | 示例 |
|----------|--------|------|
| 系统属性 | 1 | `-Dsecurities.env=production` |
| 环境变量 | 2 | `SECURITIES_ENV=production` |
| 默认值 | 3 | `dev` |

**支持的环境**：
- `dev` - 开发环境（默认）
- `production` - 生产环境

**使用示例**：

```bash
# 指定环境
java -Dsecurities.env=production -jar securities-cli.jar

# PowerShell 环境变量
$env:SECURITIES_ENV="production"

# Linux/Mac 环境变量
export SECURITIES_ENV=production
```

### 缓存配置

系统使用本地文件缓存证券列表和历史数据，可通过以下方式配置缓存目录：

| 配置方式 | 优先级 | 示例 |
|----------|--------|------|
| 系统属性 | 1 | `-Dsecurities.cache.dir=/data/cache` |
| 环境变量 | 2 | `SECURITIES_CACHE_DIR=/data/cache` |
| 配置文件 | 3 | `securities-{env}.yaml` |
| 环境默认值 | 4 | 环境相关（见下表） |

**环境默认值**：

| 环境 | 默认缓存目录 |
|------|-------------|
| `dev` | `D:\Projects\HelloCodeBuddy\securities-cache` |
| `production` | `{user.home}/securities-cache` |

**配置文件**（位于 `securities-common/src/main/resources/`）：
- `securities-dev.yaml` - 开发环境配置
- `securities-production.yaml` - 生产环境配置

**使用示例**：

```bash
# Maven 测试
mvn test -Dsecurities.cache.dir=/tmp/securities-cache

# 运行 JAR（指定环境）
java -Dsecurities.env=production -Dsecurities.cache.dir=/data/cache -jar securities-cli.jar

# PowerShell 环境变量
$env:SECURITIES_CACHE_DIR="D:\custom\cache"

# Linux/Mac 环境变量
export SECURITIES_CACHE_DIR=/var/data/securities-cache
```

### API 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/securities/search` | 搜索证券 |
| GET | `/api/securities/list` | 获取指定市场全量证券列表 |
| GET | `/api/securities/list/all` | 获取所有市场证券列表 |
| GET | `/api/securities/count` | 获取指定市场证券数量 |
| GET | `/api/securities/quote` | 获取实时行情 |
| GET | `/api/securities/quotes` | 批量获取行情 |
| GET | `/api/securities/history` | 获取历史数据（按日期范围） |
| GET | `/api/securities/history/period` | 获取历史数据（按周期） |
| GET | `/api/securities/markets` | 获取支持的市场列表 |
| GET | `/api/securities/periods` | 获取支持的周期列表 |

## SDK 使用

### 添加依赖

```xml
<dependency>
    <groupId>com.securities</groupId>
    <artifactId>securities-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 代码示例

```java
// 创建客户端
SecuritiesClient client = new SecuritiesClient("http://localhost:8080");

// 搜索证券
List<Security> stocks = client.search("Apple", 10);

// 获取行情
Quote quote = client.getQuote("AAPL");

// 获取历史数据
List<HistoricalBar> history = client.getHistory("AAPL", Period.ONE_YEAR);

// 关闭客户端
client.close();
```

## 数据源

| 市场 | 数据源 |
|------|--------|
| 美股 | Nasdaq API |
| 港股 | 新浪财经批量接口 |
| A股 | 新浪财经接口 |

> 无需 API Key，直接使用公开接口。

## 发布 SDK 到 Maven 仓库

```bash
# 发布到本地仓库
mvn install -pl securities-sdk

# 发布到远程仓库（需配置 sonatype）
mvn deploy -pl securities-sdk
```


### 待办事项

- [ ] 支持更多数据源（虚拟币、期货）
- [ ] 历史数据的持久化，根据复权因子计算复权价
- [ ] 支持RPC调用
- [ ] 增加备用数据源
- [ ] 增加外部接口测试用例，保证系统稳定性
- [ ] 调研tushare
- [ ] 调研理杏仁
- [ ] 调研历史PE数据的获取方式