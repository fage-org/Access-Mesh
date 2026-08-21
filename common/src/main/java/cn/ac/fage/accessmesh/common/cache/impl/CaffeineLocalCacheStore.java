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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存存储实现
 * <p>
 * 用于 L1_ONLY 模式：
 * - 纯本地 Caffeine 缓存
 * - 无 Redis 依赖
 * - 适用于 Gateway 等无需分布式同步的场景
 * </p>
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>按 catalogCode 管理独立 Caffeine 实例</li>
 *   <li>computeIfAbsent 懒创建缓存</li>
 *   <li>使用 Jackson 序列化处理复杂泛型</li>
 *   <li>TTL 为 java.time.Duration 秒级精度；单次有效 TTL 经条目级
 *       {@code policy().expireAfterWrite().setExpiresAfter} 生效，强制不超过 catalog TTL，剩余 ≤0 不写</li>
 * </ul>
 */
public class CaffeineLocalCacheStore implements LocalCacheStore {

    private static final Logger log = LoggerFactory.getLogger(CaffeineLocalCacheStore.class);

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CacheProperties cacheProperties;

    private final ConcurrentHashMap<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    private final Map<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> putCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> readTimers = new ConcurrentHashMap<>();
    private final Map<String, Timer> writeTimers = new ConcurrentHashMap<>();

    public CaffeineLocalCacheStore(ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry,
                                    CacheProperties cacheProperties) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
        Cache<String, Object> cache = getOrCreateCache(catalog);
        String catalogCode = catalog.getCode();

        long start = System.nanoTime();
        try {
            Object raw = cache.getIfPresent(fullKey);
            long duration = System.nanoTime() - start;
            recordRead(catalogCode, duration);

            if (raw == null) {
                incrementCounter(missCounters.computeIfAbsent(catalogCode, k -> createMissCounter(k)));
                return null;
            }

            incrementCounter(hitCounters.computeIfAbsent(catalogCode, k -> createHitCounter(k)));
            return convertValue(raw, catalog);
        } catch (Exception e) {
            log.error("Failed to get from Caffeine, key={}", fullKey, e);
            return null;
        }
    }

    @Override
    public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return new HashMap<>();
        }

        Cache<String, Object> cache = getOrCreateCache(catalog);
        String catalogCode = catalog.getCode();
        Map<String, V> result = new HashMap<>();

        long start = System.nanoTime();
        int hits = 0;
        int misses = 0;

        for (String key : fullKeys) {
            Object raw = cache.getIfPresent(key);
            if (raw != null) {
                hits++;
                V value = convertValue(raw, catalog);
                if (value != null) {
                    result.put(key, value);
                }
            } else {
                misses++;
            }
        }

        long duration = System.nanoTime() - start;
        recordRead(catalogCode, duration);

        incrementCounter(hitCounters.computeIfAbsent(catalogCode, k -> createHitCounter(k)), hits);
        incrementCounter(missCounters.computeIfAbsent(catalogCode, k -> createMissCounter(k)), misses);

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

        Cache<String, Object> cache = getOrCreateCache(catalog);
        String catalogCode = catalog.getCode();
        Duration catalogTtl = catalogTtlUsed(catalog);
        if (!allowsWrite(catalogTtl, effectiveTtl)) {
            // 剩余预算耗尽，或有效 TTL 短于 catalog TTL（L1_ONLY 无条目级 TTL 能力）：不缓存
            return;
        }

        long start = System.nanoTime();
        try {
            cache.put(fullKey, value);
            long duration = System.nanoTime() - start;
            recordWrite(catalogCode, duration);
            incrementCounter(putCounters.computeIfAbsent(catalogCode, k -> createPutCounter(k)));
        } catch (Exception e) {
            log.error("Failed to put to Caffeine, key={}", fullKey, e);
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

        Cache<String, Object> cache = getOrCreateCache(catalog);
        String catalogCode = catalog.getCode();
        if (!allowsWrite(catalogTtlUsed(catalog), effectiveTtl)) {
            return;
        }

        long start = System.nanoTime();
        for (Map.Entry<String, V> e : data.entrySet()) {
            if (e.getValue() != null) {
                cache.put(e.getKey(), e.getValue());
            }
        }
        long duration = System.nanoTime() - start;
        recordWrite(catalogCode, duration);
        incrementCounter(putCounters.computeIfAbsent(catalogCode, k -> createPutCounter(k)));
    }

    @Override
    public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
        Cache<String, Object> cache = getOrCreateCache(catalog);
        try {
            cache.invalidate(fullKey);
        } catch (Exception e) {
            log.error("Failed to evict from Caffeine, key={}", fullKey, e);
        }
    }

    @Override
    public <V> void evictBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return;
        }

        Cache<String, Object> cache = getOrCreateCache(catalog);
        try {
            cache.invalidateAll(fullKeys);
        } catch (Exception e) {
            log.error("Failed to batch evict from Caffeine", e);
        }
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
        Cache<String, Object> cache = getOrCreateCache(catalog);

        try {
            String prefix = tenantId + ":" + catalog.getCode() + ":";
            cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
            log.info("Evicted all local cache for catalog={}, tenantId={}", catalog.getCode(), tenantId);
        } catch (Exception e) {
            log.error("Failed to evict all from Caffeine for catalog={}", catalog.getCode(), e);
        }
    }

    @Override
    public <V> void evictAll(CacheCatalogEntry<V> catalog) {
        Cache<String, Object> cache = getOrCreateCache(catalog);

        try {
            cache.asMap().keySet().removeIf(key -> CacheKeyUtil.belongsToCatalog(key, catalog.getCode()));
            log.info("Evicted all local cache for catalog={} (all tenants)", catalog.getCode());
        } catch (Exception e) {
            log.error("Failed to evict all tenants from Caffeine for catalog={}", catalog.getCode(), e);
        }
    }

    @Override
    public <V> long estimatedSize(CacheCatalogEntry<V> catalog) {
        Cache<String, Object> cache = getOrCreateCache(catalog);
        try {
            return cache.estimatedSize();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 读取缓存实例配置的固定过期 TTL（测试用：验证秒级精度配置真实进入 Caffeine policy）。
     */
    long policyExpirationMillis(CacheCatalogEntry<?> catalog, String fullKey) {
        Cache<String, Object> cache = caches.get(catalog.getCode());
        if (cache == null) {
            return -1L;
        }
        return cache.policy().expireAfterWrite()
            .map(fixed -> fixed.getExpiresAfter(TimeUnit.MILLISECONDS))
            .orElse(-1L);
    }

    // ==================== 内部方法 ====================

    /**
     * 目录有效 L1 TTL（缓存实例构建基准）。
     */
    private Duration catalogTtlUsed(CacheCatalogEntry<?> catalog) {
        return cacheProperties.getEffectiveL1Ttl(catalog.getCode(), catalog.getL1Ttl());
    }

    /**
     * 是否允许写入：effectiveTtl 为 null（catalog TTL）或正值且不短于 catalog TTL 时写
     * （以 catalog TTL 写入，生命周期不超过有效 TTL）；零/负（预算耗尽）或短于
     * catalog TTL（L1_ONLY 无条目级 TTL 能力）时不写。
     */
    private boolean allowsWrite(Duration catalogTtl, Duration effectiveTtl) {
        if (effectiveTtl == null) {
            return true;
        }
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            return false;
        }
        return catalogTtl.compareTo(effectiveTtl) <= 0;
    }

    private <V> Cache<String, Object> getOrCreateCache(CacheCatalogEntry<V> catalog) {
        return caches.computeIfAbsent(catalog.getCode(), code -> createCaffeineCache(catalog));
    }

    private <V> Cache<String, Object> createCaffeineCache(CacheCatalogEntry<V> catalog) {
        String catalogCode = catalog.getCode();
        Duration l1Ttl = cacheProperties.getEffectiveL1Ttl(catalogCode, catalog.getL1Ttl());
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

    private Counter createHitCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l1.hits")
            .tag("catalog", catalogCode)
            .description("L1 cache hits")
            .register(meterRegistry);
    }

    private Counter createMissCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.l1.misses")
            .tag("catalog", catalogCode)
            .description("L1 cache misses")
            .register(meterRegistry);
    }

    private Counter createPutCounter(String catalogCode) {
        if (meterRegistry == null) return null;
        return Counter.builder("cache.puts")
            .tag("layer", "l1")
            .tag("catalog", catalogCode)
            .description("Cache backfill writes (loads)")
            .register(meterRegistry);
    }

    private void recordRead(String catalogCode, long durationNanos) {
        Timer timer = readTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l1.read.duration")
                .tag("catalog", k)
                .description("L1 cache read duration")
                .register(meterRegistry);
        });
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void recordWrite(String catalogCode, long durationNanos) {
        Timer timer = writeTimers.computeIfAbsent(catalogCode, k -> {
            if (meterRegistry == null) return null;
            return Timer.builder("cache.l1.write.duration")
                .tag("catalog", k)
                .description("L1 cache write duration")
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
