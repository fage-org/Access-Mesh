package org.dromara.permission.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.dromara.common.core.constant.HttpStatus;
import org.dromara.common.core.domain.R;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionServiceExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final PermissionServiceExceptionHandler handler = new PermissionServiceExceptionHandler();

    @Test
    void handlePermissionServiceException_mapsForbiddenAndKeepsPermissionCode() {
        when(request.getRequestURI()).thenReturn("/api/perm/service/grant");

        PermissionServiceException ex = new PermissionServiceException(
            PermissionErrorCode.CONDITION_NOT_APPROVED, "cond-1");

        R<Void> response = handler.handlePermissionServiceException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getCode());
        assertEquals("PERM_403_CONDITION", response.getErrorCode());
        assertEquals("PERM_403_CONDITION: Condition is not approved: cond-1", response.getMsg());
    }

    @Test
    void handlePermissionServiceException_mapsNotFound() {
        when(request.getRequestURI()).thenReturn("/api/perm/check");

        PermissionServiceException ex = new PermissionServiceException(PermissionErrorCode.RESOURCE_NOT_FOUND);

        R<Void> response = handler.handlePermissionServiceException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getCode());
        assertEquals("PERM_404_RESOURCE", response.getErrorCode());
        assertEquals("PERM_404_RESOURCE: Resource not found", response.getMsg());
    }
}
