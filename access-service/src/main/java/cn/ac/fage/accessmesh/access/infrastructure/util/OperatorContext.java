package cn.ac.fage.accessmesh.access.infrastructure.util;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;

/**
 * 操作者上下文工具类（T-ACCESS-004 重构：不再直接读 HTTP 请求头）。
 * <p>
 * 操作者身份由统一安全入口（RequestContextInterceptor）在验证后绑定到
 * {@link AccessRequestContext}：Sa-Token 会话（admin 域）或 HMAC 签名验证通过的
 * X-User-Id 代理主体（/api/access/** 域）。本类只读上下文，杜绝绕过验证
 * 直接读取未验证请求头的路径（修复 G1——内部凭证持有者无法再伪造 X-User-Id 冒充操作者）。
 * </p>
 */
public final class OperatorContext {

    private OperatorContext() {}

    /**
     * 获取当前操作者的用户ID。
     * <p>
     * 从可信请求上下文读取；未绑定操作者（SERVICE/TASK/匿名调用）时拒绝访问
     * （fail-closed，与"内部凭证不隐式获得 /api/access/** 全权限"验收一致）。
     * </p>
     *
     * @return 操作者用户ID
     * @throws SecurityException 如果当前上下文未绑定操作者
     */
    public static Long getOperatorId() {
        Long operatorId = AccessRequestContext.getOperatorId();
        if (operatorId == null) {
            throw new SecurityException("无法确定操作者身份 - 请求上下文未绑定操作者");
        }
        return operatorId;
    }

    /**
     * 获取当前请求 ID（T-PERM-021 F1.d：审计 request_id 关联的统一取值入口）。
     * <p>
     * HTTP 入口由 RequestContextInterceptor 绑定（X-Request-Id 头值或兜底 UUID，
     * 兼作链路追踪 ID）；未绑定上下文（TASK/bootstrap/异步无快照）返回 null，
     * 由审计落库点（AuditDomainServiceImpl）合成 UUID——本方法 null 安全、不抛异常。
     * </p>
     */
    public static String getRequestId() {
        return AccessRequestContext.getRequestId();
    }
}
