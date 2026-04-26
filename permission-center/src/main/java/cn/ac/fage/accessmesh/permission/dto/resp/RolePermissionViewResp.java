package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Permission View: role's permissions.
 */
public record RolePermissionViewResp(
    Long roleId,
    String roleName,
    Integer roleType,
    List<PermissionItem> permissions
) {
    public record PermissionItem(
        Long id,
        Long resourceEntityId,
        String resourceCode,
        String resourceName,
        Integer resourceType,
        Long operationPermissionId,
        String operationCode,
        String operationName,
        Long dependOn,
        Long conditionId,
        Boolean canManage,
        String grantSource
    ) {}
}
