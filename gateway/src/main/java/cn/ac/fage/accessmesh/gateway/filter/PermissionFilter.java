package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.ac.fage.accessmesh.gateway.service.PermissionClient;
import cn.ac.fage.accessmesh.perm.common.dto.resp.CheckInterfaceResp;
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
import java.net.SocketAddress;

/**
 * 接口级权限过滤器
 * <p>
 * 调用permission-center检查用户是否有访问目标接口的权限。
 * 使用L1 Caffeine缓存提升性能，减少远程调用次数。
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
    private static final String CACHE_KEY_PREFIX = "perm:check:";

    private final PermissionClient permissionClient;
    private final Cache<String, Boolean> permissionCheckCache;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖
     *
     * @param permissionClient     权限校验客户端
     * @param permissionCheckCache 权限校验结果缓存
     * @param gatewayProperties    网关配置属性
     * @param objectMapper         JSON序列化工具
     */
    public PermissionFilter(PermissionClient permissionClient,
                            Cache<String, Boolean> permissionCheckCache,
                            GatewayProperties gatewayProperties,
                            ObjectMapper objectMapper) {
        this.permissionClient = permissionClient;
        this.permissionCheckCache = permissionCheckCache;
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 过滤器执行逻辑
     * <p>
     * 1. 检查是否跳过认证
     * 2. 从exchange attributes获取用户ID和租户ID
     * 3. 提取路由元数据（服务编码、HTTP方法、路径）
     * 4. 查询L1缓存，命中则直接返回结果
     * 5. 缓存未命中则调用permission-center校验权限
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

        // 查询L1缓存
        String cacheKey = buildCacheKey(tenantId, subjectTypeCode, userId, serviceCode, httpMethod, path);
        Boolean cachedResult = permissionCheckCache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            if (cachedResult) {
                return chain.filter(exchange);
            } else {
                return writeForbidden(exchange, "无接口访问权限");
            }
        }

        // 调用permission-center进行权限校验
        return permissionClient.checkInterface(subjectTypeCode, userId, serviceCode, httpMethod, path,
                getClientIp(exchange), tenantId)
            .flatMap(result -> {
                if (result != null && result.getData() != null) {
                    CheckInterfaceResp resp = result.getData();
                    if (resp.allowed()) {
                        permissionCheckCache.put(cacheKey, true);
                        return chain.filter(exchange);
                    } else {
                        // 拒绝决策不缓存，确保权限授予后立即生效
                        return writeForbidden(exchange, mapReasonToMessage(resp.reason()));
                    }
                } else {
                    return writeForbidden(exchange, "无接口访问权限");
                }
            })
            .onErrorResume(e -> {
                // permission-center不可达 — 采用fail-close策略
                log.warn("Permission-center unreachable, denying request: {}", e.getMessage());
                return writeServiceUnavailable(exchange, "鉴权服务暂时不可用");
            });
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 顺序为-60，在认证过滤器之后执行
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -60;
    }

    /**
     * 获取客户端IP地址
     *
     * @param exchange 服务器Web交换对象
     * @return IP地址字符串
     */
    private String getClientIp(ServerWebExchange exchange) {
        SocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr instanceof InetSocketAddress inetAddr) {
            java.net.InetAddress inet = inetAddr.getAddress();
            if (inet != null) {
                return inet.getHostAddress();
            }
        }
        return "unknown";
    }

    /**
     * 构建缓存键
     * <p>
     * 格式：perm:check:tenantId:subjectTypeCode:userId:serviceCode:httpMethod:path
     * </p>
     *
     * @param tenantId   租户ID
     * @param subjectTypeCode 主体类型编码
     * @param userId     用户ID
     * @param serviceCode 服务编码
     * @param httpMethod HTTP方法
     * @param path       请求路径
     * @return 缓存键字符串
     */
    private String buildCacheKey(Long tenantId, String subjectTypeCode, Long userId, String serviceCode,
                                  String httpMethod, String path) {
        return CACHE_KEY_PREFIX + tenantId + ":" + subjectTypeCode + ":" + userId + ":"
            + serviceCode + ":" + httpMethod + ":" + path;
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
     * 将拒绝原因映射为用户友好的错误消息
     *
     * @param reason 拒绝原因编码
     * @return 错误消息
     */
    private String mapReasonToMessage(String reason) {
        if (reason == null) return "无接口访问权限";
        return switch (reason) {
            case "USER_DISABLED" -> "用户已停用";
            case "ROLE_DISABLED" -> "角色已停用";
            case "NO_ROLE" -> "用户无有效角色";
            case "NO_PERMISSION" -> "无接口访问权限";
            case "API_NOT_REGISTERED" -> "接口未注册";
            default -> "无接口访问权限";
        };
    }

    /**
     * 写入禁止访问响应
     *
     * @param exchange 服务器Web交换对象
     * @param message  错误消息
     * @return Mono<Void> 响应结果
     */
    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.FORBIDDEN, 403, message);
    }

    /**
     * 写入未找到响应
     *
     * @param exchange 服务器Web交换对象
     * @return Mono<Void> 响应结果
     */
    private Mono<Void> writeNotFound(ServerWebExchange exchange) {
        return writeError(exchange, HttpStatus.NOT_FOUND, 404, "服务不存在");
    }

    /**
     * 写入服务不可用响应
     *
     * @param exchange 服务器Web交换对象
     * @param message  错误消息
     * @return Mono<Void> 响应结果
     */
    private Mono<Void> writeServiceUnavailable(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, message);
    }

    /**
     * 写入错误响应
     *
     * @param exchange   服务器Web交换对象
     * @param httpStatus HTTP状态码
     * @param code       业务错误码
     * @param message    错误消息
     * @return Mono<Void> 响应结果
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