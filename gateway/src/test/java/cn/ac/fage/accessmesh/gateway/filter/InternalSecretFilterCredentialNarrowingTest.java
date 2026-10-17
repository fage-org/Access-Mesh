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
 * 内部密钥注入过滤器凭证收窄回归锁（T-PERM-070）：无凭证头时兜底注入 X-Internal-Secret；
 * 携带任一凭证头（X-Credential-Id/X-Credential-Secret）时不注入——凭证请求由
 * access-service 仲裁器按凭证链终验，「凭证+密钥并存」由服务端凭证优先规则消解。
 * T-GW-014 起注入还须路由目的为 access-service（路由维度见
 * {@link InternalSecretFilterRouteNarrowingTest}；本类固定在 access-service 路由目的上测凭证维度）。
 */
class InternalSecretFilterCredentialNarrowingTest {

    private static final String SECRET = "internal-secret-value";

    private InternalSecretFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalSecretFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", SECRET);
    }

    private ServerWebExchange run(String path, String credentialId, String credentialSecret) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post(path);
        if (credentialId != null) {
            builder.header(M2mCredentialFilter.HEADER_CREDENTIAL_ID, credentialId);
        }
        if (credentialSecret != null) {
            builder.header(M2mCredentialFilter.HEADER_CREDENTIAL_SECRET, credentialSecret);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            Route.async().id("access-service").uri(URI.create("lb://access-service"))
                .predicate(ex -> true).build());
        GatewayFilterChain target = mock(GatewayFilterChain.class);
        when(target.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, target).block();
        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(target).filter(forwarded.capture());
        return forwarded.getValue();
    }

    @Test
    @DisplayName("无凭证头 → 兜底注入 X-Internal-Secret（既有行为保持）")
    void shouldInject_whenNoCredentialHeaders() {
        ServerWebExchange exchange = run("/api/access/abstract-user/sync", null, null);
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("完整凭证头 → 不注入旧密钥（凭证链独占，服务端凭证优先消解并存窗口）")
    void shouldNotInject_whenCompleteCredentialHeaders() {
        ServerWebExchange exchange = run("/api/access/resource-entity/sync", "sc-a", "sk-b");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isNull();
    }

    @Test
    @DisplayName("半头（任一凭证头存在）→ 同样不注入（收窄条件=任一凭证头出现）")
    void shouldNotInject_whenAnyCredentialHeader() {
        ServerWebExchange exchange = run("/api/access/resource-entity/sync", "sc-a", null);
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Secret")).isNull();
    }
}
