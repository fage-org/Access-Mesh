# Gateway 网关服务 - 详细设计文档

本文档定义 Gateway 服务的所有模块、配置结构、过滤器链及核心业务规则。与 `ARCHITECTURE_DESIGN.md`（整体架构）和 `DESIGN.md`（权限中心）配套使用。

---

## 1. 概述

### 1.1 职责

Gateway 是系统唯一的流量入口，基于 Spring Cloud Gateway（WebFlux）构建，核心职责：

1. **路由转发**：按 Nacos 动态配置将请求转发到后端微服务
2. **Token 校验**：Sa-Token 令牌解析与有效性验证
3. **接口鉴权**：对接权限中心，基于接口权限快照判定用户访问权限
4. **白名单管理**：公开接口免鉴权
5. **请求头增强**：向下游注入标准化请求头，清洗外部伪造头
6. **统一异常处理**：标准化错误响应格式

### 1.2 技术选型

| 项目       | 选型                                         |
| ---------- | -------------------------------------------- |
| 基础框架   | Spring Cloud Gateway (WebFlux)               |
| 认证框架   | sa-token-reactor-spring-boot3-starter        |
| Token 存储 | Redis（sa-token-redis-jackson）              |
| 本地缓存   | Caffeine                                     |
| 注册发现   | Nacos                                        |
| RPC        | OpenFeign（WebFlux 环境下用 WebClient 封装） |

### 1.3 端口与路径

| 项目     | 值                       |
| -------- | ------------------------ |
| 默认端口 | 8080                     |
| 管理端口 | 8081（Actuator，仅内网） |
| 上下文   | /                        |

---

## 2. 过滤器链设计

### 2.1 过滤器执行顺序

```
请求到达
  │
  ├─ ① RequestIdFilter (order: -100)          生成/传递 X-Request-Id
  │
  ├─ ② HeaderCleanFilter (order: -90)         清洗外部伪造的内部头
  │
  ├─ ③ WhitelistFilter (order: -80)           白名单匹配，命中则标记跳过后续鉴权
  │
  ├─ ④ AuthTokenFilter (order: -70)           Sa-Token 令牌解析与校验
  │
  ├─ ⑤ PermissionFilter (order: -60)          接口鉴权（对接权限中心快照）
  │
  ├─ ⑥ HeaderEnrichFilter (order: -50)        注入标准请求头到下游
  │
  └─ ⑦ 路由转发 (Spring Cloud Gateway 内置)   按路由规则转发到目标服务
```

### 2.2 各过滤器详细设计

#### 2.2.1 RequestIdFilter

| 属性  | 值                                                               |
| ----- | ---------------------------------------------------------------- |
| Order | -100                                                             |
| 类型  | GlobalFilter                                                     |
| 职责  | 从请求头 `X-Request-Id` 取值或生成 UUID，写入 Exchange Attribute |

**规则**：

- 若请求头已携带 `X-Request-Id` 且格式合法（UUID），直接使用
- 否则生成新的 UUID
- 写入 `ServerWebExchange.getAttributes().put("requestId", ...)`
- 后续过滤器和下游均可读取

#### 2.2.2 HeaderCleanFilter

| 属性  | 值                                       |
| ----- | ---------------------------------------- |
| Order | -90                                      |
| 类型  | GlobalFilter                             |
| 职责  | 移除外部请求中伪造的内部标准头，防止越权 |

**清洗的请求头列表**：

| 请求头       | 说明               |
| ------------ | ------------------ |
| X-User-Id    | 用户ID（内部用）   |
| X-Tenant-Id  | 租户ID（内部用）   |
| X-User-Name  | 用户名（内部用）   |
| X-User-Roles | 角色列表（内部用） |

**规则**：

- 无条件移除以上请求头（外部不可信）
- 这些头由后续 HeaderEnrichFilter 重新注入（来自 Token 解析结果）

#### 2.2.3 WhitelistFilter

| 属性  | 值                                         |
| ----- | ------------------------------------------ |
| Order | -80                                        |
| 类型  | GlobalFilter                               |
| 职责  | 白名单路径匹配，命中则标记 `skipAuth=true` |

**白名单配置结构（Nacos YAML）**：

```yaml
gateway:
  whitelist:
    paths:
      # 认证相关
      - /auth/login
      - /auth/logout
      - /auth/token/refresh
      - /auth/oauth2/**
      # 健康检查
      - /actuator/health
      # 公开资源
      - /public/**
      # 验证码
      - /captcha/**
```

**规则**：

- 支持 Ant 风格路径匹配（`**`、`*`、`?`）
- 配置通过 Nacos 动态刷新（`@RefreshScope` 或监听机制）
- 命中白名单后设置 `Exchange.getAttribute("skipAuth") = true`
- 白名单请求也会经过 HeaderCleanFilter（安全保证）
- 白名单请求不注入 X-User-Id 等头（未登录状态）

#### 2.2.4 AuthTokenFilter

| 属性  | 值                                             |
| ----- | ---------------------------------------------- |
| Order | -70                                            |
| 类型  | GlobalFilter                                   |
| 职责  | 解析 Sa-Token 令牌，校验登录状态，提取用户信息 |

**流程**：

```
读取 skipAuth 标记 → true → 直接放行
    │
    ├─ 从 Header/Cookie 提取 Token
    │   └─ 未找到 → 返回 401 {"code": 401, "message": "未登录"}
    │
    ├─ Sa-Token 校验 Token 有效性
    │   └─ 无效/过期 → 返回 401 {"code": 401, "message": "登录已过期"}
    │
    ├─ 从 Token 中提取登录信息
    │   ├─ loginId (对应 abstract_user_id)
    │   ├─ tenantId
    │   └─ extra 附加信息
    │
    └─ 写入 Exchange Attributes
        ├─ userId = loginId
        ├─ tenantId = tenantId
        └─ permissionVersion（从 Redis 读取当前用户版本号）
```

**Sa-Token 配置要点**：

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 7200 # token 有效期 2 小时
  active-timeout: 1800 # 临时有效期 30 分钟（无操作自动过期）
  is-concurrent: true # 允许同一账号并发登录
  is-share: false # 不共享 token
  is-read-header: true # 从 Header 读取
  is-read-cookie: true # 从 Cookie 读取（门户同域 SSO 场景需要）
```

**Token 中存储的信息（由 admin-service 登录时写入）**：

| Key      | 类型   | 说明                      |
| -------- | ------ | ------------------------- |
| userId   | Long   | 权限中心 abstract_user_id |
| tenantId | Long   | 租户ID                    |
| username | String | 登录账号                  |
| userType | Int    | 用户类型                  |

#### 2.2.5 PermissionFilter

| 属性  | 值                                                 |
| ----- | -------------------------------------------------- |
| Order | -60                                                |
| 类型  | GlobalFilter                                       |
| 职责  | 对接权限中心接口权限快照，判定当前请求是否有权访问 |

**流程**（与权限中心 §6.5 和 perm-gateway-spring-boot-starter 对齐）：

```
读取 skipAuth 标记 → true → 直接放行
    │
    ├─ 从 Exchange 取 tenantId, userId
    │
    ├─ 从 Redis 实时查询用户角色列表
    │   Key: perm:user:roles:{tenantId}:{userId}
    │   Value: Set<String> (roleId 集合)
    │   （由权限中心维护：角色分配/取消时实时更新）
    │
    ├─ 解析目标服务：从路由信息中提取 serviceCode
    │
    ├─ 构建匹配 Key：serviceCode + httpMethod + path
    │
    ├─ 查 L1 本地缓存（按服务维度缓存的快照）
    │   ├─ 命中 → 匹配接口规则
    │   └─ 未命中 → 查 L2 Redis 缓存
    │       ├─ 命中 → 写入 L1 → 匹配接口规则
    │       └─ 未命中 → 返回 503（快照数据由 SnapshotRefreshScheduler 定时维护）
    │
    └─ 匹配当前请求
        ├─ 在快照 rules 中按 httpMethod + path 匹配 InterfaceRule
        │   ├─ pathPattern 支持 Ant 风格：/api/users/**, /api/users/{id}
        │   └─ 未命中 → 返回 403（白名单模式：未注册接口默认拒绝）
        │
        ├─ 遍历用户角色列表，在 InterfaceRule.roleRules 中查找匹配的角色
        │   └─ 无匹配角色 → 返回 403
        │
        └─ 检查匹配角色的条件（conditions）
            ├─ 无条件 → 放行
            ├─ 简单条件（DATE_RANGE/TIME_RANGE/IP_WHITELIST/IP_BLACKLIST）
            │   → ConditionEvaluator 本地评估
            └─ 复杂条件（CUSTOM 等）
                → 回调权限中心 POST /api/perm/auth/interface-decision
```

**缓存结构**（按服务维度，非按用户维度）：

```
L1 (Caffeine):
  Key:   "perm:snapshot:{serviceCode}"
  Value: PermissionSnapshot {
           serviceCode: String,
           version: Long,
           generatedAt: Instant,
           rules: Map<String(method:path), InterfaceRule>
         }
  TTL:   30 秒（可配置）
  最大条目: 100（服务数量级别）

L2 (Redis):
  Key:   "gateway:perm:snapshot:{serviceCode}"
  Value: JSON 序列化的 PermissionSnapshot
  TTL:   5 分钟（可配置）
```

**InterfaceRule 结构**：

```json
{
  "httpMethod": "GET",
  "pathPattern": "/api/example/reports",
  "roleRules": {
    "role_123": {
      "roleId": "role_123",
      "roleCode": "data_analyst",
      "operations": ["READ", "EXPORT"],
      "conditions": []
    },
    "role_456": {
      "roleId": "role_456",
      "roleCode": "admin",
      "operations": ["READ", "UPDATE", "DELETE"],
      "conditions": [
        {
          "conditionId": "cond_789",
          "logic": "AND",
          "items": [
            {
              "type": "DATE_RANGE",
              "params": { "start": "2025-01-01", "end": "2025-12-31" }
            }
          ]
        }
      ]
    }
  }
}
```

**快照更新机制**（SnapshotRefreshScheduler）：

- 每 30s 轮询 `POST /api/perm/permission-version/query` 获取各服务版本号
- 版本号变更的服务 → 调用 `POST /api/perm/auth/interface-snapshot?service_code=xxx` 拉取全量快照
- 更新 L1 Caffeine + 写入 L2 Redis

**启动预热**：

1. 优先从 Redis L2 加载各服务快照（毫秒级恢复）
2. L2 有数据 → 立即可用，同时异步拉取全量刷新
3. L2 无数据 → 同步阻塞拉取权限中心快照
4. 拉取失败 + L2 无数据 → 所有非白名单请求返回 503（fail-close）

**未注册接口处理**：

- 若请求路径在权限快照 rules 中无匹配 → 返回 403
- 这是白名单模式：只有注册且授权的接口才允许访问

**权限中心不可用时的降级策略**：

- **有本地缓存（L1/L2 未过期）**→ 使用缓存数据正常鉴权放行
- **无缓存且权限中心不可达** → 拒绝请求，返回 503 `{"code": 503, "message": "鉴权服务暂时不可用"}`
- 即 **fail-close + cache fallback** 模式：安全优先，缓存兜底
- 降级期间日志记录 WARN 级别告警，便于运维监控
- 建议 L2 Redis TTL 适当放宽（如 5~10 分钟），为权限中心恢复争取时间窗口

#### 2.2.6 HeaderEnrichFilter

| 属性  | 值                         |
| ----- | -------------------------- |
| Order | -50                        |
| 类型  | GlobalFilter               |
| 职责  | 向下游请求注入标准化请求头 |

**注入的请求头**：

| 请求头       | 来源                 | 说明                      |
| ------------ | -------------------- | ------------------------- |
| X-Request-Id | RequestIdFilter 生成 | 请求追踪ID                |
| X-Tenant-Id  | Token 解析结果       | 租户ID                    |
| X-User-Id    | Token 解析结果       | 用户ID (abstract_user_id) |
| X-User-Name  | Token 解析结果       | 用户名                    |
| X-User-Type  | Token 解析结果       | 用户类型                  |

**规则**：

- 仅当 Token 校验通过后才注入用户相关头
- 白名单请求只注入 X-Request-Id
- 下游服务通过这些头获取当前用户信息，**无需再次解析 Token**

---

## 3. 路由配置

### 3.1 Nacos 路由配置结构

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 管理服务
        - id: admin-service
          uri: lb://admin-service
          predicates:
            - Path=/admin/**
          filters:
            - StripPrefix=1
          metadata:
            serviceCode: admin-service

        # 权限中心
        - id: permission-center
          uri: lb://permission-center
          predicates:
            - Path=/perm/**
          filters:
            - StripPrefix=1
          metadata:
            serviceCode: permission-center

        # 演示服务
        - id: example-service
          uri: lb://example-service
          predicates:
            - Path=/example/**
          filters:
            - StripPrefix=1
          metadata:
            serviceCode: example-service

        # 认证接口（转发到管理服务）
        - id: auth-routes
          uri: lb://admin-service
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=0
```

### 3.2 路由规则说明

| 前缀          | 目标服务          | StripPrefix | 说明                                  |
| ------------- | ----------------- | ----------- | ------------------------------------- |
| /admin/\*\*   | admin-service     | 1           | /admin/api/users → /api/users         |
| /perm/\*\*    | permission-center | 1           | /perm/api/perm/auth → /api/perm/auth  |
| /example/\*\* | example-service   | 1           | /example/api/demo → /api/demo         |
| /auth/\*\*    | admin-service     | 0           | /auth/login → /auth/login（不剥前缀） |

> **可配置性说明**：每条路由的 `StripPrefix` 值在 Nacos 路由 YAML 中独立配置，新增服务路由时可根据实际路径规划自由调整剥离层级，无需修改代码。

### 3.3 serviceCode 映射

路由元数据中的 `serviceCode` 用于 PermissionFilter 中将路由与权限中心的 `service_config.service_code` 关联。

---

## 4. 异常处理

### 4.1 统一错误响应格式

```json
{
  "code": 401,
  "message": "未登录或登录已过期",
  "data": null,
  "requestId": "uuid-xxx",
  "traceId": "64-hex-trace-id"
}
```

> `requestId` 来自 RequestIdFilter 生成的 X-Request-Id（业务级）；`traceId` 来自分布式追踪框架（链路级），两者独立。

### 4.2 错误码定义

| HTTP 状态码 | 业务 code | message            | 触发场景                 |
| ----------- | --------- | ------------------ | ------------------------ |
| 401         | 401       | 未登录             | Token 缺失               |
| 401         | 401       | 登录已过期         | Token 无效/过期          |
| 403         | 403       | 无接口访问权限     | 接口鉴权不通过           |
| 403         | 403       | 接口未注册         | 接口不在权限中心注册表中 |
| 404         | 404       | 服务不存在         | 路由匹配失败             |
| 502         | 502       | 服务暂时不可用     | 后端服务不可达           |
| 503         | 503       | 鉴权服务暂时不可用 | 权限中心不可用且无缓存   |
| 504         | 504       | 服务响应超时       | 后端服务响应超时         |

### 4.3 全局异常处理器

使用 `ErrorWebExceptionHandler` 实现（WebFlux 环境），替代默认的 Whitelabel 错误页，所有异常统一返回 JSON 格式。

### 4.4 分布式链路追踪

引入 **Micrometer Tracing + OpenTelemetry** 作为分布式追踪方案：

| 组件                 | 职责                    |
| -------------------- | ----------------------- |
| micrometer-tracing   | 统一追踪 API            |
| opentelemetry-bridge | OpenTelemetry 协议适配  |
| OTLP Exporter        | 上报 Trace 数据（可选） |

**集成要点**：

- Gateway 作为 Trace 起点，自动生成 `traceId` 和 `spanId`
- `traceId` 通过 HTTP Header `traceparent`（W3C Trace Context 标准）传递到下游服务
- 所有服务引入相同的 Micrometer Tracing 依赖，自动参与链路
- 全局异常处理器从当前 Span 中提取 `traceId` 写入错误响应
- 日志 MDC 自动注入 `traceId`，方便日志检索
- 上报后端（如 Jaeger/Zipkin/OTEL Collector）为可选部署，不影响 traceId 生成和传递

---

## 5. 配置结构汇总

### 5.1 Gateway 自定义配置

```yaml
gateway:
  # 白名单配置
  whitelist:
    paths:
      - /auth/**
      - /actuator/health
      - /public/**
      - /captcha/**

  # 缓存配置
  cache:
    l1:
      max-size: 100 # L1 最大缓存条目（服务数量级别）
      ttl-seconds: 30 # L1 TTL（秒）
    l2:
      ttl-minutes: 5 # L2 Redis TTL（分钟）
      key-prefix: "gateway:perm:"

  # 请求头配置
  header:
    clean: # 需要清洗的外部头
      - X-User-Id
      - X-Tenant-Id
      - X-User-Name
      - X-User-Roles
      - X-User-Type
    enrich: # 需要注入的内部头
      request-id: X-Request-Id
      tenant-id: X-Tenant-Id
      user-id: X-User-Id
      user-name: X-User-Name
      user-type: X-User-Type

  # 权限中心对接
  permission:
    service-url: lb://permission-center
    snapshot-path: /api/perm/auth/interface-snapshot
    version-path: /api/perm/permission-version/query
    decision-path: /api/perm/auth/interface-decision
    poll-interval: 30s # 版本轮询间隔
    unregistered-policy: DENY # 未注册接口策略：DENY / ALLOW
```

### 5.2 完整 bootstrap.yml（示例）

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        file-extension: yml
        shared-configs:
          - data-id: common.yml
            group: DEFAULT_GROUP
            refresh: true

sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 7200
  active-timeout: 1800
  is-concurrent: true
  is-share: false
  is-read-header: true
  is-read-cookie: true
```

---

## 6. 与权限中心的交互接口

### 6.1 调用的权限中心接口

| 接口                                           | 用途                       | 调用时机                              |
| ---------------------------------------------- | -------------------------- | ------------------------------------- |
| POST /api/perm/permission-version/query        | 查询各服务权限版本号       | 每 30s 定时轮询                       |
| POST /api/perm/auth/interface-snapshot         | 获取指定服务的接口权限快照 | 版本号变更时拉取全量 / 启动预热时拉取 |
| POST /api/perm/auth/interface-decision         | 带条件的接口鉴权判定       | 快照规则含复杂条件（CUSTOM）时回调    |
| Redis Key: perm:user:roles:{tenantId}:{userId} | 用户当前角色列表           | 每次请求实时读取                      |

> **用户角色来源**：权限中心在用户-角色关联变更时（分配/取消角色），实时更新 Redis Key `perm:user:roles:{tenantId}:{userId}`（类型 SET，内容为 roleId 集合）。Gateway 每次鉴权请求直接从 Redis 读取，无需调用权限中心 API，保证实时性且延迟极低。

### 6.2 版本查询详情

**请求**（POST /api/perm/permission-version/query）：

```json
{
  "service_codes": ["admin-service", "example-service"]
}
```

**响应**：

```json
{
  "code": 200,
  "data": {
    "versions": {
      "admin-service": 42,
      "example-service": 18
    }
  }
}
```

### 6.3 接口快照调用详情

**请求**（POST /api/perm/auth/interface-snapshot）：

```json
{
  "service_code": "admin-service"
}
```

**响应**（权限中心返回按服务维度的完整快照）：

```json
{
  "code": 200,
  "data": {
    "serviceCode": "admin-service",
    "version": 42,
    "generatedAt": "2025-01-15T10:30:00Z",
    "rules": {
      "POST:/api/admin/users/list": {
        "httpMethod": "POST",
        "pathPattern": "/api/admin/users/list",
        "roleRules": {
          "role_1": {
            "roleId": "role_1",
            "roleCode": "admin",
            "operations": ["READ"],
            "conditions": []
          },
          "role_2": {
            "roleId": "role_2",
            "roleCode": "user_manager",
            "operations": ["READ"],
            "conditions": [
              {
                "conditionId": "cond_1",
                "logic": "AND",
                "items": [
                  { "type": "IP_WHITELIST", "params": { "ips": "10.0.0.0/8" } }
                ]
              }
            ]
          }
        }
      }
    }
  }
}
```

### 6.4 快照更新流程

```
SnapshotRefreshScheduler（每 30s）
  │
  ├─ POST /api/perm/permission-version/query → 获取各服务版本号
  │
  ├─ 对比本地缓存中各服务快照的 version 字段
  │   └─ 版本号相同 → 跳过
  │   └─ 版本号不同 → 拉取快照
  │
  ├─ POST /api/perm/auth/interface-snapshot?service_code=xxx
  │   → 拉取全量快照
  │
  ├─ 更新 L1 Caffeine（整体替换）
  │
  └─ 写入 L2 Redis（key: gateway:perm:snapshot:{serviceCode}）
```

---

## 7. 安全设计

### 7.1 防伪造

- HeaderCleanFilter 在最前端清洗所有内部请求头
- 下游服务**必须信任**经过 Gateway 注入的请求头，而非直接解析 Token
- Gateway 自身不对外暴露 Actuator 端点（仅内网管理端口可访问）

### 7.2 Token 安全

- **HTTPS 终结由前置 Nginx 负责**，Gateway 仅监听 HTTP（内网通信）
- 部署拓扑：`Client → Nginx(443/TLS) → Gateway(8080/HTTP) → 后端服务`
- 不在日志中输出 Token 原文（日志脱敏规则：Token 值仅保留前 8 位 + `***`）
- Token 支持强制下线（Sa-Token `kickout` / `logout`）

### 7.3 CORS 配置

Gateway 统一处理跨域，后端服务无需重复配置：

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          "[/**]":
            allowed-origin-patterns: "*" # 生产环境应限定为具体域名
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

> 生产环境必须将 `allowed-origin-patterns` 收窄为实际前端域名，不可使用 `*`。

### 7.4 防护措施

- 请求体大小限制（默认 10MB，可配置）
- 路由级超时配置（默认 30s）
- 异常信息不泄露内部堆栈
- Token 日志脱敏：`Bearer eyJhbGci*** → Bearer eyJhbGci***`（仅保留前 8 字符）

---

## 8. 模块文件结构（参考）

```
gateway/
├── src/main/java/com/xxx/gateway/
│   ├── GatewayApplication.java
│   ├── config/
│   │   ├── GatewayProperties.java           # 自定义配置属性类
│   │   ├── SaTokenConfig.java               # Sa-Token 配置
│   │   ├── CacheConfig.java                 # Caffeine 缓存配置
│   │   ├── CorsConfig.java                  # CORS 跨域配置
│   │   ├── TracingConfig.java               # Micrometer Tracing 配置
│   │   └── WebFluxConfig.java               # WebFlux 配置
│   ├── filter/
│   │   ├── RequestIdFilter.java             # 请求ID生成
│   │   ├── HeaderCleanFilter.java           # 请求头清洗
│   │   ├── WhitelistFilter.java             # 白名单过滤
│   │   ├── AuthTokenFilter.java             # Token 校验
│   │   ├── PermissionFilter.java            # 接口鉴权（调用 perm-gateway-starter）
│   │   └── HeaderEnrichFilter.java          # 请求头注入
│   ├── service/
│   │   ├── PermissionCacheService.java      # L1+L2 缓存管理
│   │   ├── PermissionSnapshotClient.java    # 权限中心接口调用
│   │   ├── SnapshotRefreshScheduler.java    # 定时轮询版本号 + 快照更新
│   │   └── ConditionEvaluator.java          # 条件权限本地评估器
│   ├── handler/
│   │   └── GlobalExceptionHandler.java      # 全局异常处理
│   └── model/
│       ├── PermissionSnapshot.java          # 权限快照模型（按服务维度）
│       ├── InterfaceRule.java               # 接口规则模型（含 roleRules）
│       ├── RoleRuleDetail.java              # 角色规则详情（operations + conditions）
│       ├── ConditionRule.java               # 条件规则模型
│       └── GatewayResponse.java             # 统一响应模型
├── src/main/resources/
│   ├── bootstrap.yml
│   └── application.yml
└── pom.xml
```
