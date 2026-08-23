package cn.ac.fage.accessmesh.access.permission.controller;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.service.LogQueryAppService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图控制器门禁入口测试（T-ACCESS-006 评审修复 P1：/effective-permission-codes 走带门禁方法）。
 */
@ExtendWith(MockitoExtension.class)
class PermissionViewControllerTest {

    private static final Long TENANT = 1L;

    @Mock private PermissionViewAppService permissionViewAppService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private LogQueryAppService logQueryService;

    private PermissionViewController controller;

    @BeforeEach
    void setUp() {
        controller = new PermissionViewController(permissionViewAppService, typeResolutionService, logQueryService);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("effective-permission-codes 转发到带门禁方法（getEffectivePermissionCodesForManage），而非无门禁内部方法")
    void getEffectivePermissionCodes_delegatesToGuardedMethod() {
        UserEffectivePermissionCodesReq req = new UserEffectivePermissionCodesReq("LOCAL_USER", "100", List.of("USER"));
        when(permissionViewAppService.getEffectivePermissionCodesForManage(eq(TENANT), eq(req)))
            .thenReturn(new UserEffectivePermissionCodesResp(List.of("USER:VIEW")));

        PermResult<UserEffectivePermissionCodesResp> result = controller.getEffectivePermissionCodes(req);

        assertEquals(List.of("USER:VIEW"), result.getData().permissions());
        verify(permissionViewAppService).getEffectivePermissionCodesForManage(eq(TENANT), eq(req));
    }
}
