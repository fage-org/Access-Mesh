package cn.ac.fage.accessmesh.permission.vo;

import java.util.List;

/**
 * 角色权限快照：缓存中使用
 */
public record RolePermSnapshot(
    Long tenantId,
    Long roleId,
    long version,
    List<RolePermEntry> entries
) {
    public record RolePermEntry(
        Long permissionId,
        Long roleId,
        Long resourceEntityId,
        String resourceCode,
        Integer resourceType,
        Long operationPermissionId,
        String operationCode,
        Long effectiveBits,
        String grantSource,
        Boolean canGrant,
        Long conditionId,
        boolean hasCondition,
        Long dependOn
    ) {}
}
