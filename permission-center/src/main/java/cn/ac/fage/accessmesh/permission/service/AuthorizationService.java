package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionCheckBatchReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckBatchResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckResp;

import java.util.Map;
import java.util.Set;

/**
 * Service for authorization checks.
 *
 * Note: Methods named "canManage*" check for MANAGE operation permission,
 *       NOT the canGrant field. canGrant is only used in permission granting flow
 *       to determine if a permission can be delegated to others.
 */
public interface AuthorizationService {

    /**
     * Checks if the operator has MANAGE permission on the specified role.
     * This checks operation_permission.code = "MANAGE", not canGrant field.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleId the target role ID to manage
     * @return true if operator has MANAGE permission on the role
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

    /**
     * Checks if the operator has MANAGE permission on the specified resource entity.
     * This is an instance-level permission check.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param resourceId the resource entity ID to manage
     * @return true if operator has MANAGE permission on the resource
     */
    boolean canManageResource(Long tenantId, Long operatorId, Long resourceId);

    /**
     * Batch checks if the operator has MANAGE permission on multiple roles.
     * Returns a map of role ID to permission result.
     * Optimized to avoid N+1 queries.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param roleIds the set of role IDs to check
     * @return Map of role ID to Boolean (true if has MANAGE permission)
     */
    Map<Long, Boolean> canManageRoles(Long tenantId, Long operatorId, Set<Long> roleIds);

    /**
     * Batch checks if the operator has MANAGE permission on multiple resources.
     * Returns a map of resource ID to permission result.
     * Optimized to avoid N+1 queries.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param resourceIds the set of resource entity IDs to check
     * @return Map of resource ID to Boolean (true if has MANAGE permission)
     */
    Map<Long, Boolean> canManageResources(Long tenantId, Long operatorId, Set<Long> resourceIds);

    /**
     * Unified permission check method.
     * Checks if the operator has specified operations on a target.
     *
     * @param tenantId the tenant ID
     * @param req the permission check request containing operator, target, and operations
     * @return PermissionCheckResp with boolean result for each operation code
     */
    PermissionCheckResp checkPermissions(Long tenantId, PermissionCheckReq req);

    /**
     * Batch permission check method.
     * Checks if the operator has specified operations on multiple targets.
     * Returns results for each target ID.
     *
     * @param tenantId the tenant ID
     * @param req the batch permission check request
     * @return PermissionCheckBatchResp with results for each target ID
     */
    PermissionCheckBatchResp checkPermissionsBatch(Long tenantId, PermissionCheckBatchReq req);

    /**
     * Check if operator can grant a specific permission to others.
     * Operator must:
     * 1. Have the same permission (resourceType + resource/scopeAll + operation)
     * 2. Have canGrant=true on that permission
     *
     * This is used in the permission granting flow to validate delegation.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param resourceTypeCode the resource type code
     * @param resourceCode the resource code (null for scopeAll=true)
     * @param operationCode the operation code
     * @param scopeAll whether the permission is for all resources of this type
     * @param domainCode the domain code (optional, for resource resolution)
     * @return true if operator has the permission AND canGrant=true
     */
    boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                               String resourceCode, String operationCode, boolean scopeAll, String domainCode);

    /**
     * Batch check if operator can grant multiple permissions.
     * Returns detailed results for each permission key.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param permissions the permissions to check (resourceTypeCode, resourceCode, operationCode, scopeAll)
     * @param domainCode the domain code (optional)
     * @return Map of permission key to GrantCheckResult (canGrant, reason if not)
     */
    Map<String, GrantCheckResult> checkGrantPermissionsBatch(Long tenantId, Long operatorId,
                                                              Set<GrantCheckKey> permissions, String domainCode);

    /**
     * Key for grant permission check.
     */
    record GrantCheckKey(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean scopeAll
    ) {}

    /**
     * Result of grant permission check.
     */
    record GrantCheckResult(
        boolean canGrant,
        String reason  // null if canGrant=true, otherwise explains why not
    ) {}
}