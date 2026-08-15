package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;

/**
 * 操作者权限域投影主体解析工具。
 * <p>
 * 登录会话 / 签名代理主体持有的操作者 ID 是 admin 域 {@code sys_user.id}
 * （external_id）；权限引擎按 {@code abstract_user.id} 匹配
 * {@code user_role.abstract_user_id}。因此所有 engine 门禁（hasPermission /
 * validateBatch / getDeniedIds）以及任何与投影 ID 空间比较的自查逻辑，
 * 必须先经 {@link #requireSubjectId} 把操作者 ID 转换为投影主体 ID。
 * </p>
 * <p>
 * 转换失败（操作者主体在权限投影中不存在）时 fail-closed 抛
 * {@link SecurityException}，语义同 {@code AdminPermissionValidatorImpl}。
 * </p>
 */
public final class OperatorSubjectResolver {

    private OperatorSubjectResolver() {}

    /**
     * 解析操作者权限域投影主体 ID（{@code abstract_user.id}）。
     *
     * @param tenantId 租户 ID
     * @param operatorId 操作者 sys_user.id（登录会话 / 签名代理主体 / 显式参数）
     * @param engine 权限查询引擎（内部完成主体投影解析）
     * @return 抽象用户主体 ID
     * @throws SecurityException 操作者主体在投影中不存在（fail-closed）
     */
    public static Long requireSubjectId(Long tenantId, Long operatorId, PermQueryEngine engine) {
        Long subjectId = engine.resolveOperatorSubjectId(tenantId, operatorId);
        if (subjectId == null) {
            throw new SecurityException("权限校验失败: 操作者主体不存在");
        }
        return subjectId;
    }
}
