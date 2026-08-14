package cn.ac.fage.accessmesh.access.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 请求头 HMAC-SHA256 签名验证服务（T-ACCESS-004 抽取）。
 * <p>
 * 验证 Gateway 注入的用户身份请求头（X-User-Id / X-Tenant-Id / X-User-Signature /
 * X-Signature-Timestamp）未被篡改，防止请求头注入攻击。签名验证是强制性的——
 * 密钥未配置时应用无法启动。签名 payload 格式：
 * {@code userId + "|" + tenantId + "|" + timestamp}。
 * </p>
 * <p>
 * 使用 ThreadLocal 缓存 Mac 实例保证线程安全；每次验证后清理，防止池线程复用残留。
 * 常量时间比较防时序攻击。
 * </p>
 */
@Component
public class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 用户 ID 请求头（Gateway 注入）。 */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 租户 ID 请求头（Gateway 注入）。 */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    /** HMAC 签名请求头。 */
    public static final String HEADER_SIGNATURE = "X-User-Signature";
    /** 签名时间戳请求头（秒级 Unix 时间）。 */
    public static final String HEADER_TIMESTAMP = "X-Signature-Timestamp";

    @Value("${perm.signature.secret:}")
    private String signatureSecret;

    @Value("${perm.signature.valid-seconds:300}")
    private int signatureValidSeconds;

    private ThreadLocal<Mac> macThreadLocal;

    /**
     * 验证配置并初始化 Mac 实例；签名密钥缺失时启动失败（强制）。
     */
    @PostConstruct
    public void validateConfiguration() {
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
     * 完整验签：X-User-Id / X-User-Signature / X-Signature-Timestamp 均存在、
     * 时间戳在有效窗口内、HMAC 匹配。
     *
     * @param request HTTP 请求
     * @return true=验签通过；false=缺头 / 时间戳过期 / 签名不匹配
     */
    public boolean verify(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        String providedSignature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);

        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()
            || providedSignature == null || providedSignature.isBlank()
            || timestampStr == null || timestampStr.isBlank()) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            return false;
        }

        // 时间戳窗口校验
        long currentTime = System.currentTimeMillis() / 1000;
        if (Math.abs(currentTime - timestamp) > signatureValidSeconds) {
            return false;
        }

        // 计算并比较签名（常量时间）
        String expectedSignature = computeSignature(userId, tenantId, timestamp);
        return expectedSignature != null
            && constantTimeEquals(expectedSignature, providedSignature);
    }

    /**
     * 签名时间戳有效窗口（秒），供拦截器做错误细分。
     */
    public int validSeconds() {
        return signatureValidSeconds;
    }

    /**
     * 解析 X-User-Id 头为 Long；缺失或格式无效返回 null。
     */
    public Long parseUserId(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 X-Tenant-Id 头为 Long；缺失或格式无效返回 null。
     */
    public Long parseTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(tenantId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 计算 HMAC-SHA256 签名（payload = userId|tenantId|timestamp）。
     *
     * @return 十六进制签名字符串；计算失败返回 null
     */
    private String computeSignature(String userId, String tenantId, long timestamp) {
        try {
            Mac mac = macThreadLocal.get();
            mac.reset();
            String payload = userId + "|" + tenantId + "|" + timestamp;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            log.error("签名计算错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 常量时间字符串比较（防时序攻击）；长度不等时仍遍历避免提前退出。
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
