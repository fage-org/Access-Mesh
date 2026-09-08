---
name: dual-layer-cache-framework
description: >-
  AccessMesh 统一缓存框架规范。
  TRIGGER when: 涉及缓存相关代码、`CacheService`、`CacheCatalogEntry`、`CacheMode`、
  `CombinedL1L2Store`、`RedissonBucketStore`、`CaffeineLocalCacheStore`、
  `CacheAutoConfiguration`、`RedissonCacheAutoConfiguration`、`CacheReadToken`、
  `CacheInvalidationBroadcaster`、缓存失效逻辑、关键词 "cache"、"缓存"、
  "Redis"、"Redisson"、"Caffeine"、"evictAfterCommit"、"getBatch"、`CacheProperties`、
  "剩余 TTL"、"beginRead"、"单次有效 TTL"。
origin: project
metadata:
  project: AccessMesh
  version: "3.0.0"
---

# 统一缓存框架规范

本技能描述 AccessMesh 当前生效的统一缓存方案。缓存基础设施已经从旧的 `GenericCacheManager` / `AbstractGenericCacheManager` 模型收敛为 `CacheService + CacheCatalogEntry + Store SPI`（T-ACCESS-008 起 TTL 全面 `java.time.Duration` 秒级精度，并新增剩余 TTL 回填与跨实例 L1 失效广播）。

## 框架位置

`common/src/main/java/cn/ac/fage/accessmesh/common/cache/`

**核心组件**:

- `CacheService` - 业务侧唯一缓存入口
- `DefaultCacheService` - 默认实现，按 `CacheMode` 路由到可用 store
- `CacheCatalogEntry<V>` - 类型化缓存描述符（TTL 为 `Duration`，秒级精度）
- `CacheReadToken<V>` - 授权 L2 miss 剩余 TTL 回填令牌（`beginRead` 产生）
- `CacheMode` - `L1_L2` / `L2_ONLY` / `L1_ONLY`
- `CacheKeyUtil` - 统一 key 生成工具
- `CacheProperties` - 代码默认值 + YAML 运维覆盖（Spring Duration 文法 `15s`/`5m`）
- `CacheInvalidationBroadcaster` / `RedissonCacheInvalidationBroadcaster` - 普通 L1 跨实例失效广播（RTopic）
- `CacheInvalidationMessage` - 广播消息载荷
- `CacheAutoConfiguration` - 装配唯一 `CacheService`（`accessmesh.cache.enabled`，默认启用，显式 false 关闭）
- `RedissonCacheAutoConfiguration` - 仅在 Redisson 可用时补充 store bean 与广播器

## 当前装配模型

### 1. 只有一个 CacheService bean

- `CacheAutoConfiguration` 装配唯一的 `CacheService`（`accessmesh.cache.enabled`，默认启用，显式 false 关闭）
- `RedissonCacheAutoConfiguration` 不再创建第二个 `CacheService`
- Redisson 自动配置只负责贡献：
  - `CombinedL1L2Store`
  - `RedissonBucketStore`
  - `RedissonCacheInvalidationBroadcaster`（构造即订阅失效广播 topic）
- 无 Redisson 依赖时：`CacheService` 仅支持 `L1_ONLY`（Gateway 形态，保留 `gw:` 前缀）
- 有 Redisson 依赖时：同一个 `CacheService` 自动接入 `L1_L2` 与 `L2_ONLY`

### 2. 三种缓存模式

| 模式      | 实现                      | 用途                                            |
| --------- | ------------------------- | ----------------------------------------------- |
| `L1_L2`   | `CombinedL1L2Store`       | Caffeine L1 + Redisson `RBucket` L2，条目级 TTL |
| `L2_ONLY` | `RedissonBucketStore`     | 纯分布式缓存；授权（快照链路）目录一律此模式，TTL≤10s |
| `L1_ONLY` | `CaffeineLocalCacheStore` | 纯本地缓存，例如 gateway 快照（TTL≤15s）       |

## 键格式

```text
{tenantId}:{catalogCode}:{identifier}
```

示例：

- `1:perm:effective-roles:456`
- `1:perm:role-perm-snapshot:1001`
- `1:admin:dict-types:all`

要求：

- `catalogCode` 必须带服务前缀，如 `perm:*`、`admin:*`、`gw:*`（access-service 内按 `admin:/perm:/access:` 前缀，Gateway 独立部署保留 `gw:` 前缀）
- 全部小写，使用 `:` 分隔
- 禁止直接拼接未经规整的原始用户输入

## 业务使用模式

业务侧统一采用显式 Cache Aside 四步模式，**不提供 loader 回调**：

```java
Set<Long> roles = cacheService.get(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
if (roles == null) {
    roles = userRoleMapper.selectRoleIds(tenantId, userId);
    if (roles != null) {
        cacheService.put(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId, roles);
    }
}
return roles;
```

写路径统一在事务提交后失效：

```java
cacheService.evictAfterCommit(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
cacheService.evictBatchAfterCommit(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
```

订阅重连等不依赖租户枚举的恢复场景可使用 catalog 级跨租户全清（L2 走 SCAN，禁止高频调用）：

```java
cacheService.evictAll(GatewayCacheCatalog.INTERFACE_SNAPSHOT);
```

## 授权读取剩余 TTL 回填（T-ACCESS-008，强制）

授权 L2 目录 miss 后，**在开始数据库读取事务或快照查询之前**调用 `beginRead` 记录单调时钟起点，回填只写「读取起点 + catalog TTL」扣除耗时后的剩余 TTL：

```java
Map<Long, Set<Long>> cached = cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
// ... 计算 miss 集合 ...
if (!miss.isEmpty()) {
    CacheReadToken<Set<Long>> token = cacheService.beginRead(PermCacheCatalog.EFFECTIVE_ROLES); // DB 读取前
    Map<Long, Set<Long>> loaded = loadFromDb(miss);                                            // 1 SQL
    cacheService.putBatch(token, tenantId, loaded);                                            // 只写剩余 TTL
}
```

- 剩余 ≤0 时框架不写入（预算耗尽）
- 单条、批量、并发合并和重试**不得重新调用 beginRead 重置起点**（批量共享一个令牌）
- 需要显式单次有效 TTL 时可用裸重载 `put(catalog, tenantId, id, value, Duration)` / `putBatch(..., Duration)`，框架强制不超过 catalog 有效 TTL、≤0 不写

## 授权缓存安全边界（30 秒，启动强制）

- access-service 快照链路 6 目录（`perm:effective-roles`、`perm:role-perm-snapshot`、`perm:type-value`、`perm:type-code`、`perm:condition-rules`、`perm:role-mutex-rule`）必须 **L2_ONLY 且有效 L2 TTL≤10s**（含 YAML 覆盖值）——`PermCacheBoundaryValidator` 启动校验，超限启动失败
- Gateway 快照 L1 TTL≤15s、快照加载全链路截止≤5s（`gateway.permission.snapshot-load-deadline`）——`GatewayCacheBoundaryValidator` 启动校验
- 预算 10s + 5s + 15s ≤ 30s 为最坏陈旧窗口；超截止的快照不写缓存并 fail-closed 503
- 权限缓存不可用时绕过缓存查数据库；无法得到可信授权结果 fail-closed

## 普通 L1 跨实例失效广播（T-ACCESS-008）

- `L1_L2` 目录 `evict` / `evictBatch` / 租户级 `evictAll(catalog, tenantId)` 时，`DefaultCacheService` 在清理共享 L2 的同时经 `CacheInvalidationBroadcaster` 向 RTopic `accessmesh:cache:l1-invalidate` 广播 `{catalogCode, tenantId, keys|all}`；**catalog 级跨租户 `evictAll(catalog)` 不广播**（订阅重连等恢复场景，各实例重连时各自执行同等清理，其他实例 L1 由自身 TTL 兜底）
- 各实例订阅后清理本地 `CombinedL1L2Store` 的对应 L1 条目（`invalidateLocalL1` / `invalidateLocalL1All`）
- 广播失败不抛异常、不影响已提交事务：记 WARN + `cache.invalidate.failures` 指标（type=broadcast/subscribe/listen），各实例 L1 由自身 TTL 兜底
- 回滚不失效（广播只发生在 evict 实际执行时，事务场景即提交后）
- 授权目录是 L2_ONLY 无 L1，不参与该广播

## Catalog 设计规范

每个模块维护自己的 catalog 常量类，不在业务代码里传字符串：

```java
public final class PermCacheCatalog {
    // 快照链路目录：L2_ONLY ≤10s（不创建授权 L1）
    public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("perm:effective-roles")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<Set<Long>>() {})
            .build();

    // 普通目录：L1_L2 + Duration TTL
    public static final CacheCatalogEntry<Map<Long, OperationPermission>> OPERATION_PERMISSIONS_BY_TYPE =
        CacheCatalogEntry.<Map<Long, OperationPermission>>builder()
            .code("perm:operation-permissions-by-type")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(60))
            .l1MaxSize(100)
            .l2Ttl(Duration.ofMinutes(120))
            .valueType(new TypeRef<Map<Long, OperationPermission>>() {})
            .build();
}
```

要求：

- `CacheCatalogEntry` 是业务缓存的唯一描述方式
- 默认 TTL 和容量优先写在 catalog 常量中（`Duration` 秒级精度；分钟字段 `l1TtlMinutes`/`l2TtlMinutes` 及 YAML `l1-expire-minutes`/`l2-ttl-minutes` 已删除，无兼容别名）
- 运维需要覆盖时再用 `accessmesh.cache.default-config.*` 和 `accessmesh.cache.catalogs.*`（如 `accessmesh.cache.catalogs."[perm:effective-roles]".l2-ttl: 10s`）
- 业务侧禁止硬编码 TTL 换算

## 事务与失效规范

- 查询 miss 后可调用 `put` / `putBatch`（授权目录必须走 `beginRead` 令牌形态）
- 数据变更路径统一调用 `evictAfterCommit` / `evictBatchAfterCommit`
- 业务侧禁止手写 `TransactionSynchronizationManager.registerSynchronization(...)`
- 业务侧不需要感知 store 内部的 L1/L2 协调细节

## 监控指标

框架自动埋点（Micrometer，tag `catalog`）：

- 命中区分：`cache.l1.hits` / `cache.l1.misses` / `cache.l2.hits` / `cache.l2.misses`
- 回源回填：`cache.puts`（tag `layer=l1|l2|l1_l2`）
- 失效失败：`cache.invalidate.failures`（tag `type=evict|broadcast|subscribe|listen`；evict/evictBatch/evictAll 及 catalog 级全清失败均计入）
- 故障：`cache.l2.errors`；Gateway fail-closed：`gateway.perm.fallback{mode=closed,...}`

## 禁止事项

- ❌ 禁止继续使用 `GenericCacheManager` / `AbstractGenericCacheManager`
- ❌ 禁止创建单缓存 `CacheManager`、region 类或 loader 回调适配层
- ❌ 禁止业务缓存直接操作 `RedisTemplate` / `StringRedisTemplate` / 裸 `Caffeine`
- ❌ 禁止业务缓存继续使用 `@Cacheable` / `@CacheEvict`
- ❌ 禁止在循环中逐个发起缓存 miss 加载或逐个 DB 查询，优先批量 `getBatch` / `putBatch`
- ❌ 禁止使用 Redis `KEYS` 命令，批量删除必须走 SCAN/分批删除
- ❌ 禁止在写事务中直接手工失效业务缓存，应优先使用 `evictAfterCommit`
- ❌ 禁止分钟制 TTL 字段或业务侧硬编码 TTL 换算（统一 `Duration` + catalog + `accessmesh.cache`）
- ❌ 禁止授权（快照链路）目录创建 L1 或配置有效 L2 TTL>10s（启动校验强制）

## 设计取舍

### 为什么保留 fallback CacheService

- gateway 等模块可能不依赖 Redisson
- 这类模块仍需复用统一的 `CacheService` 与 key/catalog 规范
- 因此 fallback 路径只负责 `L1_ONLY`
- 但 fallback 不能再与 Redisson 路径创建第二个 `CacheService` 竞争

### 为什么 L1_L2 不再使用 RLocalCachedMap

- 当前实现采用 `CombinedL1L2Store`
- L2 基于 `RBucket`，TTL 按条目生效
- 这样可以避免整体容器 TTL 与目录定义语义不一致的问题

### 为什么单次有效 TTL 对 L1 是"预算内才写"

- Redisson `RBucket` 原生支持 per-key TTL，L2 精确按单次有效 TTL 写入
- Caffeine `expireAfterWrite` 是缓存级固定 TTL（无条目级 API，`FixedExpiration` 只支持全局值）
- 因此当有效 TTL 短于 catalog L1 TTL 时 L1 跳过写入（宁可不缓存，不超出预算）；不短于时以 catalog TTL 写入（生命周期自然在预算内）

### 为什么 L2 命中回填 L1 要校验剩余存活时间

- Caffeine 固定过期无条目级 TTL：L1 副本只能按完整 catalog L1 TTL 存活
- 若 L2 条目剩余存活时间（remainTimeToLive）不足 catalog L1 TTL，回填会把短命条目
  （如单次有效 TTL 写入）放大到完整 L1 TTL，绕过预算上限
- 因此剩余时间覆盖 catalog L1 TTL 才回填，否则跳过（读穿透，不放大）；查询失败同样跳过回填

## 验证清单

修改缓存相关代码后，至少检查：

1. 是否仍然只注入 `CacheService`，而不是私有 manager
2. 是否使用模块内的 `CacheCatalogEntry` 常量，而不是硬编码 key
3. 写路径是否改为 `evictAfterCommit` / `evictBatchAfterCommit`
4. gateway 无 Redisson 依赖时，是否仍只使用 `L1_ONLY`
5. access-service 有 Redisson 时，是否能拿到 `CombinedL1L2Store` 与 `RedissonBucketStore`
6. 授权（快照链路）目录回填是否走 `beginRead` 令牌 + 剩余 TTL（不重置起点）
7. 新增快照链路目录是否 L2_ONLY ≤10s 并纳入边界启动校验

## 相关文件

| 文件                                               | 说明                    |
| -------------------------------------------------- | ----------------------- |
| `common/cache/CacheService.java`                   | 统一缓存接口            |
| `common/cache/DefaultCacheService.java`            | 唯一服务实现            |
| `common/cache/CacheCatalogEntry.java`              | 类型化缓存描述符        |
| `common/cache/CacheReadToken.java`                 | 剩余 TTL 读取令牌       |
| `common/cache/CacheInvalidationBroadcaster.java`   | 跨实例 L1 失效广播接口  |
| `common/cache/RedissonCacheInvalidationBroadcaster.java` | RTopic 广播实现    |
| `common/cache/CacheInvalidationMessage.java`       | 广播消息载荷            |
| `common/cache/CacheAutoConfiguration.java`         | 基础自动配置            |
| `common/cache/RedissonCacheAutoConfiguration.java` | Redisson store 自动配置 |
| `common/cache/impl/CombinedL1L2Store.java`         | L1_L2 实现              |
| `common/cache/impl/RedissonBucketStore.java`       | L2_ONLY 实现            |
| `common/cache/impl/CaffeineLocalCacheStore.java`   | L1_ONLY 实现            |
