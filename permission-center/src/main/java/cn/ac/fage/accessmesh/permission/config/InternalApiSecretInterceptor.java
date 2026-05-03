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

import java.nio.charset.StandardCharsets;

/**
 * Internal API key interceptor for permission-center.
 * Ensures that only requests from the gateway (with valid X-Internal-Secret header)
 * can access management endpoints. Requests without the header are rejected with 403.
 *
 * Auth/tenant-check endpoints are exempted as they are called by the gateway
 * on behalf of authenticated users.
 */
@Component
public class InternalApiSecretInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalApiSecretInterceptor.class);
    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${perm.internal-secret:}")
    private String expectedSecret;

    @PostConstruct
    public void validateConfiguration() {
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new IllegalStateException(
                "CRITICAL: perm.internal-secret is not configured. " +
                "This secret is required for securing internal management APIs. " +
                "Set PERM_INTERNAL_SECRET environment variable or perm.internal-secret in configuration."
            );
        }
        log.info("Internal API secret validation configured successfully");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String providedSecret = request.getHeader(SECRET_HEADER);
        if (providedSecret == null || !providedSecret.equals(expectedSecret)) {
            String userId = request.getHeader("X-User-Id");
            String tenantId = request.getHeader("X-Tenant-Id");
            
            // Use structured logging to prevent log injection attacks
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.BLOCKED_REQUEST,
                request,
                "Missing or invalid X-Internal-Secret header",
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
