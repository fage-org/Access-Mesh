package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker.LoadToken;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
import cn.ac.fage.accessmesh.gateway.config.FailMode;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher;
import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher.Decision;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
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
import java.time.Instant;

/**
 * 接口级权限过滤器（T-PERM-001 快照模式 / T-PERM-017 C4 条件 Gateway 重评 / T-GW-002 fail-mode）
 * <p>
 * 缓存维度：(tenantId,subjectTypeCode,userId,serviceCode)→InterfaceSnapshotResp。
 * 鉴权流程：
 * <ol>
 *   <li>本地查快照 → 未命中回源拉取并缓存（permission-center 实时构建全量快照）</li>
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
 * T-PERM-017 C4：含条件 entry 不再直接放行——能内联评估的本地评，不能下发的走 check-interface fallback。
 * T-GW-002 fail-mode：permission-center 不可达时按 {@link FailMode} 兜底——
 * {@code CLOSED}(默认)→503 拒绝 / {@code OPEN}(demo)→放行 / {@code STALE_ALLOW}→陈旧快照续命(T-GW-003 已实现)。
 * {@code StaleLoadDiscardedException}（回源并发失效）不受 fail-mode 影响，始终 503（显式撤销 > 不可达兜底）。
 * P1：显式失效({@code invalidationMarker}命中)后回源失败也不走 fail-mode，始终 503。
 * P2：仅 {@link PermCenterUnreachableException}（远端不可达）走 fail-mode 三模分支；其他异常始终 fail-closed。
 * T-GW-004 监控指标：{@code gateway.perm.unreachable}（不可达计数）+ {@code gateway.perm.fallback}（兜底模式计数）。
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
    /** exchange 属性键：标记当前请求已知权限被显式撤销（perm:invalidate 已到达），fail-mode 不适用 */
    private static final String EXPLICITLY_INVALIDATED_ATTR = "permExplicitlyInvalidated";

    private final PermissionClient permissionClient;
    private final Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache;
    private final Cache<String, StaleEntry> staleSnapshotCache;
    private final InvalidationMarker invalidationMarker;
    private final InterfaceSnapshotLoadRegistry loadRegistry;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;
    private final int staleGraceSeconds;
    private final FailMode failMode;

    // T-GW-004 监控指标（所有 gateway.perm.fallback counter 统一使用 {mode, reason} 标签集，保证 Prometheus 兼容）
    private final Counter unreachableSnapshotCounter;
    private final Counter unreachableCheckInterfaceCounter;
    private final Counter fallbackClosedDeniedCounter;
    private final Counter fallbackOpenAllowedCounter;
    private final Counter fallbackStaleNoEntryCounter;
    private final Counter fallbackStaleExpiredCounter;
    private final Counter fallbackStaleInvalidatedCounter;
    private final Counter fallbackStaleAllowedCounter;
    private final Counter fallbackStaleDeniedCounter;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient       权限校验客户端
     * @param interfaceSnapshotCache 接口快照缓存
     * @param staleSnapshotCache     陈旧快照缓存
     * @param invalidationMarker     失效标记
     * @param loadRegistry           回源去重注册表
     * @param gatewayProperties      网关配置属性
     * @param objectMapper           JSON序列化工具
     * @param meterRegistry          Micrometer 指标注册表（T-GW-004）
     */
    public PermissionFilter(PermissionClient permissionClient,
                            Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache,
                            Cache<String, StaleEntry> staleSnapshotCache,
                            InvalidationMarker invalidationMarker,
                            InterfaceSnapshotLoadRegistry loadRegistry,
                            GatewayProperties gatewayProperties,
                            ObjectMapper objectMapper,
                            MeterRegistry meterRegistry) {
        this.permissionClient = permissionClient;
        this.interfaceSnapshotCache = interfaceSnapshotCache;
        this.staleSnapshotCache = staleSnapshotCache;
        this.invalidationMarker = invalidationMarker;
        this.loadRegistry = loadRegistry;
        this.objectMapper = objectMapper;
        GatewayProperties.Cache.L1 l1 = gatewayProperties.getCache().getL1();
        this.ttlSeconds = l1.getTtlSeconds();
        this.staleGraceSeconds = l1.getStaleGraceSeconds();
        this.failMode = gatewayProperties.getPermission().getFailMode();

        // T-GW-004：初始化监控指标
        // 所有 gateway.perm.fallback counter 统一使用 {mode, reason} 标签集，
        // 保证 Prometheus 同名指标 label set 一致（否则 scrape 只导出先注册的子集）
        this.unreachableSnapshotCounter = Counter.builder("gateway.perm.unreachable")
            .tag("source", "snapshot")
            .description("Permission-center unreachable during snapshot fetch")
            .register(meterRegistry);
        this.unreachableCheckInterfaceCounter = Counter.builder("gateway.perm.unreachable")
            .tag("source", "check_interface")
            .description("Permission-center unreachable during check-interface fallback")
            .register(meterRegistry);
        this.fallbackClosedDeniedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "closed").tag("reason", "denied")
            .description("Fail-closed: request denied when permission-center unreachable")
            .register(meterRegistry);
        this.fallbackOpenAllowedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "open").tag("reason", "allowed")
            .description("Fail-open: request allowed when permission-center unreachable")
            .register(meterRegistry);
        this.fallbackStaleNoEntryCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "stale").tag("reason", "no_entry")
            .description("Stale-allow: no stale entry available")
            .register(meterRegistry);
        this.fallbackStaleExpiredCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "stale").tag("reason", "expired")
            .description("Stale-allow: stale entry expired past staleUntil")
            .register(meterRegistry);
        this.fallbackStaleInvalidatedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "stale").tag("reason", "invalidated")
            .description("Stale-allow: stale entry explicitly invalidated")
            .register(meterRegistry);
        this.fallbackStaleAllowedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "stale").tag("reason", "allowed")
            .description("Stale-allow: stale snapshot matched ALLOW, request allowed")
            .register(meterRegistry);
        this.fallbackStaleDeniedCounter = Counter.builder("gateway.perm.fallback")
            .tag("mode", "stale").tag("reason", "denied")
            .description("Stale-allow: stale snapshot matched DENY/FALLBACK, request denied")
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

        String cacheKey = buildCacheKey(tenantId, subjectTypeCode, userId, serviceCode);
        InterfaceSnapshotResp cached = interfaceSnapshotCache.getIfPresent(cacheKey);

        if (cached != null) {
            if (!invalidationMarker.contains(cacheKey)) {
                return decide(exchange, chain, cached, serviceCode, httpMethod, path, clientIp,
                    subjectTypeCode, userId, tenantId, cacheKey);
            }
            // P1：显式失效（perm:invalidate 已到达），驱逐双缓存后标记 exchange，
            // 后续回源失败时 handleUnreachable 据此始终 503，不走 fail-mode
            interfaceSnapshotCache.invalidate(cacheKey);
            staleSnapshotCache.invalidate(cacheKey);
            exchange.getAttributes().put(EXPLICITLY_INVALIDATED_ATTR, Boolean.TRUE);
        }

        // 未命中：回源拉取快照（T-PERM-018：permission-center 实时构建全量快照）
        // switchIfEmpty 置于 flatMap 之前：将"快照为空"转为异常，避免 decide() 返回 Mono<Void>
        // （天然 empty）时误触发 switchIfEmpty → 重复写 403
        return loadSnapshot(cacheKey, subjectTypeCode, userId, serviceCode, tenantId, true)
            .switchIfEmpty(Mono.error(new EmptySnapshotException()))
            .flatMap(snapshot -> decide(exchange, chain, snapshot, serviceCode, httpMethod, path, clientIp,
                subjectTypeCode, userId, tenantId, cacheKey))
            .onErrorResume(EmptySnapshotException.class, e ->
                writeForbidden(exchange, "无接口访问权限"))
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                // 显式失效并发——不受 fail-mode 影响，始终 503（权限主动撤销 > 服务不可达兜底）
                log.warn("Discarded stale interface snapshot load after invalidation (key={})", cacheKey);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            // P2：仅 PermCenterUnreachableException 走 fail-mode 三模分支
            .onErrorResume(PermCenterUnreachableException.class, e -> {
                unreachableSnapshotCounter.increment();
                return handleUnreachable(exchange, chain, cacheKey, e.getCause());
            })
            // P2：非远端不可达异常（代码 bug / DTO 兼容等）始终 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error during permission check (key={}), failing closed", cacheKey, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * 根据快照本地匹配结果三态分发：ALLOW 放行 / FALLBACK 调 check-interface / DENY 拒绝。
     * <p>
     * T-PERM-017 C4：FALLBACK 分支处理 gateway_evaluable=false 或 conditionRules 未下发的含条件 entry。
     * 同步 HTTP 调 permission-center 实时鉴权，context 仅承载 clientIp（跨进程时钟一致性由 NTP 保证）。
     * </p>
     */
    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain,
                              InterfaceSnapshotResp snapshot, String serviceCode, String httpMethod,
                              String path, String clientIp,
                              String subjectTypeCode, Long userId, Long tenantId,
                              String cacheKey) {
        Decision decision = InterfaceSnapshotMatcher.match(snapshot, serviceCode, httpMethod, path, clientIp);
        return switch (decision) {
            case ALLOW -> chain.filter(exchange);
            case DENY -> writeForbidden(exchange, "无接口访问权限");
            case FALLBACK -> fallbackCheckInterface(exchange, chain, subjectTypeCode, userId,
                serviceCode, httpMethod, path, clientIp, tenantId, cacheKey);
        };
    }

    /**
     * 回退实时鉴权：含条件 entry 命中但 conditionRules 未下发 Gateway 时同步调 check-interface。
     * <p>
     * permission-center 不可达时按 fail-mode 兜底处理（T-GW-002 / T-GW-003）。
     * </p>
     */
    private Mono<Void> fallbackCheckInterface(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String subjectTypeCode, Long userId, String serviceCode,
                                              String httpMethod, String path, String clientIp, Long tenantId,
                                              String cacheKey) {
        return wrapRemoteErrors(permissionClient.checkInterface(subjectTypeCode, userId, serviceCode, httpMethod, path, clientIp, tenantId))
            .flatMap(result -> {
                CheckInterfaceResp data = result != null ? result.getData() : null;
                if (data != null && data.allowed()) {
                    return chain.filter(exchange);
                }
                String reason = data != null && data.reason() != null ? data.reason() : "无接口访问权限";
                return writeForbidden(exchange, reason);
            })
            // 远端不可达走 fail-mode 三模分支（T-GW-003：stale-allow 需 cacheKey 查 stale store）
            .onErrorResume(PermCenterUnreachableException.class, e -> {
                unreachableCheckInterfaceCounter.increment();
                return handleUnreachable(exchange, chain, cacheKey, e.getCause());
            })
            // 非远端异常始终 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error in fallback check-interface (serviceCode={}), failing closed", serviceCode, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * permission-center 不可达兜底处理（T-GW-002 fail-mode 三模 / T-GW-003 stale-allow 续命）。
     * <ul>
     *   <li>P1：若 exchange 已标记 {@code explicitlyInvalidated}，始终 503（显式撤销 > 兜底）</li>
     *   <li>{@link FailMode#CLOSED}：fail-closed，返回 503</li>
     *   <li>{@link FailMode#OPEN}：fail-open，放行请求（仅限演示环境）</li>
     *   <li>{@link FailMode#STALE_ALLOW}：从 stale store 取陈旧快照续命（双重检查 staleUntil + !invalidatedKeys）；
     *       FALLBACK 条件不可评估时视为 DENY（403）；无可用 stale 条目转 closed（503）</li>
     * </ul>
     */
    private Mono<Void> handleUnreachable(ServerWebExchange exchange, GatewayFilterChain chain,
                                         String cacheKey, Throwable error) {
        // P1：显式失效后回源失败——始终 fail-closed，不受 fail-mode 影响
        if (Boolean.TRUE.equals(exchange.getAttribute(EXPLICITLY_INVALIDATED_ATTR))) {
            log.warn("Permission-center unreachable after explicit invalidation ({}), fail-closed: {}",
                cacheKey, error.getMessage());
            return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
        }
        return switch (failMode) {
            case OPEN -> {
                log.warn("Permission-center unreachable ({}), fail-open allowing request: {}", cacheKey, error.getMessage());
                fallbackOpenAllowedCounter.increment();
                yield chain.filter(exchange);
            }
            case STALE_ALLOW -> tryStaleAllow(exchange, chain, cacheKey, error);
            case CLOSED -> {
                log.warn("Permission-center unreachable ({}), fail-closed denying request: {}", cacheKey, error.getMessage());
                fallbackClosedDeniedCounter.increment();
                yield writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            }
        };
    }

    /**
     * T-GW-003 stale-allow 续命逻辑：从 stale store 取陈旧快照，双重检查后本地匹配。
     * <ul>
     *   <li>stale store 有有效条目（未超 staleUntil + 未被显式失效标记）→ 本地匹配快照</li>
     *   <li>匹配结果 ALLOW → 放行 / DENY 或 FALLBACK → 拒绝（条件不可评估视为 DENY）</li>
     *   <li>无可用 stale 条目 → 降级 closed（503）</li>
     * </ul>
     */
    private Mono<Void> tryStaleAllow(ServerWebExchange exchange, GatewayFilterChain chain,
                                     String cacheKey, Throwable error) {
        StaleEntry staleEntry = staleSnapshotCache.getIfPresent(cacheKey);
        if (staleEntry == null) {
            log.warn("Permission-center unreachable ({}), stale-allow: no stale entry, failing closed: {}",
                cacheKey, error.getMessage());
            fallbackStaleNoEntryCounter.increment();
            return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
        }
        // 双重检查：staleUntil 未过期（now < staleUntil，含边界拒绝）+ 未被显式失效标记
        if (!Instant.now().isBefore(staleEntry.staleUntil())) {
            log.warn("Permission-center unreachable ({}), stale-allow: stale entry expired (staleUntil={}), failing closed: {}",
                cacheKey, staleEntry.staleUntil(), error.getMessage());
            fallbackStaleExpiredCounter.increment();
            return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
        }
        if (invalidationMarker.contains(cacheKey)) {
            log.warn("Permission-center unreachable ({}), stale-allow: key explicitly invalidated, failing closed: {}",
                cacheKey, error.getMessage());
            fallbackStaleInvalidatedCounter.increment();
            return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
        }

        // stale 条目有效，本地匹配陈旧快照
        InterfaceSnapshotResp staleSnapshot = staleEntry.snapshot();
        String serviceCode = extractServiceCodeFromExchange(exchange);
        String httpMethod = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);
        Decision decision = InterfaceSnapshotMatcher.match(staleSnapshot, serviceCode, httpMethod, path, clientIp);

        if (decision == Decision.ALLOW) {
            log.info("Permission-center unreachable ({}), stale-allow: matched ALLOW from stale snapshot", cacheKey);
            fallbackStaleAllowedCounter.increment();
            return chain.filter(exchange);
        }
        // DENY 或 FALLBACK：条件不可评估时视为 DENY——陈旧快照显示无明确授权，安全拒绝
        log.warn("Permission-center unreachable ({}), stale-allow: stale snapshot result={}, failing closed: {}",
            cacheKey, decision, error.getMessage());
        fallbackStaleDeniedCounter.increment();
        if (decision == Decision.FALLBACK) {
            return writeForbidden(exchange, "无接口访问权限（条件权限不可评估）");
        }
        return writeForbidden(exchange, "无接口访问权限");
    }

    /**
     * 从 exchange 的路由元数据提取 serviceCode。
     */
    private String extractServiceCodeFromExchange(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return "";
        }
        return String.valueOf(route.getMetadata().getOrDefault("serviceCode", route.getId()));
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
     * P1：仅 WebClientRequestException（连接/超时）、5xx WebClientResponseException、
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
     * 判断异常是否为 permission-center 远端不可达。
     * <ul>
     *   <li>{@link WebClientRequestException}：连接拒绝 / DNS / 超时等 IO 错误</li>
     *   <li>{@link WebClientResponseException} 且 5xx：服务端错误</li>
     *   <li>{@link java.util.concurrent.TimeoutException}：Reactor 超时</li>
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
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Mono<InterfaceSnapshotResp> loadSnapshot(String cacheKey, String subjectTypeCode,
                                                     Long userId, String serviceCode, Long tenantId,
                                                     boolean retryWhenTokenInvalid) {
        return loadRegistry.load(cacheKey, () -> {
                LoadToken token = invalidationMarker.beginLoad(cacheKey);
                // P1：仅对 WebClient 远端不可达错误包装为 PermCenterUnreachableException；
                // flatMap 内部错误（extractSnapshot / putSnapshotIfCurrent 等）不做包装，
                // 由外层 catch-all 兜底 fail-closed
                return wrapRemoteErrors(permissionClient.interfaceSnapshot(subjectTypeCode, userId, serviceCode, tenantId))
                    .flatMap(result -> {
                        InterfaceSnapshotResp snapshot = extractSnapshot(result);
                        if (snapshot == null) {
                            return Mono.empty();
                        }
                        if (!putSnapshotIfCurrent(cacheKey, snapshot, token)) {
                            return Mono.<InterfaceSnapshotResp>error(new StaleLoadDiscardedException());
                        }
                        return Mono.just(snapshot);
                    });
            })
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                if (retryWhenTokenInvalid) {
                    return loadSnapshot(cacheKey, subjectTypeCode, userId, serviceCode, tenantId, false);
                }
                return Mono.error(e);
            });
    }

    private boolean putSnapshotIfCurrent(String cacheKey, InterfaceSnapshotResp snapshot, LoadToken token) {
        Instant staleUntil = Instant.now().plusSeconds((long) ttlSeconds + staleGraceSeconds);
        return invalidationMarker.commitIfCurrent(token, () -> {
            interfaceSnapshotCache.put(cacheKey, snapshot);
            staleSnapshotCache.put(cacheKey, new StaleEntry(snapshot, staleUntil));
        });
    }

    @Override
    public int getOrder() {
        return -60;
    }

    /**
     * 构建快照缓存键
     * <p>
     * 格式：perm:snapshot:tenantId:subjectTypeCode:userId:serviceCode
     * </p>
     */
    private String buildCacheKey(Long tenantId, String subjectTypeCode, Long userId, String serviceCode) {
        return InterfaceSnapshotCacheKeys.build(tenantId, subjectTypeCode, userId, serviceCode);
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
     * permission-center 远端不可达异常。
     * <p>
     * 仅包装 WebClient / 网络超时 / 远端 5xx 等明确不可达错误，
     * 供 fail-mode 三模分支处理。其他异常（代码 bug、DTO 兼容等）不应走 fail-mode。
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
}
