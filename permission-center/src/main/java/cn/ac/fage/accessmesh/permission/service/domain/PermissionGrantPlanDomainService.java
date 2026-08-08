package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import java.util.List;
import java.util.Set;

/**
 * 聚合授权计划的唯一预检与执行入口。
 */
public interface PermissionGrantPlanDomainService {

    PreparedGrantPlan prevalidate(Long tenantId, Long operatorId, Long roleId, String domainCode,
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
