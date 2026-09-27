package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新查询契约结构错误的 HTTP 映射回归锁（T-PERM-090 外评处置，2026-09-27 用户拍板；复评补强为 HTTP 层）。
 * <p>
 * 直调 advice 方法的旧锁不经 HTTP 语义：摘 {@code @ResponseStatus}（R 信封仍 200）、
 * advice 优先级破坏（落 common {@code GlobalExceptionHandler} 的 Exception 兜底 500）均不红。
 * 本锁走 MockMvc standalone（仓库先例 RetiredRoleApiContractTest/SyncEndpointAuthIT）：
 * stub 控制器经真实触发路径（CallerContext 构造对保留键结构拒绝）抛出，双 advice
 * 反序挂载（common 兜底在前）使 {@code @Order(0)} 成为承重件——摘除即被兜底吞掉变 500。
 * </p>
 */
class QueryValidationExceptionHandlerTest {

    @RestController
    static class StubController {

        @PostMapping("/stub/query-validation")
        public void trigger() {
            // 真实触发路径：context 顶层保留键由 CallerContext 构造拒绝（适配层 fromCallerMap 同形）
            new CallerContext(null, Map.of("timestamp", "2026-04-26T18:00:00"));
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler(), new QueryValidationExceptionHandler())
            .build();
    }

    @Test
    void structuralRejectionMustMapToHttp400WithValidationFailedEnvelope() throws Exception {
        mockMvc.perform(post("/stub/query-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_FAILED.code()))
            .andExpect(jsonPath("$.message").value(containsString("timestamp")));
    }
}
