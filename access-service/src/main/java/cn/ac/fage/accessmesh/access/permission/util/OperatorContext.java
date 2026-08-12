package cn.ac.fage.accessmesh.access.permission.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作者上下文工具类
 * <p>
 * 提供从HTTP请求头中提取操作者身份的工具方法。
 * 操作者身份由Gateway注入，通过请求头传递到后端服务。
 * 支持签名验证防止请求伪造。
 * </p>
 */
public final class OperatorContext {

    private static final Logger log = LoggerFactory.getLogger(OperatorContext.class);
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_SIGNATURE = "X-User-Signature";

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private OperatorContext() {}

    /**
     * 获取当前操作者的用户ID
     * <p>
     * 从HTTP请求头中提取操作者身份。
     * 优先级：X-User-Id Header（由Gateway注入）。
     * 会验证签名头是否存在，防止请求伪造。
     * </p>
     *
     * @return 操作者用户ID
     * @throws SecurityException 如果无法确定操作者身份或签名验证失败
     */
    public static Long getOperatorId() {
        HttpServletRequest request = getRequest();

        // 验证签名头是否存在（签名验证必须已通过）
        // 如果 HeaderSignatureInterceptor 验证成功，此头必须存在
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (signature == null || signature.isBlank()) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.INVALID_SIGNATURE,
                request,
                "请求未由Gateway签名 - 缺少X-User-Signature请求头",
                null,
                null
            );
            throw new SecurityException("请求未由Gateway签名 - 检测到签名验证绕过尝试");
        }

        // X-User-Id Header（由Gateway注入）
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return Long.parseLong(userIdHeader.trim());
            } catch (NumberFormatException e) {
                // 使用结构化日志防止日志注入攻击
                SecurityLogUtil.logSecurityEvent(
                    SecurityEventType.SUSPICIOUS_INPUT,
                    request,
                    "无效的X-User-Id请求头格式",
                    userIdHeader,
                    null
                );
            }
        }

        throw new SecurityException("无法确定操作者身份 - X-User-Id请求头未找到或无效");
    }

    /**
     * 获取当前HTTP请求对象
     * <p>
     * 从Spring的RequestContextHolder中获取当前请求。
     * </p>
     *
     * @return 当前HTTP请求对象
     * @throws SecurityException 如果没有HTTP请求上下文
     */
    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new SecurityException("没有可用的HTTP请求上下文");
        }
        return attrs.getRequest();
    }
}