package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.AuthorizeReq;
import cn.ac.fage.accessmesh.access.auth.dto.TokenReq;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OAuth2CredentialGenerationTest {
    private static final String CLIENT = "web-console";
    private static final String REDIRECT = "https://console.example.com/cb";
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> records = new HashMap<>();
    private final Map<String, Long> ttls = new HashMap<>();
    private final SysUser user = new SysUser();
    private OAuth2AppServiceImpl service;
    private SysOauth2Client client;
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        var clients = mock(OAuth2ClientDomainService.class);
        var users = mock(UserDomainService.class);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        // T-ACCESS-097 预读段（只读不删）：与 GET+DEL 消费段共享同一 in-memory 记录
        when(values.get(anyString())).thenAnswer(call -> records.get(call.getArgument(0)));
        doAnswer(call -> {
            records.put(call.getArgument(0), call.getArgument(1));
            ttls.put(call.getArgument(0), call.getArgument(2));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenAnswer(call ->
            records.remove(((List<String>) call.getArgument(1)).getFirst()));
        client = new SysOauth2Client();
        client.setClientId(CLIENT);
        client.setTenantId(1L);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris(REDIRECT);
        client.setClientSecret(BCrypt.hashpw("secret"));
        client.setAccessTokenTtl(3600);
        client.setRefreshTokenTtl(604800);
        // T-ACCESS-097：authorize 会话链带租户单查；匿名 token/refresh 链跨租户列表定位
        when(clients.findActiveByClientId(1L, CLIENT)).thenReturn(client);
        when(clients.findActiveListByClientId(CLIENT)).thenReturn(List.of(client));
        user.setStatus(1);
        user.setPassword(BCrypt.hashpw("initial-password"));
        when(users.selectValidById(1L, 9L)).thenReturn(user);
        // authorize 签发关键段改行锁读（2026-10-06 逐任务评审 P2）；preview 仍走 selectValidById
        when(users.lockValidById(1L, 9L)).thenReturn(user);
        service = new OAuth2AppServiceImpl(clients, users, mock(LoginLogDomainService.class), redis, json, cn.ac.fage.accessmesh.access.it.TenantTestSupport.activeGuard());
        ReflectionTestUtils.setField(service, "jwtSecretKey", "credential-generation-test-secret-0123456789");
    }

    @AfterEach
    void cleanUp() {
        TenantContextHolder.clear();
        cn.ac.fage.accessmesh.access.audit.aop.OperationLogRuntimeContext.clear();
    }

    private String authorize() {
        return authorize(REDIRECT, null, null);
    }

    private String authorize(String redirect, String challenge, String method) {
        TenantContextHolder.setTenantId(1L);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            return service.authorize(new AuthorizeReq(CLIENT, "code", redirect, null, null, challenge, method)).code();
        }
    }

    private cn.ac.fage.accessmesh.access.auth.dto.TokenResp exchange(String code) {
        return service.token(new TokenReq("authorization_code", CLIENT, "secret", code, REDIRECT, null, null));
    }

    @Test
    void codeIssuedBeforePasswordResetCannotBeExchanged() {
        String code = authorize();
        user.setPassword(BCrypt.hashpw("reset-password"));
        assertThatThrownBy(() -> exchange(code)).isInstanceOf(BizException.class);
        assertThat(records.keySet()).noneMatch(key -> key.startsWith("oauth2:refresh:"));
    }

    @Test
    void refreshRotationPreservesGenerationAndResetRejectsOldChain() throws Exception {
        var tokens = exchange(authorize());
        var original = json.readTree(records.get("oauth2:refresh:" + tokens.refreshToken()));
        for (int i = 0; i < 3; i++) {
            tokens = service.refreshToken(tokens.refreshToken(), CLIENT);
            var rotated = json.readTree(records.get("oauth2:refresh:" + tokens.refreshToken()));
            assertThat(rotated.path("passwordFingerprint").asText()).isNotBlank();
            assertThat(rotated.path("chainIssuedAt").asLong()).isPositive().isEqualTo(original.path("chainIssuedAt").asLong());
            assertThat(rotated.path("chainExpiresAt")).isEqualTo(original.path("chainExpiresAt"));
        }
        String oldRefresh = tokens.refreshToken();
        user.setPassword(BCrypt.hashpw("reset-password"));
        assertThatThrownBy(() -> service.refreshToken(oldRefresh, CLIENT)).isInstanceOf(BizException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void legacyCredentialsWithoutGenerationAreRejected(boolean authorizationCode) throws Exception {
        String code = authorize();
        String credential = authorizationCode ? code : exchange(code).refreshToken();
        String key = (authorizationCode ? "oauth2:code:" : "oauth2:refresh:") + credential;
        var stored = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(records.get(key));
        stored.remove(List.of("passwordFingerprint", "chainIssuedAt", "chainExpiresAt"));
        records.put(key, json.writeValueAsString(stored));
        assertThatThrownBy(() -> {
            if (authorizationCode) exchange(credential); else service.refreshToken(credential, CLIENT);
        }).isInstanceOf(BizException.class);
    }

    @Test
    void refreshAndAccessTtlCannotPassAbsoluteDeadline() throws Exception {
        String token = exchange(authorize()).refreshToken();
        var stored = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(records.get("oauth2:refresh:" + token));
        long now = System.currentTimeMillis() / 1000;
        stored.put("chainIssuedAt", now - 604770);
        stored.put("chainExpiresAt", now + 30);
        records.put("oauth2:refresh:" + token, json.writeValueAsString(stored));
        var rotated = service.refreshToken(token, CLIENT);
        assertThat(rotated.expiresIn()).isBetween(1, 30);
        var jwt = json.readTree(java.util.Base64.getUrlDecoder().decode(rotated.accessToken().split("\\.")[1]));
        assertThat(jwt.path("eff").asLong()).isLessThanOrEqualTo((now + 30) * 1000L);
        assertThat(ttls.get("oauth2:refresh:" + rotated.refreshToken())).isBetween(1L, 30L);
        var expired = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(records.get("oauth2:refresh:" + rotated.refreshToken()));
        expired.put("chainExpiresAt", now - 1);
        records.put("oauth2:refresh:" + rotated.refreshToken(), json.writeValueAsString(expired));
        assertThatThrownBy(() -> service.refreshToken(rotated.refreshToken(), CLIENT)).isInstanceOf(BizException.class);
    }

    @Test
    void publicClientCompletesS256CodeExchangeAndRefreshWithoutSecret() {
        client.setClientType("PUBLIC");
        client.setClientSecret(null);
        String code = authorize(REDIRECT, CHALLENGE, "S256");
        var token = service.token(new TokenReq("authorization_code", CLIENT, null, code, REDIRECT, VERIFIER, null));
        assertThat(token.accessToken()).isNotBlank();
        assertThat(service.refreshToken(token.refreshToken(), CLIENT).refreshToken()).isNotEqualTo(token.refreshToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "plain", "short"})
    void publicAuthorizationRequiresExplicitS256Challenge(String invalid) {
        client.setClientType("PUBLIC");
        client.setClientSecret(null);
        String challenge = invalid.equals("missing") ? null : invalid.equals("short") ? "short" : CHALLENGE;
        String method = invalid.equals("plain") ? "plain" : "S256";
        assertThatThrownBy(() -> authorize(REDIRECT, challenge, method))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(10907));
        assertThat(records).isEmpty();
    }

    @Test
    void publicTokenRejectsWrongVerifierAndConsumesCode() {
        client.setClientType("PUBLIC");
        client.setClientSecret(null);
        String code = authorize(REDIRECT, CHALLENGE, "S256");
        assertThatThrownBy(() -> service.token(new TokenReq("authorization_code", CLIENT, null, code, REDIRECT, "x".repeat(43), null)))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(10907));
        assertThat(records).doesNotContainKey("oauth2:code:" + code);
    }

    @Test
    void rootRegistrationDoesNotAllowArbitraryCallbackButNonRootPrefixRemains() {
        client.setRedirectUris("https://console.example.com/");
        assertThatThrownBy(this::authorize).isInstanceOfSatisfying(BizException.class,
            ex -> assertThat(ex.getErrorCode()).isEqualTo(10904));
        assertThat(authorize("https://console.example.com/", null, null)).isNotBlank();
        client.setRedirectUris("https://console.example.com/app");
        assertThat(authorize("https://console.example.com/app/cb", null, null)).isNotBlank();
    }

    @Test
    void previewValidatesRequestAndDoesNotIssueCode() {
        client.setClientType("PUBLIC");
        client.setClientName("测试应用");
        client.setScopes("profile,email");
        TenantContextHolder.setTenantId(1L);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            var preview = service.previewAuthorization(new AuthorizeReq(CLIENT, "code", REDIRECT,
                "csrf-state", "profile email", CHALLENGE, "S256"));
            assertThat(preview.clientName()).isEqualTo("测试应用");
            assertThat(preview.scopes()).containsExactly("profile", "email");
            assertThat(preview.redirectUri()).isEqualTo(REDIRECT);
            assertThat(preview.state()).isEqualTo("csrf-state");
            assertThat(records).isEmpty();
        }
    }

    @Test
    void executableCallbackSchemeIsRejectedBeforeConsent() {
        client.setRedirectUris("javascript://console.example.com/cb");
        assertThatThrownBy(() -> authorize("javascript://console.example.com/cb", null, null))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(10904));
    }

    @Test
    void publicTokenCannotDowngradeStoredChallengeMethod() throws Exception {
        client.setClientType("PUBLIC");
        client.setClientSecret(null);
        String code = authorize(REDIRECT, CHALLENGE, "S256");
        var stored = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(records.get("oauth2:code:" + code));
        stored.put("codeChallengeMethod", "plain");
        records.put("oauth2:code:" + code, json.writeValueAsString(stored));
        assertThatThrownBy(() -> service.token(new TokenReq("authorization_code", CLIENT, null, code, REDIRECT, CHALLENGE, null)))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(10907));
    }
    @Test
    void authoritylessCallbackIsRejectedAsMismatch() {
        client.setRedirectUris("https:/unregistered.example/cb");
        assertThatThrownBy(() -> authorize("https:/unregistered.example/cb", null, null))
            .isInstanceOfSatisfying(BizException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(10904));
    }
}
