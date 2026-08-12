package cn.ac.fage.accessmesh.access.permission.config;

import cn.ac.fage.accessmesh.access.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.access.permission.util.SecurityLogUtil;
import cn.ac.fage.accessmesh.access.permission.util.SecurityUtils;
import cn.ac.fage.accessmesh.access.permission.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 请求头签名校验拦截器
 * <p>
 * 验证用户身份请求头的HMAC-SHA256签名。
 * 确保Gateway注入的请求头未被篡改，防止请求头注入攻击。
 * 签名验证是强制执行的，不可通过配置禁用。
 * </p>
 */
@Component
public class HeaderSignatureInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderSignatureInterceptor.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";
    private static final String HEADER_TIMESTAMP = "X-Signature-Timestamp";

    // 不泄露签名机制细节的通用错误消息
    private static final String ERROR_ACCESS_DENIED = "拒绝访问";
    private static final String ERROR_VALIDATION_FAILED = "请求验证失败";
    private static final String ERROR_AUTH_FAILED = "认证失败";
    private static final String ERROR_INTERNAL = "内部错误";

    // 签名验证是强制执行的，不可通过配置禁用

    @Value("${perm.signature.secret:}")
    private String signatureSecret;

    @Value("${perm.signature.valid-seconds:300}")
    private int signatureValidSeconds;

    private ThreadLocal<Mac> macThreadLocal;

    /**
     * 验证配置并初始化Mac实例
     * <p>
     * 签名验证是必须的，密钥未配置时应用无法启动。
     * 创建ThreadLocal Mac实例用于线程安全的签名计算。
     * </p>
     */
    @PostConstruct
    public void validateConfiguration() {
        // 签名验证是强制的 - 不可禁用
        if (signatureSecret == null || signatureSecret.isBlank()) {
            throw new IllegalStateException("请求头签名密钥未配置。应用无法在缺少签名配置的情况下启动。");
        }

        try {
            Mac macTemplate = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                signatureSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            macTemplate.init(keySpec);
            macThreadLocal = ThreadLocal.withInitial(() -> {
                try {
                    return (Mac) macTemplate.clone();
                } catch (Exception e) {
                    throw new RuntimeException("克隆Mac实例失败", e);
                }
            });
            log.info("请求头签名验证已初始化。有效窗口: {} 秒", signatureValidSeconds);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("初始化HMAC-SHA256失败: " + e.getMessage(), e);
        }
    }

    /**
     * 请求预处理 — 4 路径决策树
     * <p>
     * 决策原则：基于已验证的 attribute（仅 InternalApiSecretInterceptor 可写）
     * 而非未验证的请求头进行信任判定，防止请求头注入与拦截器顺序绕过。
     * </p>
     * <ol>
     *   <li>路径 1：服务间内部调用 — request attribute INTERNAL_AUTHENTICATED=true
     *       表示已通过 {@link InternalApiSecretInterceptor} 校验，直接放行。</li>
     *   <li>路径 2：完全匿名请求 — 无任何用户身份头（actuator 健康检查、未登录探活），放行。</li>
     *   <li>路径 3：仅 X-Tenant-Id 而无 X-User-Id 且非内部已认证 — 异常请求，记录安全日志后 403。</li>
     *   <li>路径 4：用户态调用（X-User-Id 存在）— 必须存在合法 HMAC 签名，进入完整 HMAC 校验流程。</li>
     * </ol>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 验证通过返回true，否则返回false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String userId = request.getHeader(HEADER_USER_ID);
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        boolean internalAuthenticated = Boolean.TRUE.equals(
            request.getAttribute(InternalApiSecretInterceptor.ATTR_INTERNAL_AUTHENTICATED)
        );

        // 路径 1：服务间内部调用（已通过 InternalApiSecretInterceptor 校验）
        if (internalAuthenticated) {
            return true;
        }

        // 路径 2：完全匿名（actuator 健康检查、未登录探活）
        if (StringUtils.isBlank(userId) && StringUtils.isBlank(tenantId)) {
            return true;
        }

        // 路径 3：仅 tenantId 无 userId 且非内部已认证 — 异常请求
        if (StringUtils.isBlank(userId)) {
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

        // 路径 4：用户态调用 — 必须验签
        String providedSignature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);

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
        if (timeDiff > signatureValidSeconds) {
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

        // 计算并验证签名
        String expectedSignature = computeSignature(userId, tenantId, timestamp);
        if (expectedSignature == null) {
            log.error("签名计算过程中发生内部错误");
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ERROR_INTERNAL);
            return false;
        }

        // 使用常量时间比较防止时序攻击
        if (!SecurityUtils.constantTimeEquals(expectedSignature, providedSignature)) {
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

        return true;
    }

    /**
     * 请求完成后清理
     * <p>
     * 清理ThreadLocal Mac实例，防止线程池环境下的内存泄漏。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @param ex       异常对象
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程池环境下内存泄漏
        if (macThreadLocal != null) {
            macThreadLocal.remove();
        }
    }

    /**
     * 计算签名
     * <p>
     * 使用ThreadLocal Mac实例计算HMAC-SHA256签名。
     * 签名格式：userId + "|" + tenantId + "|" + timestamp。
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param timestamp 时间戳
     * @return 签名十六进制字符串，计算失败返回null
     */
    private String computeSignature(String userId, String tenantId, long timestamp) {
        try {
            Mac mac = macThreadLocal.get();
            mac.reset();  // 重置 Mac 状态（清除之前的计算）
            String payload = userId + "|" + tenantId + "|" + timestamp;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            log.error("签名计算错误: {}", e.getMessage());
            return null;
        }
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