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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
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
 * PermissionFilter 组合路径回归测试（T-PERM-008 / T-ACCESS-008）
 * <p>
 * 覆盖高风险组合路径：回源写缓存+unmark、token失效丢弃、重试一次、
 * 二次失效fail-closed、缓存命中但marker标记→驱逐+回源、
 * 5秒全链路硬截止（超时不写缓存+503、重试共享截止不重新计时）。
 * </p>
 */
class PermissionFilterTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "admin-service";

    private PermissionClient permissionClient;
    private CacheService cacheService;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private InterfaceSnapshotCacheInvalidator invalidator;
    private GatewayProperties gatewayProperties;
    private ObjectMapper objectMapper;
    private PermissionFilter filter;

    private GatewayFilterChain chain;

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

        gatewayProperties = new GatewayProperties();
        gatewayProperties.getPermission().setSnapshotLoadDeadline(Duration.ofSeconds(5));

        filter = new PermissionFilter(permissionClient, cacheService, marker, loadRegistry,
            invalidator, gatewayProperties, objectMapper, new SimpleMeterRegistry());

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * 构建测试用 exchange（使用 mock response 避免 ReadOnlyHttpHeaders 问题）。
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
        return InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
    }

    private InterfaceSnapshotResp cachedSnapshot() {
        return cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, cacheKey());
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

    private HttpStatus awaitCapturedStatus(ServerWebExchange exchange, long timeoutSeconds)
        throws InterruptedException {
        AtomicReference<HttpStatus> capturedStatus = new AtomicReference<>();
        when(exchange.getResponse().setStatusCode(any())).thenAnswer(inv -> {
            capturedStatus.set(inv.getArgument(0));
            return true;
        });
        CountDownLatch latch = new CountDownLatch(1);
        filter.filter(exchange, chain).subscribe(__ -> {}, __ -> latch.countDown(), () -> latch.countDown());
        latch.await(timeoutSeconds, TimeUnit.SECONDS);
        return capturedStatus.get();
    }

    // ─── 路径 1：回源成功 → 写缓存 + unmark + track ───

    @Nested
    class LoadSuccessWritesCache {

        @Test
        void shouldWriteCacheAndUnmark_whenLoadSucceedsAndTokenCurrent()
            throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(snapshot)));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();
            // 快照缓存写入
            assertThat(cachedSnapshot()).isNotNull();
            // marker 已 unmark
            assertThat(marker.contains(TENANT_ID + ":" + key)).isFalse();
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
                    marker.mark(TENANT_ID + ":" + key);
                    return Mono.just(successResult(snapshot));
                });

            ServerWebExchange exchange = buildExchange();

            HttpStatus status = awaitCapturedStatus(exchange, 5);

            // 期望 503（固定 fail-closed）
            assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            // 快照缓存不应被写入
            assertThat(cachedSnapshot()).isNull();
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
                        marker.mark(TENANT_ID + ":" + key);
                        return Mono.just(successResult(snapshot));
                    }
                    return Mono.just(successResult(snapshot));
                });

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();
            assertThat(cachedSnapshot()).isNotNull();
            assertThat(callCount).hasValue(2);
        }
    }

    // ─── 路径 4：二次失效 → fail-closed 503 ───

    @Nested
    class DoubleStaleTokenFailsClose {

        @Test
        void shouldReturn503_whenBothRetriesHaveStaleToken() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();

            AtomicInteger callCount = new AtomicInteger();
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    marker.mark(TENANT_ID + ":" + key);
                    callCount.incrementAndGet();
                    return Mono.just(successResult(snapshot));
                });

            ServerWebExchange exchange = buildExchange();

            HttpStatus status = awaitCapturedStatus(exchange, 5);

            assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(callCount).hasValue(2);
        }
    }

    // ─── 路径 5：缓存命中但 marker.contains → 驱逐缓存 → 回源 ───

    @Nested
    class CacheHitButMarkedInvalid {

        @Test
        void shouldEvictCacheAndReload_whenCachedButMarkedInvalid() throws InterruptedException {
            String key = cacheKey();
            InterfaceSnapshotResp oldSnapshot = allowSnapshot();
            InterfaceSnapshotResp newSnapshot = allowSnapshot();

            // 预填快照缓存 + 标记失效
            cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, key, oldSnapshot);
            marker.mark(TENANT_ID + ":" + key);

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(newSnapshot)));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();
            // 旧缓存条目应被驱逐，新的回源结果写入
            assertThat(cachedSnapshot()).isNotNull();
        }
    }

    // ─── T-ACCESS-008：5 秒全链路硬截止 ───

    @Nested
    class SnapshotLoadDeadline {

        @Test
        void shouldReturn503AndSkipCache_whenLoadExceedsDeadline() throws InterruptedException {
            // 截止 200ms，回源延迟 1s → 超时
            gatewayProperties.getPermission().setSnapshotLoadDeadline(Duration.ofMillis(200));
            filter = new PermissionFilter(permissionClient, cacheService, marker, loadRegistry,
                invalidator, gatewayProperties, objectMapper, new SimpleMeterRegistry());

            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(allowSnapshot()))
                    .delayElement(Duration.ofSeconds(1)));

            ServerWebExchange exchange = buildExchange();
            HttpStatus status = awaitCapturedStatus(exchange, 5);

            assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            // 超时的结果不得写入 Gateway 缓存
            assertThat(cachedSnapshot()).isNull();
        }

        @Test
        void shouldShareDeadlineAcrossRetry_notResetByRetry() throws InterruptedException {
            // 截止 400ms；第一次回源 token 失效（立即返回触发重试），重试回源延迟 1s →
            // 重试沿用首次截止（累计已超时），不得重新计时
            gatewayProperties.getPermission().setSnapshotLoadDeadline(Duration.ofMillis(400));
            filter = new PermissionFilter(permissionClient, cacheService, marker, loadRegistry,
                invalidator, gatewayProperties, objectMapper, new SimpleMeterRegistry());

            String key = cacheKey();
            InterfaceSnapshotResp snapshot = allowSnapshot();
            AtomicInteger callCount = new AtomicInteger();
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() == 1) {
                        marker.mark(TENANT_ID + ":" + key);
                        return Mono.just(successResult(snapshot));
                    }
                    return Mono.just(successResult(snapshot))
                        .delayElement(Duration.ofSeconds(1));
                });

            ServerWebExchange exchange = buildExchange();
            HttpStatus status = awaitCapturedStatus(exchange, 5);

            assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            // 超时不写缓存
            assertThat(cachedSnapshot()).isNull();
            // 确实发生了重试（共享同一截止时刻传递）
            assertThat(callCount).hasValue(2);
        }

        @Test
        void shouldCompleteWithinDeadline_whenLoadFast() throws InterruptedException {
            when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(successResult(allowSnapshot())));

            ServerWebExchange exchange = buildExchange();
            boolean completed = awaitFilterCompletion(exchange, 5);

            assertThat(completed).isTrue();
            assertThat(cachedSnapshot()).isNotNull();
        }
    }
}
