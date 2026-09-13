package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.user.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.user.service.impl.UserWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.org.service.OrgVisibilityQueryAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /user/update 的 status 启停分权门禁单测（T-ACCESS-034 外评处置——claude P2/grok 存量观察
 * 双通道收敛，用户拍板本批补齐）：admin 轨非自身 status 写入须同时过 USER:UPDATE 与
 * USER:ENABLE，对齐 perm 轨字段分档（§7.8）与 /user/enable；旧实现（仅 UPDATE）下
 * 「仅持 UPDATE 改 status 被拒」两用例必红。
 */
@ExtendWith(MockitoExtension.class)
class UserWriteAppServiceUpdateGateTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 900L;
    private static final Long TARGET = 77L;

    @Mock private UserDomainService userDomainService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgTreeConfigDomainService orgTreeConfigDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private OrgVisibilityQueryAppService orgVisibilityQueryService;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private UserWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserWriteAppServiceImpl(
            userDomainService,
            userOrgDomainService,
            orgTreeConfigDomainService,
            orgDomainService,
            permissionValidator,
            orgVisibilityQueryService,
            localProjectionDomainService,
            auditDomainService,
            new ObjectMapper()
        );
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private SysUser target() {
        SysUser user = new SysUser();
        user.setId(TARGET);
        user.setTenantId(TENANT);
        user.setUsername("u77");
        user.setName("目标用户");
        user.setStatus(1);
        return user;
    }

    @Test
    @DisplayName("status 写入补查 USER:ENABLE：ENABLE 拒绝时无任何写（旧实现仅查 UPDATE，本用例必红）")
    void statusChangeRequiresEnableGate() {
        // lenient：UPDATE 变体调用不匹配本 doThrow 桩（严格桩会误报 PotentialStubbingProblem）
        org.mockito.Mockito.lenient().doThrow(new SecurityException("denied: ENABLE"))
            .when(permissionValidator).checkInstanceLevel(
                eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.ENABLE));

        assertThatThrownBy(() -> service.updateUser(new UserUpdateReq(TARGET, "新名", null, null, 0)))
            .isInstanceOf(SecurityException.class);

        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.UPDATE));
        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.ENABLE));
        verify(userDomainService, never()).update(any(SysUser.class));
    }

    @Test
    @DisplayName("无 status 的更新不查 ENABLE（name-only 维持仅 UPDATE）")
    void updateWithoutStatusSkipsEnableGate() {
        when(userDomainService.selectValidById(TENANT, TARGET)).thenReturn(target());
        when(localProjectionDomainService.upsertAdminUser(
            anyLong(), anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
            .thenReturn(901L);

        service.updateUser(new UserUpdateReq(TARGET, "新名", null, null, null));

        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.UPDATE));
        verify(permissionValidator, never()).checkInstanceLevel(
            any(), any(), eq(OperationCode.ENABLE));
    }

    @Test
    @DisplayName("持双码（UPDATE+ENABLE）改 status 放行并落库")
    void statusChangeWithBothCodesPasses() {
        when(userDomainService.selectValidById(TENANT, TARGET)).thenReturn(target());
        when(localProjectionDomainService.upsertAdminUser(
            anyLong(), anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
            .thenReturn(901L);

        service.updateUser(new UserUpdateReq(TARGET, null, null, null, 0));

        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.UPDATE));
        verify(permissionValidator).checkInstanceLevel(
            eq(ResourceTypeCode.USER), eq(String.valueOf(TARGET)), eq(OperationCode.ENABLE));
        verify(userDomainService).update(any(SysUser.class));
    }

    @Test
    @DisplayName("自身更新（含 status）零门禁——自身豁免语义与 perm 轨/Q-002 同款保留")
    void selfUpdateSkipsAllGatesEvenWithStatus() {
        when(userDomainService.selectValidById(TENANT, OPERATOR)).thenReturn(target());
        when(localProjectionDomainService.upsertAdminUser(
            anyLong(), anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
            .thenReturn(901L);

        service.updateUser(new UserUpdateReq(OPERATOR, "自改名", null, null, 0));

        verify(permissionValidator, never()).checkInstanceLevel(any(), any(), any());
        verify(userDomainService).update(any(SysUser.class));
    }
}
