# springcloud-mall

基于 Spring Cloud Alibaba 搭建的微服务电商学习项目，覆盖商品、用户、购物车、订单、支付、交易、搜索等核心模块，重点演练服务治理、分布式事务、异步削峰与商品检索性能优化等微服务核心能力。

## 仓库结构

仓库内包含 4 个相互独立的工程，根目录下没有父 pom：

| 目录 | 说明 |
|---|---|
| `hmall/` | 文刀商城微服务实战项目（主项目，多模块 Maven 工程） |
| `mp-demo/` | MyBatis-Plus 单体 Demo |
| `mq-demo/` | RabbitMQ 多模块 Demo（publisher / consumer） |
| `domain/` | 一组孤立的领域类（DTO/Query/VO），仅作示例 |

## 技术栈

- 框架基线：Spring Boot 2.7.12 + Java 11
- 微服务套件：Spring Cloud 2021.0.3 + Spring Cloud Alibaba 2021.0.4.0
- 注册与配置中心：Nacos
- 网关：Spring Cloud Gateway
- 服务调用：OpenFeign + LoadBalancer
- 流量防护：Sentinel
- 分布式事务：Seata（AT 模式）
- 消息中间件：RabbitMQ
- 搜索引擎：Elasticsearch 7.12.1
- 持久层：MyBatis-Plus 3.4.3 + MySQL 8
- 工具库：Hutool 5.8.11 + Knife4j 4.1.0

## hmall 主项目

### 模块依赖

```
hm-common  ←─ 所有业务服务直接依赖（工具、异常、拦截器、MQ 错误自动配置）
hm-api     ←─ 需要远程调用的模块依赖（Feign 客户端 + 共享 DTO + Feign 默认配置）
            └─ hm-api 自身依赖 hm-common
hm-gateway ←─ 独立网关，仅依赖 hm-common
hm-service ←─ 单体版（拆分前的早期产物，与各微服务模块功能重叠）
```

### 服务清单

| 服务 | 端口 | 关键依赖 | 职责 |
|---|---|---|---|
| hm-gateway | 8080 | gateway, nacos | 统一入口，JWT 鉴权 + 动态路由 |
| hm-service | 8080 | 单体版 | 早期单体，包含所有 controller |
| item-service | 8081 | nacos, seata, amqp | 商品 |
| cart-service | 动态 | nacos, openfeign, sentinel, seata, amqp | 购物车 |
| user-service | 8083 | nacos, security-rsa | 用户登录 |
| pay-service | 8084 | nacos, openfeign, amqp | 支付 |
| trade-service | 8085 | nacos, openfeign, seata, amqp | 订单 |
| search-service | 8086 | nacos, seata, amqp, elasticsearch | 商品搜索 |

`hm-service`（单体版）和 `hm-gateway` 不要同时启动，端口冲突。

### 核心实现亮点

- **微服务拆分与服务治理**：基于 Nacos 完成服务注册发现与配置管理，OpenFeign 简化跨服务调用，关键 Feign 客户端配置 fallback 实现降级。
- **统一网关与身份透传**：Gateway 全局过滤器（`AuthGlobalFilter`）解析 JWT 获取 userId 写入下游请求头 `user-info`；下游服务通过 `UserInfoInterceptor` + `ThreadLocal`（`UserContext`）保存用户上下文；Feign 拦截器再把 `UserContext` 中的 userId 写回请求头，实现链路透传。
- **网关动态路由**：`DynamicRouteLoader` 启动时通过 Nacos `ConfigService.getConfigAndSignListener` 监听 `gateway-routes.json`，反序列化为 `RouteDefinition` 注入 `RouteDefinitionWriter`，支持运行时刷新，无需重启网关。
- **分布式事务**：在下单扣库存等跨服务场景引入 Seata AT 模式，保障订单与库存数据一致性。
- **异步削峰与最终一致性**：基于 RabbitMQ 解耦订单创建、库存扣减、购物车清空等流程；针对超时未支付订单使用延迟消息触发关单与库存回滚。
- **MQ 消费失败兜底**：`hm-common/MqConsumeErrorAutoConfiguration` 在开启重试时自动声明 `error.direct` 交换机和服务专属的 `${app}.error.queue` 队列，注册 `RepublishMessageRecoverer`，重试耗尽后投递错误队列，便于排查。
- **流量防护**：cart-service 引入 Sentinel，提供接口限流、熔断与降级。
- **商品检索**：search-service 使用 Elasticsearch 实现多条件组合检索、关键词搜索与分页查询，监听商品变更消息保持索引同步。

### 全局约定

- 统一返回包装：`com.hmall.common.domain.R`
- 统一异常基类：`CommonException`，子类 `BadRequestException` / `UnauthorizedException` / `ForbiddenException` / `BizIllegalException` / `DbException`，由 `CommonExceptionAdvice` 全局处理
- 分页：`PageQuery` / `PageDTO`（位于 `hm-common`）
- 当前用户：业务代码统一通过 `UserContext.getUser()` 获取，不重复解析 JWT

## 本地启动

### 外部依赖

启动 hmall 整套服务前，本地需要可达以下基础设施：

- Nacos（默认地址 `192.168.100.128:8848`，硬编码在各服务 `bootstrap.yaml` 中，可在 IDE Run Configuration 通过 `spring.cloud.nacos.server-addr` 覆盖）
- MySQL 8（库名见各服务 `hm.db.database`，如 `hm-item`、`hm-cart`、`hm-user`、`hm-pay`、`hm-trade`、`hmall`）
- RabbitMQ（pay/trade/item/search/cart 都依赖 amqp）
- Seata Server（item/cart/trade/search 依赖 AT 事务）
- Elasticsearch 7.12.1（仅 search-service 需要）
- Redis（仅单体版 hm-service 需要）

### Nacos 配置

各服务的 `application.yaml` 只配少量本地参数，大部分配置走 Nacos 共享配置：

- `shared-jdbc.yaml`：数据源 + MyBatis-Plus（占位 `hm.db.host`、`hm.db.pw`、`hm.db.database`）
- `shared-log.yaml`：日志格式
- `shared-swagger.yaml`：Knife4j 文档
- `shared-mq.yaml`：RabbitMQ 连接
- `shared-seata.yaml`：Seata 配置
- `gateway-routes.json`（`DEFAULT_GROUP`）：网关动态路由

需要在 Nacos 上提前导入这些配置文件后才能正常启动。

### 常用命令

每个工程独立操作，先 `cd` 进对应目录：

```bash
mvn clean package -DskipTests        # 打包
mvn spring-boot:run -pl <module>     # 运行单个 Spring Boot 模块
mvn test                             # 跑全部测试
mvn -Dtest=ClassName#methodName test # 跑单个测试方法
```

`hmall` 各服务模块的 `pom.xml` 都设置了 `<finalName>${project.artifactId}</finalName>`，打出的 jar 名是模块名，不带版本号。

## 其它工程
- **domain**：仅一组示例 POJO，无 pom，不需要构建。
