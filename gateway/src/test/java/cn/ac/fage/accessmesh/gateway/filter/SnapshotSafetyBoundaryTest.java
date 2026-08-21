package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheMode;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.TypeRef;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.GatewayCacheCatalog;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheInvalidator;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 30 秒安全边界组合验收测试（T-ACCESS-008 复评 P2-6 修复）。
 * <p>
 * 真实时钟串联任务验收场景：上游授权 L2 条目接近 10 秒过期（L2_ONLY 目录 TTL=10s，
 * 按绝对过期时刻存取的 SPI 存储——与 RedissonBucketStore 的 RBucket TTL 语义一致）→
 * Gateway 回源注入接近 5 秒全链路延迟（4.5s，距 5s 墙钟截止留 ~0.5s 抗负载抖动余量）→ Gateway 以 catalog
 * 15s L1 回填。验证：回填写入时刻 + 15s ≤ 条目写入时刻 + 30s，且上游条目按绝对过期
 * 时刻（写入 + 10s）死亡——最坏陈旧窗口不超过 30 秒。
 * 实际耗时约 13.7 秒（9.2s 等待 + 4.5s 回源延迟）。
 * </p>
 */
class SnapshotSafetyBoundaryTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "example-service";

    /** 定时器/JIT 抖动兜底余量 */
    private static final long CLOCK_MARGIN_MS = 800;

    /** 上游授权 L2 目录：与生产快照链路同规格（L2_ONLY，TTL 10s） */
    private final CacheCatalogEntry<String> upstreamCatalog =
        CacheCatalogEntry.<String>builder()
            .code("test:upstream:auth-l2")
            .mode(CacheMode.L2_ONLY)
            .l2Ttl(Duration.ofSeconds(10))
            .valueType(new TypeRef<String>() {})
            .build();

    /**
     * 上游 L2 存储：按绝对过期时刻存取（写入时刻 + TTL），与 RBucket per-key TTL 语义一致。
     */
    static final class AbsoluteExpiryL2Store implements DistributedCacheStore {
        record Entry(Object value, long expireAtMillis) {
        }

        final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

        private boolean alive(String key) {
            Entry entry = store.get(key);
            return entry != null && System.currentTimeMillis() < entry.expireAtMillis();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> V get(CacheCatalogEntry<V> catalog, String fullKey) {
            return alive(fullKey) ? (V) store.get(fullKey).value() : null;
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
            store.put(fullKey, new Entry(value,
                System.currentTimeMillis() + catalog.getL2Ttl().toMillis()));
        }

        @Override
        public <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value,
                            Duration effectiveTtl) {
            store.put(fullKey, new Entry(value, System.currentTimeMillis() + effectiveTtl.toMillis()));
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data) {
            data.forEach((k, v) -> put(catalog, k, v));
        }

        @Override
        public <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data,
                                 Duration effectiveTtl) {
            data.forEach((k, v) -> put(catalog, k, v, effectiveTtl));
        }

        @Override
        public <V> void evict(CacheCatalogEntry<V> catalog, String fullKey) {
            store.remove(fullKey);
        }

        @Override
        public <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId) {
            store.clear();
        }
    }

    private PermissionClient permissionClient;
    private CacheService gatewayCache;
    private PermissionFilter filter;

    @BeforeEach
    void setUp() {
        permissionClient = mock(PermissionClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        gatewayCache = new DefaultCacheService(null, null,
            new CaffeineLocalCacheStore(objectMapper, null, new CacheProperties()),
            new CacheProperties(), null);
        InvalidationMarker marker = new InvalidationMarker();
        InterfaceSnapshotLoadRegistry loadRegistry = new InterfaceSnapshotLoadRegistry();
        InterfaceSnapshotCacheInvalidator invalidator =
            new InterfaceSnapshotCacheInvalidator(gatewayCache, marker, loadRegistry,
                new CacheProperties());

        GatewayProperties props = new GatewayProperties();
        props.getPermission().setSnapshotLoadDeadline(Duration.ofSeconds(5));
        filter = new PermissionFilter(permissionClient, gatewayCache, marker, loadRegistry,
            invalidator, props, objectMapper, new SimpleMeterRegistry());
    }

    private ServerWebExchange buildExchange() {
        Route route = Route.async()
            .id("example-service-route")
            .uri(URI.create("lb://example-service"))
            .metadata("serviceCode", SERVICE_CODE)
            .predicate(exchange -> true)
            .build();
        MockServerHttpRequest request = MockServerHttpRequest
            .method(HttpMethod.GET, URI.create("/api/test"))
            .build();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.setStatusCode(any())).thenReturn(true);
        when(response.bufferFactory()).thenReturn(new DefaultDataBufferFactory());
        when(response.writeWith(any())).thenReturn(Mono.empty());
        when(response.setComplete()).thenReturn(Mono.empty());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", USER_ID);
        attributes.put("tenantId", TENANT_ID);
        attributes.put("subjectTypeCode", SUBJECT_TYPE_CODE);
        attributes.put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0)));
        when(exchange.getAttributes()).thenReturn(attributes);
        return exchange;
    }

    private InterfaceSnapshotResp snapshot() {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    @Test
    void upstreamNearExpiryPlus5sFetchPlus15sGatewayL1_shouldStayWithin30sBudget()
        throws Exception {
        CacheService upstream = new DefaultCacheService(null, new AbsoluteExpiryL2Store(),
            null, new CacheProperties(), null);

        // T0：上游写入（TTL 10s，绝对过期 T0+10s）
        long t0 = System.currentTimeMillis();
        upstream.put(upstreamCatalog, TENANT_ID, "auth", "granted-set");

        // 等待至接近 10s 过期（剩 ~0.8s）
        Thread.sleep(9_200);
        assertThat(upstream.get(upstreamCatalog, TENANT_ID, "auth"))
            .as("接近 10s 过期时上游 L2 仍可读")
            .isEqualTo("granted-set");

        // Gateway 回源：读取上游 L2 后注入接近 5s 的全链路延迟（真实墙钟截止 5s 内）
        AtomicInteger clientCalls = new AtomicInteger();
        when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
            .thenAnswer(inv -> Mono.defer(() -> {
                clientCalls.incrementAndGet();
                String upstreamValue = upstream.get(upstreamCatalog, TENANT_ID, "auth");
                InterfaceSnapshotResp resp = upstreamValue != null
                    ? snapshot()
                    : new InterfaceSnapshotResp(List.of());
                return Mono.just(PermResult.success(resp)).delayElement(Duration.ofMillis(4_500));
            }));

        filter.filter(buildExchange(), mockChain()).block(Duration.ofSeconds(8));
        long fillTime = System.currentTimeMillis();

        String identifier = InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
        InterfaceSnapshotResp cached =
            gatewayCache.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier);

        // 4.5s 延迟在 5s 截止内（余量 ~0.5s）：快照正常回填 Gateway L1（15s TTL）
        assertThat(clientCalls.get()).isEqualTo(1);
        assertThat(cached).isNotNull();

        // 组合边界：Gateway 回填时刻 + 15s L1 ≤ T0 + 30s（最坏陈旧窗口 ≤ 30s）
        long staleWindowMs = (fillTime - t0) + 15_000;
        assertThat(staleWindowMs)
            .as("10s 上游 L2 + ~5s 回源 + 15s 网关 L1 应 ≤ 30s（实际 %d ms）", staleWindowMs)
            .isLessThanOrEqualTo(30_000 + CLOCK_MARGIN_MS);

        // 上游条目按绝对过期时刻（T0+10s）死亡：越过边界后读取为 miss
        sleepUntil(t0 + 10_300);
        assertThat(upstream.get(upstreamCatalog, TENANT_ID, "auth"))
            .as("上游 L2 条目应在 T0+10s 绝对过期")
            .isNull();
    }

    private GatewayFilterChain mockChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private void sleepUntil(long deadlineMillis) throws InterruptedException {
        long remain = deadlineMillis - System.currentTimeMillis();
        if (remain > 0) {
            Thread.sleep(remain);
        }
    }
}
