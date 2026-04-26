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
 * Injects standardized headers (X-Request-Id, X-User-Id, X-Tenant-Id, etc.)
 * into downstream requests for services to consume.
 * Order: -50
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .headers(headers -> {
                // Always inject X-Request-Id
                Object requestId = exchange.getAttribute(REQUEST_ID_ATTR);
                if (requestId != null) {
                    headers.set(headerConfig.getRequestId(), requestId.toString());
                }

                Boolean skipAuth = exchange.getAttribute(SKIP_AUTH_ATTR);
                if (Boolean.TRUE.equals(skipAuth)) {
                    // Whitelist requests only get X-Request-Id
                    return;
                }

                // Inject user-related headers from exchange attributes
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

    @Override
    public int getOrder() {
        return -50;
    }
}
