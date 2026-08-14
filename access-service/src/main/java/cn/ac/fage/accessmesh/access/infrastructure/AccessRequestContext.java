package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 唯一可信请求上下文持有者（T-ACCESS-004）。
 * <p>
 * 使用 ThreadLocal 承载当前执行线程的 {@link RequestContext}（tenantId / operatorId /
 * callerType / verifiedServiceCode 四要素）。只有统一安全入口
 * （{@link RequestContextInterceptor}）可以绑定；业务代码只能读取。
 * </p>
 * <p>
 * 生命周期：请求拦截器在身份验证成功后 bind，在 afterCompletion 清理；
 * 定时任务通过 {@link #bind(RequestContext)} + finally 清理建立有界作用域；
 * 异步任务显式传递上下文快照（{@link #snapshot()} / {@link #restore(RequestContext)}），
 * 禁止盲目继承 ThreadLocal。
 * </p>
 */
public final class AccessRequestContext {

    private static final ThreadLocal<RequestContext> CTX = new ThreadLocal<>();

    private AccessRequestContext() {
    }

    /**
     * 绑定完整上下文（仅统一安全入口 / 任务作用域调用）。
     *
     * @param context 待绑定上下文；null 等价于 clear
     */
    public static void bind(RequestContext context) {
        if (context == null) {
            CTX.remove();
        } else {
            CTX.set(context);
        }
    }

    /**
     * 获取当前上下文；未绑定时返回 null（调用方按无身份处理）。
     */
    public static RequestContext get() {
        return CTX.get();
    }

    /**
     * 清理当前线程上下文（请求 afterCompletion / 任务 finally）。
     */
    public static void clear() {
        CTX.remove();
    }

    /** 已验证租户 ID；未绑定或匿名时返回 null。 */
    public static Long getTenantId() {
        RequestContext ctx = CTX.get();
        return ctx == null ? null : ctx.tenantId();
    }

    /** 已验证操作者 ID；SERVICE/TASK/ANONYMOUS 或未绑定时返回 null。 */
    public static Long getOperatorId() {
        RequestContext ctx = CTX.get();
        return ctx == null ? null : ctx.operatorId();
    }

    /** 调用方类型；未绑定时返回 null。 */
    public static CallerType getCallerType() {
        RequestContext ctx = CTX.get();
        return ctx == null ? null : ctx.callerType();
    }

    /** 已验证服务编码；非 SERVICE 调用或未绑定时返回 null。 */
    public static String getServiceCode() {
        RequestContext ctx = CTX.get();
        return ctx == null ? null : ctx.serviceCode();
    }

    /**
     * 当前上下文快照（异步 / 嵌套任务显式传递用）。
     *
     * @return 当前上下文；未绑定时返回 null
     */
    public static RequestContext snapshot() {
        return CTX.get();
    }

    /**
     * 恢复快照（null 等价于 clear）。
     */
    public static void restore(RequestContext snapshot) {
        bind(snapshot);
    }
}
