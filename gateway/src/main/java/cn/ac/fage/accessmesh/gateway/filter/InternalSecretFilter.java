package cn.ac.fage.accessmesh.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Injects X-Internal-Secret header into all downstream requests
 * when perm.internal-secret is configured. This allows downstream
 * services to verify that requests originated from the gateway.
 * Order: -40 (after HeaderEnrichFilter at -50)
 */
@Component
public class InternalSecretFilter implements GlobalFilter, Ordered {

    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${perm.internal-secret:}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (internalSecret == null || internalSecret.isBlank()) {
            // Secret not configured — skip injection
            return chain.filter(exchange);
        }

        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .headers(headers -> headers.set(SECRET_HEADER, internalSecret))
                .build())
            .build());
    }

    @Override
    public int getOrder() {
        return -40;
    }
}
