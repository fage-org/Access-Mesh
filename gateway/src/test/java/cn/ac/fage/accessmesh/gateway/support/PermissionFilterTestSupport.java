package cn.ac.fage.accessmesh.gateway.support;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 只复用权限过滤器测试的响应与属性安排，路由、请求和身份由消费者显式提供。 */
public final class PermissionFilterTestSupport {
    private PermissionFilterTestSupport() {}

    public static ServerWebExchange routedExchange(MockServerHttpRequest request, Route route,
                                                   Long tenantId, Long userId, String subjectTypeCode) {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.setStatusCode(any())).thenReturn(true);
        when(response.bufferFactory()).thenReturn(new DefaultDataBufferFactory());
        when(response.writeWith(any())).thenReturn(Mono.empty());
        when(response.setComplete()).thenReturn(Mono.empty());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", userId);
        attributes.put("tenantId", tenantId);
        attributes.put("subjectTypeCode", subjectTypeCode);
        attributes.put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(exchange.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0)));
        when(exchange.getAttributes()).thenReturn(attributes);
        return exchange;
    }
}
