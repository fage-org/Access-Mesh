package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import cn.ac.fage.accessmesh.common.cache.spi.LocalCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单次有效 TTL 与读取令牌剩余 TTL 语义测试（T-ACCESS-008）。
 * <p>
 * 使用可控单调时钟 + 捕获 TTL 的 fake store，验证：
 * put(effectiveTtl) 钳制 catalog TTL 上限、剩余 ≤0 不写；
 * beginRead 令牌回填只写「读取起点 + catalog TTL」剩余时间，批量共享同一起点不重置。
 * </p>
 */
class CacheEffectiveTtlTest {

    private static final Long TENANT_ID = 1L;

    /** 捕获写入 TTL 的 fake 分布式存储 */
    private static class CapturingL2Store implements DistributedCacheStore {
        final Map<String, Object> values = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();
        boolean failReads;

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
            if (failReads) {
                // 与真实 store 一致：读异常吞掉返回 null（业务绕过缓存查 DB）
                return null;
            }
            return (V) values.get(fullKey);
        }

        @Override
        public <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
            Map<String, V> result = new HashMap<>();
            for (String key : fullKeys) {
                V v = get(catalog, key);
                if (v != null) {
                    result.put(key, v);
                }
            }
            return result;
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value) {
            put(catalog, fullKey, value, null);
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl) {
            Duration catalogTtl = catalog.getL2Ttl();
            Duration ttl = effectiveTtl == null ? catalogTtl
                : effectiveTtl.compareTo(catalogTtl) > 0 ? catalogTtl : effectiveTtl;
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                return;
            }
            values.put(fullKey, value);
            ttls.put(fullKey, ttl);
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            putBatch(catalog, data, null);
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl) {
            data.forEach((k, v) -> put(catalog, k, v, effectiveTtl));
        }

        @Override
        public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
            values.remove(fullKey);
            ttls.remove(fullKey);
        }

        @Override
        public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
            values.clear();
            ttls.clear();
        }
    }

    private final AtomicLong clock = new AtomicLong();
    private CapturingL2Store l2Store;
    private CacheProperties properties;
    private DefaultCacheService cacheService;

    /** catalog：L2_ONLY，TTL 10s（快照链路上限） */
    private final CacheCatalogEntry<java.util.Set<Long>> catalog =
        CacheCatalogEntry.<java.util.Set<Long>>builder()
            .code("perm:effective-roles")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<java.util.Set<Long>>() {})
            .build();

    @BeforeEach
    void setUp() {
        clock.set(0);
        l2Store = new CapturingL2Store();
        properties = new CacheProperties();
        cacheService = new DefaultCacheService(null, l2Store, null, properties, null, clock::get);
    }

    private void advanceMs(long millis) {
        clock.addAndGet(millis * 1_000_000L);
    }

    private String fullKey(Object identifier) {
        return CacheKeyUtil.build(TENANT_ID, catalog.getCode(), identifier);
    }

    @Test
    void putWithEffectiveTtl_shouldClampToCatalogTtl() {
        cacheService.put(catalog, TENANT_ID, 1L, Set.of(1L), Duration.ofSeconds(999));

        assertThat(l2Store.values).containsKey(fullKey(1L));
        assertThat(l2Store.ttls.get(fullKey(1L))).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void putWithEffectiveTtl_shouldNotWriteWhenBudgetExhausted() {
        cacheService.put(catalog, TENANT_ID, 1L, Set.of(1L), Duration.ZERO);
        cacheService.put(catalog, TENANT_ID, 2L, Set.of(2L), Duration.ofSeconds(-1));

        assertThat(l2Store.values).doesNotContainKey(fullKey(1L));
        assertThat(l2Store.values).doesNotContainKey(fullKey(2L));
    }

    @Test
    void putWithToken_shouldWriteRemainingTtlFromReadStart() {
        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(3_000); // DB 读取耗时 3s
        cacheService.put(token, TENANT_ID, 1L, Set.of(1L));

        // 剩余 = 10s - 3s = 7s
        assertThat(l2Store.ttls.get(fullKey(1L))).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void putWithToken_shouldNotWriteWhenBudgetExhausted() {
        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(10_500); // 超过预算
        cacheService.put(token, TENANT_ID, 1L, Set.of(1L));

        assertThat(l2Store.values).doesNotContainKey(fullKey(1L));
    }

    @Test
    void putBatchWithToken_shouldShareSingleReadStart() {
        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(4_000);
        Map<Long, Set<Long>> data = Map.of(1L, Set.of(1L), 2L, Set.of(2L), 3L, Set.of(3L));
        cacheService.putBatch(token, TENANT_ID, data);

        // 批量全部条目共享同一读取起点（剩余 6s），不得重置为完整 10s
        assertThat(l2Store.ttls.get(fullKey(1L))).isEqualTo(Duration.ofSeconds(6));
        assertThat(l2Store.ttls.get(fullKey(2L))).isEqualTo(Duration.ofSeconds(6));
        assertThat(l2Store.ttls.get(fullKey(3L))).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void putBatchWithToken_shouldNotWriteWhenBudgetExhausted() {
        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(11_000);
        cacheService.putBatch(token, TENANT_ID, Map.of(1L, Set.of(1L)));

        assertThat(l2Store.values).isEmpty();
    }

    @Test
    void putWithToken_shouldRespectYamlOverrideBudget() {
        CacheProperties.CatalogOverride override = new CacheProperties.CatalogOverride();
        override.setL2Ttl(Duration.ofSeconds(5));
        properties.getCatalogs().put(catalog.getCode(), override);

        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(2_000);
        cacheService.put(token, TENANT_ID, 1L, Set.of(1L));

        // 有效预算取 YAML 覆盖 5s：剩余 3s
        assertThat(l2Store.ttls.get(fullKey(1L))).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void readFailure_shouldBypassCacheToDbThenBackfillStillBounded() {
        // 权限缓存不可用时：get 异常由 store 吞掉返回 null（绕过缓存查 DB 的前提），
        // 回填仍受读取起点预算约束
        l2Store.failReads = true;
        assertThat(cacheService.get(catalog, TENANT_ID, 1L)).isNull();
        l2Store.failReads = false;

        CacheReadToken<Set<Long>> token = cacheService.beginRead(catalog);
        advanceMs(9_500);
        cacheService.put(token, TENANT_ID, 1L, Set.of(1L));

        assertThat(l2Store.ttls.get(fullKey(1L))).isEqualTo(Duration.ofMillis(500));
    }
}
