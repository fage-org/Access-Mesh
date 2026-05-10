package cn.ac.fage.accessmesh.permission.config;

/**
 * 租户上下文持有者
 * <p>
 * 使用ThreadLocal存储当前执行线程的租户ID。
 * 由PermTenantInterceptor在每个请求开始时设置，
 * 在afterCompletion中清理以防止线程池环境下的租户ID泄漏。
 * </p>
 */
public class TenantContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 设置当前租户ID
     * <p>
     * 将租户ID存储到ThreadLocal中，供当前线程使用。
     * </p>
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前租户ID
     * <p>
     * 从ThreadLocal中获取当前线程关联的租户ID。
     * </p>
     *
     * @return 当前租户ID，未设置时返回null
     */
    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 清理租户上下文
     * <p>
     * 移除ThreadLocal中的租户ID，防止线程池环境下的租户ID泄漏。
     * 应在请求完成后调用。
     * </p>
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}