package cn.ac.fage.accessmesh.permission.service;

/**
 * Service for authorization checks.
 */
public interface AuthorizationService {

    /**
     * Checks if the operator has MANAGE permission on the specified role.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleId the target role ID to manage
     * @return true if operator can manage the role
     */
    boolean canManageRole(Long tenantId, Long operatorId, Long targetRoleId);

    /**
     * Checks if the operator has a specific permission on a resource type.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param resourceTypeCode the resource type code (e.g., "USER", "ROLE", "RESOURCE")
     * @param operationCode the operation code (e.g., "MANAGE", "VIEW", "CREATE", "DELETE")
     * @return true if operator has the permission
     */
    boolean hasPermission(Long tenantId, Long operatorId, String resourceTypeCode, String operationCode);

    /**
     * Checks if the operator can manage a specific target user.
     * Combines self-modification check and MANAGE permission check.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserId the target user ID
     * @return true if operator can manage the target user
     */
    default boolean canManageUser(Long tenantId, Long operatorId, Long targetUserId) {
        // Self-modification is always allowed
        if (operatorId != null && operatorId.equals(targetUserId)) {
            return true;
        }
        // Otherwise requires MANAGE permission on USER resource
        return hasPermission(tenantId, operatorId, "USER", "MANAGE");
    }

    /**
     * Checks if the operator has a specific permission type on the target role.
     * This is a generic method for checking different permission types (VIEW, MANAGE, etc.)
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleId the target role ID
     * @param permissionType the permission type to check ("VIEW", "MANAGE", etc.)
     *                       When null, checks if operator has any permission on the role
     * @return true if operator has the specified permission on the role
     */
    boolean hasPermissionOnRole(Long tenantId, Long operatorId, Long targetRoleId, String permissionType);
}
