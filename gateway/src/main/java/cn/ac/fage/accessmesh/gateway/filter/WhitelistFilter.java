package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Matches request path against whitelist. If matched, sets skipAuth=true
 * to bypass subsequent auth and permission filters.
 * Order: -80
 */
@Component
public class WhitelistFilter implements GlobalFilter, Ordered {

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> whitelistPaths;

    public WhitelistFilter(GatewayProperties gatewayProperties) {
        this.whitelistPaths = gatewayProperties.getWhitelist().getPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        for (String pattern : whitelistPaths) {
            if (pathMatcher.match(pattern, path)) {
                exchange.getAttributes().put(SKIP_AUTH_ATTR, true);
                break;
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -80;
    }
}
