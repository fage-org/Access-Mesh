package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;

public interface RoleProxyService {

    /**
     * Create a role in permission-center and associate it with an org.
     */
    Long createRoleForOrg(String roleName, Long orgId, Long tenantId);

    /**
     * Grant a menu resource permission to a role.
     * @param tenantId tenant ID
     * @param roleId role ID in permission-center
     * @param menuId menu entity ID (will be resolved to resourceId)
     * @param opCode operation code (e.g. "VIEW")
     */
    void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode);

    /**
     * Revoke a menu resource permission from a role.
     * @param tenantId tenant ID
     * @param roleId role ID in permission-center
     * @param menuId menu entity ID (will be resolved to resourceId)
     */
    void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId);

    /**
     * Load user's roles and effective permissions from permission-center.
     */
    UserInfoResp loadUserRolesAndPermissions(Long userId);
}
