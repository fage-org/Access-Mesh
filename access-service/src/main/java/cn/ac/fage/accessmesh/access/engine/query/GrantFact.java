package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;

/** 不可变的原始授权事实；缓存载荷仅在读取边界转换，不携带展示派生字段。 */
public record GrantFact(Long permissionId, Long roleId, Integer resourceType, Long resourceEntityId,
                        Long grantedBits, Boolean scopeAll, Boolean canGrant, Long conditionId,
                        boolean hasCondition, Long dependOn, String grantSource) {

    static GrantFact from(RolePermEntry entry) {
        return new GrantFact(entry.permissionId(), entry.roleId(), entry.resourceType(), entry.resourceEntityId(),
            entry.grantedBits(), entry.scopeAll(), entry.canGrant(), entry.conditionId(), entry.hasCondition(),
            entry.dependOn(), entry.grantSource());
    }
}
