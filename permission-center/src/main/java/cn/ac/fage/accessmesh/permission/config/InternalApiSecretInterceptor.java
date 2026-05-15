package cn.ac.fage.accessmesh.permission.config;

import cn.ac.fage.accessmesh.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.permission.util.SecurityLogUtil;
import cn.ac.fage.accessmesh.permission.util.StringUtils;
import jakarta.annotation.PostConstruct;
import cn.ac.fage.accessmesh.permission.util.SecurityUtils;
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
 * 内部API密钥拦截器
 * <p>
 * 用于permission-center的内部API访问控制。
 * 确保只有来自Gateway的请求（携带有效的X-Internal-Secret请求头）
 * 可以访问管理端点。未携带请求头的请求将被拒绝（返回403）。
 * </p>
 *
 * <p>认证/租户检查端点例外，因为这些端点由Gateway代理已认证用户调用。
 * </p>
 */
@Component
public class InternalApiSecretInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalApiSecretInterceptor.class);
    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${perm.internal-secret:}")
    private String expectedSecret;

    /**
     * 验证配置
     * <p>
     * 启动时验证内部密钥是否已配置。
     * 未配置时抛出异常，阻止应用启动。
     * </p>
     */
    @PostConstruct
    public void validateConfiguration() {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new IllegalStateException(
                "严重错误：perm.internal-secret 未配置。 " +
                "此密钥用于保护内部管理API的安全。 " +
                "请设置 PERM_INTERNAL_SECRET 环境变量或配置文件中的 perm.internal-secret。"
            );
        }
        log.info("内部API密钥验证配置成功");
    }

    /**
     * 请求预处理
     * <p>
     * 验证请求是否携带有效的内部密钥请求头。
     * 无效密钥时记录安全事件并拒绝访问。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 验证通过返回true，否则返回false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String providedSecret = request.getHeader(SECRET_HEADER);
        if (providedSecret == null || !SecurityUtils.constantTimeEquals(providedSecret, expectedSecret)) {
            String userId = request.getHeader("X-User-Id");
            String tenantId = request.getHeader("X-Tenant-Id");

            // 使用结构化日志防止日志注入攻击
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.BLOCKED_REQUEST,
                request,
                "缺少或无效的X-Internal-Secret请求头",
                userId,
                tenantId
            );

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                "{\"code\":403,\"message\":\"拒绝访问：缺少有效的内部调用凭证\",\"data\":null}"
            );
            return false;
        }
        return true;
    }
}