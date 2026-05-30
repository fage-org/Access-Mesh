# AGENTS.md — AccessMesh 项目上下文

## 项目概述

**AccessMesh** 是基于 Spring Cloud 微服务架构的通用访问控制平台，支持 SaaS 多租户模式。

- **当前阶段**：设计完成，待编码实现
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
| 消息队列  | RocketMQ                                                                        |
| 认证      | Sa-Token + OAuth2                                                               |
| JSON      | Jackson（禁止 FastJSON / Hutool；DTO 优先 Java 21 Record，Lombok 按需精确使用） |
| 日志      | SLF4J + Log4j2                                                                  |
| 前端      | Vue 3 + Element Plus                                                            |

## 服务架构

```text
Gateway (8080) -> admin-service (9100)      用户/组织/菜单/认证
               -> permission-center (9200)  核心权限引擎
               -> example-service (9300)    对接演示
```

整体架构见 `docs/design/architecture.md`。

## 权威来源

| 主题               | 权威文档                                   |
| ------------------ | ------------------------------------------ |
| 文档入口与阅读顺序 | `docs/README.md`                           |
| 工程规范           | `docs/design/project-rules.md`             |
| 整体架构           | `docs/design/architecture.md`              |
| 权限中心概念模型   | `docs/design/permission-center/overview.md` |
| 权限中心 API 契约  | `docs/design/permission-center/api-contract.md` |
| 权限中心核心流程   | `docs/design/permission-center/core-flows.md` |
| 权限中心实现设计   | `docs/design/permission-center/implementation.md` |
| 服务设计           | `docs/design/services/*.md`                  |
| 表结构             | `docs/design/schema/*.sql`                 |

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
- 禁止跳层调用，禁止同层横向调用。
- 不可变 DTO 优先使用 Java 21 Record。
- Lombok 允许精确导入并按需使用；`@Builder` 可用于复杂构造或测试数据装配，但禁止 `@Data`、`@Value`、`@EqualsAndHashCode` 等隐式生成过多逻辑的注解。
- 日期统一使用 `java.time.LocalDateTime`，禁止 `java.util.Date`。
- 实体类不含业务逻辑，审计字段由框架填充。

## 权限中心实现提醒

- API 路径、请求体、响应体、错误原因以 `docs/design/permission-center/api-contract.md` 为准。
- 表字段、索引、约束以 `docs/design/schema/permission-center.sql` 为准。
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
`CombinedL1L2Store`、`RedissonBucketStore`、`CaffeineLocalCacheStore`、
`CacheAutoConfiguration`、`RedissonCacheAutoConfiguration`、缓存失效逻辑、关键词 "cache"、"缓存"、
"Caffeine"、"Redis"、"Redisson"、"evictAfterCommit"、"getBatch"、`CacheProperties`。

**核心要点**:

- **框架位置**: `common/cache/` 模块，所有服务可复用
- **唯一入口**: 业务层只注入 `CacheService`，不再创建 `CacheManager` / region 类
- **装配模型**: `CacheAutoConfiguration` 始终创建唯一 `CacheService`；`RedissonCacheAutoConfiguration` 只提供 Redisson store
- **键格式**: `{tenantId}:{catalogCode}:{identifier}`
- **使用模式**: `get` → miss 后业务加载 → `put` → 写路径使用 `evictAfterCommit`
- **模式划分**: `L1_L2` 使用 `CombinedL1L2Store`，`L2_ONLY` 使用 `RedissonBucketStore`，`L1_ONLY` 使用 `CaffeineLocalCacheStore`
- **禁止事项**: 禁止 loader 回调缓存 API、禁止直接操作 `RedisTemplate` / `StringRedisTemplate` / 裸 `Caffeine`、禁止业务缓存继续使用 Spring Cache 注解、禁止循环单条查询、禁止 `KEYS`

**Catalog 定义示例**:

```java
public final class MyCacheCatalog {
    public static final CacheCatalogEntry<MyData> DETAIL =
        CacheCatalogEntry.<MyData>builder()
            .code("my:detail")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(1000)
            .l2TtlMinutes(30)
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

### resource-permission-validator

**自动触发条件**: 涉及权限校验代码、创建新 ResourcePermissionStrategy、使用 ResourcePermissionValidator、
OperationType 枚举、权限相关逻辑、关键词 "permission"、"权限"、"validate"、"hasPermission"、
"validateBatch"、"getDeniedIds"、"canGrant"、"ResourcePermissionStrategy"。

**核心 API**:

```java
// 单实例校验（无权限抛 SecurityException）
permissionValidator.validate(tenantId, operatorId, "SERVICE", serviceCode, OperationType.MANAGE_API_MAPPING);

// 批量校验
permissionValidator.validateBatch(tenantId, operatorId, "ROLE", roleIds, OperationType.DELETE);

// 非抛出检查（返回 boolean）
boolean allowed = permissionValidator.hasPermission(tenantId, operatorId, "USER", userId, OperationType.MANAGE);

// 获取被拒绝的 ID
Set<Long> denied = permissionValidator.getDeniedIds(tenantId, operatorId, "DOMAIN", domainIds, OperationType.VIEW);
```

**OperationType 枚举**: CREATE, VIEW, MANAGE, UPDATE, DELETE, ASSIGN, REVOKE, SYNC, MANAGE_API_MAPPING, SYNC_INTERFACE, GRANT

## 常用命令（开发阶段预估）

```bash
# 构建
mvn clean compile

# 运行测试
mvn test

# 本地启动（需先启动 Nacos + Redis + PostgreSQL）
mvn spring-boot:run -pl <module>

# Docker Compose 启动基础设施
docker compose -f docker-compose.yml up -d nacos redis postgresql
```
