package cn.ac.fage.accessmesh.access.infrastructure.config;

import cn.ac.fage.accessmesh.access.infrastructure.SecurityAttributes;
import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务认证仲裁器五形态状态表回归锁（T-PERM-070，service-authentication.md §3.2）。
 * <p>
 * 锁定语义：凭证优先（自报头/密钥头并存一律不采信）、凭证失败禁止降级回落旧密钥、
 * 白名单外 403、旧密钥路径行为零变化。
 * </p>
 */
class ServiceAuthArbiterTest {

    private static final String INTERNAL_SECRET = "test-internal-secret-0123456789abcdef";
    private static final String M2M_PATH = "/api/access/resource-entity/sync";
    private static final String NON_M2M_PATH = "/api/access/auth/query-resources";

    private ServiceCredentialDomainService domainService;
    private ServiceAuthArbiter arbiter;

    @BeforeEach
    void setUp() {
        domainService = mock(ServiceCredentialDomainService.class);
        arbiter = new ServiceAuthArbiter(domainService);
        ReflectionTestUtils.setField(arbiter, "expectedSecret", INTERNAL_SECRET);
        arbiter.validateConfiguration();
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        return req;
    }

    @Test
    @DisplayName("状态表①：完整凭证头+verify 成功+白名单内 → 写 SERVICE_PRINCIPAL，旧密钥头并存不采信")
    void shouldBindPrincipal_whenCompleteCredentialAndWhitelisted() throws Exception {
        MockHttpServletRequest req = request("POST", M2M_PATH);
        req.addHeader("X-Credential-Id", "sc-abc");
        req.addHeader("X-Credential-Secret", "sk-xyz");
        // 并存头：自报头+旧密钥头（一律不采信——凭证优先）
        req.addHeader("X-Service-Code", "attacker-service");
        req.addHeader("X-Tenant-Id", "999");
        req.addHeader("X-User-Id", "1");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(domainService.verify("sc-abc", "sk-xyz"))
            .thenReturn(ServiceCredentialDomainService.VerifyResult.success(
                new ServicePrincipal(1L, "example-service", "sc-abc")));

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(req.getAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL))
            .isEqualTo(new ServicePrincipal(1L, "example-service", "sc-abc"));
        // 旧密钥路径 attribute 不写（并存不采信的负向锁——防未来实现回落旧链）
        assertThat(req.getAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED)).isNull();
    }

    @Test
    @DisplayName("状态表①失败：verify 无效(20065) → 403 且禁止降级（有效旧密钥并存也不放行）")
    void shouldRejectNoFallback_whenCredentialInvalid() throws Exception {
        MockHttpServletRequest req = request("POST", M2M_PATH);
        req.addHeader("X-Credential-Id", "sc-unknown");
        req.addHeader("X-Credential-Secret", "sk-wrong");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(domainService.verify(anyString(), anyString()))
            .thenReturn(ServiceCredentialDomainService.VerifyResult.failure(AccessErrorCode.SERVICE_CREDENTIAL_INVALID));

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("20065");
        assertThat(req.getAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL)).isNull();
        assertThat(req.getAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED)).isNull();
    }

    @Test
    @DisplayName("状态表①失败：凭证过期(20066)/停用(20067) → 403 细分码透出")
    void shouldRejectWithDistinctCodes_whenExpiredOrDisabled() throws Exception {
        for (AccessErrorCode code : new AccessErrorCode[]{
            AccessErrorCode.SERVICE_CREDENTIAL_EXPIRED, AccessErrorCode.SERVICE_CREDENTIAL_DISABLED,
            AccessErrorCode.SERVICE_CREDENTIAL_SERVICE_INACTIVE}) {
            MockHttpServletRequest req = request("POST", M2M_PATH);
            req.addHeader("X-Credential-Id", "sc-x");
            req.addHeader("X-Credential-Secret", "sk-x");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            when(domainService.verify("sc-x", "sk-x"))
                .thenReturn(ServiceCredentialDomainService.VerifyResult.failure(code));

            assertThat(arbiter.preHandle(req, resp, new Object())).isFalse();
            assertThat(resp.getStatus()).isEqualTo(403);
            assertThat(resp.getContentAsString()).contains(String.valueOf(code.getCode()));
        }
    }

    @Test
    @DisplayName("状态表②：半头（有 Id 无 Secret）→ 403 不落库不比对")
    void shouldReject_whenCredentialHeadersIncomplete() throws Exception {
        MockHttpServletRequest req = request("POST", M2M_PATH);
        req.addHeader("X-Credential-Id", "sc-abc");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("20065");
        org.mockito.Mockito.verifyNoInteractions(domainService);
    }

    @Test
    @DisplayName("状态表①白名单外：verify 成功但非 M2M 路径 → 403（SDK 直连调管理/查询端点拒绝）")
    void shouldReject_whenCredentialOnNonM2mPath() throws Exception {
        MockHttpServletRequest req = request("POST", NON_M2M_PATH);
        req.addHeader("X-Credential-Id", "sc-abc");
        req.addHeader("X-Credential-Secret", "sk-xyz");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(domainService.verify(anyString(), anyString()))
            .thenReturn(ServiceCredentialDomainService.VerifyResult.success(
                new ServicePrincipal(1L, "example-service", "sc-abc")));

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(req.getAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL)).isNull();
    }

    @Test
    @DisplayName("状态表③：无凭证头+有效旧密钥 → INTERNAL_AUTHENTICATED（行为零变化）")
    void shouldKeepLegacyBehavior_whenInternalSecretValid() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/access/abstract-user/sync");
        req.addHeader("X-Internal-Secret", INTERNAL_SECRET);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isTrue();
        assertThat(req.getAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED)).isEqualTo(Boolean.TRUE);
        assertThat(req.getAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL)).isNull();
    }

    @Test
    @DisplayName("状态表④：无凭证头+无效密钥 → 403（既有行为）")
    void shouldReject_whenInternalSecretInvalid() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/access/abstract-user/sync");
        req.addHeader("X-Internal-Secret", "wrong");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("白名单 method 不匹配（GET 同路径）→ 403（method+精确路径双因子）")
    void shouldReject_whenMethodMismatch() throws Exception {
        MockHttpServletRequest req = request("GET", M2M_PATH);
        req.addHeader("X-Credential-Id", "sc-abc");
        req.addHeader("X-Credential-Secret", "sk-xyz");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(domainService.verify(anyString(), anyString()))
            .thenReturn(ServiceCredentialDomainService.VerifyResult.success(
                new ServicePrincipal(1L, "example-service", "sc-abc")));

        boolean result = arbiter.preHandle(req, resp, new Object());

        assertThat(result).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
    }
}
