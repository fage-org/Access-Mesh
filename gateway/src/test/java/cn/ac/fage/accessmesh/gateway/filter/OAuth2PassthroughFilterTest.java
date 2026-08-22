package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth2 委托令牌透传过滤器测试（T-ACCESS-013；评审 P1 修复：仅委托 JWT 启用透传）。
 * <p>
 * 验证 gateway.oauth2.passthrough-paths 命中<b>且 Authorization 为 Bearer 三段式 JWT</b>
 * 时才设置 skipAuth=true（身份过滤器跳过——OAuth2 JWT 不被 uuid 会话校验 401，
 * Authorization 原样透传下游验签）。评审 P1 回归锚点：平台用户 uuid 会话令牌
 * （无 '.'）与无 Authorization 头的请求<b>不设 skipAuth</b>——必须走正常
 * AuthTokenFilter 会话校验 + PermissionFilter 接口鉴权，平台会话认证路径不变
 * （否则透传路径上 uuid 会话被下游共享 Redis 会话分支接受，绕过 Gateway 权限）。
 * 默认清单为空（无业务路径默认开放）。
 * </p>
 */
class OAuth2PassthroughFilterTest {

    private static final String OAUTH2_JWT = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJsb2dpblR5cGUiOiJvYXV0aDIifQ.signature";
    private static final String UUID_SESSION_TOKEN = "Bearer 6f5b1a4c-9d2e-4f8a-b3c7-1e0d9a8b7c6f";

    private OAuth2PassthroughFilter filterWith(List<String> paths) {
        GatewayProperties properties = new GatewayProperties();
        properties.getOauth2().setPassthroughPaths(paths);
        return new OAuth2PassthroughFilter(properties);
    }

    private MockServerWebExchange exchange(String path, String authorization) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post(path);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    @DisplayName("命中透传路径 + Bearer 三段式 JWT → 设置 skipAuth=true")
    void matchedPathWithJwt_setsSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of("/admin/api/example/**"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/admin/api/example/resource/action", OAUTH2_JWT);

        filter.filter(exchange, chain).block();

        Boolean skipAuth = exchange.getAttribute("skipAuth");
        assertThat(skipAuth).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("评审 P1：命中路径 + 平台 uuid 会话令牌（无 '.'）→ 不设 skipAuth，走正常会话校验与 Gateway 鉴权")
    void matchedPathWithUuidSessionToken_doesNotSetSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of("/admin/api/example/**"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/admin/api/example/resource/action", UUID_SESSION_TOKEN);

        filter.filter(exchange, chain).block();

        Boolean skipAuth = exchange.getAttribute("skipAuth");
        assertThat(skipAuth).isNull();
    }

    @Test
    @DisplayName("评审 P1：命中路径 + 无 Authorization 头 → 不设 skipAuth（由 AuthTokenFilter 401）")
    void matchedPathWithoutAuthorization_doesNotSetSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of("/admin/api/example/**"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/admin/api/example/resource/action", null);

        filter.filter(exchange, chain).block();

        Boolean skipAuth = exchange.getAttribute("skipAuth");
        assertThat(skipAuth).isNull();
    }

    @Test
    @DisplayName("命中路径 + 非 Bearer 前缀 Authorization → 不设 skipAuth")
    void matchedPathWithNonBearerAuthorization_doesNotSetSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of("/admin/api/example/**"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/admin/api/example/resource/action", "Basic dXNlcjpwYXNz");

        filter.filter(exchange, chain).block();

        Boolean skipAuth = exchange.getAttribute("skipAuth");
        assertThat(skipAuth).isNull();
    }

    @Test
    @DisplayName("未命中路径（携带 JWT）→ 不设置 skipAuth（身份过滤器正常执行）")
    void unmatchedPath_doesNotSetSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of("/admin/api/example/**"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = exchange("/admin/api/user/page", OAUTH2_JWT);

        filter.filter(exchange, chain).block();

        Boolean skipAuth = exchange.getAttribute("skipAuth");
        assertThat(skipAuth).isNull();
    }

    @Test
    @DisplayName("默认清单为空 → 携带 JWT 的任何路径均不设置 skipAuth（无业务路径默认开放）")
    void defaultEmptyList_neverSetsSkipAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange matched = exchange("/admin/api/example/resource/action", OAUTH2_JWT);
        filter.filter(matched, chain).block();

        Boolean matchedSkipAuth = matched.getAttribute("skipAuth");
        assertThat(matchedSkipAuth).isNull();
    }

    @Test
    @DisplayName("过滤器顺序 -79（白名单 -80 之后、会话校验 -70 之前）")
    void orderIsBetweenWhitelistAndAuth() {
        OAuth2PassthroughFilter filter = filterWith(List.of());

        assertThat(Integer.valueOf(filter.getOrder())).isEqualTo(-79);
        assertThat(Integer.valueOf(filter.getOrder()))
            .isGreaterThan(Integer.valueOf(new WhitelistFilter(new GatewayProperties()).getOrder()));
    }
}
