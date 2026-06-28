package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
import cn.ac.fage.accessmesh.gateway.config.FailMode;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-GW-004 监控指标测试：验证 Counter 在各 fail-mode 分支正确递增。
 * <p>
 * 指标命名：
 * <ul>
 *   <li>{@code gateway.perm.unreachable}（tag: source=snapshot|check_interface）</li>
 *   <li>{@code gateway.perm.fallback}（tag: mode=closed|open|stale）</li>
 *   <li>{@code gateway.perm.fallback}（tag: mode=stale, reason=no_entry|expired|invalidated|allowed|denied）</li>
 * </ul>
 */
class PermissionFilterMetricsTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "admin-service";

    private PermissionClient permissionClient;
    private Cache<String, InterfaceSnapshotResp> mainCache;
    private Cache<String, StaleEntry> staleCache;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private ObjectMapper objectMapper;
    private GatewayFilterChain chain;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        permissionClient = mock(PermissionClient.class);
        mainCache = Caffeine.newBuilder().build();
        staleCache = Caffeine.newBuilder().build();
        marker = new InvalidationMarker();
        loadRegistry = new InterfaceSnapshotLoadRegistry();
        objectMapper = new ObjectMapper();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        meterRegistry = new SimpleMeterRegistry();
    }

    private PermissionFilter createFilter(FailMode failMode) {
        GatewayProperties props = new GatewayProperties();
        props.getCache().getL1().setTtlSeconds(30);
        props.getCache().getL1().setStaleGraceSeconds(30);
        props.getPermission().setFailMode(failMode);
        return new PermissionFilter(permissionClient, mainCache, staleCache, marker, loadRegistry,
            props, objectMapper, meterRegistry);
    }

    private ServerWebExchange buildExchange() {
        Route route = Route.async()
            .id("admin-service-route")
            .uri(URI.create("lb://admin-service"))
            .metadata("serviceCode", SERVICE_CODE)
            .predicate(exchange -> true)
            .build();

        MockServerHttpRequest request = MockServerHttpRequest
            .method(HttpMethod.GET, URI.create("/api/test"))
            .header("X-Real-IP", "10.0.0.1")
            .build();

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders writableHeaders = new HttpHeaders();
        when(response.getHeaders()).thenReturn(writableHeaders);
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

    private static WebClientRequestException connectionRefused() {
        return new WebClientRequestException(
            new java.net.ConnectException("Connection refused"),
            HttpMethod.GET, URI.create("http://permission-center/api/perm/auth/interface-snapshot"),
            HttpHeaders.EMPTY);
    }

    private InterfaceSnapshotResp allowSnapshot() {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    private void awaitCompletion(PermissionFilter filter, ServerWebExchange exchange)
        throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        filter.filter(exchange, chain)
            .subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    private double counterValue(String name, String... tags) {
        // meterRegistry.counter() does exact ID lookup (name + full tag set);
        // if already registered (by constructor), returns existing counter.
        // This avoids find().tags().counter() which does subset matching
        // and may return a different counter with additional tags.
        return meterRegistry.counter(name, tags).count();
    }

    // ─── unreachable 指标 ───

    @Nested
    class UnreachableMetrics {

        @Test
        void shouldIncrementUnreachableSnapshot_whenSnapshotFetchFails() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "check_interface")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementUnreachableCheckInterface_whenFallbackFails() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 主缓存放 FALLBACK 快照，使 decide() 走 fallbackCheckInterface
            InterfaceSnapshotResp fallbackSnapshot = new InterfaceSnapshotResp(List.of(
                new ApiPermissionEntry(SERVICE_CODE, "GET", "/api/test", true, null, null, ScopeMode.INSTANCE)
            ));
            mainCache.put(key, fallbackSnapshot);

            when(permissionClient.checkInterface(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "check_interface")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(0.0);
        }
    }

    // ─── fallback 模式指标 ───

    @Nested
    class FallbackModeMetrics {

        @Test
        void shouldIncrementFallbackClosed_whenFailClosed() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "closed")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "open")).isEqualTo(0.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementFallbackOpen_whenFailOpen() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "open")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed")).isEqualTo(0.0);
        }
    }

    // ─── stale-allow 细分指标 ───

    @Nested
    class StaleAllowMetrics {

        @Test
        void shouldIncrementStaleAllowed_whenValidStaleSnapshotMatchesAllow() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "allowed")).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementStaleNoEntry_whenNoStaleSnapshot() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "no_entry")).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementStaleExpired_whenStaleUntilPassed() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().minusSeconds(10)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "expired")).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementStaleInvalidated_whenMarkerContainsKey() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));
            marker.mark(key);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "invalidated")).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementStaleDenied_whenStaleSnapshotMatchesDeny() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            // DENY 快照：无匹配 entry
            InterfaceSnapshotResp denySnapshot = new InterfaceSnapshotResp(List.of());
            staleCache.put(key, new StaleEntry(denySnapshot, Instant.now().plusSeconds(60)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "denied")).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementStaleDenied_whenStaleSnapshotMatchesFallback() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            InterfaceSnapshotResp fallbackSnapshot = new InterfaceSnapshotResp(List.of(
                new ApiPermissionEntry(SERVICE_CODE, "GET", "/api/test", true, null, null, ScopeMode.INSTANCE)
            ));
            staleCache.put(key, new StaleEntry(fallbackSnapshot, Instant.now().plusSeconds(60)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "stale")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "stale", "reason", "denied")).isEqualTo(1.0);
        }
    }

    // ─── 显式失效不递增 fallback 指标 ───

    @Nested
    class ExplicitInvalidationMetrics {

        @Test
        void shouldNotIncrementFallback_whenExplicitlyInvalidated() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
            mainCache.put(key, allowSnapshot());
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));
            marker.mark(key);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            // 显式失效后走 P1 分支（始终 503），不经过 handleUnreachable → 不递增 fallback
            assertThat(counterValue("gateway.perm.fallback", "mode", "open")).isEqualTo(0.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed")).isEqualTo(0.0);
        }
    }
}
