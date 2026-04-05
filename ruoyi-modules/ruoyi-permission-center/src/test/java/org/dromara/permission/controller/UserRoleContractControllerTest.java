package org.dromara.permission.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.model.permission.PermissionServiceException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class UserRoleContractControllerTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UserRoleService userRoleService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private UserRoleContractController controller;

    @Test
    void list_usesFrozenContractPath() {
        when(userRoleService.list(any(UserRoleListReq.class))).thenReturn(List.of(new UserRoleVo()));

        R<List<UserRoleVo>> response = controller.list(2L, 1L);

        verify(userRoleService).list(any(UserRoleListReq.class));
        assertEquals(1, response.getData().size());
    }

    @Test
    void assign_usesFrozenContractPath() {
        UserRoleAssignReq req = new UserRoleAssignReq();
        req.setTenantId(1L);
        req.setRoleIds(List.of(3L));

        controller.assign(2L, req);

        verify(permissionService).assignUserRoles(any(UserRoleBatchAssignRequest.class));
    }

    @Test
    void revoke_mismatchUserId_throws() {
        UserRoleRevokeReq req = new UserRoleRevokeReq();
        req.setTenantId(1L);
        req.setAbstractUserId(9L);
        req.setRoleIds(List.of(3L));

        assertThrows(PermissionServiceException.class, () -> controller.revoke(2L, req));
    }

    @Test
    void revokeRequest_missingRequiredFields_hasValidationErrors() {
        UserRoleRevokeReq req = new UserRoleRevokeReq();

        assertTrue(validator.validate(req).size() >= 3);
    }
}
