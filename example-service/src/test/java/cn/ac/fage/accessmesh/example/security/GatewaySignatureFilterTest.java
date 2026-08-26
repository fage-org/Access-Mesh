package cn.ac.fage.accessmesh.example.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * GatewaySignatureFilter 单元测试：签名算法正确性 + 时效窗可配置性锁定——
 * 用非默认窗口（60s）验证边界（±30s 放行、±120s 拒绝），若实现回退为硬编码 300s，
 * ±120s 用例将通过签名而失败，防止可配置性修复回归。
 */
class GatewaySignatureFilterTest {

    private static final String SECRET = "test-signature-secret";

    private final GatewaySignatureFilter filter =
        new GatewaySignatureFilter(SECRET, 60, new ObjectMapper());
    private final FilterChain chain = mock(FilterChain.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("窗口内签名放行（自定义 60s 窗，ts=now-30）")
    void validSignatureWithinWindow_passes() throws Exception {
        MockHttpServletRequest request = signedRequest("42", "1", -30);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("过期时间戳拒绝 30003（ts=now-120，在默认 300s 内但超出自定义 60s 窗）")
    void expiredBeyondCustomWindow_rejected() throws Exception {
        // 锁定可配置性：若 validSeconds 被硬编码回 300，本用例将放行而失败
        MockHttpServletRequest request = signedRequest("42", "1", -120);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getContentAsString()).contains("30003");
    }

    @Test
    @DisplayName("未来时间戳拒绝 30003（ts=now+120，超出自定义 60s 窗）")
    void futureBeyondCustomWindow_rejected() throws Exception {
        MockHttpServletRequest request = signedRequest("42", "1", 120);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        JsonNode envelope = json.readTree(response.getContentAsString());
        assertThat(envelope.path("code").asInt()).isEqualTo(30003);
    }

    /** 构造带合法签名（对指定时间戳偏移量签名）的请求 */
    private MockHttpServletRequest signedRequest(String userId, String tenantId, long tsOffsetSeconds)
            throws Exception {
        long ts = System.currentTimeMillis() / 1000 + tsOffsetSeconds;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal((userId + "|" + tenantId + "|" + ts).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId);
        request.addHeader("X-Tenant-Id", tenantId);
        request.addHeader("X-User-Signature", HexFormat.of().formatHex(sig));
        request.addHeader("X-Signature-Timestamp", String.valueOf(ts));
        return request;
    }
}
