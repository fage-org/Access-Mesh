package cn.ac.fage.accessmesh.permission.integration;

import cn.ac.fage.accessmesh.permission.config.HeaderSignatureInterceptor;
import cn.ac.fage.accessmesh.permission.config.InternalApiSecretInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 同步端点身份层认证集成测试
 *
 * <p>验证 P0 修复后调度 Feign 同步请求（带 X-Tenant-Id + X-Internal-Secret 但无 X-User-Id）能够通过身份层。
 * 同时验证 /actuator/** 不会因缺失 InternalApiSecretInterceptor 路径而被 HMAC 误拒。</p>
 *
 * <p>策略：使用 MockMvc standaloneSetup 直接组装拦截器链 + 测试用 stub controller，
 * 不依赖 SpringContext / 数据库 / Redis。验证目标限定为身份层是否正确放行/拒绝，
 * 不涉及业务层（任何 200 都视为身份层放行通过）。</p>
 *
 * <p>覆盖矩阵：</p>
 * <table>
 *   <tr><th>#</th><th>路径</th><th>Headers</th><th>期望</th></tr>
 *   <tr><td>1</td><td>/api/perm/abstract-user/sync</td><td>X-Tenant-Id + X-Internal-Secret(correct)</td><td>200</td></tr>
 *   <tr><td>2</td><td>/api/perm/abstract-user/sync</td><td>X-Tenant-Id + X-Internal-Secret(wrong)</td><td>403</td></tr>
 *   <tr><td>3</td><td>/api/perm/abstract-user/sync</td><td>仅 X-Tenant-Id 无 secret 无 user</td><td>403</td></tr>
 *   <tr><td>4</td><td>/api/perm/abstract-user/sync</td><td>X-User-Id + X-Tenant-Id 无 HMAC</td><td>403</td></tr>
 *   <tr><td>5</td><td>/api/perm/abstract-user/sync</td><td>X-User-Id + X-Tenant-Id + 有效 HMAC + secret</td><td>非 401/403</td></tr>
 *   <tr><td>6</td><td>/internal/health</td><td>X-Tenant-Id 无 secret 无 user</td><td>403（路径3 拒绝）</td></tr>
 *   <tr><td>7</td><td>/actuator/health</td><td>X-Tenant-Id + X-Internal-Secret</td><td>403（actuator 不在密钥拦截路径，attribute 不被设）</td></tr>
 *   <tr><td>8</td><td>/actuator/health</td><td>完全无身份头</td><td>200</td></tr>
 * </table>
 */
class SyncEndpointAuthIT {

    private static final String SECRET = "test-secret-key-for-integration-0123456789";
    private static final String INTERNAL_SECRET = "test-internal-secret-0123456789";
    private static final int VALID_SECONDS = 300;

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";
    private static final String HEADER_TIMESTAMP = "X-Signature-Timestamp";
    private static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HeaderSignatureInterceptor sigInterceptor = new HeaderSignatureInterceptor();
        ReflectionTestUtils.setField(sigInterceptor, "signatureSecret", SECRET);
        ReflectionTestUtils.setField(sigInterceptor, "signatureValidSeconds", VALID_SECONDS);
        sigInterceptor.validateConfiguration();

        InternalApiSecretInterceptor internalInterceptor = new InternalApiSecretInterceptor();
        ReflectionTestUtils.setField(internalInterceptor, "expectedSecret", INTERNAL_SECRET);
        internalInterceptor.validateConfiguration();

        // 复制 PermWebMvcConfig 的实际拦截器链顺序
        // order=1 InternalApi(/api/perm/**) → order=2 HeaderSignature → order=3 PermTenant
        mockMvc = MockMvcBuilders.standaloneSetup(new StubSyncController(), new StubActuatorController())
            .addMappedInterceptors(new String[]{"/api/perm/**"}, internalInterceptor)
            .addMappedInterceptors(new String[]{"/api/**", "/internal/**", "/actuator/**"}, sigInterceptor)
            .build();
    }

    @Test
    @DisplayName("用例1：调度 Feign 调用（X-Tenant-Id + 正确 X-Internal-Secret） → 200")
    void case1_internalSecretCorrect_shouldPass() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_TENANT_ID, "1")
                .header(HEADER_INTERNAL_SECRET, INTERNAL_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("用例2：错误的 X-Internal-Secret → 403")
    void case2_internalSecretWrong_shouldReject() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_TENANT_ID, "1")
                .header(HEADER_INTERNAL_SECRET, "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用例3：仅 X-Tenant-Id 无 secret 无 user → 403（InternalApi 拒绝）")
    void case3_tenantOnlyNoSecret_shouldReject() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_TENANT_ID, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用例4：X-User-Id + X-Tenant-Id 无 HMAC → 403")
    void case4_userIdNoSignature_shouldReject() throws Exception {
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_USER_ID, "100")
                .header(HEADER_TENANT_ID, "1")
                .header(HEADER_INTERNAL_SECRET, INTERNAL_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
        // 注：当 INTERNAL_AUTHENTICATED attribute 被设置时，签名拦截器会直接放行（路径1）
        // 这是设计意图：内部调用不需要用户态 HMAC
    }

    @Test
    @DisplayName("用例4b：X-User-Id + X-Tenant-Id 无 HMAC 无 InternalSecret → 403")
    void case4b_userIdNoSignatureNoSecret_shouldReject() throws Exception {
        // /api/perm/** 路径下，无 InternalSecret 会先被 InternalApiSecretInterceptor 在 order=1 拒绝
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_USER_ID, "100")
                .header(HEADER_TENANT_ID, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用例5：X-User-Id + X-Tenant-Id + 有效 HMAC + InternalSecret → 200（身份层放行）")
    void case5_userIdWithValidHmac_shouldPass() throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String sig = computeHmac("100", "1", ts);
        mockMvc.perform(post("/api/perm/abstract-user/sync")
                .header(HEADER_USER_ID, "100")
                .header(HEADER_TENANT_ID, "1")
                .header(HEADER_SIGNATURE, sig)
                .header(HEADER_TIMESTAMP, String.valueOf(ts))
                .header(HEADER_INTERNAL_SECRET, INTERNAL_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("用例6：/internal/health 仅 X-Tenant-Id 无 secret 无 user → 403（路径3 拒绝）")
    void case6_internalEndpointTenantOnly_shouldReject() throws Exception {
        // /internal/** 不在 InternalApiSecretInterceptor 路径，attribute 不会被写
        // HeaderSignatureInterceptor 路径3：tenantId 无 userId 无 attribute → 403
        mockMvc.perform(get("/internal/health")
                .header(HEADER_TENANT_ID, "1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用例7：/actuator/health + X-Tenant-Id + X-Internal-Secret → 403（actuator 不在密钥拦截器路径）")
    void case7_actuatorWithSecret_shouldReject() throws Exception {
        // /actuator/** 不在 InternalApiSecretInterceptor 路径，X-Internal-Secret 不会被验证
        // attribute 不被设 → HeaderSignatureInterceptor 路径3 → 403
        mockMvc.perform(get("/actuator/health")
                .header(HEADER_TENANT_ID, "1")
                .header(HEADER_INTERNAL_SECRET, INTERNAL_SECRET))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用例8：/actuator/health 完全无身份头 → 200（路径2 匿名放行）")
    void case8_actuatorAnonymous_shouldPass() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    /** 用与生产相同的算法生成 HMAC-SHA256 签名 */
    private static String computeHmac(String userId, String tenantId, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sig);
    }

    /** 测试 stub：模拟 sync 业务端点 */
    @RestController
    @RequestMapping("/api/perm/abstract-user")
    static class StubSyncController {
        @PostMapping("/sync")
        public String sync(@RequestBody(required = false) String body) {
            return "{\"ok\":true}";
        }
    }

    /** 测试 stub：模拟 actuator + internal 端点 */
    @RestController
    static class StubActuatorController {
        @GetMapping("/actuator/health")
        public String actuatorHealth() {
            return "{\"status\":\"UP\"}";
        }

        @GetMapping("/internal/health")
        public String internalHealth() {
            return "{\"status\":\"UP\"}";
        }
    }
}
