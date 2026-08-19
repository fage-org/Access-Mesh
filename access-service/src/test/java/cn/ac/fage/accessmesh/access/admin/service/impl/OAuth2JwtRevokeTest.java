package cn.ac.fage.accessmesh.access.admin.service.impl;

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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 revokeToken 黑名单安全测试（评审 P1 修复，2026-08-14）。
 * <p>
 * 修复前：未验签即解析 jti（失败返回原 token 作键）+ TTL 读不存在的 exp 恒回退 86400，
 * 匿名调用者可制造任意 oauth2:blacklist:* 键（Redis 内存 DoS）。
 * 修复后：先验签（签名 + loginType + 有效期），非法令牌不写 Redis；TTL 用 SaJwtUtil.getTimeout。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2JwtRevokeTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-revoke-test-0123456789";

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

    @BeforeEach
    void setUp() {
        service = new OAuth2ServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "jwtSecretKey", JWT_SECRET);
        // lenient：仅"写黑名单"用例用到（不写用例无 opsForValue 调用，严格模式会报多余 stub）
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("评审 P1：非 JWT 任意字符串 → 不写 Redis（原为直接写入制造黑名单键）")
    void shouldNotWriteRedis_whenTokenNotJwt() {
        service.revokeToken("任意-攻击-字符串-abc123");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("评审 P1：签名无效 JWT → 不写 Redis")
    void shouldNotWriteRedis_whenSignatureInvalid() {
        String jwt = SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            Map.of("tenant_id", "1", "jti", "jti-fake"), "wrong-secret-key");

        service.revokeToken(jwt);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("评审 P1：有效 JWT → 写入黑名单（键含 jti，TTL 为实际剩余≈签发 3600s）")
    void shouldWriteBlacklist_whenValidJwt() {
        String jwt = SaJwtUtil.createToken("oauth2", 100L, "oauth2", 3600,
            Map.of("tenant_id", "1", "jti", "jti-valid-1"), JWT_SECRET);

        service.revokeToken(jwt);

        // TTL 必须来自 JWT 实际剩余有效期（eff）：签发 3600s，断言接近该值——
        // 退化回固定 86400 秒（原缺陷）时此断言失败
        verify(valueOperations).set(eq("oauth2:blacklist:jti-valid-1"), eq("1"),
            org.mockito.ArgumentMatchers.longThat(ttl -> ttl > 3500L && ttl <= 3600L),
            eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("评审 P1：有效期已过的 JWT（验签超时失败）→ 不写 Redis")
    void shouldNotWriteRedis_whenJwtExpired() {
        // timeout=-2 → EFF = now - 2000（SaJwtTemplate 逻辑：eff = timeout*1000 + now）→ 已过期
        // 注意：-1 是 NEVER_EXPIRE（永不过期）语义，不在此用例
        String expired = SaJwtUtil.createToken("oauth2", 100L, "oauth2", -2,
            Map.of("tenant_id", "1", "jti", "jti-expired"), JWT_SECRET);

        service.revokeToken(expired);

        verify(redisTemplate, never()).opsForValue();
    }
}
