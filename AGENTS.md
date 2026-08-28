# AGENTS.md — AccessMesh 项目上下文

## 项目概述

**AccessMesh** 是基于 Spring Cloud 微服务架构的通用访问控制平台，支持 SaaS 多租户模式。

- **当前阶段**：access-service 归并完成（T-ACCESS-001~012，2026-08-22 收口归档）；产品定位已定案（开源通用 IAM，2026-08-28），维护债与文档三档叙事整改已完成（T-PERM-020/T-ACCESS-027/028），下一批为简单页后端改造（类型定义/系统配置/操作日志/角色管理）；Phase 2 后端任务重基线后按依赖推进，前端联调待其后端任务完成
- **当前分支**：`feat-permission-center`
- **文档入口**：`docs/README.md`

## 技术栈

| 层面      | 技术                                                                            |
| --------- | ------------------------------------------------------------------------------- |
| 语言      | Java 21                                                                         |
| 框架      | Spring Boot 3 + Spring Cloud                                                    |
| ORM       | MyBatis-Flex                                                                    |
| 数据库    | PostgreSQL（多租户，软删除）                                                    |
| 注册/配置 | Nacos                                                                           |
| 缓存      | Caffeine (L1) + Redis (L2)                                                      |
| 消息队列  | RocketMQ（预留未启用；缓存失效经 Redis pub/sub 广播，服务间同步为同库事务/API 调用） |
| 认证      | Sa-Token + OAuth2                                                               |
| JSON      | Jackson（禁止 FastJSON / Hutool；DTO 优先 Java 21 Record，Lombok 按需精确使用） |
| 日志      | SLF4J + Log4j2                                                                  |
| 前端      | Vue 3 + Element Plus                                                            |

## 服务架构

```text
Gateway (8080) -> access-service (9100)    admin 域（用户/组织/菜单/认证）+ permission 域（权限引擎）
               -> example-service (9300)   对接演示
```

`admin-service` 与 `permission-center` 已归并为 `access-service`（T-ACCESS-001~010）。整体架构见 `docs/design/architecture.md`，归并后目标架构见 `docs/design/access-service-architecture.md`。

## 权威来源

| 主题               | 权威文档                                   |
| ------------------ | ------------------------------------------ |
| 文档入口与阅读顺序 | `docs/README.md`                           |
| 工程规范           | `docs/design/project-rules.md`             |
| 整体架构           | `docs/design/architecture.md`              |
| 归并后目标架构     | `docs/design/access-service-architecture.md` |
| 权限中心概念模型   | `docs/design/permission-center/overview.md` |
| 权限中心 API 契约  | `docs/design/permission-center/api-contract.md` |
| 权限中心核心流程   | `docs/design/permission-center/core-flows.md` |
| 权限中心实现设计   | `docs/design/permission-center/implementation.md` |
| 服务设计           | `docs/design/services/*.md`                  |
| 表结构             | `docs/design/schema/access-service.sql`（唯一权威；旧 admin/perm DDL 已归档 `docs/archive/2026-08-22/schema/`） |

`docs/archive/` 只用于历史追溯，不作为实现依据。

## 核心编码规范

完整规范见 `docs/design/project-rules.md`。常用约束：

- 所有接口使用 **POST + JSON Body**，禁止 GET/PUT/DELETE，禁止 RESTful 路径参数。
- 禁止 `@RequestParam`（文件上传除外），所有参数通过 `@RequestBody` + Request DTO。
- 路径格式：`/api/{module}/{resource}/{action}`；权限中心对外接口统一在 `/api/perm/*`。
- 统一响应体：`{ "code": 200, "message": "success", "data": {}, "requestId": "...", "traceId": "..." }`。
- 错误码分段：10001-19999(admin) / 20001-29999(perm) / 30001-39999(example) / 90001-99999(全局)。
- 分层：Controller -> 调度层 Service -> 逻辑级 DomainService -> Mapper。
- **Service 层复用规范**：新增/修改功能必须检查 DomainService 是否有可复用方法，禁止在调度层重新实现领域逻辑。详见 `docs/design/project-rules.md` §8.4。
- **N+1 查询禁止**：循环内禁止单条数据库查询，必须使用批量查询方法。详见 `docs/design/project-rules.md` §8.4.8。
- 禁止跳层调用；同层横向调用允许（2026-08-22 用户确认全局放开：仅限同层、禁循环依赖、复用优先于重实现，跨域 Mapper 直读边界不变，见 `docs/design/project-rules.md` §分层规范）。
- 不可变 DTO 优先使用 Java 21 Record。
- Lombok 允许精确导入并按需使用；`@Builder` 可用于复杂构造或测试数据装配，但禁止 `@Data`、`@Value`、`@EqualsAndHashCode` 等隐式生成过多逻辑的注解。
- 日期统一使用 `java.time.LocalDateTime`，禁止 `java.util.Date`。
- 时间语义全链路 UTC：JVM 默认时区由 common `UtcTimezoneEnvironmentPostProcessor` 启动即强制 UTC；`LocalDateTime` ↔ TIMESTAMPTZ 由 TypeHandler 显式按 UTC 换算（handler 落位 access-service，新服务引入 DB 实体时须复制）；JDBC URL 禁带 `serverTimezone`。详见 `docs/design/project-rules.md` §7.4。
- 实体类不含业务逻辑，审计字段由框架填充。

## 权限中心实现提醒

- API 路径、请求体、响应体、错误原因以 `docs/design/permission-center/api-contract.md` 为准。
- 表字段、索引、约束以 `docs/design/schema/access-service.sql` 为准（admin/perm 旧 schema 已 superseded）。
- 核心场景链路以 `docs/design/permission-center/core-flows.md` 为准。
- 对外 API 使用 `subjectTypeCode/resourceTypeCode/roleTypeCode`；内部表继续使用 `type_value` 数字值。
- `type_value` 在同一 `tenant_id + type_key` 内全局唯一；不要按业务域重复分配相同内部值。
- `query-scopes`、`scope_all` 是当前范围权限模型；不要恢复旧的 `query-data-scopes`、`includeDataScope`、`dataScopes`。
- `resource_dependency.resource_entity_id` 是源资源/被授权资源，`depends_on_resource_entity_id` 是被源资源依赖、需要自动补全的目标资源。
- **业务域分类模型**：角色、资源等实体不再内嵌 `bizDomainId` 列，域分类通过 `domain_config` 表的 `CLASSIFY` 配置实现（按 `resourceTypeCode` 关联）。全局域(`global=true`)的范围隐式包含未被其他域认领的资源类型。权限查询管线不感知业务域。管理查询通过 `DomainClassifyService.matchesTypeCode/getClassifiedTypeCodes` 按三种模式(ALL/GLOBAL_PLUS/DOMAIN_ONLY)过滤。

## 项目级 Skills（自动加载）

项目在 `.claude/skills/` 目录下定义了以下技能，会在相关场景自动加载：

### dual-layer-cache-framework

**自动触发条件**: 涉及缓存相关代码、`CacheService`、`CacheCatalogEntry`、`CacheMode`、
`CacheReadToken`、`CombinedL1L2Store`、`RedissonBucketStore`、`CaffeineLocalCacheStore`、
`CacheAutoConfiguration`、`RedissonCacheAutoConfiguration`、`CacheInvalidationBroadcaster`、
缓存失效逻辑、关键词 "cache"、"缓存"、"Caffeine"、"Redis"、"Redisson"、"evictAfterCommit"、
"getBatch"、`CacheProperties`、"剩余 TTL"、"beginRead"、"单次有效 TTL"。

**核心要点**:

- **框架位置**: `common/cache/` 模块，所有服务可复用
- **唯一入口**: 业务层只注入 `CacheService`，不再创建 `CacheManager` / region 类
- **装配模型**: `CacheAutoConfiguration` 始终创建唯一 `CacheService`；`RedissonCacheAutoConfiguration` 只提供 Redisson store 与跨实例 L1 失效广播器
- **键格式**: `{tenantId}:{catalogCode}:{identifier}`
- **TTL**: 统一 `java.time.Duration` 秒级精度；YAML 用 Spring Duration 文法（`15s`/`5m`）；分钟字段已删除、无兼容别名
- **使用模式**: `get` → miss 后业务加载 → `put` → 写路径使用 `evictAfterCommit`
- **模式划分**: `L1_L2` 使用 `CombinedL1L2Store`，`L2_ONLY` 使用 `RedissonBucketStore`，`L1_ONLY` 使用 `CaffeineLocalCacheStore`
- **剩余 TTL 回填（T-ACCESS-008）**: 授权 L2 miss 在 DB 读取前 `beginRead` 记录单调时钟起点，`put(token,...)` 只写剩余 TTL、≤0 不写、批量/重试不重置起点；`put(..., Duration)` 单次有效 TTL 强制 cap catalog TTL
- **30s 安全边界（启动强制）**: 快照链路 6 目录 L2_ONLY 且有效 L2 TTL≤10s（`PermCacheBoundaryValidator`）；Gateway 快照 L1≤15s、加载截止≤5s（`GatewayCacheBoundaryValidator`）；超截止不写缓存 fail-closed 503
- **普通 L1 跨实例失效**: L1_L2 目录失效时经 RTopic 广播，各实例清本地 L1；失败仅记 `cache.invalidate.failures` 指标，L1 TTL 兜底
- **禁止事项**: 禁止 loader 回调缓存 API、禁止直接操作 `RedisTemplate` / `StringRedisTemplate` / 裸 `Caffeine`、禁止业务缓存继续使用 Spring Cache 注解、禁止循环单条查询、禁止 `KEYS`、禁止分钟制 TTL 字段或硬编码 TTL 换算

**Catalog 定义示例**:

```java
public final class MyCacheCatalog {
    public static final CacheCatalogEntry<MyData> DETAIL =
        CacheCatalogEntry.<MyData>builder()
            .code("my:detail")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(5))
            .l1MaxSize(1000)
            .l2Ttl(Duration.ofMinutes(30))
            .valueType(new TypeRef<MyData>() {})
            .build();
}
```

**业务调用示例**:

```java
MyData data = cacheService.get(MyCacheCatalog.DETAIL, tenantId, id);
if (data == null) {
    data = mapper.selectById(id);
    if (data != null) {
        cacheService.put(MyCacheCatalog.DETAIL, tenantId, id, data);
    }
}

cacheService.evictAfterCommit(MyCacheCatalog.DETAIL, tenantId, id);
```

### permission-query-pipeline

**自动触发条件**: 涉及权限查询/校验代码、`PermQueryEngine`、`PermQuery`、`PermResult`、`OperationCodeConstants`、
`ResourceTypeCode`、批量权限检查、权限相关逻辑，关键词 "permission"、"权限"、"hasPermissionByCode"、
"getDeniedResourceCodes"、"hasPermissionByEntityId"、"getDeniedEntityIds"、"canGrant"。

**核心 API**（T-PERM-042 终态：旧 `hasPermission`/`validateBatch`/`getDeniedIds` 已从引擎删除）：

```java
// —— 业务编码轨（对外；USER/ROLE 等业务对象门禁统一使用）——
// T-ORG-001 统一后操作者 ID 即主体 ID（operatorId = abstract_user.id = sys_user.id），无转换层
// 单目标鉴权（code 传 null = 类型级）
boolean allowed = engine.hasPermissionByCode(tenantId, operatorId,
    ResourceTypeCode.USER, String.valueOf(userId), OperationCodeConstants.MANAGE);

// 批量获取拒绝的业务编码集合（引擎纯查询不抛异常，拒绝时调用方显式 throw）
Set<String> denied = engine.getDeniedResourceCodes(tenantId, operatorId,
    ResourceTypeCode.DOMAIN, domainCodes, OperationCodeConstants.VIEW);

// —— entityId 轨（仅引擎内部或已完成解析的调用方：资源树、API 映射、资源依赖、权限树等）——
boolean ok = engine.hasPermissionByEntityId(tenantId, operatorId,
    ResourceTypeCode.RESOURCE, resourceEntityId, OperationCodeConstants.MANAGE);
Set<Long> deniedEntityIds = engine.getDeniedEntityIds(tenantId, operatorId,
    ResourceTypeCode.RESOURCE, resourceEntityIds, OperationCodeConstants.DELETE);
```

**OperationCodeConstants 操作码**: CREATE, VIEW, MANAGE, UPDATE, DELETE, ASSIGN, REVOKE, SYNC,
MANAGE_API_MAPPING, SYNC_INTERFACE, GRANT。禁止 `ResourcePermissionValidator` / `OperationType` 枚举 /
`ResourcePermissionStrategy`（均已删除）；禁止绕过引擎直查 `rolePermMapper` 做权限判定。

## 常用命令（开发阶段预估）

```bash
# 全量构建（含 install，确保 SNAPSHOT 依赖刷新到本地仓库）
mvn clean install -DskipTests

# 仅编译（不刷新本地仓库，依赖方可能拿到旧 SNAPSHOT）
mvn clean compile

# 运行测试
mvn test

# 本地启动（需先启动 Nacos + Redis + PostgreSQL）
mvn spring-boot:run -pl <module>

# Docker Compose 启动基础设施
docker compose -f docker-compose.yml up -d nacos redis postgresql
```

> **⚠️ SNAPSHOT 依赖陷阱**：本项目使用多模块 SNAPSHOT 依赖（如 `perm-common` → `perm-client-spring-boot-starter` → `example-service`）。
> `mvn compile` 不会将上游模块 install 到本地仓库，依赖方编译时可能拿到**上次 install 的旧版本**。
> 当上游模块（`perm-sdk/*`、`common`、`perm-entity`）有 API 变更时，**必须**执行 `mvn install -pl <上游模块> -DskipTests` 或全量 `mvn clean install -DskipTests` 后再编译下游模块。

## 文档治理

文档分层职责 / 写入口清单 / 关键词扫描 / 测试适用性覆盖见 `docs/design/project-rules.md` §文档治理（仓库级权威）。phase plan/README 任务行只保留标题/状态/直接依赖/链接，详细范围写进任务卡。
