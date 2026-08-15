package cn.ac.fage.accessmesh.access.infrastructure;

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
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link RequestContextInterceptor} 决策树单元测试（T-ACCESS-004）。
 * <p>覆盖：公开路径匿名、内部凭证路径（纯服务 / 验签用户 / 纵深 403）、签名用户态、
 * 无身份 401、afterCompletion 清理。Sa-Token 会话路径由 SecurityMatrixIT（集成）覆盖。</p>
 */
class RequestContextInterceptorTest {

    private static final String SECRET = "test-secret-key-for-interceptor-0123456789";
    private static final int VALID_SECONDS = 300;
    private static final String JWT_SECRET = "test-jwt-secret-for-interceptor-0123456789";

    private RequestContextInterceptor interceptor;
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        SignatureVerifier verifier = new SignatureVerifier();
        ReflectionTestUtils.setField(verifier, "signatureSecret", SECRET);
        ReflectionTestUtils.setField(verifier, "signatureValidSeconds", VALID_SECONDS);
        verifier.validateConfiguration();
        // T-ACCESS-004 评审 P1：拦截器新增 OAuth2 JWT 认证分支（注入 Redis 黑名单检查）
        stringRedisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        interceptor = new RequestContextInterceptor(verifier, stringRedisTemplate);
        ReflectionTestUtils.setField(interceptor, "jwtSecretKey", JWT_SECRET);
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        MDC.clear();
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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
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
    @DisplayName("评审 P1：OAuth2 JWT 有效（未撤销）→ USER 上下文绑定（验签+黑名单通过）")
    void shouldBindUser_whenValidOAuth2Jwt() throws Exception {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            java.util.Map.of("tenant_id", "1", "jti", "jti-1"), JWT_SECRET);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.USER);
        assertThat(AccessRequestContext.getOperatorId()).isEqualTo(100L);
        assertThat(AccessRequestContext.getTenantId()).isEqualTo(1L);
        org.mockito.Mockito.verify(stringRedisTemplate).hasKey("oauth2:blacklist:jti-1");
    }

    @Test
    @DisplayName("评审 P1：OAuth2 JWT 已撤销（黑名单命中）→ 401")
    void shouldReject_whenOAuth2JwtRevoked() throws Exception {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            java.util.Map.of("tenant_id", "1", "jti", "jti-1"), JWT_SECRET);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/oauth2/userinfo");
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
            java.util.Map.of("tenant_id", "1", "jti", "jti-1"), "wrong-secret-key");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/oauth2/userinfo");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + jwt);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("P2 路径精确匹配：有效 OAuth2 JWT 访问 /auth/oauth2/authorize → 不认证 → 无会话 401")
    void shouldReject_whenOAuth2JwtOnAuthorizePath() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);
            String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
                java.util.Map.of("tenant_id", "1", "jti", "jti-1"), JWT_SECRET);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/oauth2/authorize");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            req.addHeader("Authorization", "Bearer " + jwt);

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(401);
            assertThat(AccessRequestContext.get()).isNull();
        }
    }

    @Test
    @DisplayName("P1 路径限定：有效 OAuth2 JWT 访问非 OAuth2 路径（/user/page）→ 不认证 → 无会话 401")
    void shouldReject_whenOAuth2JwtOnNonOAuth2Path() throws Exception {
        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::isLogin).thenReturn(false);
            String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
                java.util.Map.of("tenant_id", "1", "jti", "jti-1"), JWT_SECRET);

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/userinfo");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/userinfo");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/logout");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isTrue();
            assertThat(AccessRequestContext.getCallerType()).isEqualTo(CallerType.ANONYMOUS);
        }
    }

    @Test
    @DisplayName("评审 P2：afterConcurrentHandlingStarted 清理上下文与 MDC（异步线程切换防串扰）")
    void afterConcurrentHandlingStartedShouldClearContextAndMdc() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);
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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/domain-config/list");
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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/domain-config/list");
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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);

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
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/abstract-user/sync");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/auth/check");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
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

            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/user/page");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            boolean result = interceptor.preHandle(req, resp, new Object());

            assertThat(result).isFalse();
            assertThat(resp.getStatus()).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("afterCompletion 清理上下文与 MDC（异常路径亦执行）")
    void afterCompletionShouldClearContextAndMdc() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/perm/abstract-user/sync");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader("X-Tenant-Id", "1");
        req.addHeader("X-Service-Code", "example-service");
        req.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);
        interceptor.preHandle(req, resp, new Object());
        assertThat(AccessRequestContext.getServiceCode()).isEqualTo("example-service");

        interceptor.afterCompletion(req, resp, new Object(), null);

        assertThat(AccessRequestContext.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("serviceCode")).isNull();
    }

    /** 与生产相同算法生成 HMAC-SHA256（payload = userId|tenantId|timestamp）。 */
    private static String sign(String userId, String tenantId, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
