package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.auth.dto.PlatformPasswordReq;
import cn.ac.fage.accessmesh.access.auth.entity.PlatformAccount;
import cn.ac.fage.accessmesh.access.auth.security.PlatformAccountGuard;
import cn.ac.fage.accessmesh.access.auth.service.domain.PlatformAccountDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.secure.BCrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台自助改密字节上限回归（T-PERM-110）。
 * <p>
 * 26 字符汉字+字母+数字密码 UTF-8 共 74 字节，超 BCrypt 72 字节参与上限——
 * 旧实现经字符规则后直接哈希，尾部差异被静默忽略；修复后写入口先拒 10013。
 * </p>
 */
class PasswordByteLimitOnWritePathsTest {

    private static final String OLD_PASSWORD = "OldPass123";
    /** 24 汉字(72B) + a1(2B) = 74 字节、26 字符、含字母数字——只违反字节规则 */
    private static final String OVER_LIMIT = "密".repeat(24) + "a1";
    /** 23 汉字(69B) + a1!(3B) = 72 字节——两条规则同时满足 */
    private static final String AT_LIMIT = "密".repeat(23) + "a1!";

    private PlatformAccount account() {
        var account = new PlatformAccount();
        account.setId(17L);
        account.setUsername("operator");
        account.setStatus(1);
        account.setPassword(BCrypt.hashpw(OLD_PASSWORD));
        account.setForceResetPwd(false);
        return account;
    }

    @Test
    void shouldRejectOverLimitNewPasswordBeforeHashing() {
        var accounts = mock(PlatformAccountDomainService.class);
        var guard = mock(PlatformAccountGuard.class);
        when(guard.requireAccount(anyBoolean())).thenReturn(account());
        var service = new cn.ac.fage.accessmesh.access.auth.service.impl.PlatformAuthAppServiceImpl(
            accounts, guard, mock(cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService.class),
            mock(cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService.class),
            mock(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.class),
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            mock(AuthAppService.class));

        assertThatThrownBy(() -> service.changePassword(new PlatformPasswordReq(OLD_PASSWORD, OVER_LIMIT)))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                .isEqualTo(AccessErrorCode.PASSWORD_TOO_LONG.getCode()));

        verify(accounts, never()).updatePassword(anyLong(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    void shouldAcceptNewPasswordAtExactByteBoundary() {
        var accounts = mock(PlatformAccountDomainService.class);
        var guard = mock(PlatformAccountGuard.class);
        when(guard.requireAccount(anyBoolean())).thenReturn(account());
        var service = new cn.ac.fage.accessmesh.access.auth.service.impl.PlatformAuthAppServiceImpl(
            accounts, guard, mock(cn.ac.fage.accessmesh.access.auth.security.PlatformSessionService.class),
            mock(cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService.class),
            mock(cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore.class),
            mock(org.springframework.data.redis.core.StringRedisTemplate.class),
            mock(AuthAppService.class));

        service.changePassword(new PlatformPasswordReq(OLD_PASSWORD, AT_LIMIT));

        verify(accounts).updatePassword(org.mockito.ArgumentMatchers.eq(17L),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(false),
            org.mockito.ArgumentMatchers.eq(17L));
    }
}