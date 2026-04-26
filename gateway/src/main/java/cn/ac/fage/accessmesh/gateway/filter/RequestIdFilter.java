package cn.ac.fage.accessmesh.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates or propagates X-Request-Id for every request.
 * Order: -100 (runs first in the custom filter chain)
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || !isValidUuid(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        exchange.getAttributes().put("requestId", requestId);
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build())
            .build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isValidUuid(String requestId) {
        try {
            UUID.fromString(requestId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
