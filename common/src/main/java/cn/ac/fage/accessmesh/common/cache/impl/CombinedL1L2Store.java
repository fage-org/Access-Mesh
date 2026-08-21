package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.spi.LocalCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * L1_L2 组合存储实现
 * <p>
 * 使用 Caffeine (L1 本地缓存) + RBucket (L2 Redis) 组合：
 * <ul>
 *   <li>L1: Caffeine 本地缓存，快速访问，TTL 由 catalog 配置控制</li>
 *   <li>L2: Redis RBucket，分布式共享，per-key TTL 控制条目级过期</li>
 * </ul>
 * </p>
 *
 * <h3>设计说明：</h3>
 * <p>
 * 不使用 RLocalCachedMap（Hash 整体 TTL），改用 RBucket（per-key TTL）
 * 以实现目录定义的条目级 L2 TTL 语义。
 * </p>
 *
 * <h3>读写流程：</h3>
 * <ul>
 *   <li>get: 先查 L1 Caffeine，miss 则查 L2 Redis，miss 则返回 null</li>
 *   <li>put: 写 L1 Caffeine + L2 Redis（双写，L2 带 TTL）</li>
 *   <li>evict: 同时失效 L1 + L2</li>
 * </ul>
 *
 * <h3>跨节点失效（T-ACCESS-008）：</h3>
 * <p>
 * 本实例 evict 由 {@code CacheService} 触发并同时清理共享 L2；其他实例的本地 L1
 * 通过 {@code CacheInvalidationBroadcaster} 广播事件清理（见
 * {@link #invalidateLocalL1}/{@link #invalidateLocalL1All}，由广播订阅端调用）。
 * 广播丢失时由各实例 L1 TTL 兜底。
 * </p>
 *
 * <h3>单次有效 TTL：</h3>
 * <p>
 * TTL 为 java.time.Duration 秒级精度；put 传单次有效 TTL 时 L2 按钳制后的 TTL 精确写入；
 * L1（Caffeine 固定过期，无条目级 TTL 能力）仅当 catalog L1 TTL 不超出有效 TTL 预算时写入，
 * 否则跳过（宁可不缓存，不超出预算）；各层强制不超过 catalog TTL，剩余 ≤0 不写。
 * L2 命中回填 L1 前校验条目剩余存活时间（remainTimeToLive）不小于 catalog L1 TTL，
 * 否则不回填——防止单次有效 TTL 写入的短命条目经 L2 命中重新放大到完整 L1 TTL。
 * </p>
 */
public class CombinedL1L2Store implements LocalCacheStore {

    private static final Logger log = LoggerFactory.getLogger(CombinedL1L2Store.class);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CacheProperties cacheProperties;

    // L1: Caffeine 本地缓存（按 catalog 管理）
    private final ConcurrentHashMap<String, Cache<String, Object>> l1Caches = new ConcurrentHashMap<>();

    // 监控计数器
    private final Map<String, Counter> l1HitCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> l1MissCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> l2HitCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> l2MissCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> putCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> invalidateFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> readTimers = new ConcurrentHashMap<>();
    private final Map<String, Timer> writeTimers = new ConcurrentHashMap<>();

    public CombinedL1L2Store(RedissonClient redissonClient,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry,
                              CacheProperties cacheProperties) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);

        long start = System.nanoTime();
        try {
            // 1. 先查 L1 Caffeine
            Object raw = l1Cache.getIfPresent(fullKey);
            if (raw != null) {
                long duration = System.nanoTime() - start;
                recordRead(catalogCode, duration);
                incrementCounter(l1HitCounters.computeIfAbsent(catalogCode, k -> createL1HitCounter(k)));
                return convertValue(raw, catalog);
            }

            // 2. L1 miss，查 L2 Redis
            incrementCounter(l1MissCounters.computeIfAbsent(catalogCode, k -> createL1MissCounter(k)));

            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            String json = bucket.get();

            if (json == null || json.isEmpty()) {
                long duration = System.nanoTime() - start;
                recordRead(catalogCode, duration);
                incrementCounter(l2MissCounters.computeIfAbsent(catalogCode, k -> createL2MissCounter(k)));
                return null;
            }

            // 3. L2 hit，反序列化；仅当剩余存活时间覆盖 catalog L1 TTL 时回填 L1
            //（复评 P2 修复：防止单次有效 TTL 的短命条目经 L2 命中放大到完整 L1 TTL）
            incrementCounter(l2HitCounters.computeIfAbsent(catalogCode, k -> createL2HitCounter(k)));
            V value = objectMapper.readValue(json, catalog.getValueType());

            if (value != null) {
                backfillL1IfWithinBudget(l1Cache, catalog, fullKey, value, bucket);
            }

            long duration = System.nanoTime() - start;
            recordRead(catalogCode, duration);

            return value;
        } catch (Exception e) {
            log.error("Failed to get from CombinedL1L2, key={}", fullKey, e);
            return null;
        }
    }

    @Override
    public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return new HashMap<>();
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        Map<String, V> result = new HashMap<>();

        long start = System.nanoTime();
        int l1Hits = 0;
        int l1Misses = 0;
        int l2Hits = 0;
        int l2Misses = 0;

        // 1. 先查 L1
        Set<String> l2KeysToQuery = new HashSet<>();
        for (String key : fullKeys) {
            Object raw = l1Cache.getIfPresent(key);
            if (raw != null) {
                l1Hits++;
                V value = convertValue(raw, catalog);
                if (value != null) {
                    result.put(key, value);
                }
            } else {
                l1Misses++;
                l2KeysToQuery.add(key);
            }
        }

        // 2. L1 miss 的 key 查 L2（Pipeline 批量：值 + 剩余存活时间）
        if (!l2KeysToQuery.isEmpty()) {
            try {
                List<String> orderedKeys = new ArrayList<>(l2KeysToQuery);
                RBatch batch = redissonClient.createBatch();
                List<RFuture<String>> futures = new ArrayList<>(orderedKeys.size());
                List<RFuture<Long>> ttlFutures = new ArrayList<>(orderedKeys.size());
                for (String key : orderedKeys) {
                    futures.add(batch.<String>getBucket(key).getAsync());
                    ttlFutures.add(batch.getBucket(key).remainTimeToLiveAsync());
                }
                batch.execute();

                long l1TtlMillis = catalogL1Ttl(catalog).toMillis();
                for (int i = 0; i < orderedKeys.size(); i++) {
                    String key = orderedKeys.get(i);
                    String json = futures.get(i).toCompletableFuture().getNow(null);

                    if (json != null && !json.isEmpty()) {
                        l2Hits++;
                        try {
                            V value = objectMapper.readValue(json, catalog.getValueType());
                            if (value != null) {
                                result.put(key, value);
                                // 回填 L1 仅当剩余存活时间覆盖 catalog L1 TTL
                                //（复评 P2 修复：防短命条目经 L2 命中放大）
                                Long remainMs = ttlFutures.get(i).toCompletableFuture().getNow(-1L);
                                if (remainMs != null && remainMs >= l1TtlMillis) {
                                    l1Cache.put(key, value);
                                }
                            }
                        } catch (Exception ex) {
                            log.warn("Failed to deserialize L2 value for key={}", key, ex);
                        }
                    } else {
                        l2Misses++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to batch get L2", e);
            }
        }

        long duration = System.nanoTime() - start;
        recordRead(catalogCode, duration);

        incrementCounter(l1HitCounters.computeIfAbsent(catalogCode, k -> createL1HitCounter(k)), l1Hits);
        incrementCounter(l1MissCounters.computeIfAbsent(catalogCode, k -> createL1MissCounter(k)), l1Misses);
        incrementCounter(l2HitCounters.computeIfAbsent(catalogCode, k -> createL2HitCounter(k)), l2Hits);
        incrementCounter(l2MissCounters.computeIfAbsent(catalogCode, k -> createL2MissCounter(k)), l2Misses);

        return result;
    }

    @Override
    public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value) {
        put(catalog, fullKey, value, null);
    }

    @Override
    public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl) {
        if (value == null) {
            return;
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        Duration catalogL1Ttl = catalogL1Ttl(catalog);
        Duration resolvedL2Ttl = resolveL2WriteTtl(catalog, effectiveTtl);
        if (resolvedL2Ttl == null) {
            // 剩余预算耗尽（单次有效 TTL ≤ 0）：L1/L2 均不写
            return;
        }
        boolean writeL1 = allowsL1Write(catalogL1Ttl, effectiveTtl);

        long start = System.nanoTime();
        try {
            // 1. 写 L1 Caffeine（仅当 catalog L1 TTL 不超过有效 TTL 预算；否则跳过——
            //    固定过期无条目级 TTL 能力，写入会超出预算）
            if (writeL1) {
                l1Cache.put(fullKey, value);
            }

            // 2. 写 L2 Redis（精确单次有效 TTL）
            String json = objectMapper.writeValueAsString(value);
            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            bucket.set(json, resolvedL2Ttl);

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
            incrementCounter(putCounters.computeIfAbsent(catalogCode, k -> createPutCounter(k)));
        } catch (Exception e) {
            log.error("Failed to put to CombinedL1L2, key={}", fullKey, e);
        }
    }

    @Override
    public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
        putBatch(catalog, data, null);
    }

    @Override
    public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        Duration catalogL1Ttl = catalogL1Ttl(catalog);
        Duration resolvedL2Ttl = resolveL2WriteTtl(catalog, effectiveTtl);
        if (resolvedL2Ttl == null) {
            return;
        }
        boolean writeL1 = allowsL1Write(catalogL1Ttl, effectiveTtl);

        long start = System.nanoTime();
        try {
            // 1. 批量写 L1（预算允许时）
            if (writeL1) {
                for (Map.Entry<String, V> e : data.entrySet()) {
                    if (e.getValue() != null) {
                        l1Cache.put(e.getKey(), e.getValue());
                    }
                }
            }

            // 2. 批量写 L2（Pipeline，精确单次有效 TTL）
            RBatch batch = redissonClient.createBatch();
            for (Map.Entry<String, V> e : data.entrySet()) {
                if (e.getValue() != null) {
                    String json = objectMapper.writeValueAsString(e.getValue());
                    batch.getBucket(e.getKey()).setAsync(json, resolvedL2Ttl);
                }
            }
            batch.execute();

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
            incrementCounter(putCounters.computeIfAbsent(catalogCode, k -> createPutCounter(k)));
        } catch (Exception e) {
            log.error("Failed to batch put to CombinedL1L2", e);
        }
    }

    @Override
    public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);

        try {
            // 1. 失效 L1
            l1Cache.invalidate(fullKey);

            // 2. 失效 L2
            redissonClient.getBucket(fullKey).delete();
        } catch (Exception e) {
            log.error("Failed to evict from CombinedL1L2, key={}", fullKey, e);
            incrementInvalidateFailure(catalogCode);
        }
    }

    @Override
    public <V> void evictBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return;
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);

        try {
            // 1. 批量失效 L1
            l1Cache.invalidateAll(fullKeys);

            // 2. 批量失效 L2（Pipeline）
            RBatch batch = redissonClient.createBatch();
            for (String key : fullKeys) {
                batch.getBucket(key).deleteAsync();
            }
            batch.execute();
        } catch (Exception e) {
            log.error("Failed to batch evict from CombinedL1L2", e);
            incrementInvalidateFailure(catalogCode);
        }
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);

        try {
            // 1. 失效 L1（按 tenant 前缀过滤）
            String prefix = tenantId + ":" + catalogCode + ":";
            l1Cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));

            // 2. 失效 L2（SCAN 删除）
            String pattern = tenantId + ":" + catalogCode + ":*";
            Set<String> keysToDelete = new HashSet<>();
            int batchSize = 100;

            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(pattern, batchSize);
            for (String key : keys) {
                keysToDelete.add(key);
                if (keysToDelete.size() >= batchSize) {
                    redissonClient.getKeys().delete(keysToDelete.toArray(new String[0]));
                    keysToDelete.clear();
                }
            }

            if (!keysToDelete.isEmpty()) {
                redissonClient.getKeys().delete(keysToDelete.toArray(new String[0]));
            }

            log.info("Evicted all cache for catalog={}, tenantId={}", catalogCode, tenantId);
        } catch (Exception e) {
            log.error("Failed to evict all from CombinedL1L2 for catalog={}", catalogCode, e);
            incrementInvalidateFailure(catalogCode);
        }
    }

    /**
     * catalog 级全量失效（跨租户）：L1 按 catalog 前缀过滤，L2 SCAN 模式删除。
     * 供订阅重连全量清空等不依赖租户枚举的恢复场景；代价高于租户级 evictAll。
     * SCAN 宽松模式会命中其他目录 identifier 内嵌本目录编码的键，
     * L2 删除前按完整键结构精确过滤（belongsToCatalog）。
     */
    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog) {
        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);

        try {
            // 1. 失效 L1（跨租户，按完整键结构精确过滤）
            l1Cache.asMap().keySet().removeIf(key -> CacheKeyUtil.belongsToCatalog(key, catalogCode));

            // 2. 失效 L2（跨租户 SCAN + 精确过滤删除）
            String pattern = CacheKeyUtil.buildCatalogPattern(catalogCode);
            Set<String> keysToDelete = new HashSet<>();
            int batchSize = 100;

            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(pattern, batchSize);
            for (String key : keys) {
                if (!CacheKeyUtil.belongsToCatalog(key, catalogCode)) {
                    continue;
                }
                keysToDelete.add(key);
                if (keysToDelete.size() >= batchSize) {
                    redissonClient.getKeys().delete(keysToDelete.toArray(new String[0]));
                    keysToDelete.clear();
                }
            }

            if (!keysToDelete.isEmpty()) {
                redissonClient.getKeys().delete(keysToDelete.toArray(new String[0]));
            }

            log.info("Evicted all cache for catalog={} (all tenants)", catalogCode);
        } catch (Exception e) {
            log.error("Failed to evict all tenants from CombinedL1L2 for catalog={}", catalogCode, e);
            incrementInvalidateFailure(catalogCode);
        }
    }

    /**
     * 跨实例失效：按完整键集合清理指定 catalog 的本地 L1（不触碰 L2）。
     * <p>
     * 由 {@code CacheInvalidationBroadcaster} 订阅端调用——其他实例失效时广播，
     * 本实例收到后清理本地 L1，防止继续读到已失效的本地副本。
     * </p>
     *
     * @param catalogCode 缓存目录编码
     * @param fullKeys 完整缓存键集合
     */
    public void invalidateLocalL1(String catalogCode, Set<String> fullKeys) {
        Cache<String, Object> l1Cache = l1Caches.get(catalogCode);
        if (l1Cache == null || fullKeys == null || fullKeys.isEmpty()) {
            return;
        }
        try {
            l1Cache.invalidateAll(new HashSet<>(fullKeys));
        } catch (Exception e) {
            log.warn("Failed to invalidate local L1 for catalog={}: {}", catalogCode, e.getMessage());
        }
    }

    /**
     * 跨实例失效：清理指定 catalog 在指定租户下的全部本地 L1（不触碰 L2）。
     *
     * @param catalogCode 缓存目录编码
     * @param tenantId 租户ID
     */
    public void invalidateLocalL1All(String catalogCode, Long tenantId) {
        Cache<String, Object> l1Cache = l1Caches.get(catalogCode);
        if (l1Cache == null || tenantId == null) {
            return;
        }
        try {
            String prefix = tenantId + ":" + catalogCode + ":";
            l1Cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        } catch (Exception e) {
            log.warn("Failed to invalidate all local L1 for catalog={}, tenantId={}: {}",
                catalogCode, tenantId, e.getMessage());
        }
    }

    @Override
    public <V> long estimatedSize(CacheCatalogEntry<V> catalog) {
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        try {
            return l1Cache.estimatedSize();
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== 内部方法 ====================

    private Duration catalogL1Ttl(CacheCatalogEntry<?> catalog) {
        return cacheProperties.getEffectiveL1Ttl(catalog.getCode(), catalog.getL1Ttl());
    }

    private Duration catalogL2Ttl(CacheCatalogEntry<?> catalog) {
        return cacheProperties.getEffectiveL2Ttl(catalog.getCode(), catalog.getL2Ttl());
    }

    /**
     * 解析 L2 写入 TTL：null 使用 catalog 有效 L2 TTL；非空钳制为不超过 catalog L2 TTL；
     * 零/负（预算耗尽）返回 null 表示不写。
     */
    private Duration resolveL2WriteTtl(CacheCatalogEntry<?> catalog, Duration effectiveTtl) {
        return resolveTtl(catalogL2Ttl(catalog), effectiveTtl);
    }

    /**
     * 解析某层写入 TTL：null 使用该层 catalog 有效 TTL；非空钳制为不超过该层 catalog TTL；
     * 零/负（预算耗尽）返回 null 表示该层不写。
     */
    private Duration resolveTtl(Duration catalogTtl, Duration effectiveTtl) {
        if (effectiveTtl == null) {
            return catalogTtl;
        }
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            return null;
        }
        return effectiveTtl.compareTo(catalogTtl) > 0 ? catalogTtl : effectiveTtl;
    }

    /**
     * L1 是否可写：effectiveTtl 为 null（catalog TTL）或正值且 catalog L1 TTL 不超过
     * 有效 TTL 预算时写（以 catalog TTL 写入，生命周期不超过预算）；预算耗尽或
     * catalog L1 TTL 超出预算时跳过——固定过期无条目级 TTL 能力，写入会超出预算。
     */
    private boolean allowsL1Write(Duration catalogL1Ttl, Duration effectiveTtl) {
        if (effectiveTtl == null) {
            return true;
        }
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            return false;
        }
        return catalogL1Ttl.compareTo(effectiveTtl) <= 0;
    }

    /**
     * L2 命中后按剩余存活时间决定是否回填 L1。
     * <p>
     * 仅当条目剩余存活时间（remainTimeToLive）不小于 catalog L1 TTL 时回填
     * （L1 副本生命周期不超过 L2 条目自身寿命）；查询失败时跳过回填、不影响返回值。
     * </p>
     */
    private <V> void backfillL1IfWithinBudget(Cache<String, Object> l1Cache,
                                              CacheCatalogEntry<V> catalog,
                                              String fullKey, V value, RBucket<String> bucket) {
        try {
            Long remainMs = bucket.remainTimeToLive();
            if (remainMs != null && remainMs >= catalogL1Ttl(catalog).toMillis()) {
                l1Cache.put(fullKey, value);
            }
        } catch (Exception e) {
            log.warn("Skip L1 backfill for key={} (remainTimeToLive failed): {}",
                fullKey, e.getMessage());
        }
    }

    private <V> Cache<String, Object> getOrCreateL1Cache(CacheCatalogEntry<V> catalog) {
        return l1Caches.computeIfAbsent(catalog.getCode(), code -> createCaffeineCache(catalog));
    }

    private <V> Cache<String, Object> createCaffeineCache(CacheCatalogEntry<V> catalog) {
        String catalogCode = catalog.getCode();
        Duration l1Ttl = catalogL1Ttl(catalog);
        long l1MaxSize = cacheProperties.getEffectiveL1MaxSize(catalogCode, catalog.getL1MaxSize());

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(l1MaxSize)
            .expireAfterWrite(l1Ttl)
            .recordStats();

        Cache<String, Object> cache = builder.build();

        registerMetrics(catalogCode, cache);

        return cache;
    }

    @SuppressWarnings("unchecked")
    private <V> V convertValue(Object raw, CacheCatalogEntry<V> catalog) {
        if (raw == null) {
            return null;
        }

        try {
            if (catalog.getValueType().getRawClass().isInstance(raw)) {
                return (V) raw;
            }

            String json = objectMapper.writeValueAsString(raw);
            return objectMapper.readValue(json, catalog.getValueType());
        } catch (Exception e) {
            log.warn("Failed to convert value for catalog={}, error={}",
                catalog.getCode(), e.getMessage());
            return null;
        }
    }

    // ==================== 监控 ====================

    private Counter createL1HitCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l1.hits")
            .tag("catalog", catalogCode)
            .description("L1 cache hits")
            .register(meterRegistry);
    }

    private Counter createL1MissCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l1.misses")
            .tag("catalog", catalogCode)
            .description("L1 cache misses")
            .register(meterRegistry);
    }

    private Counter createL2HitCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l2.hits")
            .tag("catalog", catalogCode)
            .description("L2 cache hits (from L1 miss)")
            .register(meterRegistry);
    }

    private Counter createL2MissCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l2.misses")
            .tag("catalog", catalogCode)
            .description("L2 cache misses")
            .register(meterRegistry);
    }

    private Counter createPutCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.puts")
            .tag("layer", "l1_l2")
            .tag("catalog", catalogCode)
            .description("Cache backfill writes (loads)")
            .register(meterRegistry);
    }

    private Counter createInvalidateFailureCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.invalidate.failures")
            .tag("type", "evict")
            .tag("catalog", catalogCode)
            .description("Cache invalidation failures (evict)")
            .register(meterRegistry);
    }

    private void incrementInvalidateFailure(String catalogCode) {
        incrementCounter(invalidateFailureCounters.computeIfAbsent(catalogCode, k ->
            createInvalidateFailureCounter(k)));
    }

    private void recordRead(String catalogCode, long durationNanos) {
        Timer timer = readTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l1l2.read.duration")
                .tag("catalog", k)
                .description("L1+L2 cache read duration")
                .register(meterRegistry);
        });
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void recordWrite(String catalogCode, long durationNanos) {
        Timer timer = writeTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l1l2.write.duration")
                .tag("catalog", k)
                .description("L1+L2 cache write duration")
                .register(meterRegistry);
        });
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void registerMetrics(String catalogCode, Cache<String, Object> cache) {
        if (meterRegistry == null) {
            return;
        }

        Gauge.builder("cache.l1.size", cache, c -> c.estimatedSize())
            .tag("catalog", catalogCode)
            .description("L1 cache size")
            .register(meterRegistry);

        Gauge.builder("cache.l1.hit_rate", cache, c -> c.stats().hitRate())
            .tag("catalog", catalogCode)
            .description("L1 cache hit rate")
            .register(meterRegistry);

        Gauge.builder("cache.l1.eviction_count", cache, c -> c.stats().evictionCount())
            .tag("catalog", catalogCode)
            .description("L1 cache eviction count")
            .register(meterRegistry);
    }

    private void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void incrementCounter(Counter counter, int times) {
        if (counter != null && times > 0) {
            counter.increment(times);
        }
    }
}
