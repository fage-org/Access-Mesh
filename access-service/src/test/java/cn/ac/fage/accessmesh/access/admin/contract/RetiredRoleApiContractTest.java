package cn.ac.fage.accessmesh.access.admin.contract;

import cn.ac.fage.accessmesh.access.admin.controller.AdminRoleController;
import cn.ac.fage.accessmesh.access.admin.controller.AdminUserRoleController;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserRoleAssignReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserRoleRevokeReq;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * admin 侧角色写代理端点退役契约测试（T-ACCESS-006）。
 * <p>
 * 「角色直接由 permission 管理」：/role/create、/role/grant-menu、/role/revoke-menu、
 * /user-role/assign、/user-role/revoke 保留映射但恒拒绝（/role/create 保持 20045，
 * 其余 10111）；读端点仍由跨域只读查询服务正常提供服务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RetiredRoleApiContractTest {

    @Mock private UserRoleQueryService userRoleQueryService;
    @Mock private UserMenuQueryService userMenuQueryService;

    @Test
    @DisplayName("/role/create 退役：恒 20045（LOCAL_PROJECTION_IMMUTABLE）")
    void roleCreate_retiredWith20045() {
        AdminRoleController controller = new AdminRoleController(userRoleQueryService, userMenuQueryService);
        assertThatThrownBy(() -> controller.createRole(
            new AdminRoleController.CreateRoleReq("x", 1L)))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    @DisplayName("/role/grant-menu 退役：恒 10111（ROLE_API_RETIRED）")
    void roleGrantMenu_retiredWith10111() {
        AdminRoleController controller = new AdminRoleController(userRoleQueryService, userMenuQueryService);
        assertThatThrownBy(() -> controller.grantMenu(
            new AdminRoleController.RoleMenuReq(1L, 10L)))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AdminErrorCode.ROLE_API_RETIRED.getCode());
    }

    @Test
    @DisplayName("/role/revoke-menu 退役：恒 10111（ROLE_API_RETIRED）")
    void roleRevokeMenu_retiredWith10111() {
        AdminRoleController controller = new AdminRoleController(userRoleQueryService, userMenuQueryService);
        assertThatThrownBy(() -> controller.revokeMenu(
            new AdminRoleController.RoleMenuReq(1L, 10L)))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AdminErrorCode.ROLE_API_RETIRED.getCode());
    }

    @Test
    @DisplayName("/user-role/assign 退役：恒 10111（ROLE_API_RETIRED）")
    void userRoleAssign_retiredWith10111() {
        AdminUserRoleController controller = new AdminUserRoleController(userRoleQueryService);
        assertThatThrownBy(() -> controller.assignRole(
            new UserRoleAssignReq(1L, "BASIC_ROLE", "r-1", null, null)))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AdminErrorCode.ROLE_API_RETIRED.getCode());
    }

    @Test
    @DisplayName("/user-role/revoke 退役：恒 10111（ROLE_API_RETIRED）")
    void userRoleRevoke_retiredWith10111() {
        AdminUserRoleController controller = new AdminUserRoleController(userRoleQueryService);
        assertThatThrownBy(() -> controller.revokeRole(
            new UserRoleRevokeReq(1L, "BASIC_ROLE", "r-1")))
            .isInstanceOf(BizException.class)
            .extracting(ex -> ((BizException) ex).getErrorCode())
            .isEqualTo(AdminErrorCode.ROLE_API_RETIRED.getCode());
    }

    @Test
    @DisplayName("读端点保留：/role/list、/user-role/list 委托跨域只读查询服务")
    void readEndpoints_delegateToQueryService() {
        AdminRoleController roleController = new AdminRoleController(userRoleQueryService, userMenuQueryService);
        AdminUserRoleController userRoleController = new AdminUserRoleController(userRoleQueryService);

        assertThatCode(() -> roleController.listRoles(null)).doesNotThrowAnyException();
        assertThatCode(() -> userRoleController.listUserRoles(new UserRoleListReq(1L)))
            .doesNotThrowAnyException();
        assertThat(roleController.listRoles(null).getCode()).isEqualTo(200);
        assertThat(userRoleController.listUserRoles(new UserRoleListReq(1L)).getCode()).isEqualTo(200);
    }
}
