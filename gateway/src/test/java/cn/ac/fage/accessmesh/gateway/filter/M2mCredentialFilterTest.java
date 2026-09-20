package cn.ac.fage.accessmesh.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M2M 凭证识别过滤器测试矩阵（T-PERM-070，service-authentication.md §3.3）。
 * <p>锁定：完整凭证头 + 精确 M2M 路径 → skipAuth（仅透传、access-service 仲裁器终验）；
 * 半头/缺头/非 M2M 路径/方法不匹配 → 不置 skipAuth（回落 AuthTokenFilter 401）；
 * 管理端点不被凭证旁路（完整凭证头调管理端点同样不 skipAuth）。</p>
 */
class M2mCredentialFilterTest {

    private final M2mCredentialFilter filter = new M2mCredentialFilter();
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    private MockServerWebExchange exchange(String method, String path, String credentialId, String credentialSecret) {
        MockServerHttpRequest.BodyBuilder builder =
            MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), java.net.URI.create(path));
        if (credentialId != null) {
            builder.header(M2mCredentialFilter.HEADER_CREDENTIAL_ID, credentialId);
        }
        if (credentialSecret != null) {
            builder.header(M2mCredentialFilter.HEADER_CREDENTIAL_SECRET, credentialSecret);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private boolean invokesWithSkipAuth(MockServerWebExchange exchange) {
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
        return Boolean.TRUE.equals(exchange.getAttribute("skipAuth"));
    }

    @Test
    @DisplayName("完整凭证头 + M2M 路径（sync/full-sync/manifest）→ skipAuth=true")
    void completeCredentialOnM2mPath_setsSkipAuth() {
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/sync", "sc-a", "sk-b"))).isTrue();
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/full-sync", "sc-a", "sk-b"))).isTrue();
        assertThat(invokesWithSkipAuth(exchange("POST",
            "/api/access/integration/permission-manifest/full-sync", "sc-a", "sk-b"))).isTrue();
    }

    @Test
    @DisplayName("半头（缺 secret）→ 不 skipAuth（回落 AuthTokenFilter 401）")
    void halfHeaders_noSkipAuth() {
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/sync", "sc-a", null))).isFalse();
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/sync", null, "sk-b"))).isFalse();
    }

    @Test
    @DisplayName("完整凭证头 + 非 M2M 路径（管理/查询端点）→ 不 skipAuth（管理端点不被凭证旁路）")
    void completeCredentialOnNonM2mPath_noSkipAuth() {
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/auth/query-resources", "sc-a", "sk-b"))).isFalse();
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/service-credential/list", "sc-a", "sk-b"))).isFalse();
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/abstract-user/full-sync", "sc-a", "sk-b"))).isFalse();
    }

    @Test
    @DisplayName("方法不匹配（GET 同路径）→ 不 skipAuth（method+精确路径双因子）")
    void methodMismatch_noSkipAuth() {
        assertThat(invokesWithSkipAuth(exchange("GET", "/api/access/resource-entity/sync", "sc-a", "sk-b"))).isFalse();
    }

    @Test
    @DisplayName("空白头值视为缺失 → 不 skipAuth")
    void blankHeaderValues_noSkipAuth() {
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/sync", " ", "sk-b"))).isFalse();
        assertThat(invokesWithSkipAuth(exchange("POST", "/api/access/resource-entity/sync", "sc-a", ""))).isFalse();
    }
}
