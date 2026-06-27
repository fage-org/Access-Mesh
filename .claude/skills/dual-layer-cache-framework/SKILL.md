---
name: dual-layer-cache-framework
description: >-
  AccessMesh 统一缓存框架规范。
  TRIGGER when: 涉及缓存相关代码、`CacheService`、`CacheCatalogEntry`、`CacheMode`、
  `CombinedL1L2Store`、`RedissonBucketStore`、`CaffeineLocalCacheStore`、
  `CacheAutoConfiguration`、`RedissonCacheAutoConfiguration`、缓存失效逻辑、关键词 "cache"、"缓存"、
  "Redis"、"Redisson"、"Caffeine"、"evictAfterCommit"、"getBatch"、`CacheProperties`。
origin: project
metadata:
  project: AccessMesh
  version: "2.0.0"
---

# 统一缓存框架规范

本技能描述 AccessMesh 当前生效的统一缓存方案。缓存基础设施已经从旧的 `GenericCacheManager` / `AbstractGenericCacheManager` 模型收敛为 `CacheService + CacheCatalogEntry + Store SPI`。

## 框架位置

`common/src/main/java/cn/ac/fage/accessmesh/common/cache/`

**核心组件**:

- `CacheService` - 业务侧唯一缓存入口
- `DefaultCacheService` - 默认实现，按 `CacheMode` 路由到可用 store
- `CacheCatalogEntry<V>` - 类型化缓存描述符
- `CacheMode` - `L1_L2` / `L2_ONLY` / `L1_ONLY`
- `CacheKeyUtil` - 统一 key 生成工具
- `CacheProperties` - 代码默认值 + YAML 运维覆盖
- `CacheAutoConfiguration` - 始终创建唯一 `CacheService`
- `RedissonCacheAutoConfiguration` - 仅在 Redisson 可用时补充 store bean

## 当前装配模型

### 1. 只有一个 CacheService bean

- `CacheAutoConfiguration` 始终创建唯一的 `CacheService`
- `RedissonCacheAutoConfiguration` 不再创建第二个 `CacheService`
- Redisson 自动配置只负责贡献：
  - `CombinedL1L2Store`
  - `RedissonBucketStore`
- 无 Redisson 依赖时：`CacheService` 仅支持 `L1_ONLY`
- 有 Redisson 依赖时：同一个 `CacheService` 自动接入 `L1_L2` 与 `L2_ONLY`

### 2. 三种缓存模式

| 模式      | 实现                      | 用途                                            |
| --------- | ------------------------- | ----------------------------------------------- |
| `L1_L2`   | `CombinedL1L2Store`       | Caffeine L1 + Redisson `RBucket` L2，条目级 TTL |
| `L2_ONLY` | `RedissonBucketStore`     | 纯分布式缓存，例如跨节点共享状态或全局游标         |
| `L1_ONLY` | `CaffeineLocalCacheStore` | 纯本地缓存，例如 gateway 短 TTL 场景            |

## 键格式

```text
{tenantId}:{catalogCode}:{identifier}
```

示例：

- `1:perm:effective-roles:456`
- `1:perm:role-perm-snapshot:1001`
- `1:admin:dict-types:all`

要求：

- `catalogCode` 必须带服务前缀，如 `perm:*`、`admin:*`、`gw:*`
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

## Catalog 设计规范

每个模块维护自己的 catalog 常量类，不在业务代码里传字符串：

```java
public final class PermCacheCatalog {
    public static final CacheCatalogEntry<Set<Long>> EFFECTIVE_ROLES =
        CacheCatalogEntry.<Set<Long>>builder()
            .code("perm:effective-roles")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(2000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<Set<Long>>() {})
            .build();

    public static final CacheCatalogEntry<List<RolePermEntry>> ROLE_PERM_SNAPSHOT =
        CacheCatalogEntry.<List<RolePermEntry>>builder()
            .code("perm:role-perm-snapshot")
            .mode(CacheMode.L1_L2)
            .l1TtlMinutes(5)
            .l1MaxSize(2000)
            .l2TtlMinutes(30)
            .valueType(new TypeRef<List<RolePermEntry>>() {})
            .build();
}
```

要求：

- `CacheCatalogEntry` 是业务缓存的唯一描述方式
- 默认 TTL 和容量优先写在 catalog 常量中
- 运维需要覆盖时再用 `accessmesh.cache.default.*` 和 `accessmesh.cache.catalogs.*`

## 事务与失效规范

- 查询 miss 后可调用 `put` / `putBatch`
- 数据变更路径统一调用 `evictAfterCommit` / `evictBatchAfterCommit`
- 业务侧禁止手写 `TransactionSynchronizationManager.registerSynchronization(...)`
- 业务侧不需要感知 store 内部的 L1/L2 协调细节

## 禁止事项

- ❌ 禁止继续使用 `GenericCacheManager` / `AbstractGenericCacheManager`
- ❌ 禁止创建单缓存 `CacheManager`、region 类或 loader 回调适配层
- ❌ 禁止业务缓存直接操作 `RedisTemplate` / `StringRedisTemplate` / 裸 `Caffeine`
- ❌ 禁止业务缓存继续使用 `@Cacheable` / `@CacheEvict`
- ❌ 禁止在循环中逐个发起缓存 miss 加载或逐个 DB 查询，优先批量 `getBatch` / `putBatch`
- ❌ 禁止使用 Redis `KEYS` 命令，批量删除必须走 SCAN/分批删除
- ❌ 禁止在写事务中直接手工失效业务缓存，应优先使用 `evictAfterCommit`

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

## 验证清单

修改缓存相关代码后，至少检查：

1. 是否仍然只注入 `CacheService`，而不是私有 manager
2. 是否使用模块内的 `CacheCatalogEntry` 常量，而不是硬编码 key
3. 写路径是否改为 `evictAfterCommit` / `evictBatchAfterCommit`
4. gateway 无 Redisson 依赖时，是否仍只使用 `L1_ONLY`
5. permission-center / admin-service 有 Redisson 时，是否能拿到 `CombinedL1L2Store` 与 `RedissonBucketStore`

## 相关文件

| 文件                                               | 说明                    |
| -------------------------------------------------- | ----------------------- |
| `common/cache/CacheService.java`                   | 统一缓存接口            |
| `common/cache/DefaultCacheService.java`            | 唯一服务实现            |
| `common/cache/CacheCatalogEntry.java`              | 类型化缓存描述符        |
| `common/cache/CacheAutoConfiguration.java`         | 基础自动配置            |
| `common/cache/RedissonCacheAutoConfiguration.java` | Redisson store 自动配置 |
| `common/cache/impl/CombinedL1L2Store.java`         | L1_L2 实现              |
| `common/cache/impl/RedissonBucketStore.java`       | L2_ONLY 实现            |
| `common/cache/impl/CaffeineLocalCacheStore.java`   | L1_ONLY 实现            |
