package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.vo.DependencyCheckVo;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.model.permission.DependencyPathNode;
import org.dromara.permission.service.ResourceDependencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ResourceDependencyControllerTest {

    @Mock
    private ResourceDependencyService resourceDependencyService;

    @InjectMocks
    private ResourceDependencyController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .build();
    }

    @Test
    void saveByContract_usesFrozenPhase0Path() {
        ResourceDependencySaveReq req = new ResourceDependencySaveReq();
        req.setTenantId(1L);
        req.setResourceEntityId(2L);
        req.setDependsOnResourceEntityId(3L);
        req.setRequiredOperationPermissionId(4L);

        R<Void> response = controller.saveByContract(req);

        verify(resourceDependencyService).save(req);
        assertEquals(200, response.getCode());
    }

    @Test
    void check_mapsDependencyPath() {
        DependencyGap gap = new DependencyGap(11L, 22L, List.of(
            new DependencyPathNode(10L, 20L),
            new DependencyPathNode(11L, 22L)
        ));
        when(resourceDependencyService.check(any(DependencyCheckReq.class)))
            .thenReturn(DependencyCheckResult.fail(List.of(gap)));

        DependencyCheckReq req = new DependencyCheckReq();
        req.setTenantId(1L);
        req.setAbstractUserId(9L);
        req.setResourceEntityId(10L);
        req.setOperationPermissionId(20L);

        R<DependencyCheckVo> response = controller.check(req);

        verify(resourceDependencyService).check(req);
        assertFalse(response.getData().isSatisfied());
        assertEquals(1, response.getData().getGaps().size());
        assertEquals(2, response.getData().getGaps().get(0).getPath().size());
        assertEquals(11L, response.getData().getGaps().get(0).getResourceEntityId());
    }

    @Test
    void save_postContractBindsAuditFields() throws Exception {
        mockMvc.perform(post("/api/perm/resource-dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "resourceEntityId": 2,
                      "dependsOnResourceEntityId": 3,
                      "requiredOperationPermissionId": 4,
                      "requestId": "req-dependency-save",
                      "changeSource": "ADMIN",
                      "changeReason": "phase8-review"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ResourceDependencySaveReq> captor = ArgumentCaptor.forClass(ResourceDependencySaveReq.class);
        verify(resourceDependencyService).save(captor.capture());
        assertEquals("req-dependency-save", captor.getValue().getRequestId());
        assertEquals("ADMIN", captor.getValue().getChangeSource());
        assertEquals("phase8-review", captor.getValue().getChangeReason());
    }

    @Test
    void check_postContractReturnsDependencyGraphPayload() throws Exception {
        DependencyGap gap = new DependencyGap(11L, 22L, List.of(
            new DependencyPathNode(10L, 20L),
            new DependencyPathNode(11L, 22L)
        ));
        when(resourceDependencyService.check(any(DependencyCheckReq.class)))
            .thenReturn(DependencyCheckResult.fail(List.of(gap)));

        mockMvc.perform(post("/api/perm/resource-dependencies/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "abstractUserId": 9,
                      "resourceEntityId": 10,
                      "operationPermissionId": 20,
                      "requestId": "req-dependency-check"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.satisfied").value(false))
            .andExpect(jsonPath("$.data.gaps[0].resourceEntityId").value(11))
            .andExpect(jsonPath("$.data.gaps[0].path.length()").value(2));
    }

    @Test
    void save_missingRequiredOperationReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/perm/resource-dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "resourceEntityId": 2,
                      "dependsOnResourceEntityId": 3
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
