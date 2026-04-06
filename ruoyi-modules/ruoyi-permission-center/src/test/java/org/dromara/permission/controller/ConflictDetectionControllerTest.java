package org.dromara.permission.controller;

import org.dromara.common.core.domain.R;
import org.dromara.permission.domain.dto.ConflictDetectReq;
import org.dromara.permission.domain.vo.ConflictDetectionPageVo;
import org.dromara.permission.domain.vo.ConflictViolationVo;
import org.dromara.permission.service.ConflictRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ConflictDetectionControllerTest {

    @Mock
    private ConflictRuleService conflictRuleService;

    @InjectMocks
    private ConflictDetectionController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .build();
    }

    @Test
    void detect_usesFrozenPhase8Path() {
        ConflictDetectionPageVo page = new ConflictDetectionPageVo();
        page.setTotal(1L);
        page.setPageNum(1);
        page.setPageSize(10);
        page.setItems(List.of(new ConflictViolationVo()));
        when(conflictRuleService.detectPage(any(ConflictDetectReq.class))).thenReturn(page);

        ConflictDetectReq req = new ConflictDetectReq();
        req.setTenantId(1L);
        req.setPageNum(1);
        req.setPageSize(10);

        R<ConflictDetectionPageVo> response = controller.detect(req);

        verify(conflictRuleService).detectPage(req);
        assertEquals(1L, response.getData().getTotal());
        assertEquals(1, response.getData().getItems().size());
    }

    @Test
    void detect_postContractReturnsPagePayload() throws Exception {
        ConflictDetectionPageVo page = new ConflictDetectionPageVo();
        page.setTotal(2L);
        page.setPageNum(1);
        page.setPageSize(20);
        page.setItems(List.of(new ConflictViolationVo(), new ConflictViolationVo()));
        when(conflictRuleService.detectPage(any(ConflictDetectReq.class))).thenReturn(page);

        mockMvc.perform(post("/api/perm/conflict-detection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1,
                      "bizDomainId": 10,
                      "pageNum": 1,
                      "pageSize": 20,
                      "requestId": "req-phase8-detect"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items.length()").value(2));

        verify(conflictRuleService).detectPage(any(ConflictDetectReq.class));
    }

    @Test
    void detect_missingTenantIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/perm/conflict-detection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageNum": 1,
                      "pageSize": 20
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
