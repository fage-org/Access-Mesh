package cn.ac.fage.accessmesh.gateway.filter;

import static cn.ac.fage.accessmesh.gateway.support.PermissionFilterTestSupport.routedExchange;

import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.gateway.cache.GatewayCacheCatalog;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheInvalidator;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
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
 * T-ACCESS-008 监控指标测试（T-ACCESS-059 操作准入链适配）：固定 fail-closed（open/stale 系列指标已随 fail-mode 删除）。
 * <p>
 * 指标命名：
 * <ul>
 *   <li>{@code gateway.perm.unreachable}（tag: source=snapshot|interface_admission）</li>
 *   <li>{@code gateway.perm.fallback}（tag: mode=closed, reason=denied|deadline_exceeded）</li>
 * </ul>
 */
class PermissionFilterMetricsTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String SUBJECT_TYPE_CODE = "USER";
    private static final String SERVICE_CODE = "example-service";

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
            .id("example-service-route")
            .uri(URI.create("lb://example-service"))
            .metadata("serviceCode", SERVICE_CODE)
            .predicate(exchange -> true)
            .build();

        // T-GW-008：clientIp 直用 Gateway 观测的 remoteAddr，X-Real-IP 等头不再被采信
        MockServerHttpRequest request;
        try {
            request = MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("/api/test"))
                .remoteAddress(new InetSocketAddress(
                    InetAddress.getByAddress(new byte[]{10, 0, 0, 1}), 443))
                .build();
        } catch (UnknownHostException e) {
            throw new IllegalStateException("字面 IP 解析不应失败", e);
        }

        return routedExchange(request, route, TENANT_ID, USER_ID, SUBJECT_TYPE_CODE);
    }

    private static WebClientRequestException connectionRefused() {
        return new WebClientRequestException(
            new java.net.ConnectException("Connection refused"),
            HttpMethod.GET, URI.create("http://access-service/api/access/auth/interface-admission-snapshot"),
            HttpHeaders.EMPTY);
    }

    private InterfaceAdmissionSnapshotResp allowSnapshot() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return new InterfaceAdmissionSnapshotResp(
            InterfaceAdmissionSnapshotResp.CURRENT_SCHEMA_VERSION, TENANT_ID,
            new InterfaceAdmissionSnapshotResp.Subject(SUBJECT_TYPE_CODE, String.valueOf(USER_ID)),
            SERVICE_CODE, now, now.plusSeconds(60), 0L,
            List.of(new InterfaceAdmissionSnapshotResp.RouteEntry("GET", "/api/test",
                new AdmissionRequirement("EXAMPLE", "VIEW"))),
            List.of(new InterfaceAdmissionSnapshotResp.OperationCandidateEntry(
                "EXAMPLE", "VIEW", null, true, "ALL", null)),
            "OPERATION_ADMISSION", true);
    }

    /** FALLBACK 快照：要求命中但仅有需远端求值的条件候选（内联缺失 → 回源在线判定）。 */
    private InterfaceAdmissionSnapshotResp fallbackSnapshot() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return new InterfaceAdmissionSnapshotResp(
            InterfaceAdmissionSnapshotResp.CURRENT_SCHEMA_VERSION, TENANT_ID,
            new InterfaceAdmissionSnapshotResp.Subject(SUBJECT_TYPE_CODE, String.valueOf(USER_ID)),
            SERVICE_CODE, now, now.plusSeconds(60), 0L,
            List.of(new InterfaceAdmissionSnapshotResp.RouteEntry("GET", "/api/test",
                new AdmissionRequirement("EXAMPLE", "VIEW"))),
            List.of(new InterfaceAdmissionSnapshotResp.OperationCandidateEntry(
                "EXAMPLE", "VIEW", 5L, false, "INSTANCE", null)),
            "OPERATION_ADMISSION", true);
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
            when(permissionClient.interfaceAdmissionSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "interface_admission")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementUnreachableCheckInterface_whenFallbackFails() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofSeconds(5));
            String key = InterfaceSnapshotCacheKeys.build(SUBJECT_TYPE_CODE, USER_ID, SERVICE_CODE);

            // 快照缓存放 FALLBACK 形态（条件候选不可本地评估），使 decide() 走在线准入回源
            cacheService.put(GatewayCacheCatalog.INTERFACE_ADMISSION_SNAPSHOT, TENANT_ID, key, fallbackSnapshot());

            when(permissionClient.interfaceAdmission(anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.unreachable", "source", "interface_admission")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.unreachable", "source", "snapshot")).isEqualTo(0.0);
        }
    }

    // ─── 固定 fail-closed 指标 ───

    @Nested
    class FailClosedMetrics {

        @Test
        void shouldIncrementFallbackClosedDenied_whenUnreachable() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofSeconds(5));
            when(permissionClient.interfaceAdmissionSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.error(connectionRefused()));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "denied")).isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "deadline_exceeded")).isEqualTo(0.0);
        }

        @Test
        void shouldIncrementDeadlineExceeded_whenLoadExceedsDeadline() throws InterruptedException {
            PermissionFilter filter = createFilter(Duration.ofMillis(150));
            when(permissionClient.interfaceAdmissionSnapshot(anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(R.ok(allowSnapshot()))
                    .delayElement(Duration.ofSeconds(1)));

            awaitCompletion(filter, buildExchange());

            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "deadline_exceeded"))
                .isEqualTo(1.0);
            assertThat(counterValue("gateway.perm.fallback", "mode", "closed", "reason", "denied")).isEqualTo(0.0);
        }
    }
}
