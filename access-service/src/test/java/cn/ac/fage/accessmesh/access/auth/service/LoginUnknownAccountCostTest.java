package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.auth.dto.LoginReq;
import cn.ac.fage.accessmesh.access.auth.dto.PlatformLoginReq;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.CredentialPasswords;
import cn.ac.fage.accessmesh.access.it.TenantTestSupport;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录未知账号等成本比对回归（T-PERM-110 时间侧信道）。
 * <p>
 * 账号不存在分支原为短路返回（~0.5ms），错误密码走完整 BCrypt（~60-80ms）——
 * 远程计时可枚举账号存在性。锁：未知账号分支对预生成 dummy 哈希恰好执行一次
 * 真实比对后仍拒绝；错误码（10005）、失败计数、登录日志行为不变。
 * 断言锚=调用次数（0→1），不以毫秒数为门。
 * </p>
 */
class LoginUnknownAccountCostTest {

    private static final String PASSWORD = "Whatever1x";

    @Test
    void shouldRunOneDummyBcryptComparisonWhenTenantUserUnknown() {
        var tenants = mock(TenantDomainService.class);
        when(tenants.findByCode(TenantTestSupport.CODE)).thenReturn(TenantTestSupport.activeTenant());
        var users = mock(UserDomainService.class);
        when(users.lockValidByUsername(anyLong(), anyString())).thenReturn(null);
        var loginFailures = mock(LoginFailureStore.class);
        var loginLogs = mock(LoginLogDomainService.class);
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        var service = new cn.ac.fage.accessmesh.access.auth.service.impl.AuthAppServiceImpl(
            users, mock(UserOrgDomainService.class), tenants, mock(TenantAccessGuard.class),
            loginFailures, loginLogs, redis, mock(UserMenuQueryAppService.class));

        try (var bcrypt = org.mockito.Mockito.mockStatic(BCrypt.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.login(new LoginReq(
                TenantTestSupport.CODE, "ghost-user", PASSWORD, "captcha-id", "8888", null)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AccessErrorCode.PASSWORD_INCORRECT.getCode()));

            // 核心断言：未知账号恰好一次 dummy 比对（旧实现 0 次——短路返回）
            bcrypt.verify(() -> BCrypt.checkpw(eq(PASSWORD), eq(CredentialPasswords.DUMMY_HASH)), times(1));
            bcrypt.verify(() -> BCrypt.checkpw(anyString(), anyString()), times(1));
        }

        // 计数与审计行为不变：失败计数推进、登录日志记「用户不存在」
        verify(loginFailures, times(1)).recordTenantFailure(eq(1L), eq("ghost-user"));
        ArgumentCaptor<LoginLogEntry> captor = ArgumentCaptor.forClass(LoginLogEntry.class);
        verify(loginLogs, times(1)).recordLoginLog(captor.capture());
        assertThat(captor.getValue().failReason()).isEqualTo("用户不存在");
        assertThat(captor.getValue().status()).isEqualTo(0);
        assertThat(captor.getValue().username()).isEqualTo("ghost-user");
    }

    @Test
    void shouldRunOneDummyBcryptComparisonWhenPlatformAccountUnknown() {
        var accounts = mock(PlatformAccountDomainService.class);
        when(accounts.findByUsername(anyString())).thenReturn(null);
        var failures = mock(LoginFailureStore.class);
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        var service = new cn.ac.fage.accessmesh.access.auth.service.impl.PlatformAuthAppServiceImpl(
            accounts, mock(PlatformAccountGuard.class),
            mock(cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService.class),
            mock(cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService.class),
            failures, redis, mock(AuthAppService.class));

        try (var bcrypt = org.mockito.Mockito.mockStatic(BCrypt.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.login(new PlatformLoginReq(
                "ghost-op", PASSWORD, "captcha-id", "8888")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AccessErrorCode.PASSWORD_INCORRECT.getCode()));

            bcrypt.verify(() -> BCrypt.checkpw(eq(PASSWORD), eq(CredentialPasswords.DUMMY_HASH)), times(1));
            bcrypt.verify(() -> BCrypt.checkpw(anyString(), anyString()), times(1));
        }

        verify(failures, times(1)).recordPlatformFailure("ghost-op");
    }

    @Test
    void shouldKeepNormalVerificationCountWhenAccountExists() {
        // 对照组：账号存在（密码错误）时仍只做一次真实比对（dummy 不参与），行为不回退
        var tenants = mock(TenantDomainService.class);
        when(tenants.findByCode(TenantTestSupport.CODE)).thenReturn(TenantTestSupport.activeTenant());
        var users = mock(UserDomainService.class);
        var stored = new cn.ac.fage.accessmesh.access.user.entity.SysUser();
        stored.setId(9L);
        stored.setTenantId(1L);
        stored.setUsername("real-user");
        stored.setStatus(1);
        stored.setPassword(BCrypt.hashpw(PASSWORD));
        when(users.lockValidByUsername(anyLong(), anyString())).thenReturn(stored);
        var loginFailures = mock(LoginFailureStore.class);
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        var service = new cn.ac.fage.accessmesh.access.auth.service.impl.AuthAppServiceImpl(
            users, mock(UserOrgDomainService.class), tenants, mock(TenantAccessGuard.class),
            loginFailures, mock(LoginLogDomainService.class), redis,
            mock(UserMenuQueryAppService.class));

        try (var bcrypt = org.mockito.Mockito.mockStatic(BCrypt.class)) {
            bcrypt.when(() -> BCrypt.checkpw(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.login(new LoginReq(
                TenantTestSupport.CODE, "real-user", "WrongPass9x", "captcha-id", "8888", null)))
                .isInstanceOf(BizException.class);
            bcrypt.verify(() -> BCrypt.checkpw(eq("WrongPass9x"), eq(stored.getPassword())), times(1));
            bcrypt.verify(() -> BCrypt.checkpw(anyString(), anyString()), times(1));
        }
        verify(loginFailures, times(1)).recordTenantFailure(eq(1L), eq("real-user"));
    }
}
