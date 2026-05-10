package cn.ac.fage.accessmesh.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * 通用缓存管理器抽象实现
 * <p>
 * 实现 L1(Caffeine) + L2(Redis) 双层缓存的读写和失效机制。
 * </p>
 *
 * <h3>修复的问题：</h3>
 * <ul>
 *   <li>问题1：evictAll 使用 SCAN 分批删除，避免 KEYS 阻塞</li>
 *   <li>问题2：Class&lt;V&gt; 类型令牌解决泛型擦除</li>
 *   <li>问题3：NULL_MARKER 空值缓存防止缓存穿透</li>
 *   <li>问题4：Redis try-catch 异常处理和降级策略</li>
 *   <li>问题5：写入顺序先 L2 后 L1</li>
 *   <li>问题8：键验证（长度限制500，清理特殊字符）</li>
 *   <li>问题9：getL1MaximumSize() 抽象方法避免硬编码</li>
 *   <li>问题12：recordStats + Micrometer 监控指标</li>
 *   <li>问题A：L2 命中计数器在循环内每次成功解析后增加</li>
 *   <li>问题B：batchPutToL2 使用 Pipeline 批量设置 TTL</li>
 *   <li>问题C：统一处理 NULL_MARKER 和 PARSE_FAILED_MARKER</li>
 * </ul>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public abstract class AbstractGenericCacheManager<K, V> implements GenericCacheManager<K, V> {

    private static final Logger log = LoggerFactory.getLogger(AbstractGenericCacheManager.class);

    // 问题3：空值标记，防止缓存穿透
    protected static final String NULL_MARKER = "__NULL__";

    // 问题11：JSON 解析失败标记
    protected static final String PARSE_FAILED_MARKER = "__PARSE_FAILED__";

    // 问题8：键最大长度限制
    private static final int MAX_KEY_LENGTH = 500;

    // 问题8：非法字符模式（控制字符和特殊字符）
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");

    protected final StringRedisTemplate redisTemplate;
    protected final ObjectMapper objectMapper;
    protected final MeterRegistry meterRegistry;

    protected Cache<String, CacheEntry> l1Cache;
    protected JavaType valueType;

    // 问题12：监控指标
    protected Counter l1HitCounter;
    protected Counter l1MissCounter;
    protected Counter l2HitCounter;
    protected Counter l2MissCounter;
    protected Counter l2ErrorCounter;
    protected Timer l2ReadTimer;
    protected Timer l2WriteTimer;

    /**
     * 缓存条目，包装实际值和空值标记
     * <p>
     * 用于区分缓存中的实际值和空值缓存。
     * </p>
     */
    protected record CacheEntry(Object value, boolean isNull) {
        /**
         * 创建空值缓存条目
         *
         * @return 空值缓存条目实例
         */
        public static CacheEntry ofNull() {
            return new CacheEntry(null, true);
        }

        /**
         * 创建实际值缓存条目
         *
         * @param value 实际缓存值
         * @return 实际值缓存条目实例
         */
        public static CacheEntry of(Object value) {
            return new CacheEntry(value, false);
        }
    }

    /**
     * 构造通用缓存管理器
     * <p>
     * 注入Redis模板、JSON序列化器和指标注册器。
     * 子类需要调用此构造函数并实现必要的抽象方法。
     * </p>
     *
     * @param redisTemplate  Redis字符串操作模板
     * @param objectMapper   JSON序列化工具
     * @param meterRegistry  Micrometer指标注册器
     */
    protected AbstractGenericCacheManager(StringRedisTemplate redisTemplate,
                                          ObjectMapper objectMapper,
                                          MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 初始化缓存管理器
     * <p>
     * 在Bean构造完成后执行初始化：
     * 1. 构建值类型信息用于JSON反序列化
     * 2. 创建L1 Caffeine缓存实例
     * 3. 注册Micrometer监控指标
     * </p>
     */
    @PostConstruct
    public void init() {
        // 问题2：保存值类型信息用于反序列化
        this.valueType = objectMapper.constructType(getValueClass());

        // 问题9：使用抽象方法获取配置
        this.l1Cache = Caffeine.newBuilder()
            .maximumSize(getL1MaximumSize())
            .expireAfterWrite(getL1TtlMinutes(), TimeUnit.MINUTES)
            .recordStats() // 问题12：开启统计
            .build();

        // 问题12：注册 Micrometer 监控指标
        initMetrics();
    }

    /**
     * 初始化监控指标
     * 问题12：recordStats + Micrometer
     */
    protected void initMetrics() {
        if (meterRegistry == null) {
            return;
        }

        String namespace = getNamespace();

        // L1 缓存命中率
        this.l1HitCounter = Counter.builder("cache.l1.hits")
            .tag("namespace", namespace)
            .description("L1 cache hits")
            .register(meterRegistry);

        this.l1MissCounter = Counter.builder("cache.l1.misses")
            .tag("namespace", namespace)
            .description("L1 cache misses")
            .register(meterRegistry);

        // L2 缓存命中率
        this.l2HitCounter = Counter.builder("cache.l2.hits")
            .tag("namespace", namespace)
            .description("L2 cache hits")
            .register(meterRegistry);

        this.l2MissCounter = Counter.builder("cache.l2.misses")
            .tag("namespace", namespace)
            .description("L2 cache misses")
            .register(meterRegistry);

        // L2 错误计数
        this.l2ErrorCounter = Counter.builder("cache.l2.errors")
            .tag("namespace", namespace)
            .description("L2 cache errors")
            .register(meterRegistry);

        // L2 读写延迟
        this.l2ReadTimer = Timer.builder("cache.l2.read.duration")
            .tag("namespace", namespace)
            .description("L2 cache read duration")
            .register(meterRegistry);

        this.l2WriteTimer = Timer.builder("cache.l2.write.duration")
            .tag("namespace", namespace)
            .description("L2 cache write duration")
            .register(meterRegistry);

        // L1 缓存大小 Gauge
        Gauge.builder("cache.l1.size", l1Cache, cache -> cache.estimatedSize())
            .tag("namespace", namespace)
            .description("L1 cache estimated size")
            .register(meterRegistry);

        // L1 Caffeine 统计 Gauge
        Gauge.builder("cache.l1.stats.hit_rate", l1Cache, cache -> cache.stats().hitRate())
            .tag("namespace", namespace)
            .description("L1 cache hit rate")
            .register(meterRegistry);

        Gauge.builder("cache.l1.stats.eviction_count", l1Cache, cache -> cache.stats().evictionCount())
            .tag("namespace", namespace)
            .description("L1 cache eviction count")
            .register(meterRegistry);
    }

    // ==================== 键验证 ====================

    /**
     * 问题8：验证和清理缓存键
     *
     * @param key 原始键
     * @return 清理后的键
     */
    protected String validateAndCleanKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Cache key cannot be null or empty");
        }

        // 移除控制字符
        String cleaned = ILLEGAL_CHAR_PATTERN.matcher(key).replaceAll("");

        // 长度限制
        if (cleaned.length() > MAX_KEY_LENGTH) {
            // 使用哈希后缀来缩短键
            String hash = Integer.toHexString(cleaned.hashCode());
            cleaned = cleaned.substring(0, MAX_KEY_LENGTH - hash.length() - 1) + ":" + hash;
        }

        return cleaned;
    }

    /**
     * 将业务键转换为字符串
     */
    protected abstract String keyToString(K key);

    // ==================== 读取操作实现 ====================

    @Override
    public V get(Long tenantId, K key, BiFunction<Long, K, V> loader) {
        String fullKey = buildCacheKey(tenantId, key);

        // 1. 查询 L1 缓存
        CacheEntry entry = l1Cache.getIfPresent(fullKey);
        if (entry != null) {
            incrementCounter(l1HitCounter);
            if (entry.isNull()) {
                return null; // 问题3：空值缓存命中
            }
            @SuppressWarnings("unchecked")
            V value = (V) entry.value();
            return value;
        }

        incrementCounter(l1MissCounter);

        // 2. 查询 L2 缓存
        V l2Value = getFromL2(fullKey);
        if (l2Value != null) {
            // L2 命中，写入 L1
            l1Cache.put(fullKey, CacheEntry.of(l2Value));
            return l2Value;
        }

        // 3. 检查空值标记
        String nullMarker = getNullMarkerFromL2(fullKey);
        if (nullMarker != null) {
            // 空值缓存命中
            incrementCounter(l2HitCounter);
            l1Cache.put(fullKey, CacheEntry.ofNull());
            return null;
        }

        incrementCounter(l2MissCounter);

        // 4. 使用 loader 加载数据
        V value = loader.apply(tenantId, key);

        // 5. 写入缓存
        if (value != null) {
            put(tenantId, key, value);
        } else {
            // 问题3：缓存空值，使用较短 TTL
            putNullMarker(tenantId, key);
        }

        return value;
    }

    @Override
    public Map<K, V> getBatch(Long tenantId, Set<K> keys,
                              BiFunction<Long, Set<K>, Map<K, V>> loader) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<K, V> result = new HashMap<>();
        Set<K> missedKeys = new HashSet<>();

        // 1. 批量查询 L1
        for (K key : keys) {
            String fullKey = buildCacheKey(tenantId, key);
            CacheEntry entry = l1Cache.getIfPresent(fullKey);
            if (entry != null) {
                incrementCounter(l1HitCounter);
                if (!entry.isNull()) {
                    @SuppressWarnings("unchecked")
                    V value = (V) entry.value();
                    result.put(key, value);
                }
                // 空值不加入结果
            } else {
                incrementCounter(l1MissCounter);
                missedKeys.add(key);
            }
        }

        if (missedKeys.isEmpty()) {
            return result;
        }

        // 2. 批量查询 L2
        Map<K, V> l2Results = batchGetFromL2(tenantId, missedKeys);
        result.putAll(l2Results);

        // 更新 L1 缓存
        for (Map.Entry<K, V> e : l2Results.entrySet()) {
            String fullKey = buildCacheKey(tenantId, e.getKey());
            l1Cache.put(fullKey, CacheEntry.of(e.getValue()));
        }

        // 3. 找出仍然缺失的键
        Set<K> stillMissed = new HashSet<>(missedKeys);
        stillMissed.removeAll(l2Results.keySet());

        // 检查空值标记
        Set<K> nullMarkedKeys = batchCheckNullMarkers(tenantId, stillMissed);
        for (K key : nullMarkedKeys) {
            String fullKey = buildCacheKey(tenantId, key);
            l1Cache.put(fullKey, CacheEntry.ofNull());
        }
        stillMissed.removeAll(nullMarkedKeys);

        if (stillMissed.isEmpty()) {
            return result;
        }

        // 4. 使用 loader 加载缺失数据
        Map<K, V> loadedValues = loader.apply(tenantId, stillMissed);
        if (loadedValues != null) {
            result.putAll(loadedValues);

            // 5. 批量写入缓存
            for (Map.Entry<K, V> e : loadedValues.entrySet()) {
                if (e.getValue() != null) {
                    put(tenantId, e.getKey(), e.getValue());
                } else {
                    putNullMarker(tenantId, e.getKey());
                }
            }
        }

        return result;
    }

    @Override
    public V getOnly(Long tenantId, K key) {
        String fullKey = buildCacheKey(tenantId, key);

        // 查询 L1
        CacheEntry entry = l1Cache.getIfPresent(fullKey);
        if (entry != null) {
            if (entry.isNull()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            V value = (V) entry.value();
            return value;
        }

        // 查询 L2
        return getFromL2(fullKey);
    }

    // ==================== 写入操作实现 ====================

    @Override
    public void put(Long tenantId, K key, V value) {
        String fullKey = buildCacheKey(tenantId, key);

        // 问题5：写入顺序先 L2 后 L1
        putToL2(fullKey, value);

        // 写入 L1
        l1Cache.put(fullKey, CacheEntry.of(value));
    }

    @Override
    public void putBatch(Long tenantId, Map<K, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        // 问题5：先写入 L2
        batchPutToL2(tenantId, data);

        // 写入 L1
        for (Map.Entry<K, V> e : data.entrySet()) {
            String fullKey = buildCacheKey(tenantId, e.getKey());
            l1Cache.put(fullKey, CacheEntry.of(e.getValue()));
        }
    }

    // ==================== 失效操作实现 ====================

    @Override
    public void evict(Long tenantId, K key) {
        String fullKey = buildCacheKey(tenantId, key);

        // TODO: Redis 操作竞态条件风险
        // 问题：当前 L2 delete + L1 invalidate 操作不具备原子性
        // 建议：在高一致性场景下考虑使用分布式锁或延迟双删策略
        // 优先级：P2（性能优化，可关注但不强制整改）
        // 先失效 L2，再失效 L1
        deleteFromL2(fullKey);
        l1Cache.invalidate(fullKey);
    }

    @Override
    public void evictBatch(Long tenantId, Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<String> fullKeys = new ArrayList<>();
        for (K key : keys) {
            fullKeys.add(buildCacheKey(tenantId, key));
        }

        // 先失效 L2，再失效 L1
        batchDeleteFromL2(fullKeys);

        for (String fullKey : fullKeys) {
            l1Cache.invalidate(fullKey);
        }
    }

    @Override
    public void evictAll(Long tenantId) {
        // 问题1：使用 SCAN 分批删除，避免 KEYS 阻塞
        String pattern = getNamespace() + ":" + tenantId + ":*";

        try {
            ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100) // 每次扫描 100 个键
                .build();

            List<String> keysToDelete = new ArrayList<>();

            try (var cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());

                    // 批量删除，每 100 个一批
                    if (keysToDelete.size() >= 100) {
                        deleteKeysBatch(keysToDelete);
                        keysToDelete.clear();
                    }
                }
            }

            // 删除剩余的键
            if (!keysToDelete.isEmpty()) {
                deleteKeysBatch(keysToDelete);
            }

            log.info("Evicted all cache for namespace={}, tenantId={}", getNamespace(), tenantId);
        } catch (Exception e) {
            log.error("Failed to evict all cache for namespace={}, tenantId={}",
                getNamespace(), tenantId, e);
            incrementCounter(l2ErrorCounter);
        }

        // 清空 L1 中匹配的键
        evictAllL1(tenantId);
    }

    /**
     * 清空 L1 中匹配租户的所有键
     */
    protected void evictAllL1(Long tenantId) {
        String prefix = getNamespace() + ":" + tenantId + ":";
        l1Cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ==================== L2 操作（带异常处理） ====================

    /**
     * 问题4：从 L2 读取，带异常处理
     */
    protected V getFromL2(String fullKey) {
        try {
            long start = System.nanoTime();
            String json = redisTemplate.opsForValue().get(fullKey);
            long duration = System.nanoTime() - start;

            if (l2ReadTimer != null) {
                l2ReadTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            if (json == null || json.isEmpty()) {
                return null;
            }

            // 检查空值标记
            if (NULL_MARKER.equals(json) || PARSE_FAILED_MARKER.equals(json)) {
                return null;
            }

            incrementCounter(l2HitCounter);

            try {
                return objectMapper.readValue(json, valueType);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize L2 cache value for key={}, error={}",
                    fullKey, e.getMessage());
                // 问题11：缓存解析失败标记，避免重复解析
                redisTemplate.opsForValue().set(fullKey, PARSE_FAILED_MARKER,
                    Duration.ofMinutes(getL2TtlMinutes() / 2)); // 使用较短 TTL
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get from L2 cache, key={}", fullKey, e);
            incrementCounter(l2ErrorCounter);
            return null;
        }
    }

    /**
     * 批量从 L2 读取
     */
    protected Map<K, V> batchGetFromL2(Long tenantId, Set<K> keys) {
        Map<K, V> result = new HashMap<>();

        try {
            List<String> fullKeys = new ArrayList<>();
            Map<String, K> keyMapping = new HashMap<>();

            for (K key : keys) {
                String fullKey = buildCacheKey(tenantId, key);
                fullKeys.add(fullKey);
                keyMapping.put(fullKey, key);
            }

            long start = System.nanoTime();
            List<String> values = redisTemplate.opsForValue().multiGet(fullKeys);
            long duration = System.nanoTime() - start;

            if (l2ReadTimer != null) {
                l2ReadTimer.record(duration, TimeUnit.NANOSECONDS);
            }

            if (values != null) {
                for (int i = 0; i < fullKeys.size(); i++) {
                    String json = values.get(i);
                    if (json != null && !json.isEmpty()
                        && !NULL_MARKER.equals(json) && !PARSE_FAILED_MARKER.equals(json)) {
                        try {
                            V value = objectMapper.readValue(json, valueType);
                            K key = keyMapping.get(fullKeys.get(i));
                            result.put(key, value);
                            // 问题A修复：在每次成功解析后增加计数器，统计实际命中数
                            incrementCounter(l2HitCounter);
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to deserialize L2 cache value for key={}",
                                fullKeys.get(i), e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to batch get from L2 cache", e);
            incrementCounter(l2ErrorCounter);
        }

        return result;
    }

    /**
     * 问题4：写入 L2，带异常处理
     */
    protected void putToL2(String fullKey, V value) {
        try {
            String json = objectMapper.writeValueAsString(value);

            long start = System.nanoTime();
            redisTemplate.opsForValue().set(fullKey, json,
                Duration.ofMinutes(getL2TtlMinutes()));
            long duration = System.nanoTime() - start;

            if (l2WriteTimer != null) {
                l2WriteTimer.record(duration, TimeUnit.NANOSECONDS);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize value for L2 cache, key={}", fullKey, e);
        } catch (Exception e) {
            log.error("Failed to put to L2 cache, key={}", fullKey, e);
            incrementCounter(l2ErrorCounter);
        }
    }

    /**
     * 批量写入 L2
     */
    protected void batchPutToL2(Long tenantId, Map<K, V> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        try {
            Map<String, String> pipeline = new HashMap<>();
            for (Map.Entry<K, V> e : data.entrySet()) {
                String fullKey = buildCacheKey(tenantId, e.getKey());
                try {
                    String json = objectMapper.writeValueAsString(e.getValue());
                    pipeline.put(fullKey, json);
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to serialize value for key={}", fullKey, ex);
                }
            }

            if (!pipeline.isEmpty()) {
                long start = System.nanoTime();

                // 问题B修复：使用 Pipeline 批量设置 TTL，避免 N 次额外的 EXPIRE 命令
                long ttlSeconds = getL2TtlMinutes() * 60;
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (Map.Entry<String, String> entry : pipeline.entrySet()) {
                        byte[] keyBytes = entry.getKey().getBytes();
                        byte[] valueBytes = entry.getValue().getBytes();
                        connection.setEx(keyBytes, ttlSeconds, valueBytes);
                    }
                    return null;
                });

                long duration = System.nanoTime() - start;

                if (l2WriteTimer != null) {
                    l2WriteTimer.record(duration, TimeUnit.NANOSECONDS);
                }
            }

        } catch (Exception e) {
            log.error("Failed to batch put to L2 cache", e);
            incrementCounter(l2ErrorCounter);
        }
    }

    /**
     * 从 L2 删除
     */
    protected void deleteFromL2(String fullKey) {
        try {
            redisTemplate.delete(fullKey);
        } catch (Exception e) {
            log.error("Failed to delete from L2 cache, key={}", fullKey, e);
            incrementCounter(l2ErrorCounter);
        }
    }

    /**
     * 批量从 L2 删除
     */
    protected void batchDeleteFromL2(List<String> fullKeys) {
        if (fullKeys == null || fullKeys.isEmpty()) {
            return;
        }

        try {
            redisTemplate.delete(fullKeys);
        } catch (Exception e) {
            log.error("Failed to batch delete from L2 cache", e);
            incrementCounter(l2ErrorCounter);
        }
    }

    /**
     * 分批删除键
     */
    protected void deleteKeysBatch(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }

        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Failed to delete keys batch from L2 cache", e);
            incrementCounter(l2ErrorCounter);
        }
    }

    // ==================== 空值处理 ====================

    /**
     * 问题3：缓存空值标记
     */
    protected void putNullMarker(Long tenantId, K key) {
        String fullKey = buildCacheKey(tenantId, key);

        try {
            // 空值使用较短的 TTL（正常 TTL 的一半，最小 1 分钟）
            int nullTtl = Math.max(1, getL2TtlMinutes() / 2);
            redisTemplate.opsForValue().set(fullKey, NULL_MARKER,
                Duration.ofMinutes(nullTtl));

            // 同时在 L1 缓存空值
            l1Cache.put(fullKey, CacheEntry.ofNull());

        } catch (Exception e) {
            log.error("Failed to put null marker to L2 cache, key={}", fullKey, e);
            incrementCounter(l2ErrorCounter);
        }
    }

    /**
     * 获取空值标记或解析失败标记
     * 问题C修复：统一处理 NULL_MARKER 和 PARSE_FAILED_MARKER
     */
    protected String getNullMarkerFromL2(String fullKey) {
        try {
            String value = redisTemplate.opsForValue().get(fullKey);
            if (NULL_MARKER.equals(value) || PARSE_FAILED_MARKER.equals(value)) {
                incrementCounter(l2HitCounter);
                return value;
            }
        } catch (Exception e) {
            log.error("Failed to get null marker from L2 cache, key={}", fullKey, e);
            incrementCounter(l2ErrorCounter);
        }
        return null;
    }

    /**
     * 批量检查空值标记
     */
    protected Set<K> batchCheckNullMarkers(Long tenantId, Set<K> keys) {
        Set<K> nullMarkedKeys = new HashSet<>();

        try {
            List<String> fullKeys = new ArrayList<>();
            Map<String, K> keyMapping = new HashMap<>();

            for (K key : keys) {
                String fullKey = buildCacheKey(tenantId, key);
                fullKeys.add(fullKey);
                keyMapping.put(fullKey, key);
            }

            List<String> values = redisTemplate.opsForValue().multiGet(fullKeys);

            if (values != null) {
                for (int i = 0; i < values.size(); i++) {
                    String value = values.get(i);
                    if (NULL_MARKER.equals(value) || PARSE_FAILED_MARKER.equals(value)) {
                        K key = keyMapping.get(fullKeys.get(i));
                        nullMarkedKeys.add(key);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to batch check null markers from L2 cache", e);
            incrementCounter(l2ErrorCounter);
        }

        return nullMarkedKeys;
    }

    // ==================== 辅助方法 ====================

    @Override
    public String buildCacheKey(Long tenantId, K key) {
        String keyStr = keyToString(key);
        String cleanedKey = validateAndCleanKey(keyStr);
        return getNamespace() + ":" + tenantId + ":" + cleanedKey;
    }

    /**
     * 增加计数器
     */
    protected void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * 获取 L1 缓存统计信息
     */
    public CacheStats getL1Stats() {
        return l1Cache.stats();
    }
}