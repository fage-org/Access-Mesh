package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import cn.ac.fage.accessmesh.common.cache.spi.LocalCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 默认缓存服务实现
 * <p>
 * 按 catalog 的 CacheMode 路由到对应的 Store 实现：
 * - L1_L2: CombinedL1L2Store（Caffeine L1 + RBucket L2，per-key TTL）
 * - L2_ONLY: RedissonBucketStore（纯 Redis）
 * - L1_ONLY: CaffeineLocalCacheStore（纯本地）
 * </p>
 *
 * <h3>单次有效 TTL 与剩余 TTL 回填（T-ACCESS-008）：</h3>
 * <p>
 * {@link #put(CacheCatalogEntry, Long, Object, Object, Duration)} 及批量重载支持单次有效 TTL，
 * 强制不超过 catalog 有效 TTL、剩余 ≤ 0 不写入（底层 Store 再防御性钳制一次）。
 * {@link #beginRead(CacheCatalogEntry)} 产生携带单调时钟起点的 {@link CacheReadToken}，
 * put(token,...) / putBatch(token,...) 自动写入「读取起点 + catalog TTL」扣除耗时后的剩余 TTL；
 * 单条、批量、并发合并和重试不得重新调用 beginRead 重置起点。
 * </p>
 *
 * <h3>普通 L1 跨实例失效（T-ACCESS-008）：</h3>
 * <p>
 * L1_L2 目录 evict / evictAll 时，在清理共享 L2 的同时通过可选的
 * {@link CacheInvalidationBroadcaster} 广播失效事件，其他实例订阅后清理本地 L1；
 * 广播失败不影响主流程（各实例 L1 由自身 TTL 兜底），失败计入失效失败指标。
 * 回滚不失效——广播只发生在 evict 执行时（evictAfterCommit 在提交后触发）。
 * </p>
 *
 * <h3>无 RedissonClient 场景：</h3>
 * <p>
 * 当 RedissonClient 不可用时，L1_L2 和 L2_ONLY store 为 null，
 * 仅支持 L1_ONLY 模式。尝试使用 L1_L2 或 L2_ONLY catalog 会抛出异常。
 * </p>
 *
 * <h3>事务感知失效：</h3>
 * <p>
 * evictAfterCommit 内部检查 TransactionSynchronizationManager.isActualTransactionActive()，
 * 有事务则注册 afterCommit 钩子，无事务则立即执行。
 * </p>
 */
public class DefaultCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCacheService.class);

    private final LocalCacheStore l1L2Store;
    private final DistributedCacheStore l2OnlyStore;
    private final LocalCacheStore l1OnlyStore;
    private final CacheProperties cacheProperties;
    private final CacheInvalidationBroadcaster invalidationBroadcaster;
    private final LongSupplier monotonicClock;

    public DefaultCacheService(LocalCacheStore l1L2Store,
                                DistributedCacheStore l2OnlyStore,
                                LocalCacheStore l1OnlyStore,
                                CacheProperties cacheProperties,
                                CacheInvalidationBroadcaster invalidationBroadcaster) {
        this(l1L2Store, l2OnlyStore, l1OnlyStore, cacheProperties, invalidationBroadcaster, System::nanoTime);
    }

    /**
     * 测试友好构造：可注入单调时钟以验证剩余 TTL 计算。
     */
    public DefaultCacheService(LocalCacheStore l1L2Store,
                               DistributedCacheStore l2OnlyStore,
                               LocalCacheStore l1OnlyStore,
                               CacheProperties cacheProperties,
                               CacheInvalidationBroadcaster invalidationBroadcaster,
                               LongSupplier monotonicClock) {
        this.l1L2Store = l1L2Store;
        this.l2OnlyStore = l2OnlyStore;
        this.l1OnlyStore = l1OnlyStore;
        this.cacheProperties = cacheProperties;
        this.invalidationBroadcaster = invalidationBroadcaster;
        this.monotonicClock = monotonicClock;

        if (l1L2Store == null && l2OnlyStore == null) {
            log.info("CacheService initialized without Redis - only L1_ONLY mode available");
        } else {
            log.info("CacheService initialized with Redis - all modes available");
        }
    }

    @Override
    public <V> V get(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier) {
        String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), identifier);
        return getStore(catalog).get(catalog, fullKey);
    }

    @Override
    public <K, V> Map<K, V> getBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return new HashMap<>();
        }

        Set<String> fullKeys = CacheKeyUtil.buildBatch(tenantId, catalog.getCode(), identifiers);
        Map<String, V> cachedValues = getStore(catalog).getBatch(catalog, fullKeys);

        Map<K, V> result = new HashMap<>();
        Map<String, K> keyMapping = new HashMap<>();

        for (K id : identifiers) {
            String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), id);
            keyMapping.put(fullKey, id);
        }

        for (Map.Entry<String, V> e : cachedValues.entrySet()) {
            K id = keyMapping.get(e.getKey());
            if (id != null && e.getValue() != null) {
                result.put(id, e.getValue());
            }
        }

        return result;
    }

    @Override
    public <V> void put(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier, V value) {
        if (value == null) {
            return;
        }

        String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), identifier);
        getStore(catalog).put(catalog, fullKey, value);
    }

    @Override
    public <V> void put(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier, V value,
                        Duration effectiveTtl) {
        if (value == null || !isWriteableTtl(effectiveTtl)) {
            return;
        }

        String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), identifier);
        getStore(catalog).put(catalog, fullKey, value, clampToCatalog(catalog, effectiveTtl));
    }

    @Override
    public <K, V> void putBatch(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        Map<String, V> mappedData = toFullKeyMap(catalog, tenantId, data);
        if (!mappedData.isEmpty()) {
            getStore(catalog).putBatch(catalog, mappedData);
        }
    }

    @Override
    public <K, V> void putBatch(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data,
                                Duration effectiveTtl) {
        if (data == null || data.isEmpty() || !isWriteableTtl(effectiveTtl)) {
            return;
        }

        Map<String, V> mappedData = toFullKeyMap(catalog, tenantId, data);
        if (!mappedData.isEmpty()) {
            getStore(catalog).putBatch(catalog, mappedData, clampToCatalog(catalog, effectiveTtl));
        }
    }

    @Override
    public <V> CacheReadToken<V> beginRead(CacheCatalogEntry<V> catalog) {
        return new CacheReadToken<>(catalog, monotonicClock.getAsLong());
    }

    @Override
    public <V> void put(CacheReadToken<V> token, Long tenantId, Object identifier, V value) {
        if (value == null || token == null) {
            return;
        }
        Duration remaining = remainingTtl(token);
        if (!isWriteableTtl(remaining)) {
            log.debug("Skip backfill for catalog={} identifier={}: read budget exhausted (remaining={})",
                token.catalog().getCode(), identifier, remaining);
            return;
        }
        put(token.catalog(), tenantId, identifier, value, remaining);
    }

    @Override
    public <K, V> void putBatch(CacheReadToken<V> token, Long tenantId, Map<K, V> data) {
        if (data == null || data.isEmpty() || token == null) {
            return;
        }
        Duration remaining = remainingTtl(token);
        if (!isWriteableTtl(remaining)) {
            log.debug("Skip batch backfill for catalog={}: read budget exhausted (remaining={})",
                token.catalog().getCode(), remaining);
            return;
        }
        putBatch(token.catalog(), tenantId, data, remaining);
    }

    @Override
    public <V> void evict(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier) {
        String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), identifier);
        getStore(catalog).evict(catalog, fullKey);
        broadcastEvict(catalog, tenantId, Set.of(fullKey));
    }

    @Override
    public <K, V> void evictBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }

        Set<String> fullKeys = CacheKeyUtil.buildBatch(tenantId, catalog.getCode(), identifiers);
        getStore(catalog).evictBatch(catalog, fullKeys);
        broadcastEvict(catalog, tenantId, fullKeys);
    }

    @Override
    public <V> void evictAfterCommit(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier) {
        runAfterCommit(() -> evict(catalog, tenantId, identifier));
    }

    @Override
    public <K, V> void evictBatchAfterCommit(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers) {
        runAfterCommit(() -> evictBatch(catalog, tenantId, identifiers));
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
        getStore(catalog).evictAll(catalog, tenantId);
        broadcastEvictAll(catalog, tenantId);
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog) {
        // catalog 级跨租户全量失效（订阅重连等恢复场景）：直接清理各层，
        // 不做跨实例 L1 广播——各实例重连时各自执行同等清理
        getStore(catalog).evictAll(catalog);
    }

    // ==================== 内部方法 ====================

    /**
     * 单次有效 TTL 是否可写入：null（未指定，走 catalog TTL）或正值可写；
     * 零/负值（预算耗尽）不写入。
     */
    private boolean isWriteableTtl(Duration effectiveTtl) {
        return effectiveTtl == null || (!effectiveTtl.isNegative() && !effectiveTtl.isZero());
    }

    /**
     * 钳制单次有效 TTL 不超过 catalog 有效 TTL（按 L2/L1 适用层取上限）。
     */
    private Duration clampToCatalog(CacheCatalogEntry<?> catalog, Duration effectiveTtl) {
        Duration cap = catalogBudget(catalog);
        return effectiveTtl.compareTo(cap) > 0 ? cap : effectiveTtl;
    }

    /**
     * catalog 有效 TTL 预算：按模式取 L2（L1_L2/L2_ONLY）或 L1（L1_ONLY）。
     */
    private Duration catalogBudget(CacheCatalogEntry<?> catalog) {
        return switch (catalog.getMode()) {
            case L2_ONLY, L1_L2 -> cacheProperties.getEffectiveL2Ttl(catalog.getCode(), catalog.getL2Ttl());
            case L1_ONLY -> cacheProperties.getEffectiveL1Ttl(catalog.getCode(), catalog.getL1Ttl());
        };
    }

    /**
     * 读取令牌剩余 TTL：catalog 预算 - 已耗时（单调时钟）；剩余 ≤ 0 由调用处跳过写入。
     */
    private Duration remainingTtl(CacheReadToken<?> token) {
        long elapsedNanos = monotonicClock.getAsLong() - token.startNanos();
        return catalogBudget(token.catalog()).minusNanos(elapsedNanos);
    }

    private <K, V> Map<String, V> toFullKeyMap(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data) {
        Map<String, V> mappedData = new HashMap<>();
        for (Map.Entry<K, V> e : data.entrySet()) {
            if (e.getValue() != null) {
                String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), e.getKey());
                mappedData.put(fullKey, e.getValue());
            }
        }
        return mappedData;
    }

    /**
     * L1_L2 目录失效时广播其他实例清理本地 L1；广播失败不影响主流程。
     */
    private void broadcastEvict(CacheCatalogEntry<?> catalog, Long tenantId, Set<String> fullKeys) {
        if (invalidationBroadcaster == null || catalog.getMode() != CacheMode.L1_L2) {
            return;
        }
        try {
            invalidationBroadcaster.broadcastEvict(catalog.getCode(), tenantId, fullKeys);
        } catch (Exception e) {
            log.warn("Failed to broadcast L1 invalidation for catalog={}: {}",
                catalog.getCode(), e.getMessage());
        }
    }

    private void broadcastEvictAll(CacheCatalogEntry<?> catalog, Long tenantId) {
        if (invalidationBroadcaster == null || catalog.getMode() != CacheMode.L1_L2) {
            return;
        }
        try {
            invalidationBroadcaster.broadcastEvictAll(catalog.getCode(), tenantId);
        } catch (Exception e) {
            log.warn("Failed to broadcast L1 evict-all for catalog={}: {}",
                catalog.getCode(), e.getMessage());
        }
    }

    /**
     * 根据 CacheMode 获取对应的 Store
     *
     * @throws IllegalStateException 如果 catalog 需要 Redis 但 RedissonClient 不可用
     */
    private <V> CacheStoreAdapter<V> getStore(CacheCatalogEntry<V> catalog) {
        switch (catalog.getMode()) {
            case L1_L2:
                if (l1L2Store == null) {
                    throw new IllegalStateException(
                        "Cache catalog '" + catalog.getCode() + "' requires L1_L2 mode but RedissonClient is not available. " +
                        "Please add redisson-spring-boot-starter dependency.");
                }
                return new CacheStoreAdapter<>(l1L2Store, null);
            case L2_ONLY:
                if (l2OnlyStore == null) {
                    throw new IllegalStateException(
                        "Cache catalog '" + catalog.getCode() + "' requires L2_ONLY mode but RedissonClient is not available. " +
                        "Please add redisson-spring-boot-starter dependency.");
                }
                return new CacheStoreAdapter<>(null, l2OnlyStore);
            case L1_ONLY:
                return new CacheStoreAdapter<>(l1OnlyStore, null);
            default:
                throw new IllegalArgumentException("Unknown cache mode: " + catalog.getMode());
        }
    }

    /**
     * 事务感知执行
     * <p>
     * 有事务：注册 afterCommit 钩子
     * 无事务：立即执行
     * </p>
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception e) {
                        log.error("Failed to execute cache operation after transaction commit", e);
                    }
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Store 适配器
     * <p>
     * 统一 LocalCacheStore 和 DistributedCacheStore 的调用接口
     * </p>
     */
    private static class CacheStoreAdapter<V> {

        private final LocalCacheStore localStore;
        private final DistributedCacheStore distributedStore;

        CacheStoreAdapter(LocalCacheStore localStore, DistributedCacheStore distributedStore) {
            this.localStore = localStore;
            this.distributedStore = distributedStore;
        }

        V get(CacheCatalogEntry<V> catalog, String fullKey) {
            if (localStore != null) {
                return localStore.get(catalog, fullKey);
            }
            if (distributedStore != null) {
                return distributedStore.get(catalog, fullKey);
            }
            return null;
        }

        Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
            if (localStore != null) {
                return localStore.getBatch(catalog, fullKeys);
            }
            if (distributedStore != null) {
                return distributedStore.getBatch(catalog, fullKeys);
            }
            return new HashMap<>();
        }

        void put(CacheCatalogEntry<V> catalog, String fullKey, V value) {
            if (localStore != null) {
                localStore.put(catalog, fullKey, value);
            }
            if (distributedStore != null) {
                distributedStore.put(catalog, fullKey, value);
            }
        }

        void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl) {
            if (localStore != null) {
                localStore.put(catalog, fullKey, value, effectiveTtl);
            }
            if (distributedStore != null) {
                distributedStore.put(catalog, fullKey, value, effectiveTtl);
            }
        }

        void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            if (localStore != null) {
                localStore.putBatch(catalog, data);
            }
            if (distributedStore != null) {
                distributedStore.putBatch(catalog, data);
            }
        }

        void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl) {
            if (localStore != null) {
                localStore.putBatch(catalog, data, effectiveTtl);
            }
            if (distributedStore != null) {
                distributedStore.putBatch(catalog, data, effectiveTtl);
            }
        }

        void evict(CacheCatalogEntry<V> catalog, String fullKey) {
            if (localStore != null) {
                localStore.evict(catalog, fullKey);
            }
            if (distributedStore != null) {
                distributedStore.evict(catalog, fullKey);
            }
        }

        void evictBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
            if (localStore != null) {
                localStore.evictBatch(catalog, fullKeys);
            }
            if (distributedStore != null) {
                distributedStore.evictBatch(catalog, fullKeys);
            }
        }

        void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
            if (localStore != null) {
                localStore.evictAll(catalog, tenantId);
            }
            if (distributedStore != null) {
                distributedStore.evictAll(catalog, tenantId);
            }
        }

        void evictAll(CacheCatalogEntry<V> catalog) {
            if (localStore != null) {
                localStore.evictAll(catalog);
            }
            if (distributedStore != null) {
                distributedStore.evictAll(catalog);
            }
        }
    }
}
