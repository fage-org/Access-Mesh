package cn.ac.fage.accessmesh.access.infrastructure.config;

import cn.ac.fage.accessmesh.access.infrastructure.SecurityAttributes;
import cn.ac.fage.accessmesh.access.infrastructure.ServicePrincipal;
import cn.ac.fage.accessmesh.access.infrastructure.credential.service.domain.ServiceCredentialDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.SecurityEventType;
import cn.ac.fage.accessmesh.access.infrastructure.util.SecurityLogUtil;
import cn.ac.fage.accessmesh.access.infrastructure.util.SecurityUtils;
import cn.ac.fage.accessmesh.common.security.M2mCredentialEndpoints;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 服务认证仲裁器（T-PERM-070，order=1，替换 InternalApiSecretInterceptor 单策略位）。
 * <p>
 * 单一拦截器内含双策略（Spring MVC 拦截器链是 AND 语义，「两拦截器并列、任一通过」
 * 不可实现——service-authentication.md §3.2），完整仲裁状态表：
 * </p>
 * <pre>
 * ① 完整凭证头（X-Credential-Id + X-Credential-Secret）——自报头/密钥头并存时一律不采信
 *    → 验证成功且命中 M2M 白名单 → ServicePrincipal(CREDENTIAL) attribute
 *    → 验证失败（20065/20066/20067/20068）或白名单外 → 403，禁止降级回落旧密钥
 * ② 半头（恰一个凭证头）→ 403（20065）
 * ③ 无凭证头 + X-Internal-Secret 有效 → INTERNAL_AUTHENTICATED attribute（旧密钥路径，行为零变化）
 * ④ 无凭证头 + 密钥无效/缺失 → 403（既有行为）
 * </pre>
 * <p>
 * 凭证路径的后续消费：order=2 签名拦截器对 SERVICE_PRINCIPAL 请求跳过验签（凭证请求
 * 的 X-User-Id 等自报头一律不采信、不拒绝）；order=3 上下文拦截器只认 principal
 * （tenantId/serviceCode 由凭证行派生）。
 * </p>
 */
@Component
public class ServiceAuthArbiter implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ServiceAuthArbiter.class);

    /** 凭证请求头（service-authentication.md §3.2，TLS 传输）。 */
    static final String HEADER_CREDENTIAL_ID = "X-Credential-Id";
    static final String HEADER_CREDENTIAL_SECRET = "X-Credential-Secret";

    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${perm.internal-secret:}")
    private String expectedSecret;

    private final ServiceCredentialDomainService serviceCredentialDomainService;

    public ServiceAuthArbiter(ServiceCredentialDomainService serviceCredentialDomainService) {
        this.serviceCredentialDomainService = serviceCredentialDomainService;
    }

    /**
     * 启动校验：内部密钥必须已配置（旧密钥路径在退役判据达成前维持可用，
     * service-authentication.md §3.5）。
     */
    @PostConstruct
    public void validateConfiguration() {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new IllegalStateException(
                "严重错误：perm.internal-secret 未配置。 "
                + "此密钥用于保护内部管理API的安全。 "
                + "请设置 PERM_INTERNAL_SECRET 环境变量或配置文件中的 perm.internal-secret。"
            );
        }
        log.info("服务认证仲裁器初始化完成（凭证 + 内部密钥双策略）");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String credentialId = request.getHeader(HEADER_CREDENTIAL_ID);
        String credentialSecret = request.getHeader(HEADER_CREDENTIAL_SECRET);
        boolean hasCredentialId = credentialId != null && !credentialId.isBlank();
        boolean hasCredentialSecret = credentialSecret != null && !credentialSecret.isBlank();

        // ①② 凭证头出现即走凭证链（自报头/密钥头并存一律不采信——凭证优先）
        if (hasCredentialId || hasCredentialSecret) {
            if (!hasCredentialId || !hasCredentialSecret) {
                // 半头：形态即拒绝，不落库不比对（无凭证状态探测面）
                return rejectCredential(request, response, AccessErrorCode.SERVICE_CREDENTIAL_INVALID,
                    "credential headers incomplete");
            }
            return authenticateCredential(request, response, credentialId.trim(), credentialSecret);
        }

        // ③④ 旧密钥路径（过渡期维持，行为与 InternalApiSecretInterceptor 零变化）
        String providedSecret = request.getHeader(SECRET_HEADER);
        if (providedSecret == null || !SecurityUtils.constantTimeEquals(providedSecret, expectedSecret)) {
            String userId = request.getHeader("X-User-Id");
            String tenantId = request.getHeader("X-Tenant-Id");
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.BLOCKED_REQUEST,
                request,
                "缺少或无效的X-Internal-Secret请求头",
                userId,
                tenantId
            );
            writeForbiddenJson(response, 403, "拒绝访问：缺少有效的内部调用凭证");
            return false;
        }
        // 标记请求已通过内部密钥校验，供后续拦截器（HeaderSignatureInterceptor）决策
        request.setAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED, Boolean.TRUE);
        return true;
    }

    /**
     * 凭证链：verify（行定位→secret 比对→状态细分→服务注册校验，见
     * ServiceCredentialDomainServiceImpl 验证顺序）→ M2M 白名单强制（不依赖 Gateway，
     * SDK 直连同样受限）→ 写 SERVICE_PRINCIPAL attribute（服务端内存对象，不可伪造）。
     */
    private boolean authenticateCredential(HttpServletRequest request, HttpServletResponse response,
                                           String credentialId, String secret) throws Exception {
        ServiceCredentialDomainService.VerifyResult result =
            serviceCredentialDomainService.verify(credentialId, secret);
        if (!result.success()) {
            // 禁止降级回落旧密钥（状态表①行）：凭证失败与旧密钥并存时不得走旧链
            return rejectCredential(request, response, result.failure(),
                "credential verify failed: " + result.failure());
        }
        if (!M2mCredentialEndpoints.matches(request.getMethod(), request.getRequestURI())) {
            // 服务端白名单强制：凭证请求只可达 M2M 通道端点（防直连调管理/查询端点
            // 扩大凭证能力半径——authMethod=CREDENTIAL × 精确路径）
            return rejectCredential(request, response, AccessErrorCode.SERVICE_CREDENTIAL_INVALID,
                "credential auth not allowed on path " + request.getRequestURI());
        }
        request.setAttribute(SecurityAttributes.ATTR_SERVICE_PRINCIPAL, result.principal());
        return true;
    }

    /** 凭证链统一 403 拒绝（body 携带细分错误码 20065~20068；不泄露内部细节）。 */
    private boolean rejectCredential(HttpServletRequest request, HttpServletResponse response,
                                     AccessErrorCode code, String detail) throws Exception {
        SecurityLogUtil.logSecurityEvent(
            SecurityEventType.BLOCKED_REQUEST,
            request,
            "service credential rejected: " + detail,
            null,
            null
        );
        writeForbiddenJson(response, code.getCode(), code.getMessage());
        return false;
    }

    /**
     * 两策略共用的 403 JSON 信封写出（统一响应头与编码；body 形状与 RequestContextInterceptor
     * 的 writeJson 同族——code/message/data 三段，拦截器层不经全局异常处理器）。
     */
    private void writeForbiddenJson(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(
            "{\"code\":%d,\"message\":\"%s\",\"data\":null}", code, message));
    }
}
