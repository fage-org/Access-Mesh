package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.role.controller.AdminRoleController;
import cn.ac.fage.accessmesh.access.role.controller.AdminUserRoleController;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.ac.fage.accessmesh.access.role.service.UserRoleQueryAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
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
 * admin 侧角色端点删除契约测试（原 T-ACCESS-006 退役口径，T-ADMIN-024 起为删除口径）。
 * <p>
 * 「角色直接由 permission 管理」：原恒拒绝端点 /role/create、/role/grant-menu、
 * /role/revoke-menu、/user-role/assign、/user-role/revoke 已删除（无存量调用方，
 * 不留兼容层）——MockMvc standalone 下 POST 已删除路径必须 404 无映射（负向验收）；
 * /role/list 亦已删除（T-FE-058，2026-09-23：功能角色候选迁 /abstract-role/list
 * keyword+分页，旧通道无分页且仅类型级门禁）；/user-role/view 仍由跨域只读查询服务正常提供服务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RetiredRoleApiContractTest {

    @Mock private UserRoleQueryAppService userRoleQueryService;
    @Mock private UserMenuQueryAppService userMenuQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminRoleController(userRoleQueryService, userMenuQueryService),
                new AdminUserRoleController(userRoleQueryService))
            .build();
    }

    @ParameterizedTest(name = "{0} 无映射 404")
    @MethodSource("deletedRoutes")
    void deletedRoutes_haveNoHandler(String path, String body) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound())
            .andExpect(result -> assertThat(result.getHandler()).isNull());
    }

    private static Stream<Arguments> deletedRoutes() {
        return Stream.of(
            Arguments.of("/api/access/role/create", "{\"roleName\":\"x\",\"orgId\":1}"),
            Arguments.of("/api/access/role/grant-menu", "{\"roleId\":1,\"menuId\":10}"),
            Arguments.of("/api/access/role/revoke-menu", "{\"roleId\":1,\"menuId\":10}"),
            Arguments.of("/api/access/user-role/assign", "{\"userId\":1,\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\"}"),
            Arguments.of("/api/access/user-role/revoke", "{\"userId\":1,\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\"}"),
            Arguments.of("/api/access/role/list", "{}"));
    }

    @Test
    @DisplayName("读端点保留：/user-role/view 委托跨域只读查询服务")
    void readEndpoints_delegateToQueryService() throws Exception {
        mockMvc.perform(post("/api/access/user-role/view")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        verify(userRoleQueryService).listUserRoles(1L);
    }
}
