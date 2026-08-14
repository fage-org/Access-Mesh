package cn.ac.fage.accessmesh.access.permission.config;

import cn.ac.fage.accessmesh.access.infrastructure.SecurityAttributes;
import cn.ac.fage.accessmesh.access.infrastructure.SignatureVerifier;
import cn.ac.fage.accessmesh.access.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.access.permission.util.SecurityLogUtil;
import cn.ac.fage.accessmesh.access.permission.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 请求头签名校验拦截器（T-ACCESS-004 重构：验签逻辑抽取到 {@link SignatureVerifier}）。
 * <p>
 * 验证用户身份请求头的 HMAC-SHA256 签名，确保 Gateway 注入的请求头未被篡改，
 * 防止请求头注入攻击。签名验证是强制执行的，不可通过配置禁用。
 * </p>
 * <p>决策树（T-ACCESS-004 调整，修复 G1——内部凭证路径不再无条件信任用户头）：</p>
 * <ol>
 *   <li>X-User-Id 头存在 → 无论是否内部凭证，必须验签（缺签名 / 时间戳无效 /
 *       过期 / HMAC 不匹配均拒绝）；验签通过后写
 *       {@link SecurityAttributes#ATTR_SIGNATURE_VERIFIED} attribute，
 *       供 {@link cn.ac.fage.accessmesh.access.infrastructure.RequestContextInterceptor} 绑定身份。</li>
 *   <li>内部凭证通过（attribute INTERNAL_AUTHENTICATED）且无 X-User-Id → 纯服务调用，放行。</li>
 *   <li>完全匿名（无 X-User-Id 无 X-Tenant-Id）→ 放行（actuator 健康检查、未登录探活）。</li>
 *   <li>仅 X-Tenant-Id 而无 X-User-Id 且非内部已认证 → 异常请求，403。</li>
 * </ol>
 */
@Component
public class HeaderSignatureInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HeaderSignatureInterceptor.class);

    // 不泄露签名机制细节的通用错误消息
    private static final String ERROR_ACCESS_DENIED = "拒绝访问";
    private static final String ERROR_VALIDATION_FAILED = "请求验证失败";
    private static final String ERROR_AUTH_FAILED = "认证失败";

    private final SignatureVerifier signatureVerifier;

    public HeaderSignatureInterceptor(SignatureVerifier signatureVerifier) {
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * 请求预处理 — 4 路径决策树（T-ACCESS-004：X-User-Id 恒需验签）。
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 验证通过返回true，否则返回false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String userId = request.getHeader(SignatureVerifier.HEADER_USER_ID);
        String tenantId = request.getHeader(SignatureVerifier.HEADER_TENANT_ID);
        boolean internalAuthenticated = Boolean.TRUE.equals(
            request.getAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED)
        );

        // 路径 1：用户身份头存在 → 恒需验签（含内部凭证场景，修复 G1：
        // 凭证持有者不得伪造 X-User-Id 冒充操作者）
        if (userId != null && !userId.isBlank()) {
            return verifyUserSignature(request, response, userId, tenantId);
        }

        // 路径 2：服务间内部调用（已通过 InternalApiSecretInterceptor 校验，无用户头）
        if (internalAuthenticated) {
            return true;
        }

        // 路径 3：完全匿名（actuator 健康检查、未登录探活）
        if (StringUtils.isBlank(tenantId)) {
            return true;
        }

        // 路径 4：仅 tenantId 无 userId 且非内部已认证 — 异常请求
        SecurityLogUtil.logSecurityEvent(
            SecurityEventType.BLOCKED_REQUEST,
            request,
            "tenant-only request without internal authentication",
            null,
            tenantId
        );
        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_ACCESS_DENIED);
        return false;
    }

    /**
     * 用户态验签完整流程；通过后写 SIGNATURE_VERIFIED attribute。
     */
    private boolean verifyUserSignature(HttpServletRequest request, HttpServletResponse response,
                                        String userId, String tenantId) throws Exception {
        String providedSignature = request.getHeader(SignatureVerifier.HEADER_SIGNATURE);
        String timestampStr = request.getHeader(SignatureVerifier.HEADER_TIMESTAMP);

        // 检查签名和时间戳是否存在
        if (StringUtils.isBlank(providedSignature) || StringUtils.isBlank(timestampStr)) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "缺少签名或时间戳请求头",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_ACCESS_DENIED);
            return false;
        }

        // 解析并验证时间戳
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "时间戳格式无效",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, ERROR_VALIDATION_FAILED);
            return false;
        }

        // 检查时间戳是否在有效窗口内
        long currentTime = System.currentTimeMillis() / 1000;
        long timeDiff = Math.abs(currentTime - timestamp);
        if (timeDiff > signatureVerifier.validSeconds()) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "签名时间戳已过期",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_VALIDATION_FAILED);
            return false;
        }

        // 计算并验证签名（常量时间比较防时序攻击）
        if (!signatureVerifier.verify(request)) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "签名验证失败",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_AUTH_FAILED);
            return false;
        }

        // 验签通过：写 attribute 供 RequestContextInterceptor 绑定可信身份
        request.setAttribute(SecurityAttributes.ATTR_SIGNATURE_VERIFIED, Boolean.TRUE);
        return true;
    }

    /**
     * 发送错误响应
     * <p>
     * 构造JSON格式的错误响应并写入响应流。
     * </p>
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码
     * @param message  错误消息
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}",
            status, message);
        response.getWriter().write(json);
    }
}
