package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;

/** 不可变的原始授权事实；缓存载荷仅在读取边界转换，不携带展示派生字段。 */
public record GrantFact(Long permissionId, Long roleId, Integer resourceType, Long resourceEntityId,
                        Long grantedBits, Boolean scopeAll, Boolean canGrant, Long conditionId,
                        boolean hasCondition, Long dependOn, String grantSource) {

    /** 仅供准入投影使用；子行类别优先于 ALL/实例，不代表父运行时已经通过。 */
    public AdmissionCandidateKind admissionCandidateKind() {
        return dependOn != null ? AdmissionCandidateKind.CONTEXT_DEFERRED
            : Boolean.TRUE.equals(scopeAll) ? AdmissionCandidateKind.ALL : AdmissionCandidateKind.INSTANCE;
    }

    public enum AdmissionCandidateKind { ALL, INSTANCE, CONTEXT_DEFERRED }

    static GrantFact from(RolePermEntry entry) {
        return new GrantFact(entry.permissionId(), entry.roleId(), entry.resourceType(), entry.resourceEntityId(),
            entry.grantedBits(), entry.scopeAll(), entry.canGrant(), entry.conditionId(), entry.hasCondition(),
            entry.dependOn(), entry.grantSource());
    }
}
