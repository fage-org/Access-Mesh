package cn.ac.fage.accessmesh.access.user.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户用户重置密码字节上限回归（T-PERM-110，自助通道）。
 * <p>
 * 管理员/用户在 reset-password 入口提交 74 字节（26 字符汉字+字母+数字）密码：
 * 旧实现过字符规则后直接哈希（尾部差异静默失效）；修复后先拒 10013 且不落库。
 * 走自身路径（userId==当前会话）以隔离权限门禁与默认树边界。
 * </p>
 */
class UserResetPasswordByteLimitTest {

    /** 24 汉字(72B) + a1(2B) = 74 字节：字符规则通过、字节规则拒绝 */
    private static final String OVER_LIMIT = "密".repeat(24) + "a1";

    @AfterEach
    void clearContext() {
        TenantContextHolder.setTenantId(null);
    }

    @Test
    void shouldRejectOverLimitPasswordBeforeHashingOnSelfServicePath() {
        TenantContextHolder.setTenantId(1L);
        var user = new SysUser();
        user.setId(5L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setStatus(1);
        var userMapper = mock(SysUserMapper.class);
        var userDomainService = mock(UserDomainService.class);
        when(userDomainService.lockValidById(1L, 5L)).thenReturn(user);
        var service = new UserAppServiceImpl(userMapper, userDomainService,
            mock(cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService.class),
            mock(cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService.class),
            mock(cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService.class),
            mock(cn.ac.fage.accessmesh.access.user.service.UserWriteAppService.class),
            mock(cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator.class),
            mock(cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService.class),
            mock(cn.ac.fage.accessmesh.access.infrastructure.PasswordResetLockSupport.class));

        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(5L);

            assertThatThrownBy(() -> service.resetPassword(5L, OVER_LIMIT))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AccessErrorCode.PASSWORD_TOO_LONG.getCode()));
        }

        verify(userMapper, never()).update(any(SysUser.class));
    }
}
