package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.gateway.cache.GatewayCacheCatalog;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker;
import cn.ac.fage.accessmesh.gateway.cache.InvalidationMarker.LoadToken;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheInvalidator;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotCacheKeys;
import cn.ac.fage.accessmesh.gateway.cache.InterfaceSnapshotLoadRegistry;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.ac.fage.accessmesh.gateway.service.InterfaceAdmissionMatcher;
import cn.ac.fage.accessmesh.gateway.service.InterfaceAdmissionMatcher.Decision;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
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
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

/**
 * 操作准入过滤器（T-ACCESS-059 无迁移期切换；旧 API:ACCESS 快照链已删除）。
 * <p>
 * 缓存维度：(tenantId,subjectTypeCode,userId,serviceCode)→InterfaceAdmissionSnapshotResp，
 * 经统一 {@link CacheService}（L1_ONLY，catalog {@code gw:interface-admission-snapshot}，TTL≤15s，
 * 与旧快照 schema 命名空间隔离 N22）。判定流程：
 * <ol>
 *   <li>本地查准入快照 → 未命中回源拉取并缓存</li>
 *   <li>本地判定（{@link InterfaceAdmissionMatcher}，固定判定序）返回四态：
 *     <ul>
 *       <li>{@link Decision#ALLOW} → MAY_ENTER 放行（业务层做最终实例检查）</li>
 *       <li>{@link Decision#FALLBACK} → 同步调 interface-admission 在线判定（仅传 clientIp）</li>
 *       <li>{@link Decision#DENY} → 403 拒绝（正常准入拒绝）</li>
 *       <li>{@link Decision#CONFIG_FAULT} → 503 配置故障（路由歧义/未知 schema/时效失败，
 *           不伪装用户无权限）</li>
 *     </ul>
 *   </li>
 * </ol>
 * </p>
 * <p>
 * 安全边界（沿 T-ACCESS-008，回退=回滚网关版本）：
 * <ul>
 *   <li><b>固定 fail-closed</b>：回源不可达、显式失效后回源失败、服务端配置故障信封
 *       （20070/20071 等 data=null）、未知异常一律 503，不回落旧 API:ACCESS、不 stale-allow</li>
 *   <li><b>5 秒全链路硬截止</b>：快照加载全流程共享同一墙钟截止，重试不重新计时，
 *       超截止不写缓存固定 503</li>
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
    private static final Duration DENIAL_AUDIT_TIMEOUT = Duration.ofMillis(500);

    private final PermissionClient permissionClient;
    private final CacheService cacheService;
    private final InvalidationMarker invalidationMarker;
    private final InterfaceSnapshotLoadRegistry loadRegistry;
    private final InterfaceSnapshotCacheInvalidator invalidator;
    private final ObjectMapper objectMapper;
    private final Duration snapshotLoadDeadline;

    // 监控指标（固定 fail-closed；来源 tag 区分快照/在线回源）
    private final Counter unreachableSnapshotCounter;
    private final Counter unreachableAdmissionCounter;
    private final Counter fallbackClosedDeniedCounter;
    private final Counter deadlineExceededCounter;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient       权限校验客户端（interface-admission 族）
     * @param cacheService           统一缓存服务（L1_ONLY gw:interface-admission-snapshot）
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
            .description("Access-service unreachable during admission snapshot fetch")
            .register(meterRegistry);
        this.unreachableAdmissionCounter = Counter.builder("gateway.perm.unreachable")
            .tag("source", "interface_admission")
            .description("Access-service unreachable during online admission fallback")
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
        InterfaceAdmissionSnapshotResp cached = cacheService.get(
            GatewayCacheCatalog.INTERFACE_ADMISSION_SNAPSHOT, tenantId, identifier);

        if (cached != null) {
            if (!invalidationMarker.contains(loadKey) && isFresh(cached)) {
                return decide(exchange, chain, cached, serviceCode, httpMethod, path, clientIp,
                    subjectTypeCode, userId, tenantId);
            }
            // 显式失效（perm:invalidate 已到达）或快照自身已过期（缓存 TTL 与服务端 expiresAt
            // 起点差＝回源延迟，缓存尾部存在"缓存仍在、快照已过期"窗口）：驱逐后按未命中回源
            // 重载——过期是自然的 miss 语义，不是终端 503
            cacheService.evict(GatewayCacheCatalog.INTERFACE_ADMISSION_SNAPSHOT, tenantId, identifier);
        }

        // 未命中：回源拉取快照，整段加载流程置于 5 秒全链路硬截止内
        // switchIfEmpty 置于 flatMap 之前：将"快照为空"转为异常，避免 decide() 返回 Mono<Void>
        // （天然 empty）时误触发 switchIfEmpty → 重复写错误响应
        return loadSnapshotWithinDeadline(loadKey, identifier, subjectTypeCode, userId,
            serviceCode, tenantId)
            .switchIfEmpty(Mono.error(new EmptySnapshotException()))
            .flatMap(snapshot -> decide(exchange, chain, snapshot, serviceCode, httpMethod, path, clientIp,
                subjectTypeCode, userId, tenantId))
            .onErrorResume(EmptySnapshotException.class, e ->
                // 回源 data=null：服务端错误信封（20070/20071 等）→ 配置故障 503，不伪装用户无权限
                writeServiceUnavailable(exchange, "鉴权配置故障"))
            .onErrorResume(StaleLoadDiscardedException.class, e -> {
                // 显式失效并发——固定 fail-closed 503（权限主动撤销 > 服务不可达兜底）
                log.warn("Discarded stale admission snapshot load after invalidation (key={})", loadKey);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            .onErrorResume(DeadlineExceededException.class, e ->
                writeServiceUnavailable(exchange, "鉴权服务暂时不可用"))
            .onErrorResume(AccessServiceUnreachableException.class, e -> {
                // 固定 fail-closed（不回落旧 API:ACCESS）
                unreachableSnapshotCounter.increment();
                fallbackClosedDeniedCounter.increment();
                log.warn("Access-service unreachable ({}), fail-closed denying request: {}",
                    loadKey, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            // 非远端不可达异常（代码 bug / DTO 兼容等）固定 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error during admission check (key={}), failing closed", loadKey, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /** 缓存命中可用性预检：失效代际之外补快照时效检查（过期条目按 miss 重载，不硬 503）。 */
    private static boolean isFresh(InterfaceAdmissionSnapshotResp snapshot) {
        return snapshot.expiresAt() == null || LocalDateTime.now().isBefore(snapshot.expiresAt());
    }

    /**
     * 本地判定四态分发：ALLOW 放行 / FALLBACK 调 interface-admission 在线判定 /
     * DENY 拒绝 / CONFIG_FAULT 503（N14 路由歧义与 N22 模式/时效失败）。
     */
    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain,
                              InterfaceAdmissionSnapshotResp snapshot, String serviceCode, String httpMethod,
                              String path, String clientIp,
                              String subjectTypeCode, Long userId, Long tenantId) {
        Decision decision = InterfaceAdmissionMatcher.match(snapshot, serviceCode, httpMethod,
            path, clientIp, LocalDateTime.now());
        return switch (decision) {
            case ALLOW -> chain.filter(exchange);
            case DENY -> writeForbidden(exchange, "无接口访问权限");
            case CONFIG_FAULT -> writeServiceUnavailable(exchange, "鉴权配置故障");
            case FALLBACK -> fallbackOnlineAdmission(exchange, chain, subjectTypeCode, userId,
                serviceCode, httpMethod, path, clientIp, tenantId);
        };
    }

    /**
     * 回退在线准入判定：条件候选不可本地评估（或 CONTEXT_DEFERRED）且无其他通过分支时
     * 同步调 interface-admission（按相同规则与可信环境在线求值，N11）。
     * <p>
     * access-service 不可达或返回错误信封（data=null，含 20070/20071 配置故障）固定
     * fail-closed 503（T-ACCESS-008 形态沿承；新链失败不回落旧 API:ACCESS）。
     * </p>
     */
    private Mono<Void> fallbackOnlineAdmission(ServerWebExchange exchange, GatewayFilterChain chain,
                                               String subjectTypeCode, Long userId, String serviceCode,
                                               String httpMethod, String path, String clientIp, Long tenantId) {
        return wrapRemoteErrors(permissionClient.interfaceAdmission(
                subjectTypeCode, userId, serviceCode, httpMethod, path, clientIp, tenantId))
            .flatMap(result -> {
                InterfaceAdmissionResp data = result != null ? result.getData() : null;
                if (data == null) {
                    // 服务端错误信封（配置故障/技术故障）→ fail-closed 503
                    return writeServiceUnavailable(exchange, "鉴权配置故障");
                }
                if ("MAY_ENTER".equals(data.decision())) {
                    return chain.filter(exchange);
                }
                String reason = data.reason() != null ? data.reason() : "无接口访问权限";
                return writeForbidden(exchange, reason);
            })
            // 固定 fail-closed
            .onErrorResume(AccessServiceUnreachableException.class, e -> {
                unreachableAdmissionCounter.increment();
                fallbackClosedDeniedCounter.increment();
                log.warn("Access-service unreachable in online admission fallback ({}), fail-closed: {}",
                    serviceCode, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            })
            // 非远端异常固定 fail-closed
            .onErrorResume(e -> {
                log.error("Unexpected error in online admission fallback (serviceCode={}), failing closed",
                    serviceCode, e);
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * 提取请求 clientIp：直接采用 Gateway 自身观测的 remoteAddr（单一可信来源，T-GW-008）。
     * <p>
     * 外部 X-Forwarded-For / X-Real-IP 已由 HeaderCleanFilter 清洗且不以头为评估输入——
     * 即使清洗配置被误删，网关自评也不采信可伪造头；下游 access-service 消费
     * Gateway 重建的 XFF（值同为本观测值）。
     * </p>
     * 用于条件评估 IP_WHITELIST / IP_BLACKLIST 与在线回源上下文。
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : null;
    }

    /**
     * 将远端 WebClient 调用中仅"不可达"类异常包装为 {@link AccessServiceUnreachableException}。
     * <p>
     * 仅 WebClientRequestException（连接/超时）、5xx WebClientResponseException、
     * TimeoutException 视为不可达；解码/DTO/4xx 等错误不包装，由外层 catch-all 兜底 fail-closed。
     * </p>
     */
    private <T> Mono<T> wrapRemoteErrors(Mono<T> upstream) {
        return upstream.onErrorResume(e -> {
            if (isRemoteUnreachable(e)) {
                return Mono.error(new AccessServiceUnreachableException(e));
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
     * 在 5 秒全链路硬截止内执行快照加载。
     * <p>
     * 截止时刻在进入加载流程前一次确定；组合流（含失效竞争触发的一次重试）整体
     * 置于 {@code Mono.timeout} 之下——重试共享同一截止，不重新计时。写入缓存前
     * 再次校验截止时刻，超时的结果不写缓存直接 fail-closed。超时统一转为
     * {@link DeadlineExceededException} 计数后 503。
     * </p>
     */
    private Mono<InterfaceAdmissionSnapshotResp> loadSnapshotWithinDeadline(String loadKey, String identifier,
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

    private Mono<InterfaceAdmissionSnapshotResp> loadSnapshot(String loadKey, String identifier,
                                                              String subjectTypeCode,
                                                              Long userId, String serviceCode, Long tenantId,
                                                              boolean retryWhenTokenInvalid, long deadlineNanos) {
        return loadRegistry.load(loadKey, () -> {
                LoadToken token = invalidationMarker.beginLoad(loadKey);
                // 仅对 WebClient 远端不可达错误包装为 AccessServiceUnreachableException；
                // flatMap 内部错误不做包装，由外层 catch-all 兜底 fail-closed
                return wrapRemoteErrors(permissionClient.interfaceAdmissionSnapshot(
                        subjectTypeCode, userId, serviceCode, tenantId))
                    .flatMap(result -> {
                        InterfaceAdmissionSnapshotResp snapshot = result == null ? null : result.getData();
                        if (snapshot == null) {
                            // data=null：服务端错误信封（配置故障/技术故障）——空流触发
                            // EmptySnapshotException → 上层 503（不缓存、不伪装用户无权限）
                            return Mono.empty();
                        }
                        // 截止校验先于缓存写入：超时的结果不写缓存
                        if (System.nanoTime() > deadlineNanos) {
                            deadlineExceededCounter.increment();
                            log.warn("Admission snapshot load exceeded full-chain deadline ({}), discard without caching",
                                loadKey);
                            return Mono.<InterfaceAdmissionSnapshotResp>error(new DeadlineExceededException());
                        }
                        if (!putSnapshotIfCurrent(tenantId, identifier, loadKey, snapshot, token)) {
                            return Mono.error(new StaleLoadDiscardedException());
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
                                         InterfaceAdmissionSnapshotResp snapshot, LoadToken token) {
        return invalidationMarker.commitIfCurrent(token, () -> {
            cacheService.put(GatewayCacheCatalog.INTERFACE_ADMISSION_SNAPSHOT, tenantId, identifier, snapshot);
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
        Object user = exchange.getAttribute(USER_ID_ATTR);
        Object tenant = exchange.getAttribute(TENANT_ID_ATTR);
        Long userId = user == null ? null : toLong(user);
        Long tenantId = tenant == null ? null : toLong(tenant);
        if (userId == null || tenantId == null) {
            return writeError(exchange, HttpStatus.FORBIDDEN, 403, message);
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String service = route == null ? "unknown" : String.valueOf(route.getMetadata().getOrDefault("serviceCode", route.getId()));
        String requestId = String.valueOf(exchange.getAttributes().computeIfAbsent("requestId", key -> java.util.UUID.randomUUID().toString()));
        var audit = new cn.ac.fage.accessmesh.common.model.GatewayDenialAuditReq(userId,
            limited(service, 128), exchange.getRequest().getMethod().name(),
            limited(exchange.getRequest().getURI().getRawPath(), 512), limited(message, 128),
            limited(resolveClientIp(exchange), 64), limited(requestId, 64));
        // 有界尽力写入：失败只留兜底证据，不把权限拒绝变成放行或 503，也不创建脱离请求的订阅。
        return Mono.defer(() -> permissionClient.recordDenial(tenantId, audit))
            .timeout(DENIAL_AUDIT_TIMEOUT)
            .onErrorResume(error -> {
                log.warn("Gateway拒绝审计发送失败: requestId={}, tenantId={}, userId={}, serviceCode={}, path={}, reason={}, error={}",
                    audit.requestId(), tenantId, userId, audit.serviceCode(), audit.path(), audit.reason(), error.getClass().getSimpleName());
                return Mono.empty();
            })
            .then(Mono.defer(() -> writeError(exchange, HttpStatus.FORBIDDEN, 403, message)));
    }

    private static String limited(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
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
            // traceId 与 requestId 同值别名（T-PERM-021 F1.d 外评处置：对齐 GlobalExceptionHandler，
            // NON_NULL 下缺省即字段消失，403/503 路径不落单）
            resp.setTraceId(requestId.toString());
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
     * 其他异常（代码 bug、DTO 兼容等）不应包装。不可达固定 fail-closed。
     * </p>
     */
    static class AccessServiceUnreachableException extends RuntimeException {
        AccessServiceUnreachableException(Throwable cause) {
            super(cause);
        }
    }

    private static class StaleLoadDiscardedException extends RuntimeException {
    }

    /** 回源返回空数据（R.data=null，服务端错误信封）时抛出，触发 503 配置故障响应 */
    private static class EmptySnapshotException extends RuntimeException {
    }

    /** 快照加载超过 5 秒全链路硬截止（含写入前截止校验失败），固定 fail-closed 503 */
    static class DeadlineExceededException extends RuntimeException {
    }
}
