package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Permission View: which roles have permissions on a resource.
 */
public record ResourcePermissionViewResp(
    Long resourceEntityId,
    String resourceCode,
    String resourceName,
    List<RoleGrantInfo> roles
) {
    public record RoleGrantInfo(
        Long roleId,
        String roleName,
        Integer roleType,
        List<String> operations,
        String grantSource
    ) {}
}
