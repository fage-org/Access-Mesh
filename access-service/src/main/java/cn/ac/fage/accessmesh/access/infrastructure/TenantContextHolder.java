package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 租户上下文持有者
 * <p>
 * 使用 ThreadLocal 存储当前执行线程的租户 ID。
 * 由统一安全入口在每个请求开始时设置，在 afterCompletion 中清理以防止线程池环境下的租户 ID 泄漏。
 * admin 与 permission 域共用此唯一实例。
 * </p>
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
