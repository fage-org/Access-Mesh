package org.dromara.permission.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.RolePermissionBatchGrantRequest;
import org.dromara.permission.model.permission.RolePermissionBatchRevokeRequest;
import org.dromara.permission.service.PermissionService;
import org.dromara.permission.service.RolePermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class RolePermissionContractControllerTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private RolePermissionService rolePermissionService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RolePermissionContractController controller;

    @Test
    void list_usesFrozenContractPath() {
        when(rolePermissionService.list(any(RolePermissionListReq.class))).thenReturn(List.of(new RolePermissionVo()));

        R<List<RolePermissionVo>> response = controller.list(2L, 1L);

        verify(rolePermissionService).list(any(RolePermissionListReq.class));
        assertEquals(1, response.getData().size());
    }

    @Test
    void grant_usesFrozenContractPath() {
        RolePermissionAddReq req = new RolePermissionAddReq();
        req.setTenantId(1L);
        req.setRequestId("req-1");
        req.setChangeSource("ADMIN");
        req.setChangeReason("grant permission");
        RolePermissionAddReq.RolePermissionItem item = new RolePermissionAddReq.RolePermissionItem();
        item.setResourceEntityId(3L);
        item.setOperationPermissionId(4L);
        item.setCanManage(Boolean.TRUE);
        item.setConditionId(5L);
        req.setItems(List.of(item));

        controller.grant(2L, req);

        ArgumentCaptor<RolePermissionBatchGrantRequest> captor = ArgumentCaptor.forClass(RolePermissionBatchGrantRequest.class);
        verify(permissionService).grantRolePermissions(captor.capture());
        assertEquals(2L, captor.getValue().getAbstractRoleId());
        assertEquals("req-1", captor.getValue().getRequestId());
        assertEquals("ADMIN", captor.getValue().getChangeSource());
        assertEquals("grant permission", captor.getValue().getChangeReason());
        assertEquals(1, captor.getValue().getItems().size());
        assertEquals(3L, captor.getValue().getItems().get(0).getResourceEntityId());
        assertEquals(4L, captor.getValue().getItems().get(0).getOperationPermissionId());
        assertEquals(Boolean.TRUE, captor.getValue().getItems().get(0).getCanManage());
        assertEquals(5L, captor.getValue().getItems().get(0).getConditionId());
    }

    @Test
    void revoke_mismatchRoleId_throws() {
        RolePermissionRemoveReq req = new RolePermissionRemoveReq();
        req.setTenantId(1L);
        req.setAbstractRoleId(9L);
        req.setItems(List.of(new RolePermissionRemoveReq.RolePermissionPair()));

        assertThrows(PermissionServiceException.class, () -> controller.revoke(2L, req));
    }

    @Test
    void revoke_mapsMetadataToBatchRequest() {
        RolePermissionRemoveReq req = new RolePermissionRemoveReq();
        req.setTenantId(1L);
        req.setRequestId("req-2");
        req.setChangeSource("ADMIN");
        req.setChangeReason("revoke permission");
        RolePermissionRemoveReq.RolePermissionPair item = new RolePermissionRemoveReq.RolePermissionPair();
        item.setResourceEntityId(3L);
        item.setOperationPermissionId(4L);
        req.setItems(List.of(item));

        controller.revoke(2L, req);

        ArgumentCaptor<RolePermissionBatchRevokeRequest> captor = ArgumentCaptor.forClass(RolePermissionBatchRevokeRequest.class);
        verify(permissionService).revokeRolePermissions(captor.capture());
        assertEquals(2L, captor.getValue().getAbstractRoleId());
        assertEquals("req-2", captor.getValue().getRequestId());
        assertEquals("ADMIN", captor.getValue().getChangeSource());
        assertEquals("revoke permission", captor.getValue().getChangeReason());
        assertEquals(1, captor.getValue().getItems().size());
        assertEquals(3L, captor.getValue().getItems().get(0).getResourceEntityId());
        assertEquals(4L, captor.getValue().getItems().get(0).getOperationPermissionId());
    }

    @Test
    void revokeRequest_missingRequiredFields_hasValidationErrors() {
        RolePermissionRemoveReq req = new RolePermissionRemoveReq();

        assertTrue(validator.validate(req).size() >= 3);
    }
}
