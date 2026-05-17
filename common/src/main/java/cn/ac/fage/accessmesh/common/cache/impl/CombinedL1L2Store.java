package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
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
import org.redisson.api.RBucketAsync;
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
 * <h3>跨节点失效：</h3>
 * <p>
 * 本类不内置 pub/sub 失效机制。需由业务层调用 CacheService.evictAfterCommit
 * 触发失效。L2 TTL 提供兜底保护。
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

            // 3. L2 hit，反序列化并回填 L1
            incrementCounter(l2HitCounters.computeIfAbsent(catalogCode, k -> createL2HitCounter(k)));
            V value = objectMapper.readValue(json, catalog.getValueType());

            if (value != null) {
                l1Cache.put(fullKey, value);
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

        // 2. L1 miss 的 key 查 L2（Pipeline 批量）
        if (!l2KeysToQuery.isEmpty()) {
            try {
                List<String> orderedKeys = new ArrayList<>(l2KeysToQuery);
                RBatch batch = redissonClient.createBatch();
                List<RFuture<String>> futures = new ArrayList<>(orderedKeys.size());
                for (String key : orderedKeys) {
                    futures.add(batch.<String>getBucket(key).getAsync());
                }
                batch.execute();

                for (int i = 0; i < orderedKeys.size(); i++) {
                    String key = orderedKeys.get(i);
                    String json = futures.get(i).toCompletableFuture().getNow(null);

                    if (json != null && !json.isEmpty()) {
                        l2Hits++;
                        try {
                            V value = objectMapper.readValue(json, catalog.getValueType());
                            if (value != null) {
                                result.put(key, value);
                                // 回填 L1
                                l1Cache.put(key, value);
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
        if (value == null) {
            return;
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        int l2Ttl = cacheProperties.getEffectiveL2Ttl(catalogCode, catalog.getL2TtlMinutes());

        long start = System.nanoTime();
        try {
            // 1. 写 L1 Caffeine
            l1Cache.put(fullKey, value);

            // 2. 写 L2 Redis（带 TTL）
            String json = objectMapper.writeValueAsString(value);
            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            bucket.set(json, Duration.ofMinutes(l2Ttl));

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
        } catch (Exception e) {
            log.error("Failed to put to CombinedL1L2, key={}", fullKey, e);
        }
    }

    @Override
    public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String catalogCode = catalog.getCode();
        Cache<String, Object> l1Cache = getOrCreateL1Cache(catalog);
        int l2Ttl = cacheProperties.getEffectiveL2Ttl(catalogCode, catalog.getL2TtlMinutes());
        Duration ttl = Duration.ofMinutes(l2Ttl);

        long start = System.nanoTime();
        try {
            // 1. 批量写 L1
            for (Map.Entry<String, V> e : data.entrySet()) {
                if (e.getValue() != null) {
                    l1Cache.put(e.getKey(), e.getValue());
                }
            }

            // 2. 批量写 L2（Pipeline）
            RBatch batch = redissonClient.createBatch();
            for (Map.Entry<String, V> e : data.entrySet()) {
                if (e.getValue() != null) {
                    String json = objectMapper.writeValueAsString(e.getValue());
                    batch.getBucket(e.getKey()).setAsync(json, ttl);
                }
            }
            batch.execute();

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
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

    private <V> Cache<String, Object> getOrCreateL1Cache(CacheCatalogEntry<V> catalog) {
        return l1Caches.computeIfAbsent(catalog.getCode(), code -> createCaffeineCache(catalog));
    }

    private <V> Cache<String, Object> createCaffeineCache(CacheCatalogEntry<V> catalog) {
        String catalogCode = catalog.getCode();
        int l1Ttl = cacheProperties.getEffectiveL1Ttl(catalogCode, catalog.getL1TtlMinutes());
        long l1MaxSize = cacheProperties.getEffectiveL1MaxSize(catalogCode, catalog.getL1MaxSize());

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(l1MaxSize)
            .expireAfterWrite(l1Ttl, TimeUnit.MINUTES)
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