package cn.ac.fage.accessmesh.permission.config;

import cn.ac.fage.accessmesh.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.permission.util.SecurityLogUtil;
import cn.ac.fage.accessmesh.permission.util.StringUtils;
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
 * Validates HMAC-SHA256 signature for user identity headers.
 * Ensures that headers injected by the gateway have not been tampered with.
 */
@Component
public class HeaderSignatureInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderSignatureInterceptor.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";
    private static final String HEADER_TIMESTAMP = "X-Signature-Timestamp";

    // Generic error messages that do not reveal signature mechanism details
    private static final String ERROR_ACCESS_DENIED = "Access denied";
    private static final String ERROR_VALIDATION_FAILED = "Request validation failed";
    private static final String ERROR_AUTH_FAILED = "Authentication failed";
    private static final String ERROR_INTERNAL = "Internal error";

    @Value("${perm.signature.enabled:true}")
    private boolean signatureEnabled;

    @Value("${perm.signature.secret:}")
    private String signatureSecret;

    @Value("${perm.signature.valid-seconds:300}")
    private int signatureValidSeconds;

    private ThreadLocal<Mac> macThreadLocal;

    @PostConstruct
    public void validateConfiguration() {
        if (!signatureEnabled) {
            log.info("Header signature validation is DISABLED");
            return;
        }

        if (signatureSecret == null || signatureSecret.isBlank()) {
            throw new IllegalStateException("Header signature secret not configured. Application cannot start without proper signature configuration.");
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
                    throw new RuntimeException("Failed to clone Mac", e);
                }
            });
            log.info("Header signature validation initialized. Valid window: {} seconds", signatureValidSeconds);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to initialize HMAC-SHA256: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!signatureEnabled) {
            return true;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        String tenantId = request.getHeader(HEADER_TENANT_ID);

        if (StringUtils.isBlank(userId) && StringUtils.isBlank(tenantId)) {
            return true;
        }

        String providedSignature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);

        if (StringUtils.isBlank(providedSignature) || StringUtils.isBlank(timestampStr)) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "Missing signature or timestamp header",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_ACCESS_DENIED);
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "Invalid timestamp format",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, ERROR_VALIDATION_FAILED);
            return false;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long timeDiff = Math.abs(currentTime - timestamp);
        if (timeDiff > signatureValidSeconds) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "Signature timestamp expired",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_VALIDATION_FAILED);
            return false;
        }

        String expectedSignature = computeSignature(userId, tenantId, timestamp);
        if (expectedSignature == null) {
            log.error("Internal error during signature computation");
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ERROR_INTERNAL);
            return false;
        }

        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "Signature verification failed",
                userId,
                tenantId
            );
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ERROR_AUTH_FAILED);
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程池环境下内存泄漏
        if (macThreadLocal != null) {
            macThreadLocal.remove();
        }
    }

    private String computeSignature(String userId, String tenantId, long timestamp) {
        try {
            Mac mac = macThreadLocal.get();
            mac.reset();  // 重置 Mac 状态（清除之前的计算）
            String payload = userId + "|" + tenantId + "|" + timestamp;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            log.error("Error computing signature: {}", e.getMessage());
            return null;
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != providedBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ providedBytes[i];
        }
        return result == 0;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}",
            status, message);
        response.getWriter().write(json);
    }

    /**
     * Cleanup ThreadLocal Mac instance to prevent memory leaks in thread pool scenarios.
     */
    public void cleanup() {
        if (macThreadLocal != null) {
            macThreadLocal.remove();
        }
    }
}
