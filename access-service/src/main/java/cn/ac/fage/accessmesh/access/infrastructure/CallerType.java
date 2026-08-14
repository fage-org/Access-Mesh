package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 调用方类型（T-ACCESS-004 可信请求上下文四要素之一）。
 * <p>
 * 由统一安全入口（{@link RequestContextInterceptor}）按认证结果绑定，
 * 业务代码只读不写。语义：
 * </p>
 * <ul>
 *   <li>{@link #USER}：平台用户（Sa-Token 会话权威，或 Gateway/业务服务签名注入的代理主体）</li>
 *   <li>{@link #SERVICE}：注册业务服务（内部凭证 X-Internal-Secret 验证通过，含 perm-sdk 同步调用）</li>
 *   <li>{@link #TASK}：定时任务 / 内部调度（显式建立的有界租户作用域）</li>
 *   <li>{@link #ANONYMOUS}：公开路径（/auth/**、/actuator/**），无身份</li>
 * </ul>
 */
public enum CallerType {
    USER,
    SERVICE,
    TASK,
    ANONYMOUS
}
