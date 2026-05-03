package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;

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
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageUsersWithSelfModification(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        // Filter out self (self-modification is always allowed)
        Set<Long> nonSelfUserIds = targetUserIds.stream()
            .filter(id -> !operatorId.equals(id))
            .collect(Collectors.toSet());

        if (nonSelfUserIds.isEmpty()) {
            // All are self, all allowed
            return new PermissionBatchResult(targetUserIds, Set.of());
        }

        // USER MANAGE is type-level, so check once
        Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "USER", nonSelfUserIds, OperationType.MANAGE);

        // Build result: self + allowed non-self = allowed, denied non-self = denied
        Set<Long> allowedIds = new HashSet<>();
        for (Long id : targetUserIds) {
            if (operatorId.equals(id) || !deniedIds.contains(id)) {
                allowedIds.add(id);
            }
        }

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check if operator can manage multiple users WITHOUT self-modification exception.
     * Self-modification is NOT allowed - strict permission check.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageUsersStrict(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        // USER MANAGE is type-level permission
        Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "USER", targetUserIds, OperationType.MANAGE);

        Set<Long> allowedIds = targetUserIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check if operator can manage multiple roles.
     * No self-modification exception applies.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanManageRoles(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "ROLE", targetRoleIds, OperationType.MANAGE);

        Set<Long> allowedIds = targetRoleIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check if operator has VIEW permission on multiple roles.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to check
     * @return PermissionBatchResult containing allowed and denied IDs
     */
    public static PermissionBatchResult checkCanViewRoles(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "ROLE", targetRoleIds, OperationType.VIEW);

        Set<Long> allowedIds = targetRoleIds.stream()
            .filter(id -> !deniedIds.contains(id))
            .collect(Collectors.toSet());

        return new PermissionBatchResult(allowedIds, deniedIds);
    }

    /**
     * Check multiple operations on multiple resources.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetResourceIds the target resource IDs to check
     * @param operationTypes the operations to check
     * @return PermissionBatchResult containing allowed and denied IDs for EACH operation
     */
    public static Map<OperationType, PermissionBatchResult> checkResourcePermissions(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId,
            Set<Long> targetResourceIds, Set<OperationType> operationTypes) {

        Map<OperationType, PermissionBatchResult> resultsByOperation = new HashMap<>();
        for (OperationType opType : operationTypes) {
            Set<Long> deniedIds = permissionValidator.getDeniedIds(tenantId, operatorId, "RESOURCE", targetResourceIds, opType);
            Set<Long> allowedIds = targetResourceIds.stream()
                .filter(id -> !deniedIds.contains(id))
                .collect(Collectors.toSet());
            resultsByOperation.put(opType, new PermissionBatchResult(allowedIds, deniedIds));
        }

        return resultsByOperation;
    }

    /**
     * Validate and throw exception if any target is denied.
     * For users with self-modification exception.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetUserIds the target user IDs to validate
     * @throws SecurityException if any target user is denied
     */
    public static void validateCanManageUsersOrThrow(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetUserIds) {

        PermissionBatchResult result = checkCanManageUsersWithSelfModification(
            permissionValidator, tenantId, operatorId, targetUserIds
        );

        if (!result.deniedIds().isEmpty()) {
            throw new SecurityException("No permission to manage users: " + result.deniedIds());
        }
    }

    /**
     * Validate and throw exception if any target role is denied for MANAGE.
     *
     * @param permissionValidator the permission validator
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param targetRoleIds the target role IDs to validate
     * @throws SecurityException if any target role is denied
     */
    public static void validateCanManageRolesOrThrow(
            ResourcePermissionValidator permissionValidator, Long tenantId, Long operatorId, Set<Long> targetRoleIds) {

        PermissionBatchResult result = checkCanManageRoles(
            permissionValidator, tenantId, operatorId, targetRoleIds
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