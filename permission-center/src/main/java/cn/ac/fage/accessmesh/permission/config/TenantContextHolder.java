package cn.ac.fage.accessmesh.permission.config;

/**
 * Holds the current tenant ID for the executing thread.
 * Set by PermTenantInterceptor at the beginning of each request,
 * cleared in afterCompletion to prevent thread-pool leakage.
 */
public class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
