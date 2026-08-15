package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleProxyServiceImplTest {

    @Mock private RoleManageAppService roleManageAppService;
    @Mock private UserManageAppService userManageAppService;
    @Mock private PermissionViewAppService permissionViewAppService;
    @Mock private PermissionGrantAppService permissionGrantAppService;
    @Mock private UserOrgDomainService userOrgDomainService;
    @Mock private OrgDomainService orgDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private RoleProxyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleProxyServiceImpl(
            roleManageAppService,
            userManageAppService,
            permissionViewAppService,
            permissionGrantAppService,
            userOrgDomainService,
            orgDomainService,
            permissionValidator,
            new LocalProjectionGuard(),
            typeResolutionService,
            engine
        );
    }

    @Test
    @DisplayName("createRoleForOrg: 组织/岗位本地投影一律拒绝")
    void createRoleForOrgRejectsLocalProjection() {
        SysOrg org = new SysOrg();
        org.setId(20L);
        org.setOrgType("2");
        when(orgDomainService.selectValidById(1L, 20L)).thenReturn(org);

        assertThatThrownBy(() -> service.createRoleForOrg("position-owner", 20L, 1L))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    @DisplayName("grantMenuToRole: 功能角色走本地 apply-grant-plan")
    void grantMenuToRoleUsesFunctionalRoleBusinessKey() {
        when(roleManageAppService.getRole(1L, 99L)).thenReturn(roleResp(99L, "BASIC_ROLE", "20"));

        service.grantMenuToRole(1L, 99L, 10L, "VIEW");

        ArgumentCaptor<ApplyGrantPlanReq> grantReq = ArgumentCaptor.forClass(ApplyGrantPlanReq.class);
        verify(permissionGrantAppService).applyGrantPlan(eq(1L), grantReq.capture());
        assertThat(grantReq.getValue().roleTypeCode()).isEqualTo("BASIC_ROLE");
        assertThat(grantReq.getValue().roleExternalId()).isEqualTo("20");
        assertThat(grantReq.getValue().plan().createItems()).hasSize(1);
        assertThat(grantReq.getValue().plan().createItems().get(0).key().resourceCode()).isEqualTo("10");
        assertThat(grantReq.getValue().plan().createItems().get(0).key().operationCode()).isEqualTo("VIEW");
    }

    @Test
    @DisplayName("revokeMenuToRole: 按菜单业务键删除已有授权")
    void revokeMenuFromRoleRemovesMatchingGrants() {
        when(roleManageAppService.getRole(1L, 99L)).thenReturn(roleResp(99L, "BASIC_ROLE", "20"));
        cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp item =
            new cn.ac.fage.accessmesh.access.permission.dto.resp.RolePermissionItemResp(
                77L, "ADMIN_MENU", "10", "default", "菜单", "VIEW",
                false, null, cn.ac.fage.accessmesh.perm.common.enums.ScopeMode.INSTANCE,
                null, "MANUAL", "1", null, 0);
        when(permissionGrantAppService.listPermissions(eq(1L), any())).thenReturn(List.of(item));

        service.revokeMenuFromRole(1L, 99L, 10L);

        ArgumentCaptor<ApplyGrantPlanReq> revokeReq = ArgumentCaptor.forClass(ApplyGrantPlanReq.class);
        verify(permissionGrantAppService).applyGrantPlan(eq(1L), revokeReq.capture());
        assertThat(revokeReq.getValue().plan().removeIds()).containsExactly(77L);
    }

    @Test
    @DisplayName("grantMenuToRole: 保留角色类型拒绝")
    void grantMenuToRoleRejectsReservedRoleType() {
        when(roleManageAppService.getRole(1L, 99L)).thenReturn(roleResp(99L, "POSITION", "20"));

        assertThatThrownBy(() -> service.grantMenuToRole(1L, 99L, 10L, "VIEW"))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    private RoleResp roleResp(Long id, String roleTypeCode, String externalId) {
        return new RoleResp(
            id,
            1L,
            null,
            roleTypeCode,
            roleTypeCode,
            externalId,
            "role-" + id,
            1,
            null,
            null,
            null,
            null
        );
    }
}
