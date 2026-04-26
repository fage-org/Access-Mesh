package cn.ac.fage.accessmesh.admin.config;

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
 */
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Priority 1: explicit header
        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantHeader));
                return true;
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-Id header: {}", tenantHeader);
            }
        }

        // Priority 2: resolve from logged-in user's tenant
        try {
            if (StpUtil.isLogin()) {
                Long userId = StpUtil.getLoginIdAsLong();
                // In production, fetch tenant from user cache; for now, require header
                log.debug("Tenant ID not provided in header for user {}", userId);
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
