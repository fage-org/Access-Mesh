package cn.ac.fage.accessmesh.example.perm;

/**
 * 业务最终检查调用的租户上下文（T-ACCESS-061）。
 * <p>
 * SDK 约定「X-Tenant-Id 由各调用方业务侧拦截器从 ThreadLocal 注入」——本类即该
 * ThreadLocal 的载体：{@link BusinessPermChecker} 在每次 Feign 调用前以显式入参
 * （已验签请求头的租户值）绑定、调用后清理，{@link FeignTenantHeaderInterceptor}
 * 读取并注入请求头。显式 set/clear（而非 servlet 过滤器自动捕获）是为覆盖异步线程：
 * 导出作业的执行时点重查在调度线程上发起，servlet 上下文不可用。
 * </p>
 */
public final class PermCallContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private PermCallContext() {
    }

    /** 绑定本次检查调用的租户 ID（调用方保证来自可信认证链，非请求体）。 */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    /** 当前线程绑定的租户 ID；未绑定为 null（拦截器跳过注入，由服务端拒绝）。 */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /** 调用结束必须清理，防止线程复用串租户。 */
    public static void clear() {
        TENANT_ID.remove();
    }
}
