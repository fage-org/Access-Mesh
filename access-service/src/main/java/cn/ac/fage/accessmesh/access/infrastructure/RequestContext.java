package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 可信请求上下文（T-ACCESS-004；T-ACCESS-013 增第五要素 delegatedClientId；
 * T-PERM-021 F1.d 增第六要素 requestId，2026-09-12）。
 * <p>
 * 单进程内唯一承载六要素的不可变值对象，由统一安全入口
 * （{@link RequestContextInterceptor}）绑定到 {@link AccessRequestContext}：
 * </p>
 * <ul>
 *   <li>{@code tenantId}：已验证的租户 ID（会话租户 / 签名绑定头 / 凭证绑定头 / 任务显式租户）</li>
 *   <li>{@code operatorId}：已验证的操作者 ID（USER 为登录用户或签名代理主体；SERVICE/TASK/ANONYMOUS 为 null）</li>
 *   <li>{@code callerType}：调用方类型（USER / SERVICE / TASK / ANONYMOUS）</li>
 *   <li>{@code serviceCode}：已验证的服务编码（仅 SERVICE；凭证通过后由 X-Service-Code 头绑定，防无凭证伪造）</li>
 *   <li>{@code delegatedClientId}：OAuth2 委托客户端标识（仅 OAuth2 JWT 认证分支非 null，T-ACCESS-013；
 *       callerType 仍为 USER——委托用户身份；审计/日志据此区分第三方委托调用与用户直调）</li>
 *   <li>{@code requestId}：请求 ID（X-Request-Id 头值，缺失时兜底生成 UUID——T-PERM-021 F1.d 定案）。
 *       兼作链路追踪 ID：日志 MDC 的 traceId 与网关响应的 traceId 字段均为本值，无第二套追踪体系。
 *       仅 HTTP 入口由拦截器绑定（经 withRequestId）；TASK/未指定时为 null，
 *       审计落库点（AuditDomainServiceImpl）对 null 合成 UUID 保证 request_id NOT NULL</li>
 * </ul>
 * <p>
 * 原始请求头不能未经验证直接成为身份：operatorId 只绑定于 Sa-Token 会话或
 * {@link SignatureVerifier} 验签通过的路径；serviceCode 只绑定于内部凭证通过的路径；
 * delegatedClientId 只绑定于 OAuth2 JWT 验签 + 开放路径门禁（scope/audience/客户端启用）通过的路径。
 * requestId 非身份要素（可观测性标记），取头值或兜底生成，超长截断 64 对齐列宽。
 * </p>
 *
 * @param tenantId          已验证租户 ID（可为 null）
 * @param operatorId        已验证操作者 ID（可为 null）
 * @param callerType        调用方类型（不可为 null）
 * @param serviceCode       已验证服务编码（仅 SERVICE 调用可为非 null）
 * @param delegatedClientId OAuth2 委托客户端标识（仅 OAuth2 JWT 分支可为非 null）
 * @param requestId         请求 ID（HTTP 入口恒非 null；TASK/工厂缺省为 null）
 */
public record RequestContext(Long tenantId, Long operatorId, CallerType callerType, String serviceCode,
                             String delegatedClientId, String requestId) {

    /**
     * 紧凑构造（评审 P3-2：callerType 为不可空契约，构造时校验防误用）。
     */
    public RequestContext {
        java.util.Objects.requireNonNull(callerType, "callerType must not be null");
    }

    /** 公开路径匿名上下文（/auth/** 公开子集 + /actuator/**，评审 P1-1 精确拆分）。 */
    public static RequestContext anonymous() {
        return new RequestContext(null, null, CallerType.ANONYMOUS, null, null, null);
    }

    /** 平台用户上下文（Sa-Token 会话权威或签名代理主体；非委托调用）。 */
    public static RequestContext user(Long tenantId, Long operatorId) {
        return new RequestContext(tenantId, operatorId, CallerType.USER, null, null, null);
    }

    /**
     * OAuth2 委托用户上下文（T-ACCESS-013：JWT 验签 + 开放路径门禁通过；
     * operatorId=JWT loginId、tenantId=JWT 载荷租户、delegatedClientId=JWT client_id）。
     */
    public static RequestContext delegatedUser(Long tenantId, Long operatorId, String delegatedClientId) {
        return new RequestContext(tenantId, operatorId, CallerType.USER, null, delegatedClientId, null);
    }

    /** 注册业务服务上下文（内部凭证通过；serviceCode 由 X-Service-Code 头绑定）。 */
    public static RequestContext service(Long tenantId, String serviceCode) {
        return new RequestContext(tenantId, null, CallerType.SERVICE, serviceCode, null, null);
    }

    /** 定时任务 / 内部调度上下文（仅租户，无操作者与服务身份）。 */
    public static RequestContext task(Long tenantId) {
        return new RequestContext(tenantId, null, CallerType.TASK, null, null, null);
    }

    /**
     * 替换租户 ID，保留其余字段（供 TenantContextHolder 兼容门面的
     * 本地租户切换语义——保存/还原模式）。
     */
    public RequestContext withTenantId(Long newTenantId) {
        return new RequestContext(newTenantId, operatorId, callerType, serviceCode, delegatedClientId, requestId);
    }

    /**
     * 绑定请求 ID，保留其余字段（仅统一安全入口 RequestContextInterceptor 调用；
     * T-PERM-021 F1.d：审计两表 request_id 的同请求关联取值来源）。
     */
    public RequestContext withRequestId(String newRequestId) {
        return new RequestContext(tenantId, operatorId, callerType, serviceCode, delegatedClientId, newRequestId);
    }
}
