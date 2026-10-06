package cn.ac.fage.accessmesh.example.controller;

import cn.ac.fage.accessmesh.common.exception.GlobalExceptionHandler;
import cn.ac.fage.accessmesh.example.perm.BusinessPermChecker;
import cn.ac.fage.accessmesh.example.report.ExportJobRunner;
import cn.ac.fage.accessmesh.example.report.ReportStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报表入口 DTO 校验 HTTP 层反例（T-ACCESS-061：@Valid 缺失实证——注解在 DTO 上存在
 * 但入口不触发时，空 name/超批量上限载荷会穿透到业务层）。
 * <p>
 * MockMvc standalone + 全局异常处理器：锁「校验失败=400 + VALIDATION_FAILED 信封 +
 * 业务层零调用」。旧实现（无 @Valid）下本组用例因 200+创建成功而失败。
 * </p>
 */
class ReportControllerValidationTest {

    private static final String USER = "42";
    private static final String TENANT = "7";

    private final BusinessPermChecker permChecker = mock(BusinessPermChecker.class);
    private MockMvc mockMvc;
    private final ReportStore reportStore = new ReportStore();

    @BeforeEach
    void setUp() {
        ReportController controller = new ReportController(permChecker, reportStore,
            new ExportJobRunner(permChecker, new ReportStore(), 10));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("create 空 name → 400 校验拒绝，不创建任何报表")
    void create_blankName_rejectedAtHttpLayer() throws Exception {
        mockMvc.perform(post("/api/example/report/create")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(90001));
    }

    @Test
    @DisplayName("view 空 reportCode → 400 校验拒绝，业务检查零调用")
    void view_blankCode_rejectedBeforeBusinessCheck() throws Exception {
        mockMvc.perform(post("/api/example/report/view")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportCode\":\"\"}"))
            .andExpect(status().isBadRequest());

        verify(permChecker, never()).check(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("batch-view 超 100 上限 → 400 校验拒绝（批量上限在入口执行）")
    void batchView_overHundredRejected() throws Exception {
        StringBuilder codes = new StringBuilder("[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) codes.append(',');
            codes.append("\"report-").append(i).append("\"");
        }
        codes.append(']');

        mockMvc.perform(post("/api/example/report/batch-view")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportCodes\":" + codes + "}"))
            .andExpect(status().isBadRequest());

        verify(permChecker, never()).batchCheck(anyString(), anyString(), any(), anyString(),
            any(), anyString());
    }
    @Test
    void listUsesSharedPaginationAndReportsRemainingRows() throws Exception {
        for (int i = 0; i < 196; i++) reportStore.create(TENANT, "report " + i);
        when(permChecker.accessibleScope(TENANT, USER, null, "EXAMPLE", "VIEW"))
            .thenReturn(new BusinessPermChecker.Scope(true, Set.of()));
        mockMvc.perform(post("/api/example/report/list")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNum\":1,\"pageSize\":200}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(200))
            .andExpect(jsonPath("$.data.total").value(201))
            .andExpect(jsonPath("$.data.pageNum").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(200))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.page").doesNotExist())
            .andExpect(jsonPath("$.data.size").doesNotExist());
        mockMvc.perform(post("/api/example/report/list")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pageNum\":2147483647,\"pageSize\":200}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"pageSize\":201}", "{\"pageSize\":0}", "{\"pageNum\":0}"})
    void invalidPaginationIsRejectedBeforePermissionQuery(String body) throws Exception {
        mockMvc.perform(post("/api/example/report/list")
                .header("X-User-Id", USER).header("X-Tenant-Id", TENANT)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(permChecker);
    }

}
