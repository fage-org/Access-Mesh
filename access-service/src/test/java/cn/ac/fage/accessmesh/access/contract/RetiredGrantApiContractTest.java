package cn.ac.fage.accessmesh.access.contract;

import cn.ac.fage.accessmesh.access.grant.controller.PermissionGrantController;
import cn.ac.fage.accessmesh.access.grant.service.PermissionGrantAppService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * role-resource-permission 旧写入口删除契约测试（T-PERM-034 第 5 项，2026-08-27 端点退役收口）。
 * <p>
 * 授权写入终态为 apply-grant-plan 唯一入口（记录级 plan 单事务原子）：
 * 旧端点 /save、/revoke、/children、/add-child、/remove-child 已从 Controller 删除
 * （无存量调用方，不留兼容层）——MockMvc standalone 下 POST 已删除路径必须 404 无映射
 * （负向验收）；存活端点（/apply-grant-plan、/list）委托 AppService 正常提供服务。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class RetiredGrantApiContractTest {

    @Mock private PermissionGrantAppService permissionGrantAppService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PermissionGrantController(permissionGrantAppService))
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
            Arguments.of("/api/access/role-resource-permission/save", "{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\",\"adds\":[]}"),
            Arguments.of("/api/access/role-resource-permission/revoke", "{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\",\"permissionIds\":[1]}"),
            Arguments.of("/api/access/role-resource-permission/children", "{\"permissionId\":10}"),
            Arguments.of("/api/access/role-resource-permission/add-child", "{\"parentPermissionId\":10,\"children\":[]}"),
            Arguments.of("/api/access/role-resource-permission/remove-child", "{\"permissionId\":11}"));
    }

    @Test
    @DisplayName("存活端点委托：/apply-grant-plan 与 /list 走 AppService 唯一读写入口")
    void survivingEndpoints_delegateToAppService() throws Exception {
        // standalone 无过滤器绑定上下文，TenantContextHolder 空态返回 null
        when(permissionGrantAppService.applyGrantPlan(isNull(), any())).thenReturn(List.of());
        when(permissionGrantAppService.listPermissions(isNull(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/access/role-resource-permission/apply-grant-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\",\"plan\":{\"creates\":[],\"updates\":[],\"removes\":[]}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/access/role-resource-permission/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleTypeCode\":\"BASIC_ROLE\",\"roleExternalId\":\"r-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(permissionGrantAppService).applyGrantPlan(isNull(), any());
        verify(permissionGrantAppService).listPermissions(isNull(), any());
    }
}
