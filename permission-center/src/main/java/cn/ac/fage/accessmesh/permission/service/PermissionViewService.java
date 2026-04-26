package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;

/**
 * Permission View — read-only visibility APIs for user/resource/role permissions.
 */
public interface PermissionViewService {

    /**
     * View a user's effective permissions (grouped by resource).
     */
    UserPermissionViewResp getUserPermissions(Long tenantId, Long userId);

    /**
     * View which roles have permissions on a resource.
     */
    ResourcePermissionViewResp getResourcePermissions(Long tenantId, Long resourceEntityId);

    /**
     * View a role's permissions (flat list, optionally expanded to show sub-permissions).
     */
    RolePermissionViewResp getRolePermissions(Long tenantId, Long roleId, boolean expandSub);
}
