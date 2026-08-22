package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenReq;
import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.dev33.satoken.jwt.SaJwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OAuth2 客户端令牌自定义有效期测试（T-ACCESS-011 验收 8）。
 * <p>
 * 架构 §6.1：OAuth2 客户端令牌保持客户端自定义有效期
 * （sys_oauth2_client.access_token_ttl，默认 86400），
 * 不套用平台用户会话的 sa-token.timeout=7200 口径。
 * 断言授权码兑换与刷新令牌两条签发路径：
 * TokenResp.expiresIn == 客户端 TTL，且 JWT 实际 eff == 客户端 TTL。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2ClientTtlTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-client-ttl-0123456789ab";
    private static final String CLIENT_ID = "web-console";
    private static final int CUSTOM_TTL = 3600;

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
        ReflectionTestUtils.setField(service, "jwtSecretKey", JWT_SECRET);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private SysOauth2Client clientWithCustomTtl() {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setClientId(CLIENT_ID);
        client.setAccessTokenTtl(CUSTOM_TTL);
        client.setRefreshTokenTtl(604800);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setStatus(1);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw("secret", cn.dev33.satoken.secure.BCrypt.gensalt()));
        return client;
    }

    @Test
    @DisplayName("授权码兑换：expiresIn 与 JWT eff 均为客户端自定义 TTL（3600），非默认 86400/平台 7200")
    void authorizationCodeGrant_usesClientCustomTtl() throws Exception {
        SysOauth2Client client = clientWithCustomTtl();
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);

        OAuth2ServiceImpl.AuthCodeData codeData = new OAuth2ServiceImpl.AuthCodeData();
        codeData.setUserId(9L);
        codeData.setTenantId(1L);
        codeData.setClientId(CLIENT_ID);
        codeData.setRedirectUri("https://console.example.com/cb");
        codeData.setScope("profile");
        codeData.setCodeChallenge(null);
        String code = "code-123";
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData));

        TokenResp resp = service.token(new TokenReq("authorization_code", CLIENT_ID, "secret",
            code, "https://console.example.com/cb", null, null));

        assertThat(resp.expiresIn()).isEqualTo(CUSTOM_TTL);
        // getTimeout = eff - 当前时刻，签发后存在亚秒流逝，允许 5 秒容差
        assertThat(SaJwtUtil.getTimeout(resp.accessToken(), "oauth2", JWT_SECRET))
            .isBetween(CUSTOM_TTL - 5L, CUSTOM_TTL + 0L);
    }

    @Test
    @DisplayName("刷新令牌：新访问令牌 expiresIn 与 JWT eff 均为客户端自定义 TTL（3600）")
    void refreshTokenGrant_usesClientCustomTtl() throws Exception {
        SysOauth2Client client = clientWithCustomTtl();
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);

        OAuth2ServiceImpl.RefreshTokenData stored = new OAuth2ServiceImpl.RefreshTokenData();
        stored.setUserId(9L);
        stored.setTenantId(1L);
        stored.setClientId(CLIENT_ID);
        stored.setScope("profile");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(stored));

        TokenResp resp = service.refreshToken("old-refresh-token", CLIENT_ID);

        assertThat(resp.expiresIn()).isEqualTo(CUSTOM_TTL);
        assertThat(SaJwtUtil.getTimeout(resp.accessToken(), "oauth2", JWT_SECRET))
            .isBetween(CUSTOM_TTL - 5L, CUSTOM_TTL + 0L);
    }

    @Test
    @DisplayName("未配置 TTL 的客户端回落默认 86400（仍是客户端注册口径，非平台会话 7200）")
    void clientWithoutTtl_fallsBackToDefault86400() throws Exception {
        SysOauth2Client client = clientWithCustomTtl();
        client.setAccessTokenTtl(null);
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);

        OAuth2ServiceImpl.RefreshTokenData stored = new OAuth2ServiceImpl.RefreshTokenData();
        stored.setUserId(9L);
        stored.setTenantId(1L);
        stored.setClientId(CLIENT_ID);
        stored.setScope("profile");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(stored));

        TokenResp resp = service.refreshToken("old-refresh-token", CLIENT_ID);

        assertThat(resp.expiresIn()).isEqualTo(86400);
        assertThat(SaJwtUtil.getTimeout(resp.accessToken(), "oauth2", JWT_SECRET))
            .isBetween(86400L - 5L, 86400L);
    }
}
