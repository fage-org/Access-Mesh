package cn.ac.fage.accessmesh.access.permission.cache;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.RedissonCacheInvalidationBroadcaster;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.common.cache.impl.CombinedL1L2Store;
import cn.ac.fage.accessmesh.common.cache.impl.RedissonBucketStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RKeys;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 双实例缓存一致性测试（T-ACCESS-008：同 JVM 双 CacheService 实例共享内存版 Redis）。
 * <p>
 * 覆盖验收场景：正常广播、广播丢失（L1 TTL 兜底）、Redis 短暂故障（绕过缓存）、
 * 权限撤销跨实例一致、上游 L2 剩余 TTL 锚定读取起点（30s 安全边界的上游侧不变式）。
 * </p>
 */
class DualInstanceCacheInvalidationTest {

    private static final Long TENANT_ID = 1L;

    /**
     * 内存版 Redisson：ConcurrentHashMap 承载 RBucket 数据（带绝对过期时刻），
     * RTopic 语义为进程内同步派发（同一"Redis"），RKeys 按前缀扫描。
     * down=true 模拟 Redis 短暂故障（全部操作抛异常）；dropBroadcast=true 模拟广播丢失。
     */
    static final class FakeRedis {
        record Entry(String json, long expireAtMillis) {
        }

        final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
        final List<MessageListener<String>> topicListeners = new ArrayList<>();
        final MeterRegistry meterRegistry = new SimpleMeterRegistry();
        volatile boolean down;
        volatile boolean dropBroadcast;

        private long now() {
            return System.currentTimeMillis();
        }

        private boolean alive(String key) {
            Entry entry = store.get(key);
            return entry != null && now() < entry.expireAtMillis();
        }

        @SuppressWarnings("unchecked")
        RedissonClient client() {
            RedissonClient redisson = mock(RedissonClient.class);

            // RBucket：get/set(json, ttl)/delete
            when(redisson.<String>getBucket(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                RBucket<String> bucket = mock(RBucket.class);
                when(bucket.get()).thenAnswer(g -> {
                    if (down) throw new IllegalStateException("redis down");
                    return alive(key) ? store.get(key).json() : null;
                });
                when(bucket.remainTimeToLive()).thenAnswer(g -> {
                    if (down) throw new IllegalStateException("redis down");
                    Entry entry = store.get(key);
                    return entry == null ? -2L : Math.max(-1L, entry.expireAtMillis() - now());
                });
                doAnswer(s -> {
                    if (down) throw new IllegalStateException("redis down");
                    store.put(key, new Entry(s.getArgument(0), now() + ((Duration) s.getArgument(1)).toMillis()));
                    return null;
                }).when(bucket).set(anyString(), any(Duration.class));
                when(bucket.delete()).thenAnswer(d -> {
                    if (down) throw new IllegalStateException("redis down");
                    return store.remove(key) != null;
                });
                return bucket;
            });

            // RBatch：收集 getAsync/setAsync/deleteAsync/remainTimeToLiveAsync，execute 时统一应用
            when(redisson.createBatch()).thenAnswer(inv -> {
                record PendingGet(String key, CompletableFuture<String> cf) {
                }
                record PendingTtl(String key, CompletableFuture<Long> cf) {
                }
                record PendingSet(String key, String json, Duration ttl) {
                }
                List<PendingGet> gets = new ArrayList<>();
                List<PendingTtl> ttls = new ArrayList<>();
                List<PendingSet> sets = new ArrayList<>();
                List<String> deletes = new ArrayList<>();

                RBatch batch = mock(RBatch.class);
                when(batch.<String>getBucket(anyString())).thenAnswer(bucketInv -> {
                    String key = bucketInv.getArgument(0);
                    RBucketAsync<String> rb = mock(RBucketAsync.class);
                    when(rb.getAsync()).thenAnswer(getInv -> {
                        if (down) throw new IllegalStateException("redis down");
                        CompletableFuture<String> cf = new CompletableFuture<>();
                        RFuture<String> rf = mock(RFuture.class);
                        when(rf.toCompletableFuture()).thenReturn(cf);
                        gets.add(new PendingGet(key, cf));
                        return rf;
                    });
                    when(rb.remainTimeToLiveAsync()).thenAnswer(ttlInv -> {
                        if (down) throw new IllegalStateException("redis down");
                        CompletableFuture<Long> cf = new CompletableFuture<>();
                        RFuture<Long> rf = mock(RFuture.class);
                        when(rf.toCompletableFuture()).thenReturn(cf);
                        ttls.add(new PendingTtl(key, cf));
                        return rf;
                    });
                    doAnswer(setInv -> {
                        if (down) throw new IllegalStateException("redis down");
                        sets.add(new PendingSet(key, setInv.getArgument(0), setInv.getArgument(1)));
                        return mock(RFuture.class);
                    }).when(rb).setAsync(anyString(), any(Duration.class));
                    doAnswer(delInv -> {
                        if (down) throw new IllegalStateException("redis down");
                        deletes.add(key);
                        return mock(RFuture.class);
                    }).when(rb).deleteAsync();
                    return rb;
                });
                when(batch.execute()).thenAnswer(exec -> {
                    if (down) throw new IllegalStateException("redis down");
                    gets.forEach(g -> g.cf().complete(alive(g.key()) ? store.get(g.key()).json() : null));
                    ttls.forEach(t -> {
                        Entry entry = store.get(t.key());
                        t.cf().complete(entry == null ? -2L : Math.max(-1L, entry.expireAtMillis() - now()));
                    });
                    sets.forEach(s -> store.put(s.key(), new Entry(s.json(), now() + s.ttl().toMillis())));
                    deletes.forEach(store::remove);
                    return null;
                });
                return batch;
            });

            // RKeys：模式扫描 + 批量删除
            RKeys keys = mock(RKeys.class);
            when(redisson.getKeys()).thenReturn(keys);
            when(keys.getKeysByPattern(anyString(), any(Integer.class))).thenAnswer(inv -> {
                if (down) throw new IllegalStateException("redis down");
                String pattern = inv.getArgument(0);
                // 支持租户级 "1:code:*" 与 catalog 级 "*:code:*" 两种模式
                String prefix = pattern.replace("*", "");
                List<String> matched = store.keySet().stream()
                    .filter(k -> k.contains(prefix) && alive(k))
                    .toList();
                return matched;
            });
            doAnswer(del -> {
                if (down) throw new IllegalStateException("redis down");
                for (String k : (String[]) del.getArgument(0)) {
                    store.remove(k);
                }
                return null;
            }).when(keys).delete(any(String[].class));

            // RTopic：进程内同步派发（同一"Redis"的发布订阅）
            when(redisson.getTopic(anyString())).thenAnswer(inv -> {
                String name = inv.getArgument(0);
                RTopic topic = mock(RTopic.class);
                doAnswer(add -> {
                    topicListeners.add((MessageListener<String>) add.getArgument(1));
                    return 1;
                }).when(topic).addListener(eq(String.class), any(MessageListener.class));
                when(topic.publish(anyString())).thenAnswer(pub -> {
                    if (down) throw new IllegalStateException("redis down");
                    if (dropBroadcast) throw new IllegalStateException("broadcast lost");
                    String message = pub.getArgument(0);
                    for (MessageListener<String> listener : List.copyOf(topicListeners)) {
                        listener.onMessage(name, message);
                    }
                    return (long) topicListeners.size();
                });
                return topic;
            });

            return redisson;
        }
    }

    private FakeRedis redis;
    private ObjectMapper objectMapper;
    private CacheProperties properties;
    private CacheService instanceA;
    private CacheService instanceB;

    /** L1_L2 测试目录：L1 200ms（验证 TTL 兜底）/ L2 60s */
    private final CacheCatalogEntry<String> l1l2Catalog =
        CacheCatalogEntry.<String>builder()
            .code("test:dual:l1l2")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMillis(200))
            .l1MaxSize(100)
            .l2Ttl(Duration.ofMinutes(1))
            .valueType(new TypeRef<String>() {})
            .build();

    /** L2_ONLY 测试目录：TTL 2s（压缩演示剩余 TTL 锚定读取起点；生产 10s 由边界校验强制） */
    private final CacheCatalogEntry<String> l2OnlyCatalog =
        CacheCatalogEntry.<String>builder()
            .code("test:dual:l2only")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(2))
            .valueType(new TypeRef<String>() {})
            .build();

    @BeforeEach
    void setUp() throws Exception {
        redis = new FakeRedis();
        objectMapper = new ObjectMapper();
        properties = new CacheProperties();

        CombinedL1L2Store storeA = new CombinedL1L2Store(redis.client(), objectMapper,
            redis.meterRegistry, properties);
        RedissonCacheInvalidationBroadcaster broadcasterA = new RedissonCacheInvalidationBroadcaster(
            redis.client(), objectMapper, storeA, redis.meterRegistry);
        instanceA = new DefaultCacheService(storeA, null, null, properties, broadcasterA);

        CombinedL1L2Store storeB = new CombinedL1L2Store(redis.client(), objectMapper,
            redis.meterRegistry, properties);
        RedissonCacheInvalidationBroadcaster broadcasterB = new RedissonCacheInvalidationBroadcaster(
            redis.client(), objectMapper, storeB, redis.meterRegistry);
        instanceB = new DefaultCacheService(storeB, null, null, properties, broadcasterB);
    }

    @Test
    void normalBroadcast_instanceAEvictShouldClearInstanceBLocalL1() {
        instanceA.put(l1l2Catalog, TENANT_ID, "k1", "v1");

        // B 首读：L1 miss → L2 共享命中（双实例共享 L2）
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k1")).isEqualTo("v1");
        // B 再读：L1 命中
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k1")).isEqualTo("v1");

        // A 失效：L2 删除 + 广播 → B 本地 L1 清理
        instanceA.evict(l1l2Catalog, TENANT_ID, "k1");

        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k1")).isNull();
        assertThat(instanceA.get(l1l2Catalog, TENANT_ID, "k1")).isNull();
    }

    @Test
    void broadcastLost_instanceBL1FallsBackToTtl() throws InterruptedException {
        instanceA.put(l1l2Catalog, TENANT_ID, "k2", "v2");
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k2")).isEqualTo("v2"); // B L1 回填

        // 广播丢失：A 失效 L2 成功但广播失败
        redis.dropBroadcast = true;
        instanceA.evict(l1l2Catalog, TENANT_ID, "k2");
        redis.dropBroadcast = false;

        // 失效失败指标计数（cache.invalidate.failures type=broadcast）
        Counter failures = redis.meterRegistry.counter("cache.invalidate.failures",
            "type", "broadcast", "catalog", l1l2Catalog.getCode());
        assertThat(failures.count()).isGreaterThanOrEqualTo(1.0);

        // B 本地 L1 陈旧继续命中（最长 L1 TTL 兜底——测试目录 200ms）
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k2")).isEqualTo("v2");

        // 越过 L1 TTL 后 B 归零（TTL 兜底最终一致）
        Thread.sleep(400);
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k2")).isNull();
    }

    @Test
    void redisOutage_shouldBypassCacheWithoutException() {
        instanceA.put(l1l2Catalog, TENANT_ID, "k3", "v3");

        redis.down = true;
        // 缓存不可用：A 实例 L1 命中仍可用（本地不依赖 Redis）；B 实例 L1 miss 后
        // L2 故障被吞掉返回 null → 业务绕过缓存查数据库；put 静默跳过；全程不抛异常
        assertThatCode(() -> {
            assertThat(instanceA.get(l1l2Catalog, TENANT_ID, "k3")).isEqualTo("v3");
            assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k3")).isNull();
            instanceA.put(l1l2Catalog, TENANT_ID, "k4", "v4");
        }).doesNotThrowAnyException();

        // 故障恢复后缓存能力恢复
        redis.down = false;
        assertThat(instanceA.get(l1l2Catalog, TENANT_ID, "k3")).isEqualTo("v3");
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "k3")).isEqualTo("v3");
    }

    @Test
    void permissionRevocation_crossInstanceConsistentAfterEviction() {
        instanceA.put(l1l2Catalog, TENANT_ID, "user:10", "old-roles");
        // B 读到旧值并回填 L1
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "user:10")).isEqualTo("old-roles");

        // 权限撤销（A 实例提交后失效）：广播使 B L1 同步清理
        instanceA.evict(l1l2Catalog, TENANT_ID, "user:10");
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "user:10")).isNull();

        // 业务重查数据库回填新值，两实例一致
        instanceA.put(l1l2Catalog, TENANT_ID, "user:10", "new-roles");
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "user:10")).isEqualTo("new-roles");
        assertThat(instanceA.get(l1l2Catalog, TENANT_ID, "user:10")).isEqualTo("new-roles");
    }

    /**
     * 30s 安全边界上游侧不变式（压缩演示，生产 TTL 10s）：
     * 授权 L2 回填的绝对过期时刻锚定读取起点（读取起点 + catalog TTL），
     * 与写入时刻无关——即使回填发生在读取起点 + TTL - 0.5s，条目也只在剩余 0.5s 内可用；
     * Gateway 侧「回源 5s 截止 + L1 15s」由 gateway 模块测试与启动校验强制，
     * 三者合计 ≤ 30s（见 PermCacheCatalogBoundaryTest.safetyBudget_shouldStayWithin30Seconds）。
     */
    @Test
    void upstreamL2Backfill_shouldAnchorAbsoluteExpiryToReadStart() throws InterruptedException {
        CacheService instance = new DefaultCacheService(null,
            new RedissonBucketStore(redis.client(), objectMapper, redis.meterRegistry, properties),
            null, properties, null);

        long tokenStart = System.currentTimeMillis();
        cn.ac.fage.accessmesh.common.cache.CacheReadToken<String> token = instance.beginRead(l2OnlyCatalog);
        // 模拟 DB 读取耗时 catalog TTL - 0.5s（压缩目录 2s → 1.5s）——期间发生权限事务提交与失效
        Thread.sleep(1_500);
        instance.evict(l2OnlyCatalog, TENANT_ID, "snap");
        // 旧读取完成后回填：只获得剩余 0.5s
        instance.put(token, TENANT_ID, "snap", "value");

        assertThat(instance.get(l2OnlyCatalog, TENANT_ID, "snap")).isEqualTo("value");

        // 绝对过期锚定读取起点而非写入时刻：再等 0.8s（起点+2.3s > 起点+2s）
        // 条目必须已过期——即使距写入仅 0.8s（不得重新获得完整 TTL）
        Thread.sleep(800);
        assertThat(instance.get(l2OnlyCatalog, TENANT_ID, "snap")).isNull();

        // 条目总生命周期 ≤ catalog TTL（锚定起点，压缩目录 2s）
        long upstreamLifeMs = System.currentTimeMillis() - tokenStart;
        assertThat(upstreamLifeMs).isLessThan(3_000);
    }

    @Test
    void batchOperations_shareL2AcrossInstances() {
        instanceA.putBatch(l1l2Catalog, TENANT_ID, Map.of("b1", "v1", "b2", "v2"));

        Map<String, String> hits = instanceB.getBatch(l1l2Catalog, TENANT_ID, Set.of("b1", "b2", "b3"));
        assertThat(hits).containsOnlyKeys("b1", "b2");

        instanceA.evictBatch(l1l2Catalog, TENANT_ID, Set.of("b1"));
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "b1")).isNull();
        assertThat(instanceB.get(l1l2Catalog, TENANT_ID, "b2")).isEqualTo("v2");
    }
}
