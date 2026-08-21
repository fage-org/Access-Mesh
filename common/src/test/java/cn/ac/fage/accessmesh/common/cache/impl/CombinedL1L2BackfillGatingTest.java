package cn.ac.fage.accessmesh.common.cache.impl;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheKeyUtil;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L2 命中回填 L1 的剩余寿命门控回归测试（T-ACCESS-008 复评 P2-3 修复锁定）。
 * <p>
 * 单次有效 TTL 短于 catalog L1 TTL 时 put 会跳过 L1；L2 命中回填必须同样受控——
 * 仅当条目剩余存活时间（remainTimeToLive）≥ catalog L1 TTL 才回填，
 * 否则短命条目会被放大到完整 L1 TTL 绕过预算上限。TTL 查询异常时跳过回填但正常返回值。
 * </p>
 */
class CombinedL1L2BackfillGatingTest {

    private static final Long TENANT_ID = 1L;

    private RedissonClient redissonClient;
    private RBucket<String> bucket;
    private RBatch batch;
    private CombinedL1L2Store store;

    /** L2 值（JSON） */
    private String l2Json;
    /** remainTimeToLive 返回值（可变，测试控制） */
    private Long remainTimeToLive = -1L;

    /** 批量路径 per-key 的 getAsync 调用次数（观察 L1 是否命中） */
    private final Map<String, Integer> batchGetCalls = new ConcurrentHashMap<>();

    /** L1_L2 目录：L1 10s / L2 120s */
    private final CacheCatalogEntry<String> catalog =
        CacheCatalogEntry.<String>builder()
            .code("test:l1l2:backfill")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofSeconds(10))
            .l1MaxSize(100)
            .l2Ttl(Duration.ofMinutes(2))
            .valueType(new TypeRef<String>() {})
            .build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        l2Json = new ObjectMapper().writeValueAsString("v1");
        redissonClient = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        batch = mock(RBatch.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.createBatch()).thenReturn(batch);

        when(bucket.get()).thenReturn(l2Json);
        when(bucket.remainTimeToLive()).thenAnswer(inv -> remainTimeToLive);

        // 批量：getAsync 计数并返回值；remainTimeToLiveAsync 返回可控值
        when(batch.<String>getBucket(anyString())).thenAnswer(bucketInv -> {
            String key = bucketInv.getArgument(0);
            RBucketAsync<String> rb = mock(RBucketAsync.class);
            when(rb.getAsync()).thenAnswer(getInv -> {
                batchGetCalls.merge(key, 1, Integer::sum);
                RFuture<String> rf = mock(RFuture.class);
                when(rf.toCompletableFuture())
                    .thenReturn(CompletableFuture.completedFuture(l2Json));
                return rf;
            });
            when(rb.remainTimeToLiveAsync()).thenAnswer(ttlInv -> {
                RFuture<Long> rf = mock(RFuture.class);
                when(rf.toCompletableFuture())
                    .thenReturn(CompletableFuture.completedFuture(remainTimeToLive));
                return rf;
            });
            return rb;
        });
        when(batch.execute()).thenReturn(null);

        store = new CombinedL1L2Store(redissonClient, new ObjectMapper(),
            new SimpleMeterRegistry(), new CacheProperties());
    }

    private String key(Object id) {
        return CacheKeyUtil.build(TENANT_ID, catalog.getCode(), id);
    }

    @Test
    void singleGet_withShortRemainingTtl_shouldNotBackfillL1() {
        remainTimeToLive = 999L; // 剩余 0.999s < L1 catalog 10s

        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        // 未回填 L1：第二次读取仍打到 L2
        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        verify(bucket, times(2)).get();
    }

    @Test
    void singleGet_withSufficientRemainingTtl_shouldBackfillL1() {
        remainTimeToLive = Duration.ofMinutes(2).toMillis(); // ≥ L1 catalog 10s

        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        // 已回填 L1：第二次读取命中本地，不再打 L2
        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        verify(bucket, times(1)).get();
    }

    @Test
    void singleGet_whenTtlQueryFails_shouldReturnValueAndSkipBackfill() {
        when(bucket.remainTimeToLive()).thenThrow(new IllegalStateException("ttl query failed"));

        // TTL 查询异常：跳过回填但正常返回已读取的值
        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        assertThat(store.get(catalog, key("a"))).isEqualTo("v1");
        verify(bucket, times(2)).get();
    }

    @Test
    void batchGet_shouldGatePerKeyBackfillByRemainingTtl() {
        // 同一批两 key 共享 remainTimeToLive 桩值——分两批验证门控两个分支
        String keyA = key("a");
        String keyB = key("b");

        // 剩余充足 → 两个 key 都回填 L1
        remainTimeToLive = Duration.ofMinutes(2).toMillis();
        Map<String, String> hits = store.getBatch(catalog, Set.of(keyA, keyB));
        assertThat(hits).containsKeys(keyA, keyB);
        assertThat(batchGetCalls.get(keyA)).isEqualTo(1);
        assertThat(batchGetCalls.get(keyB)).isEqualTo(1);

        // 剩余不足：换新 key 再读——不回填 L1，重读仍打 L2（getAsync 计数递增）
        remainTimeToLive = 500L;
        String keyC = key("c");
        hits = store.getBatch(catalog, Set.of(keyC));
        assertThat(hits).containsKey(keyC);
        hits = store.getBatch(catalog, Set.of(keyC));
        assertThat(hits).containsKey(keyC);
        assertThat(batchGetCalls.get(keyC)).isEqualTo(2);
    }
}
