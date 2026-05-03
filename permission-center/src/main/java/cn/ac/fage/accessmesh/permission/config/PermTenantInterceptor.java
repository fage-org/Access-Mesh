package cn.ac.fage.accessmesh.permission.config;

import cn.ac.fage.accessmesh.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.permission.util.SecurityLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Reads X-Tenant-Id from the request header and stores it in TenantContextHolder.
 * Clears the context after the request completes to prevent thread-pool leakage.
 *
 * <p>Security: X-Tenant-Id header is mandatory. Missing or invalid header will result in 400 Bad Request.
 */
@Component
public class PermTenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermTenantInterceptor.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String tenantIdStr = request.getHeader(HEADER_TENANT_ID);

        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.BLOCKED_REQUEST,
                request,
                "Missing required header: X-Tenant-Id",
                null,
                null
            );
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing required header: X-Tenant-Id");
            return false;
        }

        try {
            Long tenantId = Long.parseLong(tenantIdStr.trim());
            TenantContextHolder.setTenantId(tenantId);
        } catch (NumberFormatException e) {
            // Use structured logging to prevent log injection attacks
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.SUSPICIOUS_INPUT,
                request,
                "Invalid X-Tenant-Id header format",
                null,
                tenantIdStr
            );
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant-Id header format");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    /**
     * Write error response in unified JSON format (PermResult style).
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // PermResult format: {"code":400,"message":"...","data":null,"requestId":null,"traceId":null}
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}", status, message);
        response.getWriter().write(json);
    }
}
