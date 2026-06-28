package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
import cn.ac.fage.accessmesh.gateway.config.FailMode;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp.MatchedResource;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionFilter fail-mode 测试（T-GW-001 / T-GW-002 / T-GW-003 + P1/P2 修复验证）
 * <p>
 * 覆盖三模行为：CLOSED（默认 503）/ OPEN（放行）/ STALE_ALLOW（陈旧快照续命）。
 * P1：显式失效后回源失败 → 始终 503，不受 fail-mode 影响。
 * P2：仅远端不可达走 fail-mode；非远端异常始终 fail-closed。
 * StaleLoadDiscardedException 不受 fail-mode 影响，始终 503。
 * T-GW-003 stale-allow：从 stale store 取陈旧快照续命，双重检查 staleUntil + !invalidatedKeys；
 * FALLBACK 条件不可评估视为 DENY（403）；无可用 stale 条目降级 closed（503）。
 * </p>
 */
class PermissionFilterFailModeTest {

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
            .header("X-Real-IP", "10.0.0.1")   // 提供 clientIp，使 checkInterface mock 的 anyString() 可匹配
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

    private boolean awaitFilterCompletion(PermissionFilter filter, ServerWebExchange exchange,
                                          long timeoutSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        filter.filter(exchange, chain)
            .subscribe(
                __ -> {},
                e -> { capturedError.set(e); success.set(false); latch.countDown(); },
                () -> { success.set(true); latch.countDown(); }
            );
        latch.await(timeoutSeconds, TimeUnit.SECONDS);
        if (!success.get() && capturedError.get() != null) {
            throw new AssertionError("Filter Mono errored: " + capturedError.get(), capturedError.get());
        }
        return success.get();
    }

    private InterfaceSnapshotResp allowSnapshot() {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    /**
     * 含条件 FALLBACK 快照：hasCondition=true, gateway_evaluable=false (conditionRules=null)
     */
    private InterfaceSnapshotResp fallbackSnapshot() {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE, "GET", "/api/test", true, null, null, ScopeMode.INSTANCE)
        ));
    }

    private CheckInterfaceResp allowedCheckResp() {
        return new CheckInterfaceResp(true, null, List.of(), 0);
    }

    /** 创建 WebClientRequestException 模拟 permission-center 连接拒绝 */
    private static WebClientRequestException connectionRefused() {
        return new WebClientRequestException(
            new java.net.ConnectException("Connection refused"),
            HttpMethod.GET, URI.create("http://permission-center/api/perm/auth/interface-snapshot"),
            HttpHeaders.EMPTY);
    }

    // ─── fail-closed（默认）───

    @Nested
    class FailClosedMode {

        @Test
        void shouldReturn503_whenPermCenterUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        void shouldReturn503_whenFallbackCheckInterfaceUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(PermResult.success(fallbackSnapshot())));
            when(permissionClient.checkInterface(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            // 验证实际进入了 check-interface fallback 分支
            verify(permissionClient).checkInterface(eq(SUBJECT_TYPE_CODE), eq(USER_ID),
                eq(SERVICE_CODE), eq("GET"), eq("/api/test"), eq("10.0.0.1"), eq(TENANT_ID));
        }

        @Test
        void shouldReturn503_whenStaleLoadDiscardedEvenInClosedMode() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.CLOSED);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    marker.mark(key);
                    return Mono.just(PermResult.success(allowSnapshot()));
                });

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ─── fail-open ───

    @Nested
    class FailOpenMode {

        @Test
        void shouldAllowThrough_whenPermCenterUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(filter, exchange, 5);

            assertThat(completed).isTrue();
            // P2：验证 fail-open 放行时 chain.filter 被调用
            verify(chain).filter(exchange);
        }

        @Test
        void shouldAllowThrough_whenFallbackCheckInterfaceUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(PermResult.success(fallbackSnapshot())));
            when(permissionClient.checkInterface(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(filter, exchange, 5);

            assertThat(completed).isTrue();
            // 验证实际进入了 check-interface fallback 分支
            verify(permissionClient).checkInterface(eq(SUBJECT_TYPE_CODE), eq(USER_ID),
                eq(SERVICE_CODE), eq("GET"), eq("/api/test"), eq("10.0.0.1"), eq(TENANT_ID));
            // P2：验证 fail-open 放行时 chain.filter 被调用
            verify(chain).filter(exchange);
        }

        @Test
        void shouldReturn503_whenStaleLoadDiscardedEvenInOpenMode() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    marker.mark(key);
                    return Mono.just(PermResult.success(allowSnapshot()));
                });

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        // P1：显式失效后回源不可达 → 始终 503，OPEN 也不放行
        @Test
        void shouldReturn503_whenExplicitlyInvalidatedAndPermCenterUnreachableInOpenMode()
            throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填主缓存 + stale store + 标记失效（模拟 perm:invalidate 已到达）
            mainCache.put(key, allowSnapshot());
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));
            marker.mark(key);

            // 回源不可达
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            // P1：显式撤销 > 兜底，OPEN 模式也不放行
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ─── P2：非远端不可达异常始终 fail-closed ───

    @Nested
    class NonRemoteErrorsAlwaysClosed {

        @Test
        void shouldReturn503_whenUnexpectedErrorInOpenMode() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.OPEN);

            // 模拟代码 bug（非远端不可达）：loadSnapshot 内部抛非 PermCenterUnreachableException
            // 因为 loadSnapshot 内会把远端错误包装为 PermCenterUnreachableException，
            // 非 StaleLoadDiscardedException 的非包装异常不会出现在正常路径。
            // 此处通过模拟一个 loadRegistry 抛出异常来测试 catch-all 分支。
            InterfaceSnapshotLoadRegistry faultyRegistry = new InterfaceSnapshotLoadRegistry() {
                @Override
                public Mono<InterfaceSnapshotResp> load(String cacheKey,
                    java.util.function.Supplier<Mono<InterfaceSnapshotResp>> loader) {
                    return Mono.error(new IllegalStateException("bug: unexpected state"));
                }
            };

            GatewayProperties props = new GatewayProperties();
            props.getCache().getL1().setTtlSeconds(30);
            props.getCache().getL1().setStaleGraceSeconds(30);
            props.getPermission().setFailMode(FailMode.OPEN);
            PermissionFilter filterWithFaultyRegistry = new PermissionFilter(
                permissionClient, mainCache, staleCache, marker, faultyRegistry, props, objectMapper, meterRegistry);

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filterWithFaultyRegistry.filter(exchange, chain)
                .subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            // P2：非远端不可达异常 → 始终 fail-closed，OPEN 也不放行
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ─── stale-allow（T-GW-003 实现）───

    @Nested
    class StaleAllowMode {

        /** stale store 有 ALLOW 快照 + staleUntil 未过期 + 未被标记失效 → 续命放行 */
        @Test
        void shouldAllowThrough_whenValidStaleSnapshotExists() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填 stale store（模拟主缓存自然过期后陈旧快照仍在 stale store）
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));

            // 回源不可达
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(filter, exchange, 5);

            assertThat(completed).isTrue();
            // stale-allow 续命放行时 chain.filter 被调用
            verify(chain).filter(exchange);
        }

        /** stale store 为空 → 降级 closed（503） */
        @Test
        void shouldReturn503_whenNoStaleSnapshotExists() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);

            // 不预填 stale store
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        /** stale store 有条目但 staleUntil 已过期 → 降级 closed（503） */
        @Test
        void shouldReturn503_whenStaleEntryExpired() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // staleUntil 已过期
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().minusSeconds(10)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        /** stale store 有条目但 key 已被标记失效（invalidationMarker）→ 降级 closed（503） */
        @Test
        void shouldReturn503_whenStaleEntryExplicitlyInvalidated() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填 stale store + 标记失效（注意：filter() 入口不会清除 stale store 中的此条目，
            // 因为此时主缓存没有此 key，filter 不会进入显式失效分支。
            // 此场景测试 handleUnreachable 中 invalidationMarker.contains 检查）
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));
            marker.mark(key);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            // 显式失效标记阻止 stale-allow 续命
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        /** stale 快照匹配结果为 FALLBACK → 不可评估条件，视为 DENY（403） */
        @Test
        void shouldReturn403_whenStaleSnapshotMatchesFallback() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填含条件 FALLBACK 快照
            staleCache.put(key, new StaleEntry(fallbackSnapshot(), Instant.now().plusSeconds(60)));

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            // FALLBACK 条件不可评估 → 403
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        /** P1：显式失效（perm:invalidate 到达，主缓存有此 key）后回源不可达 → 始终 503 */
        @Test
        void shouldReturn503_whenExplicitlyInvalidatedAndPermCenterUnreachableInStaleAllowMode()
            throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填主缓存 + stale store + 标记失效（模拟 perm:invalidate 已到达）
            mainCache.put(key, allowSnapshot());
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));
            marker.mark(key);

            // 回源不可达
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            // P1：显式撤销 > 兜底，stale-allow 也不续命
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        /** fallback check-interface 不可达时，stale-allow 也可从 stale store 续命 */
        @Test
        void shouldAllowThrough_whenFallbackCheckInterfaceUnreachableAndValidStaleExists()
            throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 预填主缓存（ALLOW 快照，确保 filter() 入口走 decide → ALLOW 直接放行，
            // 不走 fallback 路径。此测试需要触发 fallback 路径）
            // 用 FALLBACK 快照让 decide() 走 fallbackCheckInterface
            mainCache.put(key, fallbackSnapshot());

            // stale store 放 ALLOW 快照——fallback check-interface 失败后，
            // stale-allow 用 stale store 的 ALLOW 快照续命
            staleCache.put(key, new StaleEntry(allowSnapshot(), Instant.now().plusSeconds(60)));

            // check-interface 不可达
            when(permissionClient.checkInterface(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(filter, exchange, 5);

            assertThat(completed).isTrue();
            // stale-allow 从 stale store 续命放行
            verify(chain).filter(exchange);
        }

        /** StaleLoadDiscardedException 不受 stale-allow 影响，始终 503 */
        @Test
        void shouldReturn503_whenStaleLoadDiscardedEvenInStaleAllowMode() throws InterruptedException {
            PermissionFilter filter = createFilter(FailMode.STALE_ALLOW);
            String key = InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    marker.mark(key);
                    return Mono.just(PermResult.success(allowSnapshot()));
                });

            ServerWebExchange exchange = buildExchange();
            AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
            when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
                capturedStatus.set(inv.getArgument(0));
                return true;
            });

            CountDownLatch latch = new CountDownLatch(1);
            filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);

            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ─── 默认配置 ───

    @Nested
    class DefaultConfig {

        @Test
        void shouldDefaultToClosed() {
            GatewayProperties props = new GatewayProperties();
            assertThat(props.getPermission().getFailMode()).isEqualTo(FailMode.CLOSED);
        }
    }
}
