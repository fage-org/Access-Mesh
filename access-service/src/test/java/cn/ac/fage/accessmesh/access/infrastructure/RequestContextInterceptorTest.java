package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.support.OAuth2CredentialFixtures;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link RequestContextInterceptor} 决策树单元测试（T-ACCESS-004；T-ACCESS-013 扩展 JWT 分支）。
 * <p>覆盖：公开路径匿名、内部凭证路径（纯服务 / 验签用户 / 纵深 403）、签名用户态、
 * 无身份 401、afterCompletion 清理；OAuth2 JWT 分支（T-ACCESS-013）：配置化开放路径、
 * 客户端启用动态校验、scope/audience/clientIds 门禁、默认路径 userinfo 豁免 audience。
 * Sa-Token 会话路径由 SecurityMatrixIT（集成）覆盖。</p>
 */
class RequestContextInterceptorTest {

    private static final String SECRET = "test-secret-key-for-interceptor-0123456789";
    private static final int VALID_SECONDS = 300;
    private static final String JWT_SECRET = "test-jwt-secret-for-interceptor-0123456789";
    private static final String CLIENT_ID = "admin-web";

    private cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService userDomainService;
    private RequestContextInterceptor interceptor;
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private OAuth2ClientDomainService oauth2ClientDomainService;
    private OAuth2ResourcePathProperties oauth2ResourcePaths;

    @BeforeEach
    void setUp() {
        SignatureVerifier verifier = new SignatureVerifier();
        ReflectionTestUtils.setField(verifier, "signatureSecret", SECRET);
        ReflectionTestUtils.setField(verifier, "signatureValidSeconds", VALID_SECONDS);
        verifier.validateConfiguration();
        // T-ACCESS-004 评审 P1：拦截器新增 OAuth2 JWT 认证分支（注入 Redis 黑名单检查）；
        // T-ACCESS-013：注入开放路径配置（默认仅 /auth/oauth2/userinfo）+ 客户端域服务（启用校验）
        stringRedisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        oauth2ClientDomainService = mock(OAuth2ClientDomainService.class);
        oauth2ResourcePaths = new OAuth2ResourcePathProperties();
        userDomainService = mock(cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService.class);
        interceptor = new RequestContextInterceptor(verifier, stringRedisTemplate,
            oauth2ResourcePaths, oauth2ClientDomainService, userDomainService,
            mock(org.springframework.beans.factory.ObjectProvider.class),
            mock(cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard.class));
        ReflectionTestUtils.setField(interceptor, "jwtSecretKey", JWT_SECRET);

        // 默认 mock：客户端启用（禁用用例单独覆盖）
        SysOauth2Client activeClient = new SysOauth2Client();
        activeClient.setClientId(CLIENT_ID);
        activeClient.setStatus(1);
        activeClient.setTenantId(1L);
        var activeUser = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
        activeUser.setStatus(1);
        activeUser.setPassword(OAuth2CredentialFixtures.PASSWORD_HASH);
        when(userDomainService.selectValidById(1L, 100L)).thenReturn(activeUser);
        when(oauth2ClientDomainService.findActiveByClientId(1L, CLIENT_ID)).thenReturn(activeClient);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"password", "legacy", "expired"})
    void jwtWithInvalidCredentialGenerationIsRejected(String invalid) throws Exception {
        var claims = oauth2Claims(null, null);
        switch (invalid) {
            case "password" -> claims.put("pwd_generation", "$2a$10$changed-password-salt00");
            case "legacy" -> claims.remove("pwd_generation");
            case "expired" -> claims.put("chain_exp", System.currentTimeMillis() / 1000 - 1);
            default -> throw new IllegalArgumentException(invalid);
        }
        String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600, claims, JWT_SECRET);
        var req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        req.addHeader("Authorization", "Bearer " + jwt);
        var resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, -1})
    void shouldRejectNextJwtRequest_whenUserDisabledOrDeleted(int status) throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        req.addHeader("Authorization", "Bearer " + jwt(oauth2Claims(null, null)));
        assertThat(interceptor.preHandle(req, new MockHttpServletResponse(), new Object())).isTrue();
        interceptor.afterCompletion(req, new MockHttpServletResponse(), new Object(), null);
        cn.ac.fage.accessmesh.access.user.entity.SysUser user = null;
        if (status == 0) {
            user = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
            user.setStatus(0);
        }
        when(userDomainService.selectValidById(1L, 100L)).thenReturn(user);
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    void shouldRejectJwt_whenPayloadTenantDiffersFromClient() throws Exception {
        var claims = oauth2Claims(null, null);
        claims.put("tenant_id", "2");
        var req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        req.addHeader("Authorization", "Bearer " + jwt(claims));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        org.mockito.Mockito.verifyNoInteractions(userDomainService);
    }

    @Test
    void shouldPropagateUserLookupFailure_withoutBindingJwtContext() {
        var failure = new org.springframework.dao.DataAccessResourceFailureException("database unavailable");
        when(userDomainService.selectValidById(1L, 100L)).thenThrow(failure);
        var req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        req.addHeader("Authorization", "Bearer " + jwt(oauth2Claims(null, null)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            interceptor.preHandle(req, new MockHttpServletResponse(), new Object())).isSameAs(failure);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "/api/access/auth/check",
        "/api/access/auth/batch-check",
        "/api/access/auth/query-resources",
        "/api/access/auth/query-scopes",
        "/api/access/abstract-user/sync",
        "/api/access/abstract-user/full-sync",
        "/api/access/abstract-role/sync",
        "/api/access/abstract-role/full-sync",
        "/api/access/user-role/sync",
        "/api/access/user-role/full-sync"})
    void shouldRejectLegacySelfDeclaredService_onMigratedEndpoints(String path) throws Exception {
        var req = new MockHttpServletRequest("POST", path);
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, true);
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        var resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    void shouldRejectPlainInternalSecret_onSyncEndpoint_withoutSelfDeclaredHeader() throws Exception {
        // 旧「共享密钥+无自报头」纯服务通道的另一半：sync 族端点即使不带 X-Service-Code
        // 也一律 403（参数化用例只锁了带自报头形态；旧实现该形态放行为 SERVICE(null) 即红）
        var req = new MockHttpServletRequest("POST", "/api/access/abstract-user/sync");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, true);
        req.addHeader("X-Tenant-Id", "1");
        var resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    void shouldAllowPlatformInternalQuery_withoutServiceHeader_onAuthPrefixEndpoints() throws Exception {
        // /api/access/auth/** 前缀排除保留平台内部查询形态：无自报头内部密钥 →
        // SERVICE(serviceCode=null)——网关平台信任域，非纯服务通道复活
        var req = new MockHttpServletRequest("POST", "/api/access/auth/check");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, true);
        req.addHeader("X-Tenant-Id", "1");
        var resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, resp, new Object())).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(AccessRequestContext.get()).isNotNull();
        assertThat(AccessRequestContext.get().callerType()).isEqualTo(CallerType.SERVICE);
        assertThat(AccessRequestContext.get().serviceCode()).isNull();
        assertThat(AccessRequestContext.get().tenantId()).isEqualTo(1L);
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        MDC.clear();
    }

    @Test
    void should_rejectLegacyServiceIdentity_whenCallingV2InterfaceSync() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/service-config/sync-v2");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, true);
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "svc-a");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, resp, new Object())).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("评审 P1-1：ERROR dispatch（/error 转发）→ 放行且不绑定、不 401 掩蔽真实错误")
    void shouldPassThrough_whenErrorDispatch() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/error");
        req.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("公开路径 /auth/** → 匿名上下文放行")
    void shouldBindAnonymous_whenAuthPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.ANONYMOUS);
        assertThat(AccessRequestContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("公开路径 /actuator/** → 匿名放行（G4 修复：不再强制 X-Tenant-Id）")
    void shouldBindAnonymous_whenActuatorPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.ANONYMOUS);
    }

    @Test
    @DisplayName("评审 P3：精确路径 /actuator（根发现端点）→ 匿名放行")
    void shouldBindAnonymous_whenExactActuatorRootPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.ANONYMOUS);
    }

    @Test
    @DisplayName("评审 P3：相邻命名空间 /actuator-admin 不被误判为匿名（未登录 → 401）")
    void shouldReject_whenAdjacentActuatorNamespaceWithoutLogin() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator-admin");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("评审 P1：OAuth2 JWT 有效（未撤销）→ USER 上下文绑定（验签+黑名单+客户端启用通过）")
    void shouldBindUser_whenValidOAuth2Jwt() throws Exception {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        String jwt = jwt(oauth2Claims(null, null));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        // T-ACCESS-013：委托上下文第五要素（审计区分第三方委托调用与用户直调）
        assertThat(AccessRequestContext.getDelegatedClientId()).isEqualTo(CLIENT_ID);
        org.mockito.Mockito.verify(stringRedisTemplate).hasKey("oauth2:blacklist:jti-1");
    }

    @Test
    @DisplayName("T-ACCESS-013：旧令牌无 aud claim 访问默认路径 userinfo → 通过（audience 豁免）")
    void shouldBindUser_whenLegacyJwtWithoutAudOnUserinfo() throws Exception {
        String jwt = jwt(oauth2Claims(null, null));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
    }

    @Test
    @DisplayName("T-ACCESS-013：客户端被禁用 → 401（动态启用校验，禁用立即失效）")
    void shouldReject_whenClientDisabled() throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(1L, CLIENT_ID)).thenReturn(null);
        String jwt = jwt(oauth2Claims(null, null));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("T-ACCESS-013：JWT 载荷缺失 client_id → 401（必填 claim）")
    void shouldReject_whenJwtMissingClientId() throws Exception {
        Map<String, Object> claims = oauth2Claims(null, null);
        claims.remove("client_id");
        String jwt = jwt(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("T-ACCESS-013：配置业务路径 + scope 满足 → 通过（audience/scope 门禁全过）")
    void shouldBindUser_whenBusinessPathAllGatesPassed() throws Exception {
        configureBusinessPath("/api/example/open", "example:read", "access-service", null);
        String jwt = jwt(oauth2Claims("example:read example:write", "access-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getDelegatedClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("T-ACCESS-013：scope 不足（requiredScopes 未全包含）→ 403 授权不足")
    void shouldReject_whenInsufficientScope() throws Exception {
        configureBusinessPath("/api/example/open", "example:read example:admin", "access-service", null);
        String jwt = jwt(oauth2Claims("example:read", "access-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("T-ACCESS-013：audience 不匹配（业务路径强制受众）→ 403")
    void shouldReject_whenAudienceMismatch() throws Exception {
        configureBusinessPath("/api/example/open", null, "example-service", null);
        String jwt = jwt(oauth2Claims("example:read", "access-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("T-ACCESS-013：业务路径令牌无 aud claim（受众强制，不豁免）→ 403")
    void shouldReject_whenBusinessPathAndTokenWithoutAud() throws Exception {
        configureBusinessPath("/api/example/open", "example:read", "access-service", null);
        String jwt = jwt(oauth2Claims("example:read", null));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("T-ACCESS-013：clientIds 限定不满足 → 403")
    void shouldReject_whenClientNotInAllowedList() throws Exception {
        configureBusinessPath("/api/example/open", "example:read", "access-service", "9:other-client");
        String jwt = jwt(oauth2Claims("example:read", "access-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("T-ACCESS-097 外评 P2：clientIds 结构化身份命中（1:clientId）→ 放行")
    void shouldAllow_whenStructuredClientIdMatchesResolvedRow() throws Exception {
        // 旧实现（裸 clientId 名字比较）对条目 "1:example-web" 恒不匹配 → 403，本用例即红
        configureBusinessPath("/api/example/open", "example:read", "access-service", "1:" + CLIENT_ID);
        String jwt = jwt(oauth2Claims("example:read", "access-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getDelegatedClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("T-ACCESS-097 外评 P2：他租户同名客户端不借白名单名通过（身份粒度，非名字粒度）→ 403")
    void shouldReject_whenSameNameClientOfAnotherTenantNotInAllowlist() throws Exception {
        // 白名单只批准租户 1 的客户端；令牌为租户 2 的同名客户端（行/claim/用户均在租户 2，
        // 验签与启用校验全过）——身份比较 "2:example-web" ∉ {"1:example-web"} → 403
        configureBusinessPath("/api/example/open", "example:read", "access-service", "1:" + CLIENT_ID);
        SysOauth2Client tenant2Client = new SysOauth2Client();
        tenant2Client.setClientId(CLIENT_ID);
        tenant2Client.setStatus(1);
        tenant2Client.setTenantId(2L);
        when(oauth2ClientDomainService.findActiveByClientId(2L, CLIENT_ID)).thenReturn(tenant2Client);
        var tenant2User = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
        tenant2User.setStatus(1);
        tenant2User.setPassword(OAuth2CredentialFixtures.PASSWORD_HASH);
        when(userDomainService.selectValidById(2L, 100L)).thenReturn(tenant2User);

        var claims = oauth2Claims("example:read", "access-service");
        claims.put("tenant_id", "2");
        String jwt = jwt(claims);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/open");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("T-ACCESS-013：配置通配路径（/api/example/**）→ 命中子路径按规则放行")
    void shouldMatchAntPatternPath() throws Exception {
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/example/**");
        rule.getRequiredScopes().add("example:read");
        rule.setAudience("example-service");
        oauth2ResourcePaths.setResourcePaths(new ArrayList<>(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/userinfo"), rule)));
        String jwt = jwt(oauth2Claims("example:read", "example-service"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/example/resource/action");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getDelegatedClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("评审 P1：OAuth2 JWT 已撤销（黑名单命中）→ 401")
    void shouldReject_whenOAuth2JwtRevoked() throws Exception {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        String jwt = jwt(oauth2Claims(null, null));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("评审 P1：OAuth2 JWT 签名无效（错误密钥签发）→ 401")
    void shouldReject_whenOAuth2JwtSignatureInvalid() throws Exception {
        String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            oauth2Claims(null, null), "wrong-secret-key");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("P2 路径限定：有效 OAuth2 JWT 访问 /auth/oauth2/authorize → 不认证 → 无会话 401")
    void shouldReject_whenOAuth2JwtOnAuthorizePath() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);
            String jwt = jwt(oauth2Claims(null, null));

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/oauth2/authorize");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("Authorization", "Bearer " + jwt);

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("P1 路径限定：有效 OAuth2 JWT 访问非开放路径（/user/page）→ 不认证 → 无会话 401（默认拒绝）")
    void shouldReject_whenOAuth2JwtOnNonOAuth2Path() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);
            String jwt = jwt(oauth2Claims(null, null));

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("Authorization", "Bearer " + jwt);

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("评审 P1-1：/auth/userinfo 未登录 → 401（会话端点不再匿名放行）")
    void shouldReject_whenAuthSessionEndpointWithoutLogin() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/userinfo");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("评审 P1-1：/auth/userinfo 已登录 → USER 上下文（会话租户绑定，修复空租户查询）")
    void shouldBindUser_whenAuthSessionEndpointWithLogin() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/userinfo");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
            assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
            assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("评审 P1-1：/auth/logout 匿名放行（用户决策：保持未登录 200 幂等语义）")
    void shouldBindAnonymous_whenLogoutPath() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/logout");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.ANONYMOUS);
        }
    }

    @Test
    @DisplayName("评审 P2：afterConcurrentHandlingStarted 清理上下文与 MDC（异步线程切换防串扰）")
    void afterConcurrentHandlingStartedShouldClearContextAndMdc() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL, new ServicePrincipal(1L, "example-service", "sc-test"));
        interceptor.preHandle(req, resp, new Object());
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");

        interceptor.afterConcurrentHandlingStarted(req, resp, new Object());

        assertThat(AccessRequestContext.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("serviceCode")).isNull();
    }

    @Test
    @DisplayName("内部凭证 + X-User-Id 无 SIGNATURE_VERIFIED → 403（纵深防链序绕过）")
    void shouldReject_whenInternalWithUserIdWithoutVerifiedAttribute() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/domain-config/list");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-User-Id", "100");
        req.addHeader("X-Tenant-Id", "1");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("内部凭证 + X-User-Id + 验签通过 → USER 上下文（G1 修复：验签才绑定操作者）")
    void shouldBindUser_whenInternalWithVerifiedUserId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/domain-config/list");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        long ts = System.currentTimeMillis() / 1000;
        req.addHeader("X-User-Id", "100");
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-User-Signature", sign("100", "1", ts));
        req.addHeader("X-Signature-Timestamp", String.valueOf(ts));
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);
        req.setAttribute(SecurityAttributes.ATTR_SIGNATURE_VERIFIED, Boolean.TRUE);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        assertThat(AccessRequestContext.getServiceCode()).isNull();
    }

    @Test
    @DisplayName("内部凭证 + 无 X-User-Id → SERVICE 上下文（serviceCode 凭证通过后绑定）")
    void shouldBindService_whenInternalWithoutUserId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL, new ServicePrincipal(1L, "example-service", "sc-test"));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.SERVICE);
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        assertThat(AccessRequestContext.getOperatorId()).isNull();
    }

    @Test
    @DisplayName("内部凭证 + 无 X-Tenant-Id → 400（服务调用租户必填）")
    void shouldReject_whenInternalWithoutTenantHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/interface-admission");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(400);
        assertThat(AccessRequestContext.get()).isNull();
    }

    @Test
    @DisplayName("签名用户态（无会话 + 验签 X-User-Id）→ USER 上下文")
    void shouldBindUser_whenSignatureOnly() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/check");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            long ts = System.currentTimeMillis() / 1000;
            req.addHeader("X-User-Id", "100");
            req.addHeader("X-Tenant-Id", "1");
            req.addHeader("X-User-Signature", sign("100", "1", ts));
            req.addHeader("X-Signature-Timestamp", String.valueOf(ts));
            req.setAttribute(SecurityAttributes.ATTR_SIGNATURE_VERIFIED, Boolean.TRUE);

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
            assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
            assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("无身份非公开路径 → 401 显式门禁（G3 修复）")
    void shouldReject_whenUnauthenticatedProtectedPath() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("会话路径：X-Tenant-Id 头与会话租户不一致 → 403（伪造头拒绝）")
    void shouldReject_whenTenantHeaderMismatchesSession() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-Tenant-Id", "2");

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(403);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("会话路径：X-Tenant-Id 头格式非法 → 400（评审 P2-2：与旧 TenantInterceptor 语义一致）")
    void shouldReject_whenTenantHeaderInvalidFormat() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-Tenant-Id", "abc");

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("会话路径：X-User-Id 头格式非法 → 400（对抗核实补测：parse 失败先于一致性判定）")
    void shouldReject_whenUserHeaderInvalidFormat() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-User-Id", "abc");

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("会话路径：X-User-Id 头与会话登录用户不一致 → 403（伪造用户拒绝）")
    void shouldReject_whenUserHeaderMismatchesSession() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-User-Id", "999");

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(403);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("会话路径：头一致 → USER 上下文（会话权威）")
    void shouldBindUser_whenSessionWithConsistentHeaders() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("X-Tenant-Id", "1");
            req.addHeader("X-User-Id", "100");

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
            assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
            assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("会话路径：无头 → 用会话租户绑定（现状回退语义）")
    void shouldBindUser_whenSessionWithoutHeaders() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(1L);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
            assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("会话路径：会话无租户且无头 → 400（租户不可确定）")
    void shouldReject_whenSessionWithoutTenantAndHeader() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(true);
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            SaSession session = mock(SaSession.class);
            when(session.get("tenantId")).thenReturn(null);
            mocked.when(StpUtil::getSession).thenReturn(session);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("afterCompletion 清理上下文与 MDC（异常路径亦执行）")
    void afterCompletionShouldClearContextAndMdc() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL, new ServicePrincipal(1L, "example-service", "sc-test"));
        interceptor.preHandle(req, resp, new Object());
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");

        interceptor.afterCompletion(req, resp, new Object(), null);

        assertThat(AccessRequestContext.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("serviceCode")).isNull();
    }

    @Test
    @DisplayName("T-PERM-021 F1.d：X-Request-Id 头 → 上下文第六要素与 MDC traceId 同源头值")
    void shouldBindRequestIdFromHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.addHeader("X-Request-Id", "550e8400-e29b-41d4-a716-446655440000");
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL, new ServicePrincipal(1L, "example-service", "sc-test"));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getRequestId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(MDC.get("traceId")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("T-PERM-021 F1.d：无 X-Request-Id 头 → 上下文兜底生成 UUID 且与 MDC traceId 同值（单源）")
    void shouldSynthesizeRequestIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        String requestId = AccessRequestContext.getRequestId();
        assertThat(requestId).isNotBlank();
        // 合法 UUID（与 Gateway RequestIdFilter 生成口径一致）
        assertThatCode(() -> java.util.UUID.fromString(requestId)).doesNotThrowAnyException();
        // 上下文与 MDC 单源同值（旧实现 MDC 独立生成，漂移源）
        assertThat(MDC.get("traceId")).isEqualTo(requestId);
    }

    @Test
    @DisplayName("T-PERM-021 F1.d：超长 X-Request-Id 头 → 截断 64 对齐审计列宽")
    void shouldTruncateOverlongRequestIdHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Request-Id", "x".repeat(100));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getRequestId()).hasSize(64);
    }

    /** 与生产相同算法生成 HMAC-SHA256（payload = userId|tenantId|timestamp）。 */
    private static String sign(String userId, String tenantId, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** OAuth2 JWT 标准测试载荷（tenant_id/jti/client_id + 可选 scope/aud）。 */
    private static Map<String, Object> oauth2Claims(String scope, String aud) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", "1");
        claims.put("jti", "jti-1");
        claims.put("client_id", CLIENT_ID);
        if (scope != null) {
            claims.put("scope", scope);
        }
        if (aud != null) {
            claims.put("aud", List.of(aud));
        }
        return OAuth2CredentialFixtures.claims(claims);
    }

    /** 以测试密钥签发 OAuth2 JWT（与拦截器验签密钥一致）。 */
    private static String jwt(Map<String, Object> claims) {
        return cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            claims, JWT_SECRET);
    }

    /** 配置一条业务开放路径规则（保留 userinfo 默认条目；path + requiredScopes/audience/clientIds）。 */
    private void configureBusinessPath(String path, String requiredScope, String audience,
                                       String allowedClientId) {
        OAuth2ResourcePathProperties.ResourcePathRule rule =
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath(path);
        if (requiredScope != null) {
            rule.setRequiredScopes(new LinkedHashSet<>(List.of(requiredScope.split(" "))));
        }
        rule.setAudience(audience);
        if (allowedClientId != null) {
            rule.setClientIds(new LinkedHashSet<>(List.of(allowedClientId)));
        }
        oauth2ResourcePaths.setResourcePaths(new ArrayList<>(List.of(
            OAuth2ResourcePathProperties.ResourcePathRule.exactPath("/api/access/auth/oauth2/userinfo"), rule)));
    }

    // ===== T-PERM-070 凭证路径（SERVICE_PRINCIPAL 只认 principal，自报头一律忽略） =====

    @Test
    @DisplayName("T-PERM-070：SERVICE_PRINCIPAL → 绑定凭证派生 service 上下文，自报头（服务码/租户/用户头）一律忽略")
    void shouldBindCredentialPrincipal_ignoringSelfDeclaredHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/resource-entity/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL,
            new ServicePrincipal(7L, "example-service", "sc-real"));
        // 自报头全部与凭证派生值相异——旧路径会绑定这些值，凭证路径必须忽略
        req.addHeader("X-Service-Code", "attacker-service");
        req.addHeader("X-Tenant-Id", "999");
        req.addHeader("X-User-Id", "1");

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.SERVICE);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(7L);
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");
    }

    @Test
    @DisplayName("T-PERM-070：SERVICE_PRINCIPAL + 畸形 X-Tenant-Id 自报头 → 仍放行（不采信即不因格式拒绝）")
    void shouldBindCredentialPrincipal_evenWithMalformedSelfDeclaredHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/access/resource-entity/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL,
            new ServicePrincipal(7L, "example-service", "sc-real"));
        req.addHeader("X-Tenant-Id", "not-a-number");

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(7L);
    }
}
