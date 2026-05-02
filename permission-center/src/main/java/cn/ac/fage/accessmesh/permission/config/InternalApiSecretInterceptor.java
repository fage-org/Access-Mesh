package cn.ac.fage.accessmesh.permission.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Skip validation if secret is not configured (dev mode)
        if (expectedSecret == null || expectedSecret.isBlank()) {
            log.debug("X-Internal-Secret not configured, skipping validation");
            return true;
        }

        String providedSecret = request.getHeader(SECRET_HEADER);
        if (providedSecret == null || !providedSecret.equals(expectedSecret)) {
            log.warn("Blocked request without valid X-Internal-Secret: {} {}",
                request.getMethod(), request.getRequestURI());
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
