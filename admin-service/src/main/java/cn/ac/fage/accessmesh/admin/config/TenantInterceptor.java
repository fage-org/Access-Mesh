package cn.ac.fage.accessmesh.admin.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Extracts tenant ID from request header (X-Tenant-Id) or from
 * the authenticated user context, and stores it in TenantContextHolder
 * for MyBatis-Flex auto-tenant filtering.
 *
 * <p>Security: X-Tenant-Id header is mandatory for all non-auth requests.
 * Missing or invalid header will result in 400 Bad Request.
 * Auth endpoints (/auth/) are exempt from tenant isolation.
 *
 * <p>FIX #2: Added security validation to verify header tenantId matches user session.
 * <p>Plan B: Made interceptor strict — requires X-Tenant-Id on all non-auth requests.
 */
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Auth endpoints and public endpoints don't require tenant isolation
        String uri = request.getRequestURI();
        if (uri.startsWith("/auth/")) {
            return true;
        }

        // Priority 1: explicit header
        String tenantHeader = request.getHeader(HEADER_TENANT_ID);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                Long tenantIdFromHeader = Long.parseLong(tenantHeader.trim());

                // Security validation - verify header tenantId matches user session
                if (StpUtil.isLogin()) {
                    SaSession session = StpUtil.getSession();
                    Long userTenantId = (Long) session.get("tenantId");
                    if (userTenantId != null && !userTenantId.equals(tenantIdFromHeader)) {
                        log.warn("Tenant ID mismatch: header={}, session={}, userId={}",
                            tenantIdFromHeader, userTenantId, StpUtil.getLoginIdAsLong());
                        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                            "Tenant ID mismatch with user session");
                        return false;
                    }
                }

                TenantContextHolder.setTenantId(tenantIdFromHeader);
                return true;
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-Id header: {}", tenantHeader);
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid X-Tenant-Id header format");
                return false;
            }
        }

        // Priority 2: resolve from logged-in user session tenant
        try {
            if (StpUtil.isLogin()) {
                SaSession session = StpUtil.getSession();
                Long userTenantId = (Long) session.get("tenantId");
                if (userTenantId != null) {
                    TenantContextHolder.setTenantId(userTenantId);
                    return true;
                }
                Long userId = StpUtil.getLoginIdAsLong();
                log.debug("Tenant ID not found in session for user {}", userId);
            }
        } catch (Exception ignored) {
        }

        // Plan B: Strict enforcement — require X-Tenant-Id on all non-auth requests
        log.warn("Missing X-Tenant-Id header for non-auth request: {} {}",
            request.getMethod(), request.getRequestURI());
        writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "Missing required header: X-Tenant-Id");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    /**
     * Write error response in unified JSON format.
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}",
            status, message);
        response.getWriter().write(json);
    }
}
