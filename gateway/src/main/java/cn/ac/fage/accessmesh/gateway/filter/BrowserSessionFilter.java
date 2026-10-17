package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Rejected;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import cn.dev33.satoken.SaManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validate before whitelist/M2M/JWT dispatch; downstream receives the existing Bearer protocol. */
@Component
public class BrowserSessionFilter implements GlobalFilter, Ordered {
    private final BrowserSessionPolicy policy;
    private final ObjectMapper json;
    public BrowserSessionFilter(@Value("${accessmesh.browser-session.allowed-origins:${ACCESS_BROWSER_ALLOWED_ORIGINS:" + BrowserSessionPolicy.DEFAULT_ORIGINS + "}}") String origins,
                                ObjectMapper json) {
        this.policy = new BrowserSessionPolicy(origins); this.json = json;
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            var request = exchange.getRequest();
            String path = request.getURI().getPath();
            Map<String, List<String>> cookies = new LinkedHashMap<>();
            request.getCookies().forEach((name, values) -> cookies.put(name, values.stream().map(cookie -> cookie.getValue()).toList()));
            try {
                var candidate = policy.resolve(new BrowserSessionPolicy.Input(request.getMethod().name(), path,
                    single(exchange, "Authorization"), single(exchange, "Origin"), single(exchange, "Referer"),
                    single(exchange, BrowserSessionPolicy.REQUEST_HEADER), single(exchange, BrowserSessionPolicy.SESSION_HEADER), cookies,
                    request.getHeaders().containsHeader("X-Credential-Id") || request.getHeaders().containsHeader("X-Credential-Secret")));
                BrowserSessionPolicy.validateBinding(candidate, selected -> {
                    var logic = SaManager.getStpLogic(selected.realm() == BrowserSessionPolicy.Realm.PLATFORM ? "platform-operator" : "login");
                    var session = logic.getTokenSessionByToken(selected.token(), false);
                    Object marker = session == null ? null : session.get(BrowserSessionPolicy.SESSION_ATTRIBUTE);
                    return marker instanceof String value ? value : null;
                });
                if (candidate != null) exchange.getResponse().getHeaders().setCacheControl("no-store");
                if (BrowserSessionPolicy.isLogin(path) || BrowserSessionPolicy.isLogout(path)) {
                    org.springframework.cloud.gateway.route.Route route = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    if (route == null || !"access-service".equalsIgnoreCase(route.getUri().getHost()))
                        throw new Rejected(503, "浏览器认证路由不可用");
                    return chain.filter(exchange);
                }
                // Native/internal authentication owns identity after normalization. No client-visible bypass header is introduced.
                String retainedCookies = request.getCookies().entrySet().stream().filter(e -> !BrowserSessionPolicy.isCookie(e.getKey()))
                    .flatMap(e -> e.getValue().stream()).map(c -> c.getName() + "=" + c.getValue()).collect(java.util.stream.Collectors.joining("; "));
                var normalized = request.mutate().headers(headers -> {
                    headers.remove("Cookie");
                    if (!retainedCookies.isEmpty()) headers.set("Cookie", retainedCookies);
                    headers.remove(BrowserSessionPolicy.SESSION_HEADER);
                    headers.remove(BrowserSessionPolicy.REQUEST_HEADER);
                    if (candidate != null && candidate.token() != null) headers.set("Authorization", "Bearer " + candidate.token());
                }).build();
                return chain.filter(exchange.mutate().request(normalized).build());
            } catch (Rejected rejected) {
                var response = exchange.getResponse();
                response.setStatusCode(HttpStatus.valueOf(rejected.status()));
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setCacheControl("no-store");
                GatewayResponse body = GatewayResponse.error(rejected.code(), rejected.getMessage());
                String requestId = exchange.getAttribute("requestId");
                body.setRequestId(requestId); body.setTraceId(requestId);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(json.writeValueAsBytes(body))));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    private static String single(ServerWebExchange exchange, String name) {
        List<String> values = exchange.getRequest().getHeaders().get(name);
        if (values != null && values.size() != 1) throw new Rejected(400, "重复的认证或来源请求头");
        return values == null ? null : values.getFirst();
    }
    @Override public int getOrder() { return -85; }
}
