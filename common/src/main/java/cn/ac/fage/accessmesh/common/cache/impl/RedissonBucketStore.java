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
 * Redisson Bucket 存储实现
 * <p>
 * 用于 L2_ONLY 模式：
 * - 纯 Redis 存储，无本地缓存
 * - 使用 RBucket<String> 存储 JSON 字符串
 * - 批量操作使用 RBatch Pipeline
 * </p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>JsonJacksonCodec 序列化</li>
 *   <li>批量写入使用 Pipeline 提升性能</li>
 *   <li>SCAN 删除避免 KEYS 阻塞</li>
 *   <li>put(null) 静默忽略，不缓存 null</li>
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
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
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
        if (value == null) {
            return;
        }

        String catalogCode = catalog.getCode();
        int l2Ttl = cacheProperties.getEffectiveL2Ttl(catalogCode, catalog.getL2TtlMinutes());

        long start = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(value);
            RBucket<String> bucket = redissonClient.getBucket(fullKey);
            bucket.set(json, Duration.ofMinutes(l2Ttl));

            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
        } catch (Exception e) {
            log.error("Failed to put to L2, key={}", fullKey, e);
            incrementCounter(getErrorCounter(catalogCode));
        }
    }

    @Override
    public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        String catalogCode = catalog.getCode();
        int l2Ttl = cacheProperties.getEffectiveL2Ttl(catalogCode, catalog.getL2TtlMinutes());
        Duration ttl = Duration.ofMinutes(l2Ttl);

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
        }
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
        String pattern = CacheKeyUtil.buildScanPattern(tenantId, catalog.getCode());

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

            log.info("Evicted all L2 cache for catalog={}, tenantId={}", catalog.getCode(), tenantId);
        } catch (Exception e) {
            log.error("Failed to evict all from L2 for catalog={}", catalog.getCode(), e);
            incrementCounter(getErrorCounter(catalog.getCode()));
        }
    }

    private void deleteBatchKeys(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            redissonClient.getKeys().delete(keys.toArray(new String[0]));
        } catch (Exception e) {
            log.error("Failed to delete keys batch", e);
        }
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

    private Counter getErrorCounter(String catalogCode) {
        return errorCounters.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Counter.builder("cache.l2.errors")
                .tag("catalog", k)
                .description("L2 cache errors")
                .register(meterRegistry);
        });
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