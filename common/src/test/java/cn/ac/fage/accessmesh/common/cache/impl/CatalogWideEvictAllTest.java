package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * catalog 级跨租户全量失效测试（T-ACCESS-008 复评 P2-1：订阅重连全量清空的底层能力）。
 * <p>
 * L1_ONLY：本地按 catalog 前缀跨租户清空；L2_ONLY：SCAN "*:code:*" 模式删除。
 * </p>
 */
class CatalogWideEvictAllTest {

    private final CacheCatalogEntry<String> catalog =
        CacheCatalogEntry.<String>builder()
            .code("test:catalog-wide")
            .mode(CacheMode.L1_ONLY)
            .l1Ttl(Duration.ofMinutes(1))
            .l1MaxSize(100)
            .valueType(new TypeRef<String>() {})
            .build();

    private CaffeineLocalCacheStore l1Store;

    @BeforeEach
    void setUp() {
        l1Store = new CaffeineLocalCacheStore(new ObjectMapper(), null, new CacheProperties());
    }

    @Test
    void caffeineStore_evictAllCatalog_shouldClearAllTenantsButKeepOtherCatalogs() {
        CacheCatalogEntry<String> otherCatalog =
            CacheCatalogEntry.<String>builder()
                .code("test:other")
                .mode(CacheMode.L1_ONLY)
                .l1Ttl(Duration.ofMinutes(1))
                .valueType(new TypeRef<String>() {})
                .build();

        l1Store.put(catalog, "1:test:catalog-wide:a", "v1");
        l1Store.put(catalog, "2:test:catalog-wide:b", "v2");
        l1Store.put(otherCatalog, "1:test:other:c", "keep");
        // 碰撞键：其他目录的 identifier 内嵌目标目录编码——不得被误清（复评 P2）
        l1Store.put(otherCatalog, "1:test:other:x:test:catalog-wide:y", "collision");

        l1Store.evictAll(catalog);

        assertThat(l1Store.get(catalog, "1:test:catalog-wide:a")).isNull();
        assertThat(l1Store.get(catalog, "2:test:catalog-wide:b")).isNull();
        // 其他目录不受影响（含 identifier 内嵌目标编码的碰撞键）
        assertThat(l1Store.get(otherCatalog, "1:test:other:c")).isEqualTo("keep");
        assertThat(l1Store.get(otherCatalog, "1:test:other:x:test:catalog-wide:y")).isEqualTo("collision");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redissonStore_evictAllCatalog_shouldScanCatalogPatternAndDelete() {
        RedissonClient redisson = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        when(redisson.getKeys()).thenReturn(keys);
        // SCAN 宽松模式同时返回碰撞键（其他目录 identifier 内嵌目标编码）
        when(keys.getKeysByPattern(anyString(), any(Integer.class)))
            .thenReturn(List.of("1:test:catalog-wide:a", "2:test:catalog-wide:b",
                "1:test:other:x:test:catalog-wide:y"));

        CacheCatalogEntry<String> l2Catalog =
            CacheCatalogEntry.<String>builder()
                .code("test:catalog-wide")
                .mode(CacheMode.L2_ONLY)
                .l2Ttl(Duration.ofMinutes(1))
                .valueType(new TypeRef<String>() {})
                .build();
        RedissonBucketStore store = new RedissonBucketStore(redisson, new ObjectMapper(),
            new SimpleMeterRegistry(), new CacheProperties());

        store.evictAll(l2Catalog);

        // SCAN 使用 catalog 级模式（跨租户）
        verify(keys).getKeysByPattern(eq("*:test:catalog-wide:*"), any(Integer.class));
        ArgumentCaptor<String[]> deleted = ArgumentCaptor.forClass(String[].class);
        verify(keys).delete(deleted.capture());
        // 碰撞键经完整键结构精确过滤后不得被删除（复评 P2）
        assertThat(deleted.getValue()).containsExactlyInAnyOrder(
            "1:test:catalog-wide:a", "2:test:catalog-wide:b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redissonStore_evictAllCatalogFailure_shouldCountInvalidateFailureMetric() {
        RedissonClient redisson = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        when(redisson.getKeys()).thenReturn(keys);
        when(keys.getKeysByPattern(anyString(), any(Integer.class)))
            .thenThrow(new IllegalStateException("redis down"));
        doAnswer(inv -> null).when(keys).delete(any(String[].class));

        CacheCatalogEntry<String> l2Catalog =
            CacheCatalogEntry.<String>builder()
                .code("test:catalog-wide")
                .mode(CacheMode.L2_ONLY)
                .l2Ttl(Duration.ofMinutes(1))
                .valueType(new TypeRef<String>() {})
                .build();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedissonBucketStore store = new RedissonBucketStore(redisson, new ObjectMapper(),
            meterRegistry, new CacheProperties());

        store.evictAll(l2Catalog);

        assertThat(meterRegistry.counter("cache.invalidate.failures",
            "type", "evict", "catalog", "test:catalog-wide").count()).isEqualTo(1.0);
    }
}
