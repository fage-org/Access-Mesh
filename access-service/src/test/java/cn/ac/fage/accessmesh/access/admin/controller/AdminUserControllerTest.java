package cn.ac.fage.accessmesh.access.admin.controller;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.service.UserService;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理控制器门禁行为测试（T-ACCESS-006 评审修复 P1-2：/user/user-menus 查己豁免、查他人需 USER:VIEW）。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final Long SELF_ID = 100L;
    private static final Long OTHER_ID = 200L;

    @Mock private UserService userService;
    @Mock private UserMenuQueryService userMenuQueryService;
    @Mock private AdminPermissionValidator permissionValidator;

    private MockedStatic<StpUtil> stpMock;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        stpMock = mockStatic(StpUtil.class);
        stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(SELF_ID);
        controller = new AdminUserController(userService, userMenuQueryService, permissionValidator);
    }

    @AfterEach
    void tearDown() {
        stpMock.close();
    }

    @Test
    @DisplayName("自查（req.id == 登录用户）豁免门禁，直接返回")
    void getUserMenus_self_skipsGuard() {
        when(userMenuQueryService.loadUserRolesAndPermissions(SELF_ID))
            .thenReturn(new UserInfoResp(SELF_ID, null, null, null, null, null, null,
                List.of(), List.of(), List.of()));

        PermResult<UserInfoResp> result = controller.getUserMenus(new IdReq(SELF_ID));

        assertEquals(SELF_ID, result.getData().userId());
        verify(permissionValidator, never()).checkInstanceLevel(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("查他人需 USER:VIEW 实例级门禁，通过后返回")
    void getUserMenus_other_requiresAdminUserView() {
        when(userMenuQueryService.loadUserRolesAndPermissions(OTHER_ID))
            .thenReturn(new UserInfoResp(OTHER_ID, null, null, null, null, null, null,
                List.of(), List.of(), List.of()));

        PermResult<UserInfoResp> result = controller.getUserMenus(new IdReq(OTHER_ID));

        verify(permissionValidator).checkInstanceLevel(
            ResourceTypeCode.USER, String.valueOf(OTHER_ID), AdminOperationCode.VIEW);
        assertEquals(OTHER_ID, result.getData().userId());
    }

    @Test
    @DisplayName("查他人无 USER:VIEW 时门禁异常传播（fail-closed）")
    void getUserMenus_other_withoutPermission_propagates() {
        doThrow(new SecurityException("Permission denied"))
            .when(permissionValidator)
            .checkInstanceLevel(ResourceTypeCode.USER, String.valueOf(OTHER_ID), AdminOperationCode.VIEW);

        assertThrows(SecurityException.class, () -> controller.getUserMenus(new IdReq(OTHER_ID)));
        verify(userMenuQueryService, never()).loadUserRolesAndPermissions(OTHER_ID);
    }
}
