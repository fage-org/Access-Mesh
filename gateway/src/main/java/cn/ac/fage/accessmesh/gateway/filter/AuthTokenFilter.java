package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 认证令牌过滤器
 * <p>
 * 验证Sa-Token并提取用户信息到exchange attributes。
 * 使用getLoginIdByToken(token)方法，该方法在WebFlux响应式环境中线程安全，
 * 不依赖ThreadLocal。
 * 执行顺序：-70
 * </p>
 */
@Component
public class AuthTokenFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String USER_ID_ATTR = "userId";
    private static final String TENANT_ID_ATTR = "tenantId";
    private static final String USER_NAME_ATTR = "userName";

    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入JSON映射器
     *
     * @param objectMapper JSON序列化工具
     */
    public AuthTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 过滤器执行逻辑
     * <p>
     * 1. 检查是否跳过认证（白名单路径）
     * 2. 从请求中提取Token
     * 3. 使用Token验证登录状态
     * 4. 提取用户信息并存入exchange attributes
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

        String token = extractToken(exchange);
        if (token == null || token.isBlank()) {
            return writeUnauthorized(exchange, 401, "未登录");
        }

        try {
            // 使用getLoginIdByToken — 不依赖ThreadLocal，WebFlux环境安全
            Object loginId = StpUtil.getLoginIdByToken(token);
            exchange.getAttributes().put(USER_ID_ATTR, loginId);

            // 使用基于Token的API从Sa-Token session读取额外数据
            String loginIdStr = loginId.toString();
            try {
                Object tenantId = StpUtil.getExtra(loginIdStr, "tenantId");
                if (tenantId != null) {
                    exchange.getAttributes().put(TENANT_ID_ATTR, tenantId);
                } else {
                    log.warn("租户ID为空，loginId={}", loginIdStr);
                    return writeUnauthorized(exchange, 401, "租户信息缺失");
                }
            } catch (Exception e) {
                log.error("获取租户ID异常，loginId={}: {}", loginIdStr, e.getMessage());
                return writeUnauthorized(exchange, 401, "租户信息缺失");
            }

            try {
                Object username = StpUtil.getExtra(loginIdStr, "username");
                if (username != null) {
                    exchange.getAttributes().put(USER_NAME_ATTR, username.toString());
                }
            } catch (Exception e) {
                // 用户名可能未设置
            }

        } catch (NotLoginException e) {
            return writeUnauthorized(exchange, 401, "登录已过期");
        }

        return chain.filter(exchange);
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 顺序为-70，在白名单过滤器之后执行
     * </p>
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return -70;
    }

    /**
     * 从请求中提取Token
     * <p>
     * 优先从Authorization Header提取Bearer Token，
     * 备选方案从Cookie中提取。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @return Token字符串，不存在时返回null
     */
    private String extractToken(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authHeader = headers.getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // 备选方案：从Cookie提取
        var cookie = exchange.getRequest().getCookies().getFirst("Authorization");
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * 写入未授权响应
     *
     * @param exchange 服务器Web交换对象
     * @param code     错误码
     * @param message  错误消息
     * @return Mono<Void> 响应结果
     */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
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