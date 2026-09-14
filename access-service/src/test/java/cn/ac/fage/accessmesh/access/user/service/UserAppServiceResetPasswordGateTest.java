package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.user.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.user.service.impl.UserAppServiceImpl;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /user/reset-password 门禁单测（T-PERM-067 Q-002 收窄定案）：
 * 自身路径为自助改密通道——免 RESET_PASSWORD 门禁与默认树边界（豁免显式定位保留，
 * 旧密码验证另立任务）；非自身为管理员重置，须持 USER:RESET_PASSWORD 实例级操作位。
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceResetPasswordGateTest {

    private static final Long OPERATOR = 900L;
    private static final Long TARGET = 77L;

    @Mock private SysUserMapper userMapper;
    @Mock private UserDomainService userDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private UserWriteAppService userWriteAppService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;

    private UserAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAppServiceImpl(
            userMapper, null, userDomainService, userOrgDomainService,
            orgTreeConfigDomainService, orgDomainService, userWriteAppService,
            permissionValidator, orgVisibilityQueryService);
    }

    private SysUser user(long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("u" + id);
        user.setStatus(1);
        return user;
    }

    @Test
    @DisplayName("自助改密通道：自身重置免 RESET_PASSWORD 门禁（豁免定位保留，T-PERM-067）")
    void selfResetPasswordSkipsGate() {
        when(userDomainService.selectValidById(Mockito.any(), eq(OPERATOR))).thenReturn(user(OPERATOR));

        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR);

            var resp = service.resetPassword(OPERATOR, "new-pass-123");
            assertThat(resp.newPassword()).isEqualTo("new-pass-123");
        }

        verify(permissionValidator, never()).checkInstanceLevel(any(), any(), any());
    }

    @Test
    @DisplayName("非自身重置须持 USER:RESET_PASSWORD：门禁拒绝时无任何写")
    void nonSelfResetPasswordRequiresGate() {
        Mockito.doThrow(new SecurityException("denied: RESET_PASSWORD"))
            .when(permissionValidator).checkInstanceLevel(
                eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.RESET_PASSWORD));

        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR);

            assertThatThrownBy(() -> service.resetPassword(TARGET, null))
                .isInstanceOf(SecurityException.class);
        }

        verify(userDomainService, never()).update(any(SysUser.class));
    }

    @Test
    @DisplayName("非自身重置持码放行：未指定新密码时自动生成随机密码并强制改密")
    void nonSelfResetPasswordWithCodeGeneratesPassword() {
        when(userDomainService.selectValidById(Mockito.any(), eq(TARGET))).thenReturn(user(TARGET));

        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR);

            var resp = service.resetPassword(TARGET, null);
            assertThat(resp.newPassword()).hasSize(12);
        }

        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.RESET_PASSWORD));
    }

    @Test
    @DisplayName("目标用户不存在拒绝（业务异常非安全异常）")
    void resetPasswordRejectsMissingUser() {
        when(userDomainService.selectValidById(Mockito.any(), eq(OPERATOR))).thenReturn(null);

        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(OPERATOR);

            assertThatThrownBy(() -> service.resetPassword(OPERATOR, "x"))
                .isInstanceOf(BizException.class);
        }
    }
}
