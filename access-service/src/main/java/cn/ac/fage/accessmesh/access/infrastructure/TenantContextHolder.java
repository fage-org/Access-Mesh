package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 租户上下文持有者（兼容门面，T-ACCESS-004）。
 * <p>
 * 委托唯一可信上下文 {@link AccessRequestContext} 的租户字段：admin 与 permission 域
 * 共用的旧引用点（约 60 处）无需迁移即可获得统一上下文语义。语义：
 * </p>
 * <ul>
 *   <li>setTenantId：当前线程无上下文时建立仅租户的 TASK 作用域（本地跨域 / 任务）；
 *       已有上下文（请求线程）时仅替换租户、保留其余身份字段。</li>
 *   <li>getTenantId / clear：委托新上下文。</li>
 * </ul>
 * <p>
 * 新代码应直接使用 {@link AccessRequestContext}；本门面在 T-ACCESS-012 收口时扁平化。
 * </p>
 */
public class TenantContextHolder {

    private TenantContextHolder() {
    }

    /**
     * 设置当前线程租户 ID（兼容门面）。
     * <p>
     * 无上下文（调度线程 / 本地跨域调用）→ 建立仅租户作用域；
     * 已有上下文（请求线程内的租户切换）→ 替换租户保留身份。
     * </p>
     *
     * @param tenantId 租户 ID（null 等价于清空租户）
     */
    public static void setTenantId(Long tenantId) {
        RequestContext current = AccessRequestContext.get();
        if (current == null) {
            if (tenantId == null) {
                AccessRequestContext.clear();
            } else {
                AccessRequestContext.bind(RequestContext.task(tenantId));
            }
        } else {
            AccessRequestContext.bind(current.withTenantId(tenantId));
        }
    }

    /**
     * 获取当前线程租户 ID；未绑定或匿名时返回 null。
     */
    public static Long getTenantId() {
        return AccessRequestContext.getTenantId();
    }

    /**
     * 清理当前线程上下文（请求 afterCompletion / 任务 finally）。
     */
    public static void clear() {
        AccessRequestContext.clear();
    }
}
