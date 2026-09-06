package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.filter.PermissionFilter;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gateway 主动失效与在途回源的代际竞态测试（T-ACCESS-008 复评 P1 修复回归）。
 * <p>
 * 修复前行为：① 租户级失效先 evictAll 再递增 epoch——慢 evictAll 期间旧回源
 * 可通过代际校验写回旧快照（复活窗口）；② 用户级失效只枚举跟踪索引——首次
 * 回源（尚未 track）的在途 key 不被标记，撤权后旧回源正常提交。
 * 修复后：失效先递增代际/标记（作废在途 LoadToken）再清缓存；用户级候选
 * 纳入在途回源注册表。旧回源提交被作废并重试拉取新数据。
 * </p>
 */
class GatewayInvalidationRaceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "example-service";

    private PermissionClient permissionClient;
    private CacheService cacheService;
    private InvalidationMarker marker;
    private InterfaceSnapshotLoadRegistry loadRegistry;
    private InterfaceSnapshotCacheInvalidator invalidator;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        permissionClient = mock(PermissionClient.class);
        marker = new InvalidationMarker();
        loadRegistry = new InterfaceSnapshotLoadRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        cacheService = Mockito.spy(new DefaultCacheService(null, null,
            new CaffeineLocalCacheStore(objectMapper, null, new CacheProperties()),
            new CacheProperties(), null));
        invalidator = new InterfaceSnapshotCacheInvalidator(cacheService, marker, loadRegistry,
            new CacheProperties());
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private PermissionFilter createFilter() {
        GatewayProperties props = new GatewayProperties();
        props.getPermission().setSnapshotLoadDeadline(Duration.ofSeconds(5));
        return new PermissionFilter(permissionClient, cacheService, marker, loadRegistry,
            invalidator, props, new ObjectMapper(), new SimpleMeterRegistry());
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

    private InterfaceSnapshotResp snapshot(String marker) {
        return new InterfaceSnapshotResp(List.of(
            new ApiPermissionEntry(SERVICE_CODE + ":" + marker, null, null, false, null, null, ScopeMode.ALL)
        ));
    }

    private String identifier() {
        return InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);
    }

    /**
     * 场景①：租户级失效期间（evictAll 阻塞模拟慢 SCAN 删除）旧回源到达——
     * 修复后先递增 epoch，旧回源提交被作废，重试拉取新快照，旧快照不得写回缓存。
     */
    @Test
    void tenantWideEvictDuringSlowEvictAll_shouldDiscardStaleCommitAndRefetch()
        throws Exception {
        // evictAll 阻塞 400ms：构造"清理进行中"窗口
        doAnswer(inv -> {
            Thread.sleep(400);
            return inv.callRealMethod();
        }).when(cacheService).evictAll(any(), any());

        InterfaceSnapshotResp stale = snapshot("stale");
        InterfaceSnapshotResp fresh = snapshot("fresh");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loadStarted = new CountDownLatch(1);
        when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
            .thenAnswer(inv -> {
                loadStarted.countDown();
                if (calls.incrementAndGet() == 1) {
                    // 旧回源：150ms 后返回（失效发生后、慢 evictAll 期间到达）
                    return Mono.just(R.ok(stale)).delayElement(Duration.ofMillis(150));
                }
                // 重试拉取新快照：延迟到慢 evictAll（400ms）完成后提交，
                // 使最终缓存状态确定（新快照在清理结束后写入并保留）
                return Mono.just(R.ok(fresh)).delayElement(Duration.ofMillis(600));
            });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PermissionFilter filter = createFilter();
            Future<?> request = executor.submit(() ->
                filter.filter(buildExchange(), chain).block(Duration.ofSeconds(5)));

            assertThat(loadStarted.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50); // 确保 beginLoad 已完成（在途 token 已建立）
            // 租户级失效（仅 roleIds）：修复后先递增 epoch 再执行慢 evictAll
            invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(99L), Set.of(), Set.of()));

            request.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 旧快照被作废（重试发生）；新快照在清理完成后写入并保留
        assertThat(calls.get()).isEqualTo(2);
        InterfaceSnapshotResp cached =
            cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier());
        assertThat(cached).isNotNull();
        assertThat(cached.allowedApis().get(0).serviceCode()).isEqualTo(SERVICE_CODE + ":fresh");
    }

    /**
     * 场景②：订阅重连 clearAll 期间旧回源到达——同样先递增 epoch，旧快照不得写回。
     */
    @Test
    void clearAllDuringSlowEvictAll_shouldDiscardStaleCommitAndRefetch()
        throws Exception {
        doAnswer(inv -> {
            Thread.sleep(400);
            return inv.callRealMethod();
        }).when(cacheService).evictAll(any(), any());

        // 预置同租户另一用户的已跟踪快照：clearAll 据索引执行（慢）evictAll，
        // 同时保证当前请求用户未命中缓存、必然走回源
        String otherUserId = InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, 99L, SERVICE_CODE);
        cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, otherUserId, snapshot("old"));
        invalidator.track(TENANT_ID, otherUserId);

        InterfaceSnapshotResp stale = snapshot("stale");
        InterfaceSnapshotResp fresh = snapshot("fresh");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loadStarted = new CountDownLatch(1);
        when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
            .thenAnswer(inv -> {
                loadStarted.countDown();
                if (calls.incrementAndGet() == 1) {
                    return Mono.just(R.ok(stale)).delayElement(Duration.ofMillis(150));
                }
                return Mono.just(R.ok(fresh)).delayElement(Duration.ofMillis(600));
            });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PermissionFilter filter = createFilter();
            Future<?> request = executor.submit(() ->
                filter.filter(buildExchange(), chain).block(Duration.ofSeconds(5)));

            assertThat(loadStarted.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50);
            invalidator.clearAll();

            request.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(calls.get()).isEqualTo(2);
        InterfaceSnapshotResp cached =
            cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier());
        assertThat(cached).isNotNull();
        assertThat(cached.allowedApis().get(0).serviceCode()).isEqualTo(SERVICE_CODE + ":fresh");
    }

    /**
     * 场景③（修复 P1-2）：首次回源在途（尚未 track）收到仅含 userIds 的撤权事件——
     * 在途 key 从 loadRegistry 纳入候选并标记，旧回源提交被作废，重试拉取新快照。
     */
    @Test
    void userLevelEvictDuringFirstLoad_shouldMarkInFlightKeyAndDiscardStaleCommit()
        throws Exception {
        InterfaceSnapshotResp stale = snapshot("stale");
        InterfaceSnapshotResp fresh = snapshot("fresh");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loadStarted = new CountDownLatch(1);
        // 首次回源提交闸门：撤权事件先发出、再放行 stale——「已开始（注册表在途）、未提交」
        // 窗口被确定性命中。原 delayElement(150ms)+sleep(50) 的固定余量在机器负载下会被
        // 主线程调度延迟击穿（T-ACCESS-031 -T 模块并行日常形态下实证失败：evict 落到
        // stale 提交之后，无在途 key 可标、不触发重试）
        CompletableFuture<Void> staleCommitGate = new CompletableFuture<>();
        when(permissionClient.interfaceSnapshot(anyString(), anyLong(), anyString(), anyLong()))
            .thenAnswer(inv -> {
                loadStarted.countDown();
                if (calls.incrementAndGet() == 1) {
                    return Mono.just(R.ok(stale)).delayUntil(v -> Mono.fromFuture(staleCommitGate));
                }
                return Mono.just(R.ok(fresh));
            });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            PermissionFilter filter = createFilter();
            Future<?> request = executor.submit(() ->
                filter.filter(buildExchange(), chain).block(Duration.ofSeconds(5)));

            // 首次回源已开始（loadRegistry.load 经 computeIfAbsent 先注册在途再订阅），
            // 但尚未写缓存——跟踪索引侧的失效标记为空
            assertThat(loadStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(marker.contains(TENANT_ID + ":" + identifier())).isFalse();
            // 仅 userIds 的用户级撤权事件：先于 stale 提交发出（确定性在途未提交窗口）
            invalidator.evict(new PermInvalidateEvent(TENANT_ID, Set.of(), Set.of(USER_ID), Set.of()));
            staleCommitGate.complete(null);

            request.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 修复后：在途 key 被标记 → 旧回源作废 → 重试新快照入缓存
        assertThat(calls.get()).isEqualTo(2);
        InterfaceSnapshotResp cached =
            cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, TENANT_ID, identifier());
        assertThat(cached).isNotNull();
        assertThat(cached.allowedApis().get(0).serviceCode()).isEqualTo(SERVICE_CODE + ":fresh");
    }
}
