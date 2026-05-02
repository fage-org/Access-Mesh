package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionCheckBatchReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckBatchResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckResp;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for permission check operations.
 * Provides common validation patterns for batch permission checks.
 */
public final class PermissionCheckUtils {

    private PermissionCheckUtils() {
        // Utility class, no instances
    }

    /**
     * Check if operator can manage multiple users.
     * Handles self-modification exception: operator can always manage themselves.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageUsersWithSelfModification(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        PermissionCheckBatchResp resp = authService.checkPermissionsBatch(
            tenantId,
            new PermissionCheckBatchReq(operatorId, "USER", targetUserIds, Set.of("MANAGE"))
        );

        Set<Long> allowedIds = resp.getIdsWithPermission("MANAGE");
        Set<Long> deniedIds = resp.getIdsWithoutPermission("MANAGE");

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check if operator can manage multiple users WITHOUT self-modification exception.
     * Self-modification is NOT allowed - strict permission check.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageUsersStrict(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        // For strict check, we need to override the self-modification logic
        // Use RESOURCE type instead of USER type to avoid self-modification exception
        // But since USER permissions are global, we just check if operator has USER:MANAGE

        boolean hasManagePermission = authService.hasPermission(tenantId, operatorId, "USER", "MANAGE");

        if (hasManagePermission) {
            return new PermissionBatchResult(targetUserIds, Set.of());
        } else {
            return new PermissionBatchResult(Set.of(), targetUserIds);
        }
    }

    /**
     * Check if operator can manage multiple roles.
     * No self-modification exception applies.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageRoles(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        PermissionCheckBatchResp resp = authService.checkPermissionsBatch(
            tenantId,
            new PermissionCheckBatchReq(operatorId, "ROLE", targetRoleIds, Set.of("MANAGE"))
        );

        Set<Long> allowedIds = resp.getIdsWithPermission("MANAGE");
        Set<Long> deniedIds = resp.getIdsWithoutPermission("MANAGE");

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check if operator has VIEW permission on multiple roles.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanViewRoles(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        PermissionCheckBatchResp resp = authService.checkPermissionsBatch(
            tenantId,
            new PermissionCheckBatchReq(operatorId, "ROLE", targetRoleIds, Set.of("VIEW"))
        );

        Set<Long> allowedIds = resp.getIdsWithPermission("VIEW");
        Set<Long> deniedIds = resp.getIdsWithoutPermission("VIEW");

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check multiple operations on multiple resources.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetResourceIds the target resource IDs to check
     * @param operationCodes the operations to check
     * @return PermissionBatchResult containing allowed and denied IDs for EACH operation
     */
    public static Map<String, PermissionBatchResult> checkResourcePermissions(
            AuthorizationService authService, Long tenantId, Long operatorId,
            Set<Long> targetResourceIds, Set<String> operationCodes) {

        PermissionCheckBatchResp resp = authService.checkPermissionsBatch(
            tenantId,
            new PermissionCheckBatchReq(operatorId, "RESOURCE", targetResourceIds, operationCodes)
        );

        Map<String, PermissionBatchResult> resultsByOperation = new HashMap<>();
        for (String opCode : operationCodes) {
            Set<Long> allowedIds = resp.getIdsWithPermission(opCode);
            Set<Long> deniedIds = resp.getIdsWithoutPermission(opCode);
            resultsByOperation.put(opCode, new PermissionBatchResult(allowedIds, deniedIds));
        }

        return resultsByOperation;
    }

    /**
     * Validate and throw exception if any target is denied.
     * For users with self-modification exception.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to validate
     * @throws SecurityException if any target user is denied
     */
    public static void validateCanManageUsersOrThrow(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        PermissionBatchResult result = checkCanManageUsersWithSelfModification(
            authService, tenantId, operatorId, targetUserIds
        );

        if (!result.deniedIds().isEmpty()) {
            throw new SecurityException("No permission to manage users: " + result.deniedIds());
        }
    }

    /**
     * Validate and throw exception if any target role is denied for MANAGE.
     *
     * @param authService the authorization service
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to validate
     * @throws SecurityException if any target role is denied
     */
    public static void validateCanManageRolesOrThrow(
            AuthorizationService authService, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        PermissionBatchResult result = checkCanManageRoles(
            authService, tenantId, operatorId, targetRoleIds
        );

        if (!result.deniedIds().isEmpty()) {
            throw new SecurityException("No permission to manage roles: " + result.deniedIds());
        }
    }

    /**
     * Result of batch permission check.
     * Contains IDs that have permission (allowed) and IDs that don't (denied).
     */
    public record PermissionBatchResult(
        Set<Long> allowedIds,
        Set<Long> deniedIds
    ) {
        /**
         * Check if all targets have permission.
         */
        public boolean allAllowed() {
            return deniedIds.isEmpty();
        }

        /**
         * Check if any target has permission.
         */
        public boolean anyAllowed() {
            return !allowedIds.isEmpty();
        }

        /**
         * Check if any target is denied.
         */
        public boolean anyDenied() {
            return !deniedIds.isEmpty();
        }

        /**
         * Get denied IDs as a list (for error messages).
         */
        public List<Long> getDeniedList() {
            return new ArrayList<>(deniedIds);
        }
    }
}