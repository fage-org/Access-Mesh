package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.AuthorizeReq;
import cn.ac.fage.accessmesh.access.admin.dto.oauth2.AuthorizeResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * OAuth2 授权请求 scope 校验测试（T-ACCESS-013 评审 P1 修复）。
 * <p>
 * 客户端注册 scopes 为空（null/空白）时不再解释为"无限制"：scope 已是资源端业务开放
 * 路径的核心授权门禁，未注册可授予范围的客户端请求非空 scope 必须拒绝（否则可在
 * authorize 声明任意 scope，签发 JWT 通过资源端 requiredScopes 校验）。请求空 scope
 * 仍放行——签发的无 scope 令牌因业务路径 requiredScopes 强制非空而访问不了任何业务
 * 路径，仅可访问 userinfo 豁免端点。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2ScopeValidationTest {

    private static final String CLIENT_ID = "example-web";
    private static final String REDIRECT_URI = "https://web.example.com/cb";

    @Mock
    private OAuth2ClientDomainService oauth2ClientDomainService;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private LoginLogDomainService loginLogDomainService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuth2ServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OAuth2ServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "jwtSecretKey", "test-jwt-secret-for-scope-0123456789");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SysOauth2Client clientWithScopes(String scopes) {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setClientId(CLIENT_ID);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris(REDIRECT_URI);
        client.setScopes(scopes);
        client.setStatus(1);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw("secret",
            cn.dev33.satoken.secure.BCrypt.gensalt()));
        return client;
    }

    private AuthorizeResp authorize(String scope) throws Exception {
        try (MockedStatic<cn.dev33.satoken.stp.StpUtil> stp = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            stp.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(100L);
            // authorize 生产链路运行于会话上下文（拦截器已绑定租户）；AuthCodeData.tenantId
            // 为基本类型，测试环境需显式绑定租户防拆箱 NPE
            cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.setTenantId(1L);
            try {
                return service.authorize(new AuthorizeReq(CLIENT_ID, "code", REDIRECT_URI, null,
                    scope, null, null));
            } finally {
                cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.clear();
            }
        }
    }

    @Test
    @DisplayName("评审 P1：客户端注册 scopes 为 null + 请求非空 scope → 拒绝（不再解释为无限制）")
    void nullRegisteredScopes_nonEmptyRequestScope_rejected() {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(clientWithScopes(null));

        assertThatThrownBy(() -> authorize("example:admin"))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.OAUTH2_SCOPE_INVALID.getCode());
    }

    @Test
    @DisplayName("评审 P1：客户端注册 scopes 为空白串 + 请求非空 scope → 拒绝")
    void blankRegisteredScopes_nonEmptyRequestScope_rejected() {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(clientWithScopes("  "));

        assertThatThrownBy(() -> authorize("profile"))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.OAUTH2_SCOPE_INVALID.getCode());
    }

    @Test
    @DisplayName("客户端注册 scopes 为空 + 请求 scope 也为空 → 放行（无 scope 令牌仅可访问 userinfo 豁免端点）")
    void nullRegisteredScopes_emptyRequestScope_allowed() throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(clientWithScopes(null));

        AuthorizeResp resp = authorize(null);

        assertThat(resp).isNotNull();
        assertThat(resp.code()).isNotBlank();
    }

    @Test
    @DisplayName("已注册 scopes：请求 scope 为其子集 → 放行（既有行为回归）")
    void registeredScopes_subsetRequest_allowed() throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID))
            .thenReturn(clientWithScopes("profile,email"));

        AuthorizeResp resp = authorize("profile email");

        assertThat(resp).isNotNull();
    }
}
