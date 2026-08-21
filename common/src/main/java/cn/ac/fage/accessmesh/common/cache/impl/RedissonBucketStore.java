package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
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
 * Redisson Bucket 存储实现
 * <p>
 * 用于 L2_ONLY 模式：
 * - 纯 Redis 存储，无本地缓存
 * - 使用 RBucket&lt;String&gt; 存储 JSON 字符串
 * - 批量操作使用 RBatch Pipeline
 * </p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>JsonJacksonCodec 序列化</li>
 *   <li>批量写入使用 Pipeline 提升性能</li>
 *   <li>SCAN 删除避免 KEYS 阻塞</li>
 *   <li>put(null) 静默忽略，不缓存 null</li>
 *   <li>TTL 为 java.time.Duration 秒级精度；单次有效 TTL 强制不超过 catalog TTL，剩余 ≤0 不写</li>
 * </ul>
 */
public class RedissonBucketStore implements DistributedCacheStore {

    private static final Logger log = LoggerFactory.getLogger(RedissonBucketStore.class);

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CacheProperties cacheProperties;

    private final Map<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> putCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> invalidateFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> readTimers = new ConcurrentHashMap<>();
    private final Map<String, Timer> writeTimers = new ConcurrentHashMap<>();

    public RedissonBucketStore(RedissonClient redissonClient,
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

        long start = System.nanoTime();
        try {
            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            String json = bucket.get();
            long duration = System.nanoTime() - start;
            recordRead(catalogCode, duration);

            if (json == null || json.isEmpty()) {
                incrementCounter(getMissCounter(catalogCode));
                return null;
            }

            incrementCounter(getHitCounter(catalogCode));

            try {
                return objectMapper.readValue(json, catalog.getValueType());
            } catch (Exception e) {
                log.warn("Failed to deserialize L2 value for key={}, error={}", fullKey, e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get from L2, key={}", fullKey, e);
            incrementCounter(getErrorCounter(catalogCode));
            return null;
        }
    }

    @Override
    public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return new HashMap<>();
        }

        String catalogCode = catalog.getCode();
        Map<String, V> result = new HashMap<>();

        long start = System.nanoTime();
        try {
            // 使用 RBatch Pipeline 批量读取，避免 N+1 串行 GET
            List<String> orderedKeys = new ArrayList<>(fullKeys);
            RBatch batch = redissonClient.createBatch();
            List<RFuture<String>> futures = new ArrayList<>(orderedKeys.size());
            for (String key : orderedKeys) {
                futures.add(batch.<String>getBucket(key).getAsync());
            }
            batch.execute();

            long duration = System.nanoTime() - start;
            recordRead(catalogCode, duration);

            int hits = 0;
            int misses = 0;

            for (int i = 0; i < orderedKeys.size(); i++) {
                String key = orderedKeys.get(i);
                String json = futures.get(i).toCompletableFuture().getNow(null);

                if (json != null && !json.isEmpty()) {
                    hits++;
                    try {
                        V value = objectMapper.readValue(json, catalog.getValueType());
                        if (value != null) {
                            result.put(key, value);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to deserialize L2 value for key={}", key, ex);
                    }
                } else {
                    misses++;
                }
            }

            incrementCounter(getHitCounter(catalogCode), hits);
            incrementCounter(getMissCounter(catalogCode), misses);

        } catch (Exception e) {
            log.error("Failed to batch get from L2", e);
            incrementCounter(getErrorCounter(catalogCode));
        }

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
        Duration ttl = resolveWriteTtl(catalog, effectiveTtl);
        if (ttl == null) {
            return;
        }

        long start = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(value);
            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            bucket.set(json, ttl);

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
            incrementCounter(getPutCounter(catalogCode));
        } catch (Exception e) {
            log.error("Failed to put to L2, key={}", fullKey, e);
            incrementCounter(getErrorCounter(catalogCode));
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
        Duration ttl = resolveWriteTtl(catalog, effectiveTtl);
        if (ttl == null) {
            return;
        }

        long start = System.nanoTime();
        try {
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
            incrementCounter(getPutCounter(catalogCode));
        } catch (Exception e) {
            log.error("Failed to batch put to L2", e);
            incrementCounter(getErrorCounter(catalogCode));
        }
    }

    @Override
    public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
        try {
            redissonClient.getBucket(fullKey).delete();
        } catch (Exception e) {
            log.error("Failed to evict from L2, key={}", fullKey, e);
            incrementCounter(getErrorCounter(catalog.getCode()));
            incrementCounter(invalidateFailureCounters.computeIfAbsent(catalog.getCode(), k ->
                createInvalidateFailureCounter(k)));
        }
    }

    @Override
    public <V> void evictBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return;
        }

        try {
            RBatch batch = redissonClient.createBatch();
            for (String key : fullKeys) {
                batch.getBucket(key).deleteAsync();
            }
            batch.execute();
        } catch (Exception e) {
            log.error("Failed to batch evict from L2", e);
            incrementCounter(getErrorCounter(catalog.getCode()));
            incrementCounter(invalidateFailureCounters.computeIfAbsent(catalog.getCode(), k ->
                createInvalidateFailureCounter(k)));
        }
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
        String catalogCode = catalog.getCode();
        String pattern = CacheKeyUtil.buildScanPattern(tenantId, catalogCode);

        try {
            Set<String> keysToDelete = new HashSet<>();
            int batchSize = 100;

            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(pattern, batchSize);
            for (String key : keys) {
                keysToDelete.add(key);
                if (keysToDelete.size() >= batchSize) {
                    deleteBatchKeys(keysToDelete);
                    keysToDelete.clear();
                }
            }

            if (!keysToDelete.isEmpty()) {
                deleteBatchKeys(keysToDelete);
            }

            log.info("Evicted all L2 cache for catalog={}, tenantId={}", catalogCode, tenantId);
        } catch (Exception e) {
            // 复评 P2 修复：L2 全量失效失败不得误报成功——记录失效失败指标，
            // 剩余条目由 TTL 兜底最终一致
            log.error("Failed to evict all from L2 for catalog={}, tenantId={}", catalogCode, tenantId, e);
            incrementCounter(invalidateFailureCounters.computeIfAbsent(catalogCode, k ->
                createInvalidateFailureCounter(k)));
        }
    }

    /**
     * catalog 级全量失效（跨租户）：SCAN 模式删除。
     * 供订阅重连全量清空等不依赖租户枚举的恢复场景；代价高于租户级 evictAll。
     * SCAN 宽松模式会命中其他目录 identifier 内嵌本目录编码的键，
     * 删除前按完整键结构精确过滤（belongsToCatalog）。
     */
    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog) {
        String catalogCode = catalog.getCode();
        String pattern = CacheKeyUtil.buildCatalogPattern(catalogCode);

        try {
            Set<String> keysToDelete = new HashSet<>();
            int batchSize = 100;

            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(pattern, batchSize);
            for (String key : keys) {
                if (!CacheKeyUtil.belongsToCatalog(key, catalogCode)) {
                    continue;
                }
                keysToDelete.add(key);
                if (keysToDelete.size() >= batchSize) {
                    deleteBatchKeys(keysToDelete);
                    keysToDelete.clear();
                }
            }

            if (!keysToDelete.isEmpty()) {
                deleteBatchKeys(keysToDelete);
            }

            log.info("Evicted all L2 cache for catalog={} (all tenants)", catalogCode);
        } catch (Exception e) {
            // 复评 P2 修复：L2 全量失效失败不得误报成功——记录失效失败指标，
            // 剩余条目由 TTL 兜底最终一致
            log.error("Failed to evict all tenants from L2 for catalog={}", catalogCode, e);
            incrementCounter(invalidateFailureCounters.computeIfAbsent(catalogCode, k ->
                createInvalidateFailureCounter(k)));
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 解析本次写入 TTL：null 使用 catalog 有效 TTL；非空钳制为不超过 catalog TTL；
     * 零/负（预算耗尽）返回 null 表示不写。
     */
    private Duration resolveWriteTtl(CacheCatalogEntry<?> catalog, Duration effectiveTtl) {
        Duration catalogTtl = cacheProperties.getEffectiveL2Ttl(catalog.getCode(), catalog.getL2Ttl());
        if (effectiveTtl == null) {
            return catalogTtl;
        }
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            return null;
        }
        return effectiveTtl.compareTo(catalogTtl) > 0 ? catalogTtl : effectiveTtl;
    }

    /**
     * 分批删除键；失败向上抛出由 evictAll 统一计入失效失败指标（不静默吞掉）。
     */
    private void deleteBatchKeys(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        redissonClient.getKeys().delete(keys.toArray(new String[0]));
    }

    // ==================== 监控 ====================

    private Counter getHitCounter(String catalogCode) {
        return hitCounters.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Counter.builder("cache.l2.hits")
                .tag("catalog", k)
                .description("L2 cache hits")
                .register(meterRegistry);
        });
    }

    private Counter getMissCounter(String catalogCode) {
        return missCounters.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Counter.builder("cache.l2.misses")
                .tag("catalog", k)
                .description("L2 cache misses")
                .register(meterRegistry);
        });
    }

    private Counter getPutCounter(String catalogCode) {
        return putCounters.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Counter.builder("cache.puts")
                .tag("layer", "l2")
                .tag("catalog", k)
                .description("Cache backfill writes (loads)")
                .register(meterRegistry);
        });
    }

    private Counter getErrorCounter(String catalogCode) {
        return errorCounters.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Counter.builder("cache.l2.errors")
                .tag("catalog", k)
                .description("L2 cache errors")
                .register(meterRegistry);
        });
    }

    private Counter createInvalidateFailureCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.invalidate.failures")
            .tag("type", "evict")
            .tag("catalog", catalogCode)
            .description("Cache invalidation failures (evict)")
            .register(meterRegistry);
    }

    private void recordRead(String catalogCode, long durationNanos) {
        Timer timer = readTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l2.read.duration")
                .tag("catalog", k)
                .description("L2 cache read duration")
                .register(meterRegistry);
        });
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void recordWrite(String catalogCode, long durationNanos) {
        Timer timer = writeTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l2.write.duration")
                .tag("catalog", k)
                .description("L2 cache write duration")
                .register(meterRegistry);
        });
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
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
