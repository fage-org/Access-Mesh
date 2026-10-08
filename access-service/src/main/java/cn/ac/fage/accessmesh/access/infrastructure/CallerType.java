package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 调用方类型（T-ACCESS-004 可信请求上下文身份要素之一；上下文现为六要素，见 {@link RequestContext}）。
 * <p>
 * 由统一安全入口（{@link RequestContextInterceptor}）按认证结果绑定，
 * 业务代码只读不写。语义：
 * </p>
 * <ul>
 *   <li>{@link #PLATFORM}：独立平台运营账号，无所属租户</li>
 *   <li>{@link #USER}：租户用户（原生 Sa-Token 会话，或 Gateway/业务服务签名注入的代理主体）</li>
 *   <li>{@link #SERVICE}：注册业务服务（内部凭证 X-Internal-Secret 验证通过，含 perm-sdk 同步调用）</li>
 *   <li>{@link #TASK}：定时任务 / 内部调度（显式建立的有界租户作用域）</li>
 *   <li>{@link #ANONYMOUS}：公开路径（/auth/** 公开子集：验证码/登录/令牌/撤销/登出；/actuator/** 已随 T-PERM-094 移独立管理端口，不经主端口拦截链），无身份</li>
 * </ul>
 */
public enum CallerType {
    /** 独立平台运营账号，无所属租户，不能作为租户用户进入业务门禁。 */
    PLATFORM,
    USER,
    SERVICE,
    TASK,
    ANONYMOUS
}
