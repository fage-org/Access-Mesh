package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.RolePermissionService;
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
class RolePermissionControllerTest {

    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RolePermissionController controller;

    @Test
    void list_usesOldReadService() {
        when(rolePermissionService.list(any(RolePermissionListReq.class))).thenReturn(List.of(new RolePermissionVo()));

        R<List<RolePermissionVo>> response = controller.list(new RolePermissionListReq());

        verify(rolePermissionService).list(any(RolePermissionListReq.class));
        assertEquals(1, response.getData().size());
    }

    @Test
    void add_usesPermissionServicePerItem() {
        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(1L);
        req.setAbstractRoleId(2L);
        RolePermissionAddReq.RolePermissionItem item1 = new RolePermissionAddReq.RolePermissionItem();
        item1.setResourceEntityId(3L);
        item1.setOperationPermissionId(4L);
        RolePermissionAddReq.RolePermissionItem item2 = new RolePermissionAddReq.RolePermissionItem();
        item2.setResourceEntityId(5L);
        item2.setOperationPermissionId(6L);
        req.setItems(List.of(item1, item2));
        controller.add(req);

        verify(permissionService).grantRolePermissions(any(RolePermissionBatchGrantRequest.class));
    }

    @Test
    void remove_usesPermissionServicePerItem() {
        RolePermissionRemoveReq req = new RolePermissionRemoveReq();
        req.setTenantId(1L);
        req.setAbstractRoleId(2L);
        RolePermissionRemoveReq.RolePermissionPair item = new RolePermissionRemoveReq.RolePermissionPair();
        item.setResourceEntityId(3L);
        item.setOperationPermissionId(4L);
        req.setItems(List.of(item));
        controller.remove(req);

        verify(permissionService).revokeRolePermissions(any(RolePermissionBatchRevokeRequest.class));
    }
}
