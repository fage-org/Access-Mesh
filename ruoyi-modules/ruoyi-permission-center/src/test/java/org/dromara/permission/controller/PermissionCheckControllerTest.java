package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.DenyReason;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionCheckResult;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionCheckControllerTest {

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private PermissionCheckController controller;

    @Test
    void check_mapsExpandedRequestAndResponse() {
        PermissionCheckReq req = new PermissionCheckReq();
        req.setTenantId(1L);
        req.setUserId(100L);
        req.setResourceEntityId(300L);
        req.setOperationPermissionId(400L);
        req.setInheritMode(InheritMode.CHILDREN);
        req.setCheckDependency(false);
        req.setContext(Map.of("k", "v"));

        PermissionCheckResult result = PermissionCheckResult.denied(DenyReason.CONFLICT,
            List.of(new ConflictDetail()), List.of());
        result.setGrantedBy(List.of(new MatchedPermission()));
        when(permissionService.check(any())).thenReturn(result);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/perm/check");
        servletRequest.setRemoteAddr("172.16.0.10");

        R<PermissionCheckVo> response = controller.check(req, servletRequest);

        ArgumentCaptor<org.dromara.permission.model.permission.PermissionCheckRequest> captor =
            ArgumentCaptor.forClass(org.dromara.permission.model.permission.PermissionCheckRequest.class);
        verify(permissionService).check(captor.capture());
        assertEquals(100L, captor.getValue().getAbstractUserId());
        assertEquals(Map.of("k", "v"), captor.getValue().getContext());
        assertFalse(response.getData().getGranted());
        assertEquals("CONFLICT", response.getData().getDenyReason());
        assertNotNull(response.getData().getConflicts());
        assertNotNull(response.getData().getGrantedBy());
    }

    @Test
    void check_reservedBusinessAliasThrowsInvalidRequest() {
        PermissionCheckReq req = new PermissionCheckReq();
        req.setTenantId(1L);
        req.setUserId(100L);
        req.setResourceEntityId(300L);
        req.setOperationPermissionId(400L);
        req.setContext(Map.of(
            "clientIp", "8.8.8.8",
            "request", Map.of("httpMethod", "DELETE"),
            "network", Map.of("clientIp", "1.1.1.1"),
            "business", Map.of("enabled", true),
            "level", 3,
            "resourceCode", "FAKE"
        ));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/perm/check");
        servletRequest.setRemoteAddr("172.16.0.10");
        servletRequest.addHeader("X-Request-Id", "req-001");

        assertThrows(PermissionServiceException.class, () -> controller.check(req, servletRequest));
        verifyNoInteractions(permissionService);
    }
}
