package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
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

/**
 * 接口级权限过滤器（T-PERM-001 快照模式）
 * <p>
 * 缓存维度从 (user,service,method,path)→Boolean 改为 (tenantId,subjectTypeCode,userId,serviceCode)→InterfaceSnapshotResp。
 * 鉴权流程：本地查快照 → 命中则本地匹配放行/拒绝 → 未命中回源拉取快照后本地匹配。
 * permission-center 不可达时 fail-close（503）；stale-allow 由 T-GW-003 实现。
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
    private static final String CACHE_KEY_PREFIX = "perm:snapshot:";

    private final PermissionClient permissionClient;
    private final Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient       权限校验客户端
     * @param interfaceSnapshotCache 接口快照缓存
     * @param objectMapper           JSON序列化工具
     */
    public PermissionFilter(PermissionClient permissionClient,
                            Cache<String, InterfaceSnapshotResp> interfaceSnapshotCache,
                            ObjectMapper objectMapper) {
        this.permissionClient = permissionClient;
        this.interfaceSnapshotCache = interfaceSnapshotCache;
        this.objectMapper = objectMapper;
    }

    /**
     * 过滤器执行逻辑
     * <p>
     * 1. 检查是否跳过认证
     * 2. 从exchange attributes获取用户ID和租户ID
     * 3. 提取路由元数据（服务编码、HTTP方法、路径）
     * 4. 查本地快照缓存，命中则本地匹配放行/拒绝
     * 5. 未命中回源拉取快照，缓存后本地匹配
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void> 处理结果
     */
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

        String cacheKey = buildCacheKey(tenantId, subjectTypeCode, userId, serviceCode);
        InterfaceSnapshotResp cached = interfaceSnapshotCache.getIfPresent(cacheKey);

        // 本地快照命中：直接本地匹配
        if (cached != null) {
            if (InterfaceSnapshotMatcher.matches(cached, serviceCode, httpMethod, path)) {
                return chain.filter(exchange);
            }
            return writeForbidden(exchange, "无接口访问权限");
        }

        // 未命中：回源拉取快照（T-PERM-018：permission-center 实时构建全量快照）
        return permissionClient.interfaceSnapshot(subjectTypeCode, userId, serviceCode, tenantId)
            .flatMap(result -> {
                InterfaceSnapshotResp snapshot = extractSnapshot(result);
                if (snapshot == null) {
                    return writeForbidden(exchange, "无接口访问权限");
                }
                // 缓存拉取到的全量快照
                interfaceSnapshotCache.put(cacheKey, snapshot);
                if (InterfaceSnapshotMatcher.matches(snapshot, serviceCode, httpMethod, path)) {
                    return chain.filter(exchange);
                }
                return writeForbidden(exchange, "无接口访问权限");
            })
            .onErrorResume(e -> {
                // permission-center不可达 — fail-close（stale-allow 由 T-GW-003 实现）
                log.warn("Permission-center unreachable, denying request: {}", e.getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
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
     * 获取过滤器执行顺序
     *
     * @return 顺序值
     */
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
        return CACHE_KEY_PREFIX + tenantId + ":" + subjectTypeCode + ":" + userId + ":" + serviceCode;
    }

    /**
     * 将对象转换为Long类型
     *
     * @param obj 待转换对象
     * @return Long值，转换失败返回null
     */
    private Long toLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 写入禁止访问响应
     */
    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    /**
     * 写入未找到响应
     */
    private Mono<Void> writeNotFound(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.NOT_FOUND, 404, "服务不存在");
    }

    /**
     * 写入服务不可用响应
     */
    private Mono<Void> writeServiceUnavailable(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, message);
    }

    /**
     * 写入错误响应
     */
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
}
