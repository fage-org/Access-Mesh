package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Permission View: role's permissions.
 */
public record RolePermissionViewResp(
    Long roleId,
    String roleName,
    String roleTypeCode,
    List<PermissionItem> permissions
) {
    public record PermissionItem(
        Long id,
        Long resourceEntityId,
        String resourceCode,
        String resourceName,
        String resourceTypeCode,
        Long operationPermissionId,
        String operationCode,
        String operationName,
        Long dependOn,
        Long conditionId,
        Boolean canGrant,
        String grantSource
    ) {}
}
