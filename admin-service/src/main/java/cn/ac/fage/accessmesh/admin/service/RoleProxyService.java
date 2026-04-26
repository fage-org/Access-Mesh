package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;

public interface RoleProxyService {

    /**
     * Create a role in permission-center and associate it with an org.
     */
    Long createRoleForOrg(String roleName, Long orgId, Long tenantId);

    /**
     * Grant a resource permission to a role.
     * @param tenantId tenant ID
     * @param roleId role ID in permission-center
     * @param resourceId resource entity ID in permission-center
     * @param opCode operation code (e.g. "VIEW") — resolved to operationPermissionId internally
     */
    void grantResourceToRole(Long tenantId, Long roleId, Long resourceId, String opCode);

    /**
     * Revoke a resource permission from a role.
     */
    void revokeResourceFromRole(Long tenantId, Long roleId, Long resourceId);

    /**
     * Load user's roles and effective permissions from permission-center.
     */
    UserInfoResp loadUserRolesAndPermissions(Long userId);
}
