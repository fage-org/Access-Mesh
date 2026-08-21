package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redisson RBucket 秒级精度 TTL 测试（T-ACCESS-008：新增 10 秒与 15 秒单条及批量写入 TTL 验证）。
 * <p>
 * 通过 mock RBucket/RBatch 捕获 set(json, Duration) 参数，验证：
 * 10s/15s 目录单条/批量写入使用精确 Duration TTL；单次有效 TTL 钳制 catalog 上限；
 * 剩余 ≤0 不写。真实过期行为由 Caffeine 侧端到端用例与 access-service 集成测试覆盖。
 * </p>
 */
class RedissonSecondsPrecisionTest {

    private static final Long TENANT_ID = 1L;

    private RedissonClient redissonClient;
    private RBucket<String> bucket;
    private RBatch batch;
    private RBucketAsync<String> batchBucket;
    private RedissonBucketStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        batch = mock(RBatch.class);
        batchBucket = mock(RBucketAsync.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.createBatch()).thenReturn(batch);
        when(batch.<String>getBucket(anyString())).thenReturn(batchBucket);
        store = new RedissonBucketStore(redissonClient, new ObjectMapper(),
            new SimpleMeterRegistry(), new CacheProperties());
    }

    private CacheCatalogEntry<String> catalog(Duration ttl) {
        return CacheCatalogEntry.<String>builder()
            .code("test:l2only:" + ttl.toMillis())
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(ttl)
            .valueType(new TypeRef<String>() {})
            .build();
    }

    @Test
    void singlePut_with10sTtl_shouldSetExactDuration() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        store.put(catalog, fullKey(catalog, "a"), "v");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(bucket).set(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void singlePut_with15sTtl_shouldSetExactDuration() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(15));
        store.put(catalog, fullKey(catalog, "a"), "v");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(bucket).set(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void batchPut_with10sTtl_shouldSetExactDuration() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        Map<String, String> data = Map.of(
            fullKey(catalog, "a"), "v1",
            fullKey(catalog, "b"), "v2");
        store.putBatch(catalog, data);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(batchBucket, org.mockito.Mockito.times(2)).setAsync(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getAllValues()).containsOnly(Duration.ofSeconds(10));
    }

    @Test
    void batchPut_with15sTtl_shouldSetExactDuration() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(15));
        Map<String, String> data = Map.of(fullKey(catalog, "a"), "v1");
        store.putBatch(catalog, data);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(batchBucket).setAsync(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void put_withEffectiveTtl_shouldClampToCatalogTtl() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        store.put(catalog, fullKey(catalog, "a"), "v", Duration.ofSeconds(999));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(bucket).set(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void put_withEffectiveTtl_shouldUseShorterRemaining() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        store.put(catalog, fullKey(catalog, "a"), "v", Duration.ofMillis(500));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(bucket).set(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void put_withExhaustedBudget_shouldNotWrite() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        String key = fullKey(catalog, "a");
        store.put(catalog, key, "v", Duration.ZERO);
        store.put(catalog, key, "v", Duration.ofSeconds(-1));
        Map<String, String> data = new HashMap<>();
        data.put(key, "v");
        store.putBatch(catalog, data, Duration.ofMillis(-100));

        verify(bucket, never()).set(anyString(), any(Duration.class));
        verify(batchBucket, never()).setAsync(anyString(), any(Duration.class));
    }

    @Test
    void putBatch_withEffectiveTtl_shouldClampEachEntry() {
        CacheCatalogEntry<String> catalog = catalog(Duration.ofSeconds(10));
        Map<String, String> data = Map.of(fullKey(catalog, "a"), "v1");
        store.putBatch(catalog, data, Duration.ofMinutes(5));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(batchBucket).setAsync(anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(10));
    }

    private String fullKey(CacheCatalogEntry<String> catalog, Object id) {
        return CacheKeyUtil.build(TENANT_ID, catalog.getCode(), id);
    }
}
