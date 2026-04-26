package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Validates Sa-Token and extracts user info into exchange attributes.
 * Uses getLoginIdByToken(token) which is thread-safe in reactive context.
 * Order: -70
 */
@Component
public class AuthTokenFilter implements GlobalFilter, Ordered {

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String USER_ID_ATTR = "userId";
    private static final String TENANT_ID_ATTR = "tenantId";
    private static final String USER_NAME_ATTR = "userName";

    private final ObjectMapper objectMapper;

    public AuthTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
            // Use getLoginIdByToken — does NOT rely on ThreadLocal, safe for WebFlux
            Object loginId = StpUtil.getLoginIdByToken(token);
            exchange.getAttributes().put(USER_ID_ATTR, loginId);

            // Read extra data from Sa-Token session using token-based API
            String loginIdStr = loginId.toString();
            try {
                Object tenantId = StpUtil.getExtra(loginIdStr, "tenantId");
                if (tenantId != null) {
                    exchange.getAttributes().put(TENANT_ID_ATTR, tenantId);
                }
            } catch (Exception e) {
                // Extra data may not be set — tenant info will be missing but user is still authenticated
            }

            try {
                Object username = StpUtil.getExtra(loginIdStr, "username");
                if (username != null) {
                    exchange.getAttributes().put(USER_NAME_ATTR, username.toString());
                }
            } catch (Exception e) {
                // Username may not be set
            }

        } catch (NotLoginException e) {
            return writeUnauthorized(exchange, 401, "登录已过期");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -70;
    }

    private String extractToken(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authHeader = headers.getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Fallback: check cookie
        var cookie = exchange.getRequest().getCookies().getFirst("Authorization");
        return cookie != null ? cookie.getValue() : null;
    }

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
