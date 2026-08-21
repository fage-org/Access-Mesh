package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.GatewayCacheCatalog;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker.LoadToken;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheInvalidator;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher;
import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher.Decision;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 接口级权限过滤器（T-PERM-001 快照模式 / T-PERM-017 条件 Gateway 重评 / T-ACCESS-008 安全边界）
 * <p>
 * 缓存维度：(tenantId,subjectTypeCode,userId,serviceCode)→InterfaceSnapshotResp，
 * 经统一 {@link CacheService}（L1_ONLY，catalog {@code gw:interface-snapshot}，TTL≤15s）。
 * 鉴权流程：
 * <ol>
 *   <li>本地查快照 → 未命中回源拉取并缓存（access-service 实时构建全量快照）</li>
 *   <li>本地匹配 ({@link InterfaceSnapshotMatcher}) 返回三态：
 *     <ul>
 *       <li>{@link Decision#ALLOW} → 直接放行</li>
 *       <li>{@link Decision#FALLBACK} → 同步调 {@code /perm/check-interface} 实时鉴权（仅传 clientIp）</li>
 *       <li>{@link Decision#DENY} → 403 拒绝</li>
 *     </ul>
 *   </li>
 * </ol>
 * </p>
 * <p>
 * T-ACCESS-008 安全边界：
 * <ul>
 *   <li><b>固定 fail-closed</b>：删除可切换 fail-mode 及 open/stale-allow 分支——
 *       回源不可达、显式失效后回源失败、未知异常一律 503 拒绝，不得绕过授权或使用过期结果</li>
 *   <li><b>5 秒全链路硬截止</b>：一次授权请求触发的整个快照加载流程（服务发现/负载均衡、
 *       连接、请求发送、access-service 处理、响应读取/解码、失效竞争重试）共享同一墙钟截止；
 *       重试不重新计时。超过截止不得写入 Gateway 缓存并固定 503。
 *       连接/响应分段超时（WebClientConfig）不替代该总截止</li>
 *   <li>保留失效代际校验（{@link InvalidationMarker}）、per-key 回源去重
 *       （{@link InterfaceSnapshotLoadRegistry}）与订阅重连全量清空</li>
 * </ul>
 * 执行顺序：-60
 * </p>
 */
@Component
public class PermissionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PermissionFilter.class);
    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String USER_ID_ATTR = "userId";
    private static final String TENANT_ID_ATTR = "tenantId";
    private static final String SUBJECT_TYPE_CODE_ATTR = "subjectTypeCode";

    private final PermissionClient permissionClient;
    private final CacheService cacheService;
    private final InvalidationMarker invalidationMarker;
    private final InterfaceSnapshotLoadRegistry loadRegistry;
    private final InterfaceSnapshotCacheInvalidator invalidator;
    private final ObjectMapper objectMapper;
    private final Duration snapshotLoadDeadline;

    // T-ACCESS-008 监控指标（固定 fail-closed；open/stale 系列随 fail-mode 删除）
    private final Counter unreachableSnapshotCounter;
    private final Counter unreachableCheckInterfaceCounter;
    private final Counter fallbackClosedDeniedCounter;
    private final Counter deadlineExceededCounter;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient       权限校验客户端
     * @param cacheService           统一缓存服务（L1_ONLY gw:interface-snapshot）
     * @param invalidationMarker     失效代际标记
     * @param loadRegistry           回源去重注册表
     * @param invalidator            快照失效器（回填成功后 track 登记跟踪索引）
     * @param gatewayProperties      网关配置属性
     * @param objectMapper           JSON序列化工具
     * @param meterRegistry          Micrometer 指标注册表
     */
    public PermissionFilter(PermissionClient permissionClient,
                            CacheService cacheService,
                            InvalidationMarker invalidationMarker,
                            InterfaceSnapshotLoadRegistry loadRegistry,
                            InterfaceSnapshotCacheInvalidator invalidator,
                            GatewayProperties gatewayProperties,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.permissionClient = permissionClient;
        this.cacheService = cacheService;
        this.invalidationMarker = invalidationMarker;
        this.loadRegistry = loadRegistry;
        this.invalidator = invalidator;
        this.objectMapper = objectMapper;
        this.snapshotLoadDeadline = gatewayProperties.getPermission().getSnapshotLoadDeadline();

        this.unreachableSnapshotCounter = Counter.builder("gateway.perm.unreachable")
            .tag("source", "snapshot")
            .description("Access-service unreachable during snapshot fetch")
            .register(meterRegistry);
        this.unreachableCheckInterfaceCounter = Counter.builder("gateway.perm.unreachable")
            .tag("source", "check_interface")
            .description("Access-service unreachable during check-interface fallback")
            .register(meterRegistry);
        this.fallbackClosedDeniedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "closed").tag("reason", "denied")
            .description("Fail-closed: request denied when access-service unreachable")
            .register(meterRegistry);
        this.deadlineExceededCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "closed").tag("reason", "deadline_exceeded")
            .description("Fail-closed: snapshot load exceeded the full-chain wall-clock deadline")
            .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Boolean skipAuth = exchange.getAttribute(SKIP_AUTH_ATTR);
        if (Boolean.TRUE.equals(skipAuth)) {
            return chain.filter(exchange);
        }

        Object userIdObj = exchange.getAttribute(USER_ID_ATTR);
        Object tenantIdObj = exchange.getAttribute(TENANT_ID_ATTR);
        Object subjectTypeCodeObj = exchange.getAttribute(SUBJECT_TYPE_CODE_ATTR);
        if (userIdObj == null || tenantIdObj == null || subjectTypeCodeObj == null) {
            return writeForbidden(exchange, "无接口访问权限");
        }

        Long userId = toLong(userIdObj);
        Long tenantId = toLong(tenantIdObj);
        String subjectTypeCode = subjectTypeCodeObj.toString();
        if (userId == null || tenantId == null || subjectTypeCode.isBlank()) {
            return writeForbidden(exchange, "无接口访问权限");
        }

        // 提取路由元数据
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return writeNotFound(exchange);
        }

        String serviceCode = String.valueOf(route.getMetadata().getOrDefault("serviceCode", route.getId()));
        String httpMethod = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);

        String identifier = InterfaceSnapshotCacheKeys.build(subjectTypeCode, userId, serviceCode);
        // marker / in-flight 去重命名空间使用租户限定键（identifier 不含租户，
        // 直接复用会跨租户串扰）；CacheService 自行组装含租户前缀的完整缓存键
        String loadKey = tenantId + ":" + identifier;
        InterfaceSnapshotResp cached = cacheService.get(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId, identifier);

        if (cached != null) {
            if (!invalidationMarker.contains(loadKey)) {
                return decide(exchange, chain, cached, serviceCode, httpMethod, path, clientIp,
                    subjectTypeCode, userId, tenantId, identifier);
            }
            // 显式失效（perm:invalidate 已到达）：驱逐本地快照后强制回源
            cacheService.evict(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId, identifier);
        }

        // 未命中：回源拉取快照，整段加载流程置于 5 秒全链路硬截止内（T-ACCESS-008）
        // switchIfEmpty 置于 flatMap 之前：将"快照为空"转为异常，避免 decide() 返回 Mono<Void>
        // （天然 empty）时误触发 switchIfEmpty → 重复写 403
        return loadSnapshotWithinDeadline(exchange, loadKey, identifier, subjectTypeCode, userId,
            serviceCode, tenantId)
            .switchIfEmpty(Mono.error(new EmptySnapshotException()))
            .flatMap(snapshot -> decide(exchange, chain, snapshot, serviceCode, httpMethod, path, clientIp,
                subjectTypeCode, userId, tenantId, identifier))
            .onErrorResume(EmptySnapshotException.class, e ->
                writeForbidden(exchange, "无接口访问权限"))
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                // 显式失效并发——固定 fail-closed 503（权限主动撤销 > 服务不可达兜底）
                log.warn("Discarded stale interface snapshot load after invalidation (key={})", loadKey);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            .onErrorResume(DeadlineExceededException.class, e ->
                writeServiceUnavailable(exchange, "鉴权服务暂时不可用"))
            .onErrorResume(PermCenterUnreachableException.class, e -> {
                // T-ACCESS-008：固定 fail-closed（fail-mode/open/stale-allow 已删除）
                unreachableSnapshotCounter.increment();
                fallbackClosedDeniedCounter.increment();
                log.warn("Permission-center unreachable ({}), fail-closed denying request: {}",
                    loadKey, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            // 非远端不可达异常（代码 bug / DTO 兼容等）固定 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error during permission check (key={}), failing closed", loadKey, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * 根据快照本地匹配结果三态分发：ALLOW 放行 / FALLBACK 调 check-interface / DENY 拒绝。
     * <p>
     * T-PERM-017 C4：FALLBACK 分支处理 gateway_evaluable=false 或 conditionRules 未下发的含条件 entry。
     * 同步 HTTP 调 access-service 实时鉴权，context 仅承载 clientIp（跨进程时钟一致性由 NTP 保证）。
     * </p>
     */
    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain,
                              InterfaceSnapshotResp snapshot, String serviceCode, String httpMethod,
                              String path, String clientIp,
                              String subjectTypeCode, Long userId, Long tenantId,
                              String identifier) {
        Decision decision = InterfaceSnapshotMatcher.match(snapshot, serviceCode, httpMethod, path, clientIp);
        return switch (decision) {
            case ALLOW -> chain.filter(exchange);
            case DENY -> writeForbidden(exchange, "无接口访问权限");
            case FALLBACK -> fallbackCheckInterface(exchange, chain, subjectTypeCode, userId,
                serviceCode, httpMethod, path, clientIp, tenantId);
        };
    }

    /**
     * 回退实时鉴权：含条件 entry 命中但 conditionRules 未下发 Gateway 时同步调 check-interface。
     * <p>
     * access-service 不可达时固定 fail-closed（T-ACCESS-008）。
     * </p>
     */
    private Mono<Void> fallbackCheckInterface(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String subjectTypeCode, Long userId, String serviceCode,
                                              String httpMethod, String path, String clientIp, Long tenantId) {
        return wrapRemoteErrors(permissionClient.checkInterface(subjectTypeCode, userId, serviceCode, httpMethod, path, clientIp, tenantId))
            .flatMap(result -> {
                CheckInterfaceResp data = result != null ? result.getData() : null;
                if (data != null && data.allowed()) {
                    return chain.filter(exchange);
                }
                String reason = data != null && data.reason() != null ? data.reason() : "无接口访问权限";
                return writeForbidden(exchange, reason);
            })
            // 固定 fail-closed（T-ACCESS-008）
            .onErrorResume(PermCenterUnreachableException.class, e -> {
                unreachableCheckInterfaceCounter.increment();
                fallbackClosedDeniedCounter.increment();
                log.warn("Permission-center unreachable in check-interface fallback ({}), fail-closed: {}",
                    serviceCode, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            // 非远端异常固定 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error in fallback check-interface (serviceCode={}), failing closed", serviceCode, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * 提取请求 clientIp：优先 X-Forwarded-For 首段 → X-Real-IP → 远端地址。
     * 用于条件评估 IP_WHITELIST / IP_BLACKLIST 与 fallback check-interface 上下文。
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = headers.getFirst("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }

    /**
     * 从 PermResult 提取快照数据
     */
    private InterfaceSnapshotResp extractSnapshot(PermResult<InterfaceSnapshotResp> result) {
        if (result == null || result.getData() == null) {
            return null;
        }
        return result.getData();
    }

    /**
     * 将远端 WebClient 调用中仅"不可达"类异常包装为 {@link PermCenterUnreachableException}。
     * <p>
     * 仅 WebClientRequestException（连接/超时）、5xx WebClientResponseException、
     * TimeoutException 视为不可达；解码/DTO/4xx 等错误不包装，由外层 catch-all 兜底 fail-closed。
     * </p>
     */
    private <T> Mono<T> wrapRemoteErrors(Mono<T> upstream) {
        return upstream.onErrorResume(e -> {
            if (isRemoteUnreachable(e)) {
                return Mono.error(new PermCenterUnreachableException(e));
            }
            return Mono.error(e);
        });
    }

    /**
     * 判断异常是否为 access-service 远端不可达。
     * <ul>
     *   <li>{@link WebClientRequestException}：连接拒绝 / DNS / 超时等 IO 错误</li>
     *   <li>{@link WebClientResponseException} 且 5xx：服务端错误</li>
     *   <li>{@link TimeoutException}：Reactor 超时</li>
     * </ul>
     * 其余（解码错误、4xx、本地异常等）均非"不可达"。
     */
    private boolean isRemoteUnreachable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientRequestException) {
                return true;
            }
            if (current instanceof WebClientResponseException wcre) {
                return wcre.getStatusCode().is5xxServerError();
            }
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 在 5 秒全链路硬截止内执行快照加载（T-ACCESS-008）。
     * <p>
     * 截止时刻在进入加载流程前一次确定；组合流（含失效竞争触发的一次重试）整体
     * 置于 {@code Mono.timeout} 之下——重试共享同一截止，不重新计时。写入缓存前
     * 再次校验截止时刻，超时的结果不写缓存直接 fail-closed。超时统一转为
     * {@link DeadlineExceededException} 计数后 503。
     * </p>
     */
    private Mono<InterfaceSnapshotResp> loadSnapshotWithinDeadline(ServerWebExchange exchange,
                                                                   String loadKey, String identifier,
                                                                   String subjectTypeCode,
                                                                   Long userId, String serviceCode,
                                                                   Long tenantId) {
        return Mono.defer(() -> {
            long deadlineNanos = System.nanoTime() + snapshotLoadDeadline.toNanos();
            return loadSnapshot(loadKey, identifier, subjectTypeCode, userId, serviceCode, tenantId,
                true, deadlineNanos)
                .timeout(snapshotLoadDeadline)
                .onErrorResume(TimeoutException.class, e -> {
                    deadlineExceededCounter.increment();
                    return Mono.error(new DeadlineExceededException());
                });
        });
    }

    private Mono<InterfaceSnapshotResp> loadSnapshot(String loadKey, String identifier, String subjectTypeCode,
                                                     Long userId, String serviceCode, Long tenantId,
                                                     boolean retryWhenTokenInvalid, long deadlineNanos) {
        return loadRegistry.load(loadKey, () -> {
                LoadToken token = invalidationMarker.beginLoad(loadKey);
                // 仅对 WebClient 远端不可达错误包装为 PermCenterUnreachableException；
                // flatMap 内部错误（extractSnapshot / putSnapshotIfCurrent 等）不做包装，
                // 由外层 catch-all 兜底 fail-closed
                return wrapRemoteErrors(permissionClient.interfaceSnapshot(subjectTypeCode, userId, serviceCode, tenantId))
                    .flatMap(result -> {
                        InterfaceSnapshotResp snapshot = extractSnapshot(result);
                        if (snapshot == null) {
                            return Mono.empty();
                        }
                        // 截止校验先于缓存写入：超时的结果不写缓存（T-ACCESS-008）
                        if (System.nanoTime() > deadlineNanos) {
                            deadlineExceededCounter.increment();
                            log.warn("Interface snapshot load exceeded full-chain deadline ({}), discard without caching",
                                loadKey);
                            return Mono.<InterfaceSnapshotResp>error(new DeadlineExceededException());
                        }
                        if (!putSnapshotIfCurrent(tenantId, identifier, loadKey, snapshot, token)) {
                            return Mono.<InterfaceSnapshotResp>error(new StaleLoadDiscardedException());
                        }
                        return Mono.just(snapshot);
                    });
            })
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                if (retryWhenTokenInvalid) {
                    // 失效竞争重试：共享同一截止时刻（deadlineNanos 原样传递，不重新计时）
                    return loadSnapshot(loadKey, identifier, subjectTypeCode, userId, serviceCode, tenantId,
                        false, deadlineNanos);
                }
                return Mono.error(e);
            });
    }

    /**
     * 写入快照缓存（代际校验）。
     * <p>
     * 代际失效（LoadToken 过期）不写并返回 false（走重试/503）；写入成功后向失效器
     * 登记跟踪索引（用户级精确失效枚举用）。截止校验由调用方在写入前完成。
     * </p>
     */
    private boolean putSnapshotIfCurrent(Long tenantId, String identifier, String loadKey,
                                         InterfaceSnapshotResp snapshot, LoadToken token) {
        return invalidationMarker.commitIfCurrent(token, () -> {
            cacheService.put(GatewayCacheCatalog.INTERFACE_SNAPSHOT, tenantId, identifier, snapshot);
            invalidator.track(tenantId, identifier);
        });
    }

    @Override
    public int getOrder() {
        return -60;
    }

    private Long toLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    private Mono<Void> writeNotFound(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.NOT_FOUND, 404, "服务不存在");
    }

    private Mono<Void> writeServiceUnavailable(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, message);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus httpStatus,
                                   int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        GatewayResponse resp = GatewayResponse.error(code, message);
        Object requestId = exchange.getAttribute("requestId");
        if (requestId != null) {
            resp.setRequestId(requestId.toString());
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(resp);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    /**
     * access-service 远端不可达异常。
     * <p>
     * 仅包装 WebClient / 网络超时 / 远端 5xx 等明确不可达错误；
     * 其他异常（代码 bug、DTO 兼容等）不应包装。T-ACCESS-008：不可达固定 fail-closed。
     * </p>
     */
    static class PermCenterUnreachableException extends RuntimeException {
        PermCenterUnreachableException(Throwable cause) {
            super(cause);
        }
    }

    private static class StaleLoadDiscardedException extends RuntimeException {
    }

    /** 回源返回空快照（PermResult.data 为 null）时抛出，触发 403 */
    private static class EmptySnapshotException extends RuntimeException {
    }

    /** 快照加载超过 5 秒全链路硬截止（含写入前截止校验失败），固定 fail-closed 503 */
    static class DeadlineExceededException extends RuntimeException {
    }
}
