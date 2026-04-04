package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.model.permission.UserRoleBatchAssignRequest;
import org.dromara.permission.model.permission.UserRoleBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.UserRoleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class UserRoleControllerTest {

    @Mock
    private UserRoleService userRoleService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private UserRoleController controller;

    @Test
    void list_usesOldReadService() {
        when(userRoleService.list(any(UserRoleListReq.class))).thenReturn(List.of(new UserRoleVo()));

        R<List<UserRoleVo>> response = controller.list(new UserRoleListReq());

        verify(userRoleService).list(any(UserRoleListReq.class));
        assertEquals(1, response.getData().size());
    }

    @Test
    void assign_usesPermissionService() {
        UserRoleAssignReq req = new UserRoleAssignReq();
        req.setTenantId(1L);
        req.setAbstractUserId(2L);
        req.setRoleIds(List.of(3L));

        controller.assign(req);

        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void revoke_usesPermissionService() {
        UserRoleRevokeReq req = new UserRoleRevokeReq();
        req.setTenantId(1L);
        req.setAbstractUserId(2L);
        req.setRoleIds(List.of(3L));

        controller.revoke(req);

        verify(permissionService).revokeUserRoles(any(UserRoleBatchRevokeRequest.class));
    }
}
