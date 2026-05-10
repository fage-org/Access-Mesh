package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求头增强过滤器
 * <p>
 * 将标准化的请求头（X-Request-Id、X-User-Id、X-Tenant-Id等）注入到下游请求中，
 * 供后端服务使用。
 * 执行顺序：-50
 * </p>
 */
@Component
public class HeaderEnrichFilter implements GlobalFilter, Ordered {

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String REQUEST_ID_ATTR = "requestId";
    private static final String USER_ID_ATTR = "userId";
    private static final String TENANT_ID_ATTR = "tenantId";
    private static final String USER_NAME_ATTR = "userName";

    private final GatewayProperties.Header.Enrich headerConfig;

    public HeaderEnrichFilter(GatewayProperties gatewayProperties) {
        this.headerConfig = gatewayProperties.getHeader().getEnrich();
    }

    /**
     * 执行过滤器逻辑
     * <p>
     * 从交换对象的属性中提取用户身份信息，注入到请求头中。
     * 白名单请求仅注入请求ID，不注入用户身份信息。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .headers(headers -> {
                // 始终注入X-Request-Id
                Object requestId = exchange.getAttribute(REQUEST_ID_ATTR);
                if (requestId != null) {
                    headers.set(headerConfig.getRequestId(), requestId.toString());
                }

                Boolean skipAuth = exchange.getAttribute(SKIP_AUTH_ATTR);
                if (Boolean.TRUE.equals(skipAuth)) {
                    // 白名单请求仅注入请求ID
                    return;
                }

                // 从交换属性注入用户相关请求头
                Object userId = exchange.getAttribute(USER_ID_ATTR);
                if (userId != null) {
                    headers.set(headerConfig.getUserId(), userId.toString());
                }

                Object tenantId = exchange.getAttribute(TENANT_ID_ATTR);
                if (tenantId != null) {
                    headers.set(headerConfig.getTenantId(), tenantId.toString());
                }

                Object userName = exchange.getAttribute(USER_NAME_ATTR);
                if (userName != null) {
                    headers.set(headerConfig.getUserName(), userName.toString());
                }
            })
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-50，确保在权限校验之后、内部密钥注入之前执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -50;
    }
}