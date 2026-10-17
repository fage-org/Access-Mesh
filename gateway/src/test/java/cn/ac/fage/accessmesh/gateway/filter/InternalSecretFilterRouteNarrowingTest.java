package cn.ac.fage.accessmesh.gateway.filter;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 内部密钥注入按路由目的收窄回归锁（T-GW-014）：仅实际路由目的为 access-service
 * 的请求注入 X-Internal-Secret；路由到其他业务下游的用户请求不携带该头——密钥不再
 * 随用户流量外发到业务进程（此前业务进程可从转发头持续读到每次轮换后的新值）。
 * 无法判定路由目的时不注入（fail-closed）。判据=路由 URI host（实际转发目的）。
 */
class InternalSecretFilterRouteNarrowingTest {

    private static final String SECRET = "internal-secret-value";

    private InternalSecretFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalSecretFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
    }

    /** 构造带路由属性的交换对象并驱动过滤器；routeUri 为 null 表示不设置路由属性。 */
    private ServerWebExchange run(String routeId, String routeUri) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/access/role/list").build());
        if (routeUri != null) {
            Route route = Route.async().id(routeId).uri(URI.create(routeUri))
                .predicate(ex -> true).build();
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        }
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        return forwarded.getValue();
    }

    @Test
    @DisplayName("路由目的 access-service → 注入（平台内部互信保持）")
    void shouldInject_whenRoutedToAccessService() {
        ServerWebExchange exchange = run("access-service", "lb://access-service");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("同入口、路由目的 example-service → 不注入（密钥不出平台信任域；旧实现下本断言失败）")
    void shouldNotInject_whenRoutedToOtherDownstream() {
        ServerWebExchange exchange = run("example-service", "lb://example-service");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isNull();
    }

    @Test
    @DisplayName("路由属性缺失（未经路由解析的调用）→ 不注入（fail-closed）")
    void shouldNotInject_whenRouteAttributeMissing() {
        ServerWebExchange exchange = run(null, null);
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isNull();
    }

    @Test
    @DisplayName("判据=路由 URI host（实际转发目的），与 route id 无关 → id 任意而目的为 access-service 仍注入")
    void shouldInject_judgedByRouteUriHostNotRouteId() {
        ServerWebExchange exchange = run("admin-legacy-route", "lb://access-service");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isEqualTo(SECRET);
    }
}
