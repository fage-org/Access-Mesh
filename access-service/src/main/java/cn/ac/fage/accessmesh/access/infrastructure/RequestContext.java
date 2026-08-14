package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 可信请求上下文（T-ACCESS-004）。
 * <p>
 * 单进程内唯一承载四要素的不可变值对象，由统一安全入口
 * （{@link RequestContextInterceptor}）绑定到 {@link AccessRequestContext}：
 * </p>
 * <ul>
 *   <li>{@code tenantId}：已验证的租户 ID（会话租户 / 签名绑定头 / 凭证绑定头 / 任务显式租户）</li>
 *   <li>{@code operatorId}：已验证的操作者 ID（USER 为登录用户或签名代理主体；SERVICE/TASK/ANONYMOUS 为 null）</li>
 *   <li>{@code callerType}：调用方类型（USER / SERVICE / TASK / ANONYMOUS）</li>
 *   <li>{@code serviceCode}：已验证的服务编码（仅 SERVICE；凭证通过后由 X-Service-Code 头绑定，防无凭证伪造）</li>
 * </ul>
 * <p>
 * 原始请求头不能未经验证直接成为身份：operatorId 只绑定于 Sa-Token 会话或
 * {@link SignatureVerifier} 验签通过的路径；serviceCode 只绑定于内部凭证通过的路径。
 * </p>
 *
 * @param tenantId    已验证租户 ID（可为 null）
 * @param operatorId  已验证操作者 ID（可为 null）
 * @param callerType  调用方类型（不可为 null）
 * @param serviceCode 已验证服务编码（仅 SERVICE 调用可为非 null）
 */
public record RequestContext(Long tenantId, Long operatorId, CallerType callerType, String serviceCode) {

    /**
     * 紧凑构造（评审 P3-2：callerType 为不可空契约，构造时校验防误用）。
     */
    public RequestContext {
        java.util.Objects.requireNonNull(callerType, "callerType must not be null");
    }

    /** 公开路径匿名上下文（/auth/** 公开子集 + /actuator/**，评审 P1-1 精确拆分）。 */
    public static RequestContext anonymous() {
        return new RequestContext(null, null, CallerType.ANONYMOUS, null);
    }

    /** 平台用户上下文（Sa-Token 会话权威或签名代理主体）。 */
    public static RequestContext user(Long tenantId, Long operatorId) {
        return new RequestContext(tenantId, operatorId, CallerType.USER, null);
    }

    /** 注册业务服务上下文（内部凭证通过；serviceCode 由 X-Service-Code 头绑定）。 */
    public static RequestContext service(Long tenantId, String serviceCode) {
        return new RequestContext(tenantId, null, CallerType.SERVICE, serviceCode);
    }

    /** 定时任务 / 内部调度上下文（仅租户，无操作者与服务身份）。 */
    public static RequestContext task(Long tenantId) {
        return new RequestContext(tenantId, null, CallerType.TASK, null);
    }

    /**
     * 替换租户 ID，保留其余字段（供 TenantContextHolder 兼容门面的
     * 本地租户切换语义——SyncTaskScheduler 的保存/还原模式）。
     */
    public RequestContext withTenantId(Long newTenantId) {
        return new RequestContext(newTenantId, operatorId, callerType, serviceCode);
    }
}
