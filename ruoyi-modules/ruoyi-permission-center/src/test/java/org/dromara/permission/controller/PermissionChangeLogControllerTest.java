package org.dromara.permission.controller;

import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.permission.domain.vo.ChangeLogVo;
import org.dromara.permission.service.PermissionChangeLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PermissionChangeLogControllerTest {

    @Mock
    private PermissionChangeLogService permissionChangeLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PermissionChangeLogController(permissionChangeLogService))
            .build();
    }

    @Test
    void list_usesPostContract() throws Exception {
        when(permissionChangeLogService.queryPage(any(), any()))
            .thenReturn(TableDataInfo.build(List.of(new ChangeLogVo())));

        mockMvc.perform(post("/api/perm/change-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": {
                        "tenantId": 1
                      },
                      "pageQuery": {
                        "pageNum": 1,
                        "pageSize": 10
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.total").value(1));

        verify(permissionChangeLogService).queryPage(any(), any());
    }

    @Test
    void pageRoute_removed() throws Exception {
        mockMvc.perform(post("/api/perm/change-logs/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": 1
                    }
                    """))
            .andExpect(status().isNotFound());
    }
}
