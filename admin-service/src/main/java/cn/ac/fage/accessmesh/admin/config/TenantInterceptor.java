package cn.ac.fage.accessmesh.admin.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Extracts tenant ID from request header (X-Tenant-Id) or from
 * the authenticated user context, and stores it in TenantContextHolder
 * for MyBatis-Flex auto-tenant filtering.
 * 
 * FIX #2: Added security validation to verify header tenantId matches user session.
 */
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Priority 1: explicit header
        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                Long tenantIdFromHeader = Long.parseLong(tenantHeader);
                
                // FIX #2: Security validation - verify header tenantId matches user session
                if (StpUtil.isLogin()) {
                    SaSession session = StpUtil.getSession();
                    Long userTenantId = (Long) session.get("tenantId");
                    if (userTenantId != null && !userTenantId.equals(tenantIdFromHeader)) {
                        log.warn("Tenant ID mismatch: header={}, session={}, userId={}", 
                            tenantIdFromHeader, userTenantId, StpUtil.getLoginIdAsLong());
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"Tenant ID mismatch with user session\"}");
                        return false;
                    }
                }
                
                TenantContextHolder.setTenantId(tenantIdFromHeader);
                return true;
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-Id header: {}", tenantHeader);
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

        // Auth endpoints and public endpoints don't require tenant
        String uri = request.getRequestURI();
        if (uri.startsWith("/auth/")) {
            return true;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
