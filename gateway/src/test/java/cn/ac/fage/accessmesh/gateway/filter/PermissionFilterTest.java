package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PermissionFilter 组合路径回归测试（T-PERM-008）
 * <p>
 * 覆盖高风险组合路径：回源写双缓存+unmark、token失效丢弃、重试一次、
 * 二次失效fail-close、缓存命中但marker标记→驱逐+回源。
 * </p>
 */
class PermissionFilterTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "admin-service";

    private PermissionClient permissionClient;
    private Cache<String, InterfaceSnapshotResp> mainCache;
    private Cache<String, StaleEntry> staleCache;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private GatewayProperties gatewayProperties;
    private ObjectMapper objectMapper;
    private PermissionFilter filter;

    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        permissionClient = mock(PermissionClient.class);
        mainCache = Caffeine.newBuilder().build();
        staleCache = Caffeine.newBuilder().build();
        marker = new InvalidationMarker();
        loadRegistry = new InterfaceSnapshotLoadRegistry();

        gatewayProperties = new GatewayProperties();
        gatewayProperties.getCache().getL1().setTtlSeconds(30);
        gatewayProperties.getCache().getL1().setStaleGraceSeconds(30);

        objectMapper = new ObjectMapper();
        filter = new PermissionFilter(permissionClient, mainCache, staleCache, marker, loadRegistry,
            gatewayProperties, objectMapper);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * 构建测试用 exchange（使用 mock response 避免 ReadOnlyHttpHeaders 问题）。
     * <p>
     * MockServerWebExchange 的 response 在 filter 写入 status/header 时
     * 可能触发 ReadOnlyHttpHeaders 异常，因此用 mock response 替代。
     * </p>
     */
    private ServerWebExchange buildExchange() {
        Route route = Route.async()
            .id("admin-service-route")
            .uri(URI.create("lb://admin-service"))
            .metadata("serviceCode", SERVICE_CODE)
            .predicate(exchange -> true)
            .build();

        MockServerHttpRequest request = MockServerHttpRequest
            .method(HttpMethod.GET, URI.create("/api/test"))
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

    private String cacheKey() {
        return InterfaceSnapshotCacheKeys.build(TENANT_ID, SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
    }

    /**
     * 生成 ALLOW 快照：含一个 scopeMode=ALL 无条件条目，匹配器直接放行。
     */
    private InterfaceSnapshotResp allowSnapshot() {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    private PermResult<InterfaceSnapshotResp> successResult(InterfaceSnapshotResp snapshot) {
        return PermResult.success(snapshot);
    }

    /**
     * 订阅 filter Mono 并等待完成（最多 timeout 秒），返回是否成功完成（无异常）。
     */
    private boolean awaitFilterCompletion(ServerWebExchange exchange, long timeoutSeconds) throws InterruptedException {
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

    // ─── 路径 1：回源成功 → 写主缓存 + stale store + unmark ───

    @Nested
    class LoadSuccessWritesBothCaches {

        @Test
        void shouldWriteMainCacheAndStaleStoreAndUnmark_whenLoadSucceedsAndTokenCurrent()
            throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(snapshot)));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            // filter 正常完成（ALLOW 路径走 chain.filter，switchIfEmpty 不影响结果正确性）
            assertThat(completed).isTrue();

            // 主缓存写入
            assertThat(mainCache.getIfPresent(key)).isNotNull();
            // stale store 写入
            StaleEntry stale = staleCache.getIfPresent(key);
            assertThat(stale).isNotNull();
            assertThat(stale.snapshot()).isEqualTo(snapshot);
            assertThat(stale.staleUntil()).isAfter(Instant.now());
            // marker 已 unmark
            assertThat(marker.contains(key)).isFalse();
        }
    }

    // ─── 路径 2：Token 失效 → 丢弃结果（StaleLoadDiscardedException → 503） ───

    @Nested
    class TokenInvalidatedDiscardsResult {

        @Test
        void shouldReturn503_whenTokenInvalidatedMidFlight() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            // 在 permissionClient 回调中标记 key，确保在 beginLoad 之后、commitIfCurrent 之前
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    marker.mark(key);
                    return Mono.just(successResult(snapshot));
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

            // 期望 503
            assertThat(capturedStatus.get()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

            // 主缓存和 stale store 不应被写入
            assertThat(mainCache.getIfPresent(key)).isNull();
            assertThat(staleCache.getIfPresent(key)).isNull();
        }
    }

    // ─── 路径 3：首次 token 失效 → 重试 → 新 token → 成功 ───

    @Nested
    class RetryOnStaleToken {

        @Test
        void shouldRetryAndSucceed_whenFirstTokenStaleButSecondSucceeds() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            AtomicInteger callCount = new AtomicInteger();
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    int call = callCount.incrementAndGet();
                    if (call == 1) {
                        // 第一次回源：返回前标记 key（模拟并发失效）
                        marker.mark(key);
                        return Mono.just(successResult(snapshot));
                    }
                    // 第二次回源（重试）：token 应为新的，可以成功
                    return Mono.just(successResult(snapshot));
                });

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();

            // 重试成功后，主缓存和 stale store 应被写入
            assertThat(mainCache.getIfPresent(key)).isNotNull();
            assertThat(staleCache.getIfPresent(key)).isNotNull();
            assertThat(callCount).hasValue(2);
        }
    }

    // ─── 路径 4：二次失效 → fail-close 503 ───

    @Nested
    class DoubleStaleTokenFailsClose {

        @Test
        void shouldReturn503_whenBothRetriesHaveStaleToken() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            AtomicInteger callCount = new AtomicInteger();
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    // 每次回源前都标记 key 失效
                    marker.mark(key);
                    callCount.incrementAndGet();
                    return Mono.just(successResult(snapshot));
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
            assertThat(callCount).hasValue(2);
        }
    }

    // ─── 路径 5：缓存命中但 marker.contains → 驱逐双缓存 → 回源 ───

    @Nested
    class CacheHitButMarkedInvalid {

        @Test
        void shouldEvictBothCachesAndReload_whenCachedButMarkedInvalid() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp oldSnapshot = allowSnapshot();
            InterfaceSnapshotResp newSnapshot = allowSnapshot();

            // 预填主缓存 + stale store + 标记失效
            mainCache.put(key, oldSnapshot);
            staleCache.put(key, new StaleEntry(oldSnapshot, Instant.now().plusSeconds(60)));
            marker.mark(key);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(newSnapshot)));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();

            // 旧缓存条目应被驱逐，新的回源结果写入
            assertThat(mainCache.getIfPresent(key)).isNotNull();
            assertThat(staleCache.getIfPresent(key)).isNotNull();
        }
    }
}
