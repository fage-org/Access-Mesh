package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.TokenReq;
import cn.ac.fage.accessmesh.access.auth.dto.TokenResp;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.OAuth2JwtSupport;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.jwt.SaJwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 授权码客户端关联校验测试（F002 / T-ADMIN-028）。
 * <p>
 * 缺口形态（旧实现）：tokenByAuthorizationCode 校验 clientId+secret、grant type、
 * redirect_uri、PKCE，但从不比对授权码记录中的 clientId——B 用自身合法凭据
 * （原样回传 A 的 redirectUri、无 PKCE 授权码无需 verifier）即可兑换签发给 A 的
 * 授权码，令牌以 B 的 clientId、B 的 audiences 签发。本组负向用例在旧实现下
 * 兑换成功（无异常抛出），即断言失败，构成行为锁。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2AuthCodeClientBindingTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-client-binding-0123456789";
    private static final String CLIENT_A = "web-console";
    private static final String CLIENT_B = "mobile-app";
    private static final String SECRET_A = "secret-a";
    private static final String SECRET_B = "secret-b";
    private static final String REDIRECT_A = "https://console.example.com/cb";
    private static final String CODE = "code-issued-for-binding";

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

    private OAuth2AppServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OAuth2AppServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "jwtSecretKey", JWT_SECRET);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SysOauth2Client client(String clientId, String secret, long tenantId, String audiences) {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setClientId(clientId);
        client.setTenantId(tenantId);
        client.setGrantTypes("authorization_code");
        client.setRedirectUris("https://registered.example.com/cb");
        client.setStatus(1);
        client.setAudiences(audiences);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw(secret,
            cn.dev33.satoken.secure.BCrypt.gensalt()));
        return client;
    }

    /** 签发给 {@code clientId} 的授权码数据（redirectUri/scope 固定，无 PKCE——缺口最宽形态）。 */
    private OAuth2AppServiceImpl.AuthCodeData codeFor(String clientId, long tenantId, String redirectUri) {
        OAuth2AppServiceImpl.AuthCodeData codeData = new OAuth2AppServiceImpl.AuthCodeData();
        codeData.setClientId(clientId);
        codeData.setUserId(9L);
        codeData.setTenantId(tenantId);
        codeData.setRedirectUri(redirectUri);
        codeData.setScope("profile read");
        codeData.setCodeChallenge(null);
        return codeData;
    }

    /** 认证方 = {@code authenticatedClient}（凭据合法），兑换 Redis 中的 {@code codeData}。 */
    private void stubCode(SysOauth2Client authenticatedClient,
                          OAuth2AppServiceImpl.AuthCodeData codeData) throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(authenticatedClient.getClientId()))
            .thenReturn(authenticatedClient);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData));
    }

    private TokenResp exchange(String clientId, String secret, String redirectUri) {
        return service.token(new TokenReq("authorization_code", clientId, secret,
            CODE, redirectUri, null, null));
    }

    @Test
    @DisplayName("同租户：B 的合法凭据兑换签发给 A 的授权码 → 拒绝 OAUTH2_CODE_INVALID（旧实现可成功）")
    void crossClientExchange_sameTenant_rejected() throws Exception {
        stubCode(client(CLIENT_B, SECRET_B, 1L, "aud-b"), codeFor(CLIENT_A, 1L, REDIRECT_A));

        assertThatThrownBy(() -> exchange(CLIENT_B, SECRET_B, REDIRECT_A))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }

    @Test
    @DisplayName("跨租户：租户 2 的 B 兑换租户 1 签发给 A 的授权码 → 同样拒绝（客户端关联先于租户语义）")
    void crossClientExchange_crossTenant_rejected() throws Exception {
        stubCode(client(CLIENT_B, SECRET_B, 2L, "aud-b"), codeFor(CLIENT_A, 1L, REDIRECT_A));

        assertThatThrownBy(() -> exchange(CLIENT_B, SECRET_B, REDIRECT_A))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }

    @Test
    @DisplayName("反向：A 的合法凭据兑换签发给 B 的授权码 → 拒绝（关联校验对称）")
    void crossClientExchange_reverseDirection_rejected() throws Exception {
        String redirectB = "https://mobile.example.com/cb";
        stubCode(client(CLIENT_A, SECRET_A, 1L, "aud-a"), codeFor(CLIENT_B, 1L, redirectB));

        assertThatThrownBy(() -> exchange(CLIENT_A, SECRET_A, redirectB))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }

    @Test
    @DisplayName("合法兑换：令牌绑定授权码的 scope/tenant 与签发客户端的 audience（关联保持）")
    void legitExchange_preservesScopeAudienceTenantBinding() throws Exception {
        stubCode(client(CLIENT_A, SECRET_A, 1L, "access-service,example-service"),
            codeFor(CLIENT_A, 1L, REDIRECT_A));

        TokenResp resp = exchange(CLIENT_A, SECRET_A, REDIRECT_A);

        Map<String, Object> payloads = SaJwtUtil.getPayloads(resp.accessToken(),
            OAuth2JwtSupport.LOGIN_TYPE, JWT_SECRET);
        assertThat(payloads.get(OAuth2JwtSupport.CLIENT_ID_CLAIM)).isEqualTo(CLIENT_A);
        assertThat(payloads.get(OAuth2JwtSupport.TENANT_CLAIM)).isEqualTo("1");
        assertThat(payloads.get(OAuth2JwtSupport.SCOPE_CLAIM)).isEqualTo("profile read");
        assertThat(OAuth2JwtSupport.audiencesOf(payloads))
            .containsExactly("access-service", "example-service");
        assertThat(resp.scope()).isEqualTo("profile read");
    }

    @Test
    @DisplayName("跨客户端拒绝：失败审计 status=0、租户=授权码租户（非兑换方注册租户兜底）、failReason 含 client mismatch")
    void crossClientRejection_recordsFailureAudit() throws Exception {
        // B 注册租户与授权码租户相异：tenantId 断言才有判别力——
        // 授权码租户（holder 路径）解析成功 vs recordOauth2Failure 按 clientId 兜底取 B 租户，两形态结果不同
        stubCode(client(CLIENT_B, SECRET_B, 2L, null), codeFor(CLIENT_A, 1L, REDIRECT_A));

        assertThatThrownBy(() -> exchange(CLIENT_B, SECRET_B, REDIRECT_A))
            .isInstanceOf(BizException.class);

        ArgumentCaptor<LoginLogDomainService.LoginLogEntry> captor =
            ArgumentCaptor.forClass(LoginLogDomainService.LoginLogEntry.class);
        verify(loginLogDomainService).recordLoginLog(captor.capture());
        LoginLogDomainService.LoginLogEntry entry = captor.getValue();
        // 租户取授权码（A/租户1）登记的上下文；若走 B 注册租户兜底则为 2，断言即红
        assertThat(entry.tenantId()).isEqualTo(1L);
        assertThat(entry.loginType()).isEqualTo("OAUTH2");
        assertThat(entry.clientId()).isEqualTo(CLIENT_B);
        assertThat(entry.status()).isEqualTo(0);
        assertThat(entry.failReason()).contains("client mismatch");
    }
}
