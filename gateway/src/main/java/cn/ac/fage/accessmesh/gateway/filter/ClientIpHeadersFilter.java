package cn.ac.fage.accessmesh.gateway.filter;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Rebuild the downstream client IP after Gateway's own outbound header cleanup. */
@Component
public final class ClientIpHeadersFilter implements HttpHeadersFilter, Ordered {

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        HttpHeaders output = new HttpHeaders();
        output.addAll(input);
        output.remove("X-Forwarded-For");
        var peer = exchange.getRequest().getRemoteAddress();
        if (peer != null && peer.getAddress() != null) {
            output.set("X-Forwarded-For", peer.getAddress().getHostAddress());
        }
        return output;
    }

    @Override
    public int getOrder() {
        // Gateway 5 removes X-Forwarded-* when trusted proxies are not configured.
        // Only the socket peer is restored, never a value from an incoming header.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
