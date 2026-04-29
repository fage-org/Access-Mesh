package cn.ac.fage.accessmesh.permission.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads X-Tenant-Id from the request header and stores it in TenantContextHolder.
 * Clears the context after the request completes to prevent thread-pool leakage.
 */
public class PermTenantInterceptor implements HandlerInterceptor {

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantIdStr = request.getHeader(HEADER_TENANT_ID);
        if (tenantIdStr != null && !tenantIdStr.isBlank()) {
            try {
                TenantContextHolder.setTenantId(Long.parseLong(tenantIdStr.trim()));
            } catch (NumberFormatException ignored) {
                // Invalid header value — tenant remains null; controller validation handles it
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
