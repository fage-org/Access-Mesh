package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Removes external request headers that could be forged to impersonate internal headers.
 * Order: -90
 */
@Component
public class HeaderCleanFilter implements GlobalFilter, Ordered {

    private final List<String> headersToClean;

    public HeaderCleanFilter(GatewayProperties gatewayProperties) {
        this.headersToClean = gatewayProperties.getHeader().getClean();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders filteredHeaders = new HttpHeaders();
        HttpHeaders original = exchange.getRequest().getHeaders();
        original.forEach((key, values) -> {
            if (!headersToClean.contains(key)) {
                values.forEach(v -> filteredHeaders.add(key, v));
            }
        });
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .headers(h -> h.putAll(filteredHeaders))
                .build())
            .build());
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
