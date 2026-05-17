package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import cn.ac.fage.accessmesh.common.cache.spi.LocalCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 默认缓存服务实现
 * <p>
 * 按 catalog 的 CacheMode 路由到对应的 Store 实现：
 * - L1_L2: CombinedL1L2Store（Caffeine L1 + RBucket L2，per-key TTL）
 * - L2_ONLY: RedissonBucketStore（纯 Redis）
 * - L1_ONLY: CaffeineLocalCacheStore（纯本地）
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

    public DefaultCacheService(LocalCacheStore l1L2Store,
                                DistributedCacheStore l2OnlyStore,
                                LocalCacheStore l1OnlyStore) {
        this.l1L2Store = l1L2Store;
        this.l2OnlyStore = l2OnlyStore;
        this.l1OnlyStore = l1OnlyStore;

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
    public <K, V> void putBatch(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        Map<String, V> mappedData = new HashMap<>();
        for (Map.Entry<K, V> e : data.entrySet()) {
            if (e.getValue() != null) {
                String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), e.getKey());
                mappedData.put(fullKey, e.getValue());
            }
        }

        if (!mappedData.isEmpty()) {
            getStore(catalog).putBatch(catalog, mappedData);
        }
    }

    @Override
    public <V> void evict(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier) {
        String fullKey = CacheKeyUtil.build(tenantId, catalog.getCode(), identifier);
        getStore(catalog).evict(catalog, fullKey);
    }

    @Override
    public <K, V> void evictBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }

        Set<String> fullKeys = CacheKeyUtil.buildBatch(tenantId, catalog.getCode(), identifiers);
        getStore(catalog).evictBatch(catalog, fullKeys);
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
    }

    // ==================== 内部方法 ====================

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

        void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            if (localStore != null) {
                localStore.putBatch(catalog, data);
            }
            if (distributedStore != null) {
                distributedStore.putBatch(catalog, data);
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
    }
}