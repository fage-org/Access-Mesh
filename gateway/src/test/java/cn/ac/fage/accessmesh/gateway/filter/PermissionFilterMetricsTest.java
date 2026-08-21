package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
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
import java.time.Duration;
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
 * T-ACCESS-008 监控指标测试：固定 fail-closed（open/stale 系列指标已随 fail-mode 删除）。
 * <p>
 * 指标命名：
 * <ul>
 *   <li>{@code gateway.perm.unreachable}（tag: source=snapshot|check_interface）</li>
 *   <li>{@code gateway.perm.fallback}（tag: mode=closed, reason=denied|deadline_exceeded）</li>
 * </ul>
 */
class PermissionFilterMetricsTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "admin-service";

    private PermissionClient permissionClient;
    private CacheService cacheService;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private InterfaceSnapshotCacheInvalidator invalidator;
    private ObjectMapper objectMapper;
    private GatewayFilterChain chain;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        permissionClient = mock(PermissionClient.class);
        marker = new InvalidationMarker();
        loadRegistry = new InterfaceSnapshotLoadRegistry();
        objectMapper = new ObjectMapper();
        cacheService = new DefaultCacheService(null, null,
            new CaffeineLocalCacheStore(objectMapper, null, new CacheProperties()),
            new CacheProperties(), null);
        invalidator = new InterfaceSnapshotCacheInvalidator(cacheService, marker, loadRegistry,
            new cn.ac.fage.accessmesh.common.cache.CacheProperties());
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        meterRegistry = new SimpleMeterRegistry();
    }

    private PermissionFilter createFilter(Duration deadline) {
        GatewayProperties props = new GatewayProperties();
        props.getPermission().setSnapshotLoadDeadline(deadline);
        return new PermissionFilter(permissionClient, cacheService, marker, loadRegistry,
            invalidator, props, objectMapper, meterRegistry);
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
            HttpMethod.GET, URI.create("http://access-service/api/perm/auth/interface-snapshot"),
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
        return meterRegistry.counter(name, tags).count();
    }

    // ─── unreachable 指标 ───

    @Nested
    class UnreachableMetrics {

        @Test
        void shouldIncrementUnreachableSnapshot_whenSnapshotFetchFails() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofSeconds(5));
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "check_interface")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementUnreachableCheckInterface_whenFallbackFails() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofSeconds(5));
            String key = InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 快照缓存放 FALLBACK 条目，使 decide() 走 fallbackCheckInterface
            InterfaceSnapshotResp fallbackSnapshot = new InterfaceSnapshotResp(List.of(
                new ApiPermissionEntry(SERVICE_CODE, "GET", "/api/test", true, null, null, ScopeMode.INSTANCE)
            ));
            cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, key, fallbackSnapshot);

            when(permissionClient.checkInterface(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "check_interface")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(0.0);
        }
    }

    // ─── 固定 fail-closed 指标 ───

    @Nested
    class FailClosedMetrics {

        @Test
        void shouldIncrementFallbackClosedDenied_whenUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofSeconds(5));
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "denied")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "deadline_exceeded")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementDeadlineExceeded_whenLoadExceedsDeadline() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofMillis(150));
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(PermResult.success(allowSnapshot()))
                    .delayElement(Duration.ofSeconds(1)));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "deadline_exceeded"))
                .isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "denied")).isEqualTo(0.0);
        }
    }
}
