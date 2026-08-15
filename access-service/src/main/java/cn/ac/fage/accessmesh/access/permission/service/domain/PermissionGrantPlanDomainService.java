package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;

import java.util.List;
import java.util.Set;

/**
 * 聚合授权计划的唯一预检与执行入口。
 * <p>主体参数（{@code subjectId}）为权限域投影主体（{@code abstract_user.id}），
 * 禁止直接传 admin 域 {@code sys_user.id}（先经 {@code OperatorSubjectResolver.requireSubjectId} 转换）。</p>
 */
public interface PermissionGrantPlanDomainService {

    /**
     * 预检授权计划（含 canGrant 委托验证）
     *
     * @param tenantId   租户ID
     * @param subjectId  权限域投影主体ID（abstract_user.id）
     * @param roleId     目标角色ID
     * @param domainCode 业务域编码，可选
     * @param plan       授权计划
     * @return 预检通过的计划
     */
    PreparedGrantPlan prevalidate(Long tenantId, Long subjectId, Long roleId, String domainCode,
                                  ApplyGrantPlanReq.GrantPlan plan);

    void apply(PreparedGrantPlan preparedPlan);

    record PreparedCreate(
        RoleResourcePermission permission,
        List<RoleResourcePermission> children
    ) {}

    record PreparedGrantPlan(
        Long tenantId,
        Long roleId,
        List<PreparedCreate> creates,
        List<RoleResourcePermission> updates,
        List<Long> removes,
        Set<PermissionGrantDomainService.GrantCheckKey> delegationKeys
    ) {}
}
