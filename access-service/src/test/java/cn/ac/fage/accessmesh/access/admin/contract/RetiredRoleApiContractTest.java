package cn.ac.fage.accessmesh.access.admin.contract;

import cn.ac.fage.accessmesh.access.admin.controller.AdminRoleController;
import cn.ac.fage.accessmesh.access.admin.controller.AdminUserRoleController;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.UserRoleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * admin 侧角色写代理端点删除契约测试（原 T-ACCESS-006 退役口径，T-ADMIN-024 起为删除口径）。
 * <p>
 * 「角色直接由 permission 管理」：原恒拒绝端点 /role/create、/role/grant-menu、
 * /role/revoke-menu、/user-role/assign、/user-role/revoke 已删除（无存量调用方，
 * 不留兼容层）——MockMvc standalone 下 POST 已删除路径必须 404 无映射（负向验收）；
 * 读端点（/role/list、/user-role/list）仍由跨域只读查询服务正常提供服务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RetiredRoleApiContractTest {

    @Mock private UserRoleQueryService userRoleQueryService;
    @Mock private UserMenuQueryService userMenuQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminRoleController(userRoleQueryService, userMenuQueryService),
                new AdminUserRoleController(userRoleQueryService))
            .build();
    }

    @Test
    @DisplayName("/role/create 已删除：POST 无映射 404")
    void roleCreate_deletedReturns404() throws Exception {
        mockMvc.perform(post("/role/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleName\":\"x\",\"orgId\":1}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/role/grant-menu 已删除：POST 无映射 404")
    void roleGrantMenu_deletedReturns404() throws Exception {
        mockMvc.perform(post("/role/grant-menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":1,\"menuId\":10}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/role/revoke-menu 已删除：POST 无映射 404")
    void roleRevokeMenu_deletedReturns404() throws Exception {
        mockMvc.perform(post("/role/revoke-menu")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":1,\"menuId\":10}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/user-role/assign 已删除：POST 无映射 404")
    void userRoleAssign_deletedReturns404() throws Exception {
        mockMvc.perform(post("/user-role/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/user-role/revoke 已删除：POST 无映射 404")
    void userRoleRevoke_deletedReturns404() throws Exception {
        mockMvc.perform(post("/user-role/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("读端点保留：/role/list、/user-role/list 委托跨域只读查询服务")
    void readEndpoints_delegateToQueryService() throws Exception {
        mockMvc.perform(post("/role/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/user-role/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        verify(userRoleQueryService).listRoles(null);
        verify(userRoleQueryService).listUserRoles(1L);
    }
}
