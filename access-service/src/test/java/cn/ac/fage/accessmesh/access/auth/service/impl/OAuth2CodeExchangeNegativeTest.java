package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.TokenReq;
import cn.ac.fage.accessmesh.access.auth.dto.TokenResp;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OAuth2 授权码兑换既有负向语义特征化锁（T-ADMIN-028 验收第 2 条核实载体）。
 * <p>
 * redirect/PKCE/过期/重复兑换与「失败校验同样消费授权码」为既有行为，本组用例
 * 钉住客户端关联校验（{@link OAuth2AuthCodeClientBindingTest}）插入后校验链
 * 顺序与一次性语义不变：mock 的 Lua GET+DEL 返回序列即 Redis 消费序列建模。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2CodeExchangeNegativeTest {

    private static final String CLIENT_ID = "web-console";
    private static final String SECRET = "secret";
    private static final String REDIRECT_URI = "https://console.example.com/cb";
    private static final String CODE = "code-x";

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
        ReflectionTestUtils.setField(service, "jwtSecretKey",
            "test-jwt-secret-for-code-negative-0123456789abcd");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SysOauth2Client client() {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setClientId(CLIENT_ID);
        client.setTenantId(1L);
        client.setGrantTypes("authorization_code");
        client.setStatus(1);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw(SECRET,
            cn.dev33.satoken.secure.BCrypt.gensalt()));
        return client;
    }

    private OAuth2AppServiceImpl.AuthCodeData codeData(String codeChallenge, String method) {
        OAuth2AppServiceImpl.AuthCodeData codeData = new OAuth2AppServiceImpl.AuthCodeData();
        codeData.setClientId(CLIENT_ID);
        codeData.setUserId(9L);
        codeData.setTenantId(1L);
        codeData.setRedirectUri(REDIRECT_URI);
        codeData.setScope("profile");
        codeData.setCodeChallenge(codeChallenge);
        codeData.setCodeChallengeMethod(method);
        return codeData;
    }

    private void stubClientAndCode(OAuth2AppServiceImpl.AuthCodeData codeData) throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client());
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData));
    }

    private TokenResp exchange(String verifier, String redirectUri) {
        return service.token(new TokenReq("authorization_code", CLIENT_ID, SECRET,
            CODE, redirectUri, verifier, null));
    }

    private static String s256(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    @Test
    @DisplayName("redirect_uri 与授权码记录不一致 → 拒绝 OAUTH2_REDIRECT_MISMATCH")
    void redirectMismatch_rejected() throws Exception {
        stubClientAndCode(codeData(null, null));

        assertThatThrownBy(() -> exchange(null, "https://evil.example.com/cb"))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_REDIRECT_MISMATCH.getCode());
    }

    @Test
    @DisplayName("授权码带 PKCE 挑战但缺 code_verifier → 拒绝 OAUTH2_CODE_VERIFIER_MISMATCH")
    void pkceMissingVerifier_rejected() throws Exception {
        stubClientAndCode(codeData(s256("correct-verifier"), "S256"));

        assertThatThrownBy(() -> exchange(null, REDIRECT_URI))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode());
    }

    @Test
    @DisplayName("PKCE 错误 verifier 被拒后，同码换正确 verifier 重试 → 授权码已消费拒绝（失败校验烧码语义）")
    void pkceFailedAttempt_burnsCode() throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client());
        // 第一次 GET+DEL 返回授权码（PKCE 失败消费），第二次 GET 已无值
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData(s256("correct-verifier"), "S256")))
            .thenReturn(null);

        assertThatThrownBy(() -> exchange("wrong-verifier", REDIRECT_URI))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_VERIFIER_MISMATCH.getCode());

        assertThatThrownBy(() -> exchange("correct-verifier", REDIRECT_URI))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }

    @Test
    @DisplayName("PKCE 正确 verifier → 兑换成功（校验链插入后 PKCE 正向路径不变）")
    void pkceCorrectVerifier_succeeds() throws Exception {
        stubClientAndCode(codeData(s256("correct-verifier"), "S256"));

        TokenResp resp = exchange("correct-verifier", REDIRECT_URI);

        assertThat(resp.tokenType()).isEqualTo("Bearer");
        assertThat(resp.scope()).isEqualTo("profile");
    }

    @Test
    @DisplayName("Redis 无授权码（未知/过期/已消费）→ 拒绝 OAUTH2_CODE_INVALID")
    void unknownOrExpiredCode_rejected() {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client());
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(null);

        assertThatThrownBy(() -> exchange(null, REDIRECT_URI))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }

    @Test
    @DisplayName("成功兑换后同码重放 → 拒绝 OAUTH2_CODE_INVALID（一次性语义）")
    void replayAfterSuccess_rejected() throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client());
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData(null, null)))
            .thenReturn(null);

        assertThat(exchange(null, REDIRECT_URI).tokenType()).isEqualTo("Bearer");

        assertThatThrownBy(() -> exchange(null, REDIRECT_URI))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AccessErrorCode.OAUTH2_CODE_INVALID.getCode());
    }
}
