package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenReq;
import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.OAuth2JwtSupport;
import cn.dev33.satoken.jwt.SaJwtUtil;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OAuth2 访问令牌 aud claim 签发测试（T-ACCESS-013）。
 * <p>
 * 用户决策：客户端注册 audiences（sys_oauth2_client.audiences，逗号分隔）非空时
 * 签发写入 aud claim（List 形态）；未配置客户端不写 aud（旧令牌兼容，userinfo 豁免）。
 * 资源服务器按开放路径声明的 audience 强制校验（业务路径），userinfo 默认豁免。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2AudienceClaimTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-aud-claim-0123456789";
    private static final String CLIENT_ID = "example-web";

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

    private SysOauth2Client clientWithAudiences(String audiences) {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setClientId(CLIENT_ID);
        client.setAccessTokenTtl(3600);
        client.setRefreshTokenTtl(604800);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setStatus(1);
        client.setAudiences(audiences);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw("secret",
            cn.dev33.satoken.secure.BCrypt.gensalt()));
        return client;
    }

    private String issueTokenByCodeExchange(SysOauth2Client client) throws Exception {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);
        OAuth2ServiceImpl.AuthCodeData codeData = new OAuth2ServiceImpl.AuthCodeData();
        codeData.setUserId(9L);
        codeData.setTenantId(1L);
        codeData.setClientId(CLIENT_ID);
        codeData.setRedirectUri("https://web.example.com/cb");
        codeData.setScope("profile");
        codeData.setCodeChallenge(null);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(codeData));

        TokenResp resp = service.token(new TokenReq("authorization_code", CLIENT_ID, "secret",
            "code-1", "https://web.example.com/cb", null, null));
        return resp.accessToken();
    }

    @Test
    @DisplayName("客户端配置 audiences → JWT 写入 aud claim（List 形态，拆分逗号）")
    void audiencesConfigured_writtenAsAudClaim() throws Exception {
        String jwt = issueTokenByCodeExchange(clientWithAudiences("access-service, example-service"));

        Map<String, Object> payloads = SaJwtUtil.getPayloads(jwt, "oauth2", JWT_SECRET);
        List<String> aud = OAuth2JwtSupport.audiencesOf(payloads);
        assertThat(aud).containsExactly("access-service", "example-service");
    }

    @Test
    @DisplayName("客户端未配置 audiences → JWT 无 aud claim（旧令牌兼容，userinfo 豁免口径）")
    void audiencesNotConfigured_noAudClaim() throws Exception {
        String jwt = issueTokenByCodeExchange(clientWithAudiences(null));

        Map<String, Object> payloads = SaJwtUtil.getPayloads(jwt, "oauth2", JWT_SECRET);
        assertThat(payloads).doesNotContainKey(OAuth2JwtSupport.AUD_CLAIM);
        assertThat(OAuth2JwtSupport.audiencesOf(payloads)).isEmpty();
    }

    @Test
    @DisplayName("audiences 为空白串 → 不写 aud claim（等同未配置）")
    void audiencesBlank_noAudClaim() throws Exception {
        String jwt = issueTokenByCodeExchange(clientWithAudiences("  "));

        Map<String, Object> payloads = SaJwtUtil.getPayloads(jwt, "oauth2", JWT_SECRET);
        assertThat(payloads).doesNotContainKey(OAuth2JwtSupport.AUD_CLAIM);
    }

    @Test
    @DisplayName("刷新链路同样写入 aud claim（客户端 audiences 非空）")
    void refreshTokenPath_alsoWritesAudClaim() throws Exception {
        SysOauth2Client client = clientWithAudiences("example-service");
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(client);
        OAuth2ServiceImpl.RefreshTokenData stored = new OAuth2ServiceImpl.RefreshTokenData();
        stored.setUserId(9L);
        stored.setTenantId(1L);
        stored.setClientId(CLIENT_ID);
        stored.setScope("profile");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
            .thenReturn(objectMapper.writeValueAsString(stored));

        TokenResp resp = service.refreshToken("old-refresh-token", CLIENT_ID);

        Map<String, Object> payloads = SaJwtUtil.getPayloads(resp.accessToken(), "oauth2", JWT_SECRET);
        assertThat(OAuth2JwtSupport.audiencesOf(payloads)).containsExactly("example-service");
        // scope/client_id 载荷随链路保留
        assertThat(payloads.get(OAuth2JwtSupport.SCOPE_CLAIM)).isEqualTo("profile");
        assertThat(payloads.get(OAuth2JwtSupport.CLIENT_ID_CLAIM)).isEqualTo(CLIENT_ID);
    }
}
