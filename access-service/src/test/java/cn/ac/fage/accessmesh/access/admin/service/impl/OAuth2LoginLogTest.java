package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenReq;
import cn.ac.fage.accessmesh.access.admin.dto.oauth2.TokenResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 令牌环节 OAUTH2 登录日志测试（T-ACCESS-007）。
 * <p>
 * 验证授权码换取令牌成功后写入 sys_login_log（loginType=OAUTH2，
 * DDL 列注释声明的第三种登录方式自此有真实写入场景），用户名回填自用户库；
 * 以及失败尝试（凭据/授权码/刷新令牌无效等）在租户可解析时写 status=0、
 * 租户不可解析时跳过并告警的安全审计。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginLogTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-oauth2-login-log-0123456789";

    @Mock private OAuth2ClientDomainService oauth2ClientDomainService;
    @Mock private UserDomainService userDomainService;
    @Mock private LoginLogDomainService loginLogDomainService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private OAuth2ServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OAuth2ServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "jwtSecretKey", JWT_SECRET);
    }

    @Test
    @DisplayName("评审 P2#6：授权码换取令牌成功后写 OAUTH2 登录日志")
    void shouldRecordOauth2LoginLog_whenTokenIssued() throws Exception {
        // 1. mock 客户端（findActiveByClientId 生效，BCrypt.checkpw 校验通过）
        String clientSecret = "plain-client-secret";
        SysOauth2Client client = new SysOauth2Client();
        client.setClientSecret(BCrypt.hashpw(clientSecret, BCrypt.gensalt()));
        client.setGrantTypes("authorization_code");
        client.setAccessTokenTtl(3600);
        client.setRefreshTokenTtl(604800);
        client.setRedirectUris("http://app/cb");
        when(oauth2ClientDomainService.findActiveByClientId("client-1")).thenReturn(client);

        // 2. 授权码已存 Redis（execute LUA 脚本一次性 GET+DEL；指定 RedisScript 类型定位脚本重载，
        //    避免 varargs 双 any() 触发返回泛型 V 的 Boolean 解引用 NPE）
        org.mockito.BDDMockito.doReturn(
            "{\"clientId\":\"client-1\",\"userId\":100,\"tenantId\":10,"
                + "\"redirectUri\":\"http://app/cb\",\"codeChallenge\":null,\"codeChallengeMethod\":null,"
                + "\"scope\":\"read\"}")
            .when(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList());

        // 3. 用户库回填用户名
        SysUser user = new SysUser();
        user.setId(100L);
        user.setUsername("oauth-user");
        when(userDomainService.selectValidById(10L, 100L)).thenReturn(user);
        // generateAccessToken / 刷新令牌存储用 opsForValue
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TokenResp resp = service.token(new TokenReq(
            "authorization_code", "client-1", clientSecret, "code-abc",
            "http://app/cb", null, null));

        assertNotNull(resp);

        ArgumentCaptor<LoginLogEntry> captor = ArgumentCaptor.forClass(LoginLogEntry.class);
        verify(loginLogDomainService).recordLoginLog(captor.capture());
        LoginLogEntry entry = captor.getValue();
        assertEquals(10L, entry.tenantId());
        assertEquals(100L, entry.userId());
        assertEquals("oauth-user", entry.username());
        assertEquals("OAUTH2", entry.loginType());
        assertEquals("client-1", entry.clientId());
        assertEquals(1, entry.status());
    }

    @Test
    @DisplayName("刷新令牌无效（客户端有效）→ 写 OAUTH2 登录日志失败（status=0）")
    void shouldRecordOauth2LoginFailure_whenRefreshTokenInvalid() {
        SysOauth2Client client = new SysOauth2Client();
        client.setTenantId(10L);
        client.setGrantTypes("authorization_code");
        when(oauth2ClientDomainService.findActiveByClientId("client-1")).thenReturn(client);

        // 刷新令牌存在但 Redis 中无对应数据（LUA 一次性 GET+DEL 返回 null）
        org.mockito.BDDMockito.doReturn(null)
            .when(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<String>>any(), anyList());

        assertThrows(BizException.class, () -> service.refreshToken("refresh-token-x", "client-1"));

        ArgumentCaptor<LoginLogEntry> captor = ArgumentCaptor.forClass(LoginLogEntry.class);
        verify(loginLogDomainService).recordLoginLog(captor.capture());
        LoginLogEntry entry = captor.getValue();
        assertEquals(10L, entry.tenantId());
        assertEquals("OAUTH2", entry.loginType());
        assertEquals("client-1", entry.clientId());
        assertEquals(0, entry.status());
    }

    @Test
    @DisplayName("客户端密钥错误 → 写 OAUTH2 登录日志失败（status=0）")
    void shouldRecordOauth2LoginFailure_whenClientSecretMismatch() {
        String correctSecret = "right-secret";
        SysOauth2Client client = new SysOauth2Client();
        client.setTenantId(10L);
        client.setClientSecret(BCrypt.hashpw(correctSecret, BCrypt.gensalt()));
        client.setGrantTypes("authorization_code");
        when(oauth2ClientDomainService.findActiveByClientId("client-1")).thenReturn(client);

        // 授权码已存（密钥校验在授权码读取之前被拒绝，无需走到 Redis）
        TokenResp resp = null;
        try {
            resp = service.token(new TokenReq(
                "authorization_code", "client-1", "wrong-secret", "code-abc",
                "http://app/cb", null, null));
        } catch (BizException ignored) {
            // 预期抛出
        }
        assertNull(resp);

        ArgumentCaptor<LoginLogEntry> captor = ArgumentCaptor.forClass(LoginLogEntry.class);
        verify(loginLogDomainService).recordLoginLog(captor.capture());
        LoginLogEntry entry = captor.getValue();
        assertEquals("client-1", entry.clientId());
        assertEquals("OAUTH2", entry.loginType());
        assertEquals(0, entry.status());
    }

    @Test
    @DisplayName("缺少客户端ID（租户不可解析）→ 跳过登录日志（不写 status=0 于不可解析租户）")
    void shouldSkipOauth2LoginFailure_whenTenantUnresolvable() {
        // clientId 为 null：租户无法从任何来源解析，按匿名语义跳过并告警
        assertThrows(BizException.class,
            () -> service.token(new TokenReq(
                "authorization_code", null, null, null, null, null, null)));

        verify(loginLogDomainService, never()).recordLoginLog(any());
    }
}
