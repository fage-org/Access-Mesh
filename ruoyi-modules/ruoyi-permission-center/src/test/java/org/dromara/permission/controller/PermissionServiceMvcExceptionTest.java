package org.dromara.permission.controller;

import org.dromara.permission.handler.PermissionServiceExceptionHandler;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionServiceMvcExceptionTest {

    @Mock
    private PermissionService permissionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new PermissionServiceController(permissionService),
                new PermissionCheckController(permissionService)
            )
            .setControllerAdvice(new PermissionServiceExceptionHandler())
            .build();
    }

    @Test
    void grant_endpoint_usesPermissionExceptionAdvice() throws Exception {
        when(permissionService.grant(any()))
            .thenThrow(new PermissionServiceException(PermissionErrorCode.CONDITION_UNAVAILABLE, "cond-1"));

        mockMvc.perform(post("/api/perm/service/grant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "abstractRoleId": 2,
                      "resourceEntityId": 3,
                      "operationPermissionId": 4
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.errorCode").value("PERM-106"))
            .andExpect(jsonPath("$.msg").value("PERM-106: Condition is not approved or disabled: cond-1"));
    }

    @Test
    void check_endpoint_usesPermissionExceptionAdvice() throws Exception {
        when(permissionService.check(any()))
            .thenThrow(new PermissionServiceException(PermissionErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/perm/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "userId": 2,
                      "resourceEntityId": 3,
                      "operationPermissionId": 4
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.errorCode").value("PERM-102"))
            .andExpect(jsonPath("$.msg").value("PERM-102: Resource not found"));
    }
}
