package cn.ac.fage.accessmesh.access.auth.service.impl;

import cn.ac.fage.accessmesh.access.auth.dto.AuthorizeReq;
import cn.ac.fage.accessmesh.access.auth.dto.AuthorizeResp;
import cn.ac.fage.accessmesh.access.auth.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * authorize 签发关键段行锁读 + 锁窗内会话复核回归锁（2026-10-06 逐任务评审 P2，
 * 行锁串行化拍板延伸、同 login 形态）。
 * <p>
 * 缺陷：authorize 的会话验证（StpUtil.getLoginIdAsLong）与用户读取之间无串行边界——
 * 重置（改密+logout）提交后，在途 authorize 重读用户拿到新哈希指纹并签出携带新指纹的
 * 授权码，兑换/刷新/JWT 三处校验全过，旧凭据链最长活 refreshTokenTtl。
 * 修复：authorize 事务化 + lockValidById 行锁读（与 resetPassword UPDATE 行锁互斥）+
 * 锁窗内会话复核。旧实现（无锁读、无复核）下「复核拒绝」用例必红。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OAuth2AuthorizeSerializeRecheckTest {

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

    private OAuth2AppServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new OAuth2AppServiceImpl(oauth2ClientDomainService, userDomainService,
            loginLogDomainService, redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "jwtSecretKey", "authorize-recheck-test-secret-0123456789");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private SysOauth2Client confidentialClient() {
        SysOauth2Client client = new SysOauth2Client();
        client.setId(1L);
        client.setTenantId(1L);
        client.setClientId(CLIENT_ID);
        client.setGrantTypes("authorization_code,refresh_token");
        client.setRedirectUris(REDIRECT_URI);
        client.setStatus(1);
        client.setClientSecret(cn.dev33.satoken.secure.BCrypt.hashpw("secret"));
        return client;
    }

    private cn.ac.fage.accessmesh.access.user.entity.SysUser activeUser() {
        var user = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
        user.setId(100L);
        user.setStatus(1);
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw("initial-password"));
        return user;
    }

    private AuthorizeReq req() {
        return new AuthorizeReq(CLIENT_ID, "code", REDIRECT_URI, null, null, null, null);
    }

    @Test
    @DisplayName("锁读后复核会话：主体已变（重置吊销后重建）→ authorize 拒绝且不签发码（旧实现无复核必红）")
    void authorizeRejectedWhenSessionSubjectChangedAfterLockRead() {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(confidentialClient());
        when(userDomainService.lockValidById(1L, 100L)).thenReturn(activeUser());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L).thenReturn(999L);
            TenantContextHolder.setTenantId(1L);

            assertThatThrownBy(() -> service.authorize(req()))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .hasMessageContaining("会话主体已变更");
        }

        // 签发关键段确实走了行锁读（旧实现走 selectValidById，本 verify 必红）
        verify(userDomainService).lockValidById(1L, 100L);
        // 拒绝发生在授权码存储之前——无任何签发副作用
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("会话稳定路径不受影响：复核通过 → 正常签发授权码")
    void authorizeSucceedsWhenSessionStable() {
        when(oauth2ClientDomainService.findActiveByClientId(CLIENT_ID)).thenReturn(confidentialClient());
        when(userDomainService.lockValidById(1L, 100L)).thenReturn(activeUser());

        AuthorizeResp resp;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(100L);
            TenantContextHolder.setTenantId(1L);
            resp = service.authorize(req());
        }

        assertThat(resp.code()).isNotBlank();
        verify(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.SECONDS));
    }
}
