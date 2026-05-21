# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库性质

这是一个 Spring Cloud 微服务学习项目仓库，包含 4 个相互独立的 Maven 工程（不是一个统一的多模块工程，根目录下没有父 pom）：

- `hmall/` —— 文刀商城微服务实战项目（主项目，多模块 Maven 工程）
- `mp-demo/` —— MyBatis-Plus 单体 Demo
- `mq-demo/` —— RabbitMQ 多模块 Demo（publisher / consumer）
- `domain/` —— 一组孤立的领域类（DTO/Query/VO），不是 Maven 工程，仅作课程示例

技术栈基线：Spring Boot 2.7.12 + Java 11 + Spring Cloud 2021.0.3 + Spring Cloud Alibaba 2021.0.4.0 + MyBatis-Plus 3.4.3 + Hutool 5.8.11。

## 常用命令

每个工程独立操作，必须先 `cd` 进对应目录：

```bash
# 在某个工程目录下：
mvn clean package -DskipTests        # 打包
mvn spring-boot:run -pl <module>     # 运行单个 Spring Boot 模块（hmall 多模块下使用）
mvn test                             # 全部测试
mvn -Dtest=ClassName#methodName test # 单个测试方法
```

`hmall` 各服务模块的 `pom.xml` 都设置了 `<finalName>${project.artifactId}</finalName>`，打出的 jar 名是模块名，不带版本号。

## hmall 架构（重点）

### 模块依赖关系

```
hm-common  ←─ 所有业务服务直接依赖（工具、异常、拦截器、MQ 错误自动配置）
hm-api     ←─ 需要调用其它服务的模块依赖（Feign 客户端 + 共享 DTO + 默认 Feign 配置）
            └─ hm-api 自身也依赖 hm-common
hm-gateway ←─ 独立网关，仅依赖 hm-common
hm-service ←─ 单体版（早期版本，未拆分前的产物，与微服务模块功能重叠）
```

业务服务（item / cart / user / pay / trade / search）均依赖 `hm-common`；需要远程调用其它服务的（cart / pay / trade / search）额外依赖 `hm-api`。

### 服务清单

| 服务 | 端口 | 关键依赖 | 备注 |
|---|---|---|---|
| hm-gateway | 8080 | gateway, nacos-discovery/config | JWT 鉴权 + 动态路由 |
| hm-service | 8080 | 单体版（不与 gateway 同时启动） | 早期单体，内含 hmall 库全部 controller |
| item-service | 8081 | nacos, seata, amqp | 商品 |
| cart-service | - | nacos, openfeign, sentinel, seata, amqp | 购物车（唯一引入 Sentinel 的服务）|
| user-service | - | nacos, security-rsa | 用户登录 |
| pay-service | - | nacos, openfeign, amqp | 支付 |
| trade-service | - | nacos, openfeign, seata, amqp | 订单 |
| search-service | - | nacos, seata, amqp, elasticsearch 7.12.1 | ES 搜索 |

### 鉴权链路（务必理解）

1. **网关侧**：`hm-gateway/AuthGlobalFilter`（order=0）拦截除 `hm.auth.excludePaths` 之外的请求，用 `JwtTool` 解析 `authorization` 头中的 token，得到 `userId`，写入下游请求头 `user-info`。
2. **服务侧**：`hm-common/UserInfoInterceptor`（在 MVC 配置里注册）从 `user-info` 头读取，存入 `UserContext`（基于 `ThreadLocal<Long>`），请求结束后 `removeUser()`。
3. **Feign 透传**：`hm-api/DefaultFeignConfig#userInfoRequestInterceptor` 从 `UserContext.getUser()` 取出再写入下游 Feign 请求头，跨服务调用保持登录身份。

业务代码获取当前用户**只能**通过 `UserContext.getUser()`，不要重复解析 JWT。

### Feign 约定

- 所有 Feign 接口统一放在 `hm-api/com/hmall/api/client/`。
- `DefaultFeignConfig` 不是自动装配的，需要在使用方的启动类上 `@EnableFeignClients(defaultConfiguration = DefaultFeignConfig.class, ...)`。
- 关键 Feign 客户端（`ItemClient`、`PayClient`）有 `fallback`，存在于 `client/fallback/` 下。

### 网关动态路由

`hm-gateway/route/DynamicRouteLoader` 在启动时通过 Nacos `ConfigService.getConfigAndSignListener` 监听 `gateway-routes.json`（`DEFAULT_GROUP`），把 JSON 反序列化为 `RouteDefinition` 注入 `RouteDefinitionWriter`。**路由不在本地 yaml 里**——`hm-gateway/application.yaml` 内被注释掉的 spring.cloud.gateway 块仅作参考。修改路由要改 Nacos 上的 `gateway-routes.json`。

### 配置中心（Nacos）

- Nacos 地址在各服务的 `bootstrap.yaml` 里硬编码：`192.168.100.128:8848`。本地开发需要可达这个地址，或在 IDEA Run Configuration 里覆盖 `spring.cloud.nacos.server-addr`。
- 共享配置（在 Nacos 上托管，不是本地文件）：
  - `shared-jdbc.yaml` —— 数据源 + MyBatis-Plus（占位 `hm.db.host`、`hm.db.pw`、`hm.db.database`）
  - `shared-log.yaml`、`shared-swagger.yaml`、`shared-mq.yaml`、`shared-seata.yaml`
- 各服务的 `application.yaml` 只配少量本地参数（如 `server.port`、`hm.swagger.*`、`hm.db.database`）。**找配置时如果本地 yaml 没有，多半在 Nacos 上**。

### MQ 错误处理

`hm-common/MqConsumeErrorAutoConfiguration` 在 `spring.rabbitmq.listener.simple.retry.enabled=true` 时自动声明：
- 交换机 `error.direct`
- 队列 `${spring.application.name}.error.queue`
- RoutingKey 为服务名
- 注册 `RepublishMessageRecoverer`，重试耗尽后投递到错误交换机。

写消费者时不要重复声明这些。

## 其它工程

- **mp-demo** —— `com.itheima.mp` 包，独立 Spring Boot 应用，演示 MyBatis-Plus + Knife4j；初始化 SQL 在 `mp.sql`。
- **mq-demo** —— 父 pom 聚合 `publisher` 和 `consumer`，演示 RabbitMQ 各种交换机/队列模式，`application.yml` 内连接的 RabbitMQ 主机需要本地可达。
- **domain** —— 仅一组示例 POJO，没有 pom，不需要构建。

## 全局约定

- 统一返回包装：`com.hmall.common.domain.R`（`hm-common`）。
- 统一异常基类：`CommonException`，子类按场景使用（`BadRequestException`、`UnauthorizedException`、`ForbiddenException`、`BizIllegalException`、`DbException`），由 `CommonExceptionAdvice` 全局处理。
- 分页：使用 `PageQuery` / `PageDTO`（`hm-common`，与 `domain/` 下的同名类**不是**同一个，不要混用）。
- 包名：业务服务统一在 `com.hmall.<service>` 下；`user-service` 启动类是小写 `userApplication`（历史遗留，不要随手改）。

## 外部依赖

启动 hmall 一整套服务前，本地需要的基础设施：

- Nacos（`192.168.100.128:8848`）—— 服务发现 + 配置中心，必需
- MySQL —— 数据源，库名见各服务 `hm.db.database`（如 `hm-item`、`hmall`）
- RabbitMQ —— 异步消息（pay/trade/item/search/cart 都依赖 amqp）
- Seata —— 分布式事务（item/cart/trade/search 引入了 seata starter）
- Elasticsearch 7.12.1 —— 仅 search-service 需要
- Redis —— hm-service（单体版）需要

## 仓库根目录的 logs/

`logs/` 在仓库根，不在某个服务下。是各服务通过 Nacos `shared-log.yaml` 配出的相对路径产物，不要提交日志文件。
