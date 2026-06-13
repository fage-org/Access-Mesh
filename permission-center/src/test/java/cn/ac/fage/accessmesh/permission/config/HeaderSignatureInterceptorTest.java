package cn.ac.fage.accessmesh.permission.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HeaderSignatureInterceptor 4 路径决策树单元测试
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>internalAuthenticated attribute → 直接放行</li>
 *   <li>完全匿名 → 放行</li>
 *   <li>仅 tenantId → 403</li>
 *   <li>用户态 + 缺签名 → 403</li>
 *   <li>用户态 + 合法签名 → 放行</li>
 *   <li>用户态 + 过期时间戳 → 403</li>
 *   <li>用户态 + 错误签名 → 403</li>
 * </ol>
 *
 * <p>所有测试独立构造 request/response，不共享状态。</p>
 */
class HeaderSignatureInterceptorTest {

    private static final String SECRET = "test-secret-key-for-unit-test-0123456789";
    private static final int VALID_SECONDS = 300;

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";
    private static final String HEADER_TIMESTAMP = "X-Signature-Timestamp";

    private HeaderSignatureInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new HeaderSignatureInterceptor();
        ReflectionTestUtils.setField(interceptor, "signatureSecret", SECRET);
        ReflectionTestUtils.setField(interceptor, "signatureValidSeconds", VALID_SECONDS);
        // 初始化 macThreadLocal
        interceptor.validateConfiguration();
    }

    @Test
    @DisplayName("路径1：INTERNAL_AUTHENTICATED=true → 放行（任何 user/signature 头都可缺）")
    void shouldPassThrough_whenInternalAuthenticatedAttributeSet() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader(HEADER_TENANT_ID, "1");
        req.setAttribute(InternalApiSecretInterceptor.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("路径2：完全匿名 → 放行")
    void shouldPassThrough_whenAnonymousRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // 没有任何 X-* 头

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("路径3：仅 tenantId 无 userId 且非内部已认证 → 403")
    void shouldReject_whenTenantIdOnlyWithoutInternalAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader(HEADER_TENANT_ID, "1");
        // 没有 INTERNAL_AUTHENTICATED attribute

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("路径4：有 userId 但无签名 → 403")
    void shouldReject_whenUserIdWithoutSignature() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        req.addHeader(HEADER_USER_ID, "100");
        req.addHeader(HEADER_TENANT_ID, "1");
        // 缺 HEADER_SIGNATURE 与 HEADER_TIMESTAMP

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("路径4：有 userId + 合法 HMAC + 当前时间戳 → 放行")
    void shouldPassThrough_whenUserIdWithValidSignature() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        String userId = "100";
        String tenantId = "1";
        long ts = System.currentTimeMillis() / 1000;
        String signature = sign(userId, tenantId, ts);

        req.addHeader(HEADER_USER_ID, userId);
        req.addHeader(HEADER_TENANT_ID, tenantId);
        req.addHeader(HEADER_SIGNATURE, signature);
        req.addHeader(HEADER_TIMESTAMP, String.valueOf(ts));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("路径4：HMAC 正确但时间戳超过 valid-seconds → 403")
    void shouldReject_whenUserIdWithExpiredTimestamp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        String userId = "100";
        String tenantId = "1";
        long ts = System.currentTimeMillis() / 1000 - (VALID_SECONDS + 60);
        String signature = sign(userId, tenantId, ts);

        req.addHeader(HEADER_USER_ID, userId);
        req.addHeader(HEADER_TENANT_ID, tenantId);
        req.addHeader(HEADER_SIGNATURE, signature);
        req.addHeader(HEADER_TIMESTAMP, String.valueOf(ts));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("路径4：时间戳新鲜但 signature 错误 → 403")
    void shouldReject_whenUserIdWithInvalidSignature() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        String userId = "100";
        String tenantId = "1";
        long ts = System.currentTimeMillis() / 1000;

        req.addHeader(HEADER_USER_ID, userId);
        req.addHeader(HEADER_TENANT_ID, tenantId);
        req.addHeader(HEADER_SIGNATURE, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        req.addHeader(HEADER_TIMESTAMP, String.valueOf(ts));

        boolean result = interceptor.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    /**
     * 用与生产相同的算法生成 HMAC-SHA256 签名。
     * payload 格式：userId + "|" + tenantId + "|" + timestamp
     */
    private static String sign(String userId, String tenantId, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sig);
    }
}
