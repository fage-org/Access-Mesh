package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker.LoadToken;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.cache.StaleEntry;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * 接口级权限过滤器（T-PERM-001 快照模式 / T-PERM-017 C4 条件 Gateway 重评）
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
 * permission-center 不可达时 fail-close（503），stale-allow 由 T-GW-003 实现。执行顺序：-60
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
    private final Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache;
    private final Cache<String, StaleEntry> staleSnapshotCache;
    private final InvalidationMarker invalidationMarker;
    private final InterfaceSnapshotLoadRegistry loadRegistry;
    private final ObjectMapper objectMapper;
    private final int ttlSeconds;
    private final int staleGraceSeconds;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient       权限校验客户端
     * @param interfaceSnapshotCache 接口快照缓存
     * @param objectMapper           JSON序列化工具
     */
    public PermissionFilter(PermissionClient permissionClient,
                            Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache,
                            Cache<String, StaleEntry> staleSnapshotCache,
                            InvalidationMarker invalidationMarker,
                            InterfaceSnapshotLoadRegistry loadRegistry,
                            GatewayProperties gatewayProperties,
                            ObjectMapper objectMapper) {
        this.permissionClient = permissionClient;
        this.interfaceSnapshotCache = interfaceSnapshotCache;
        this.staleSnapshotCache = staleSnapshotCache;
        this.invalidationMarker = invalidationMarker;
        this.loadRegistry = loadRegistry;
        this.objectMapper = objectMapper;
        GatewayProperties.Cache.L1 l1 = gatewayProperties.getCache().getL1();
        this.ttlSeconds = l1.getTtlSeconds();
        this.staleGraceSeconds = l1.getStaleGraceSeconds();
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
                    subjectTypeCode, userId, tenantId);
            }
            interfaceSnapshotCache.invalidate(cacheKey);
            staleSnapshotCache.invalidate(cacheKey);
        }

        // 未命中：回源拉取快照（T-PERM-018：permission-center 实时构建全量快照）
        return loadSnapshot(cacheKey, subjectTypeCode, userId, serviceCode, tenantId, true)
            .flatMap(snapshot -> decide(exchange, chain, snapshot, serviceCode, httpMethod, path, clientIp,
                subjectTypeCode, userId, tenantId))
            .switchIfEmpty(Mono.defer(() -> writeForbidden(exchange, "无接口访问权限")))
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                log.warn("Discarded stale interface snapshot load after invalidation (key={})", cacheKey);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            .onErrorResume(e -> {
                // permission-center不可达 — fail-close（stale-allow 由 T-GW-003 实现）
                log.warn("Permission-center unreachable, denying request: {}", e.getMessage());
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
                              String subjectTypeCode, Long userId, Long tenantId) {
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
     * 与本类未命中快照路径同样在 permission-center 不可达时 fail-close 503。
     * </p>
     */
    private Mono<Void> fallbackCheckInterface(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String subjectTypeCode, Long userId, String serviceCode,
                                              String httpMethod, String path, String clientIp, Long tenantId) {
        return permissionClient.checkInterface(subjectTypeCode, userId, serviceCode, httpMethod, path, clientIp, tenantId)
            .flatMap(result -> {
                CheckInterfaceResp data = result != null ? result.getData() : null;
                if (data != null && data.allowed()) {
                    return chain.filter(exchange);
                }
                String reason = data != null && data.reason() != null ? data.reason() : "无接口访问权限";
                return writeForbidden(exchange, reason);
            })
            .onErrorResume(e -> {
                log.warn("含条件 entry fallback check-interface 失败 — fail-close: {}", e.getMessage());
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

    private Mono<InterfaceSnapshotResp> loadSnapshot(String cacheKey, String subjectTypeCode,
                                                     Long userId, String serviceCode, Long tenantId,
                                                     boolean retryWhenTokenInvalid) {
        return loadRegistry.load(cacheKey, () -> {
                LoadToken token = invalidationMarker.beginLoad(cacheKey);
                return permissionClient.interfaceSnapshot(subjectTypeCode, userId, serviceCode, tenantId)
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

    private static class StaleLoadDiscardedException extends RuntimeException {
    }
}
