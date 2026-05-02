package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.PermissionCheckBatchReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionCheckReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckBatchResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionCheckResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

/**
 * Implementation of authorization checks.
 *
 * Authorization logic:
 * 1. Query operator's roles via UserRole table (targetType = 'ROLE')
 * 2. Check if operator has the specified operation permission on the target resource
 *    via RoleResourcePermission table (matching operation_permission_id)
 *
 * Note: canGrant field is NOT used for authorization checks.
 *       canGrant indicates whether the permission can be granted to others by the user,
 *       it is only used in the permission granting flow.
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServiceImpl.class);

    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final OperationPermissionMapper operationPermissionMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleDomainService userRoleDomainService;

    public AuthorizationServiceImpl(RoleResourcePermissionMapper roleResourcePermissionMapper,
                                     TypeResolutionService typeResolutionService,
                                     OperationPermissionMapper operationPermissionMapper,
                                     ResourceEntityMapper resourceEntityMapper,
                                     AbstractRoleMapper abstractRoleMapper,
                                     UserRoleDomainService userRoleDomainService) {
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.operationPermissionMapper = operationPermissionMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleDomainService = userRoleDomainService;
    }

    @Override
    public boolean canManageRole(Long tenantId, Long operatorId, Long targetRoleId) {
        // Use hasPermissionOnRole with MANAGE operation
        return hasPermissionOnRole(tenantId, operatorId, targetRoleId, "MANAGE");
    }

    @Override
    public boolean canManageResource(Long tenantId, Long operatorId, Long resourceId) {
        // Delegate to checkPermissionOnResource with MANAGE operation
        return checkPermissionOnResource(tenantId, operatorId, resourceId, "MANAGE");
    }

    @Override
    public Map<Long, Boolean> canManageRoles(Long tenantId, Long operatorId, Set<Long> roleIds) {
        if (tenantId == null || operatorId == null || roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }

        // Get operator's active roles once (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            Map<Long, Boolean> results = new HashMap<>();
            for (Long roleId : roleIds) {
                results.put(roleId, false);
            }
            return results;
        }

        // Resolve ROLE resource type once
        Integer roleResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "ROLE");
        if (roleResourceType == null) {
            Map<Long, Boolean> results = new HashMap<>();
            for (Long roleId : roleIds) {
                results.put(roleId, false);
            }
            return results;
        }

        // Find MANAGE operation permission for ROLE type
        OperationPermission managePerm = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(roleResourceType))
                .and(OPERATION_PERMISSION.CODE.eq("MANAGE"))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (managePerm == null) {
            Map<Long, Boolean> results = new HashMap<>();
            for (Long roleId : roleIds) {
                results.put(roleId, false);
            }
            return results;
        }

        // Batch query all role_resource_permissions for target roles with MANAGE operation
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(managePerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Build result map - collect all role IDs that have permission
        Set<Long> permittedRoleIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, Boolean> results = new HashMap<>();
        for (Long roleId : roleIds) {
            results.put(roleId, permittedRoleIds.contains(roleId));
        }

        return results;
    }

    @Override
    public Map<Long, Boolean> canManageResources(Long tenantId, Long operatorId, Set<Long> resourceIds) {
        if (tenantId == null || operatorId == null || resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }

        // Get operator's active roles once (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            Map<Long, Boolean> results = new HashMap<>();
            for (Long resourceId : resourceIds) {
                results.put(resourceId, false);
            }
            return results;
        }

        // Batch query all role_resource_permissions for target resources
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(resourceIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, OperationPermission> opMap = opIds.isEmpty() ? Map.of() :
            operationPermissionMapper.selectListByQuery(
                QueryWrapper.create().where(OPERATION_PERMISSION.ID.in(opIds))
            ).stream().collect(Collectors.toMap(OperationPermission::getId, op -> op));

        // Group permissions by resource entity ID, checking if any has MANAGE operation
        Map<Long, Boolean> results = new HashMap<>();
        for (Long resourceId : resourceIds) {
            results.put(resourceId, false);  // default to false
        }

        for (RoleResourcePermission perm : perms) {
            OperationPermission op = opMap.get(perm.getOperationPermissionId());
            if (op != null && "MANAGE".equalsIgnoreCase(op.getCode())) {
                Long resId = perm.getResourceEntityId();
                if (resId != null) {
                    results.put(resId, true);
                }
            }
        }

        return results;
    }

    @Override
    public boolean hasPermission(Long tenantId, Long operatorId, String resourceTypeCode, String operationCode) {
        if (tenantId == null || operatorId == null || resourceTypeCode == null || operationCode == null) {
            return false;
        }

        // 1. Resolve resource type to internal value
        Integer resourceTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceTypeValue == null) {
            log.debug("Cannot resolve resource type: {}", resourceTypeCode);
            return false;
        }

        // 2. Find the operation permission for this resource type and operation code
        OperationPermission operationPermission = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceTypeValue))
                .and(OPERATION_PERMISSION.CODE.eq(operationCode))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (operationPermission == null) {
            log.debug("Cannot find operation permission: resourceType={}, operation={}", resourceTypeCode, operationCode);
            return false;
        }

        // 3. Get operator's active roles (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);

        if (operatorRoleIds.isEmpty()) {
            log.debug("Operator {} has no active roles in tenant {}", operatorId, tenantId);
            return false;
        }

        // 4. Check if operator has the specific operation permission on the resource type
        Long count = roleResourcePermissionMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(resourceTypeValue))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPermission.getId()))
        );

        boolean hasPermission = count != null && count > 0;

        if (!hasPermission) {
            log.debug("Operator {} lacks permission {}:{} in tenant {}", operatorId, resourceTypeCode, operationCode);
        }

        return hasPermission;
    }

    @Override
    public boolean hasPermissionOnRole(Long tenantId, Long operatorId, Long targetRoleId, String permissionType) {
        if (tenantId == null || operatorId == null || targetRoleId == null) {
            return false;
        }

        // Step 1: Get operator's active roles (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);

        if (operatorRoleIds.isEmpty()) {
            log.debug("Operator {} has no active roles in tenant {}", operatorId, tenantId);
            return false;
        }

        // Step 2: If permissionType is null, check if operator has any permission on the target role
        if (permissionType == null) {
            Long count = roleResourcePermissionMapper.selectCountByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                    .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(targetRoleId))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            return count != null && count > 0;
        }

        // Step 3: Resolve ROLE resource type
        Integer roleResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "ROLE");
        if (roleResourceType == null) {
            log.debug("Cannot resolve ROLE resource type");
            return false;
        }

        // Step 4: Find operation_permission for the specified permission type
        OperationPermission operationPerm = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(roleResourceType))
                .and(OPERATION_PERMISSION.CODE.eq(permissionType.toUpperCase()))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (operationPerm == null) {
            log.debug("Cannot find operation permission: ROLE/{}", permissionType);
            return false;
        }

        // Step 5: Check if operator has this specific operation permission on the target role
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(targetRoleId))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        return !perms.isEmpty();
    }

    @Override
    public PermissionCheckResp checkPermissions(Long tenantId, PermissionCheckReq req) {
        if (tenantId == null || req == null || req.operatorId() == null
            || req.targetType() == null || req.targetId() == null
            || req.operationCodes() == null || req.operationCodes().isEmpty()) {
            return new PermissionCheckResp(new HashMap<>());
        }

        Map<String, Boolean> results = new HashMap<>();

        for (String operationCode : req.operationCodes()) {
            boolean hasPermission = switch (req.targetType().toUpperCase()) {
                case "USER" -> {
                    if ("MANAGE".equalsIgnoreCase(operationCode)) {
                        yield canManageUser(tenantId, req.operatorId(), req.targetId());
                    } else {
                        yield hasPermission(tenantId, req.operatorId(), "USER", operationCode);
                    }
                }
                case "ROLE" -> hasPermissionOnRole(tenantId, req.operatorId(), req.targetId(), operationCode);
                case "RESOURCE" -> {
                    // For generic resource, need to resolve resource type from the resource entity
                    // This requires additional lookup - delegate to hasPermission with resolved type
                    // For now, use the operation directly on the resource entity
                    yield checkPermissionOnResource(tenantId, req.operatorId(), req.targetId(), operationCode);
                }
                default -> false;
            };
            results.put(operationCode, hasPermission);
        }

        return new PermissionCheckResp(results);
    }

    /**
     * Checks if operator has a specific operation permission on a resource entity.
     */
    private boolean checkPermissionOnResource(Long tenantId, Long operatorId, Long resourceEntityId, String operationCode) {
        if (tenantId == null || operatorId == null || resourceEntityId == null || operationCode == null) {
            return false;
        }

        // Get operator's active roles (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);

        if (operatorRoleIds.isEmpty()) {
            return false;
        }

        // Find any permission on this resource entity with the specified operation
        // First find the role_resource_permission records for this resource
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (perms.isEmpty()) {
            return false;
        }

        // Batch load operation permissions to avoid N+1 queries
        Set<Long> opIds = perms.stream()
            .map(RoleResourcePermission::getOperationPermissionId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Map<Long, OperationPermission> opMap = opIds.isEmpty() ? Map.of() :
            operationPermissionMapper.selectListByQuery(
                QueryWrapper.create().where(OPERATION_PERMISSION.ID.in(opIds))
            ).stream().collect(java.util.stream.Collectors.toMap(OperationPermission::getId, op -> op));

        // Check if any of these permissions match the operation code
        for (RoleResourcePermission perm : perms) {
            OperationPermission op = opMap.get(perm.getOperationPermissionId());
            if (op != null && operationCode.equalsIgnoreCase(op.getCode())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public PermissionCheckBatchResp checkPermissionsBatch(Long tenantId, PermissionCheckBatchReq req) {
        if (tenantId == null || req == null || req.operatorId() == null
            || req.targetType() == null || req.targetIds() == null || req.targetIds().isEmpty()
            || req.operationCodes() == null || req.operationCodes().isEmpty()) {
            return new PermissionCheckBatchResp(new HashMap<>());
        }

        Map<Long, PermissionCheckResp> results = new HashMap<>();
        String targetType = req.targetType().toUpperCase();

        // Optimize: Query operator's roles once (reuse for all targets)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, req.operatorId(), null);
        if (operatorRoleIds.isEmpty()) {
            // No roles means no permissions (except self-modification for USER)
            for (Long targetId : req.targetIds()) {
                Map<String, Boolean> targetResults = new HashMap<>();
                for (String opCode : req.operationCodes()) {
                    // Self-modification exception for USER type
                    if ("USER".equals(targetType) && "MANAGE".equalsIgnoreCase(opCode)
                        && req.operatorId().equals(targetId)) {
                        targetResults.put(opCode, true);
                    } else {
                        targetResults.put(opCode, false);
                    }
                }
                results.put(targetId, new PermissionCheckResp(targetResults));
            }
            return new PermissionCheckBatchResp(results);
        }

        // Batch query based on target type
        switch (targetType) {
            case "USER" -> checkPermissionsBatchForUser(tenantId, req, operatorRoleIds, results);
            case "ROLE" -> checkPermissionsBatchForRole(tenantId, req, operatorRoleIds, results);
            case "RESOURCE" -> checkPermissionsBatchForResource(tenantId, req, operatorRoleIds, results);
            default -> {
                for (Long targetId : req.targetIds()) {
                    results.put(targetId, new PermissionCheckResp(new HashMap<>()));
                }
            }
        }

        return new PermissionCheckBatchResp(results);
    }

    /**
     * Batch check for USER targets.
     * Key optimization: MANAGE permission is global (doesn't depend on targetUserId).
     */
    private void checkPermissionsBatchForUser(Long tenantId, PermissionCheckBatchReq req,
                                                Set<Long> operatorRoleIds,
                                                Map<Long, PermissionCheckResp> results) {
        // Resolve USER resource type once
        Integer userResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "USER");

        // Batch query operation permissions for USER type
        Map<String, Long> operationIdByCode = new HashMap<>();
        if (userResourceType != null) {
            List<OperationPermission> ops = operationPermissionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(userResourceType))
                    .and(OPERATION_PERMISSION.CODE.in(req.operationCodes()))
                    .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
            );
            for (OperationPermission op : ops) {
                operationIdByCode.put(op.getCode().toUpperCase(), op.getId());
            }
        }

        // Batch query all role_resource_permissions for USER resource type
        Set<Long> operationIds = new HashSet<>(operationIdByCode.values());
        Map<Long, Set<String>> permissionsByOpId = new HashMap<>();
        if (!operationIds.isEmpty() && userResourceType != null) {
            List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                    .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(userResourceType))
                    .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(operationIds))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            for (RoleResourcePermission perm : perms) {
                Long opId = perm.getOperationPermissionId();
                permissionsByOpId.computeIfAbsent(opId, k -> new HashSet<>());
            }
        }

        // For USER type, permission is global - same for all targets (except self-modification)
        Set<String> grantedOperations = new HashSet<>();
        for (Map.Entry<String, Long> entry : operationIdByCode.entrySet()) {
            if (permissionsByOpId.containsKey(entry.getValue())) {
                grantedOperations.add(entry.getKey());
            }
        }

        // Build results for each target
        for (Long targetUserId : req.targetIds()) {
            Map<String, Boolean> targetResults = new HashMap<>();
            for (String opCode : req.operationCodes()) {
                String normalizedOp = opCode.toUpperCase();
                // Self-modification exception: always allowed for MANAGE on self
                if ("MANAGE".equals(normalizedOp) && req.operatorId().equals(targetUserId)) {
                    targetResults.put(opCode, true);
                } else {
                    targetResults.put(opCode, grantedOperations.contains(normalizedOp));
                }
            }
            results.put(targetUserId, new PermissionCheckResp(targetResults));
        }
    }

    /**
     * Batch check for ROLE targets.
     * Permission is per-role (role_resource_permission.resource_entity_id = targetRoleId).
     */
    private void checkPermissionsBatchForRole(Long tenantId, PermissionCheckBatchReq req,
                                               Set<Long> operatorRoleIds,
                                               Map<Long, PermissionCheckResp> results) {
        // Resolve ROLE resource type once
        Integer roleResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "ROLE");

        // Batch query operation permissions for ROLE type
        Map<String, Long> operationIdByCode = new HashMap<>();
        if (roleResourceType != null) {
            List<OperationPermission> ops = operationPermissionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(roleResourceType))
                    .and(OPERATION_PERMISSION.CODE.in(req.operationCodes()))
                    .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
            );
            for (OperationPermission op : ops) {
                operationIdByCode.put(op.getCode().toUpperCase(), op.getId());
            }
        }

        Set<Long> operationIds = new HashSet<>(operationIdByCode.values());

        // Batch query all role_resource_permissions for target roles
        Map<Long, Set<Long>> permissionsByRole = new HashMap<>();
        if (!operationIds.isEmpty()) {
            List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                    .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(req.targetIds()))
                    .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(operationIds))
                    .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            );
            for (RoleResourcePermission perm : perms) {
                Long targetRoleId = perm.getResourceEntityId();
                Long opId = perm.getOperationPermissionId();
                permissionsByRole.computeIfAbsent(targetRoleId, k -> new HashSet<>()).add(opId);
            }
        }

        // Build results for each target
        for (Long targetRoleId : req.targetIds()) {
            Set<Long> grantedOpIds = permissionsByRole.getOrDefault(targetRoleId, Set.of());
            Map<String, Boolean> targetResults = new HashMap<>();
            for (String opCode : req.operationCodes()) {
                Long opId = operationIdByCode.get(opCode.toUpperCase());
                targetResults.put(opCode, opId != null && grantedOpIds.contains(opId));
            }
            results.put(targetRoleId, new PermissionCheckResp(targetResults));
        }
    }

    /**
     * Batch check for RESOURCE targets.
     * Permission is per-resource-entity.
     */
    private void checkPermissionsBatchForResource(Long tenantId, PermissionCheckBatchReq req,
                                                   Set<Long> operatorRoleIds,
                                                   Map<Long, PermissionCheckResp> results) {
        // Batch query all role_resource_permissions for target resources
        // Need to match by operation code, not operation permission ID
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(req.targetIds()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Group permissions by resource entity ID
        Map<Long, List<RoleResourcePermission>> permsByResource = perms.stream()
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));

        // Build results for each target
        for (Long resourceEntityId : req.targetIds()) {
            List<RoleResourcePermission> resourcePerms = permsByResource.getOrDefault(resourceEntityId, List.of());
            Map<String, Boolean> targetResults = new HashMap<>();

            // Batch load operation permissions to avoid N+1 queries
            Set<Long> opIds = resourcePerms.stream()
                .map(RoleResourcePermission::getOperationPermissionId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
            Map<Long, OperationPermission> opMap = opIds.isEmpty() ? Map.of() :
                operationPermissionMapper.selectListByQuery(
                    QueryWrapper.create().where(OPERATION_PERMISSION.ID.in(opIds))
                ).stream().collect(java.util.stream.Collectors.toMap(OperationPermission::getId, op -> op));

            // Resolve operation codes from permission IDs
            Set<String> grantedOps = new HashSet<>();
            for (RoleResourcePermission perm : resourcePerms) {
                OperationPermission op = opMap.get(perm.getOperationPermissionId());
                if (op != null) {
                    grantedOps.add(op.getCode().toUpperCase());
                }
            }

            for (String opCode : req.operationCodes()) {
                targetResults.put(opCode, grantedOps.contains(opCode.toUpperCase()));
            }
            results.put(resourceEntityId, new PermissionCheckResp(targetResults));
        }
    }

    @Override
    public boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                       String resourceCode, String operationCode, boolean scopeAll, String domainCode) {
        // Single permission check - delegate to batch method for consistency
        Set<GrantCheckKey> keys = Set.of(new GrantCheckKey(resourceTypeCode, resourceCode, operationCode, scopeAll));
        Map<String, GrantCheckResult> results = checkGrantPermissionsBatch(tenantId, operatorId, keys, domainCode);
        String key = buildPermissionKey(new GrantCheckKey(resourceTypeCode, resourceCode, operationCode, scopeAll));
        GrantCheckResult result = results.get(key);
        return result != null && result.canGrant();
    }

    @Override
    public Map<String, GrantCheckResult> checkGrantPermissionsBatch(Long tenantId, Long operatorId,
                                                                      Set<GrantCheckKey> permissions, String domainCode) {
        if (tenantId == null || operatorId == null || permissions == null || permissions.isEmpty()) {
            return Map.of();
        }

        // Get operator's active roles once
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            // No roles means no permissions
            Map<String, GrantCheckResult> results = new HashMap<>();
            for (GrantCheckKey key : permissions) {
                String permKey = buildPermissionKey(key);
                results.put(permKey, new GrantCheckResult(false, "NO_ROLE"));
            }
            return results;
        }

        // ===== Batch optimization: query all needed data once =====

        // 1. Collect all unique resource type codes and operation codes
        Set<String> resourceTypeCodes = permissions.stream()
            .map(GrantCheckKey::resourceTypeCode)
            .collect(Collectors.toSet());
        Set<String> operationCodes = permissions.stream()
            .map(GrantCheckKey::operationCode)
            .collect(Collectors.toSet());

        // 2. Batch resolve resource types
        Map<String, Integer> resourceTypeByCode = new HashMap<>();
        for (String code : resourceTypeCodes) {
            Integer value = typeResolutionService.resolveTypeValue(tenantId, "resource_type", code);
            if (value != null) {
                resourceTypeByCode.put(code.toUpperCase(), value);
            }
        }

        // 3. Batch query operation permissions for all resource types and operations
        Set<Integer> resourceTypeValues = new HashSet<>(resourceTypeByCode.values());
        List<OperationPermission> allOpPerms = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.in(resourceTypeValues))
                .and(OPERATION_PERMISSION.CODE.in(operationCodes.stream().map(String::toUpperCase).collect(Collectors.toSet())))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Map: resourceType + operationCode -> OperationPermission
        Map<String, OperationPermission> opPermByKey = new HashMap<>();
        Map<Long, OperationPermission> opPermById = new HashMap<>();
        for (OperationPermission op : allOpPerms) {
            String key = op.getResourceType() + ":" + op.getCode().toUpperCase();
            opPermByKey.put(key, op);
            opPermById.put(op.getId(), op);
        }

        // 4. Collect all resource codes (for non-scopeAll permissions) and batch resolve
        Set<String> resourceCodes = permissions.stream()
            .filter(k -> !k.scopeAll() && k.resourceCode() != null && !k.resourceCode().isBlank())
            .map(GrantCheckKey::resourceCode)
            .collect(Collectors.toSet());

        Map<String, Long> resourceEntityIdByCode = new HashMap<>();
        for (GrantCheckKey key : permissions) {
            if (!key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank()) {
                Long entityId = typeResolutionService.resolveResourceId(
                    tenantId, key.resourceTypeCode(), key.resourceCode(), "default", domainCode);
                if (entityId != null) {
                    resourceEntityIdByCode.put(key.resourceTypeCode().toUpperCase() + ":" + key.resourceCode(), entityId);
                }
            }
        }

        // 5. Batch query role_resource_permissions for all combinations
        // Query permissions for all resource types and operations operator might have
        Set<Long> opPermIds = allOpPerms.stream()
            .map(OperationPermission::getId)
            .collect(Collectors.toSet());

        List<RoleResourcePermission> allPerms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.in(resourceTypeValues))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(opPermIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // 6. Build lookup maps for fast access
        // Map: resourceType:operationCode:resourceEntityId -> List<RoleResourcePermission>
        // Map: resourceType:operationCode:scopeAll -> List<RoleResourcePermission>
        Map<String, List<RoleResourcePermission>> permsBySpecificResource = new HashMap<>();
        Map<String, List<RoleResourcePermission>> permsByScopeAll = new HashMap<>();

        for (RoleResourcePermission perm : allPerms) {
            OperationPermission op = opPermById.get(perm.getOperationPermissionId());
            if (op == null) continue;

            Integer resType = perm.getResourceType();
            String opCode = op.getCode().toUpperCase();
            String baseKey = resType + ":" + opCode;

            if (Boolean.TRUE.equals(perm.getScopeAll())) {
                permsByScopeAll.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(perm);
            } else if (perm.getResourceEntityId() != null) {
                permsBySpecificResource.computeIfAbsent(baseKey + ":" + perm.getResourceEntityId(), k -> new ArrayList<>()).add(perm);
            }
        }

        // ===== Evaluate each permission =====
        Map<String, GrantCheckResult> results = new HashMap<>();
        for (GrantCheckKey key : permissions) {
            GrantCheckResult result = evaluateGrantPermission(key, resourceTypeByCode, opPermByKey,
                resourceEntityIdByCode, permsBySpecificResource, permsByScopeAll);
            results.put(buildPermissionKey(key), result);
        }
        return results;
    }

    /**
     * Evaluate single grant permission using pre-loaded data.
     */
    private GrantCheckResult evaluateGrantPermission(GrantCheckKey key,
                                                      Map<String, Integer> resourceTypeByCode,
                                                      Map<String, OperationPermission> opPermByKey,
                                                      Map<String, Long> resourceEntityIdByCode,
                                                      Map<String, List<RoleResourcePermission>> permsBySpecificResource,
                                                      Map<String, List<RoleResourcePermission>> permsByScopeAll) {
        String resTypeCodeUpper = key.resourceTypeCode().toUpperCase();
        String opCodeUpper = key.operationCode().toUpperCase();

        // 1. Check resource type
        Integer resourceTypeValue = resourceTypeByCode.get(resTypeCodeUpper);
        if (resourceTypeValue == null) {
            return new GrantCheckResult(false, "INVALID_RESOURCE_TYPE");
        }

        // 2. Check operation permission
        String opPermKey = resourceTypeValue + ":" + opCodeUpper;
        OperationPermission opPerm = opPermByKey.get(opPermKey);
        if (opPerm == null) {
            return new GrantCheckResult(false, "INVALID_OPERATION");
        }

        // 3. For non-scopeAll, get resource entity ID
        Long resourceEntityId = null;
        if (!key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank()) {
            String resKey = resTypeCodeUpper + ":" + key.resourceCode();
            resourceEntityId = resourceEntityIdByCode.get(resKey);
            if (resourceEntityId == null) {
                return new GrantCheckResult(false, "RESOURCE_NOT_FOUND");
            }
        }

        // 4. Find operator's matching permissions
        String baseKey = resourceTypeValue + ":" + opCodeUpper;
        List<RoleResourcePermission> matchingPerms = new ArrayList<>();

        if (key.scopeAll()) {
            // For scopeAll, only look at scopeAll=true permissions
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(scopeAllPerms);
        } else {
            // For specific resource, check both specific and scopeAll
            String specificKey = baseKey + ":" + resourceEntityId;
            List<RoleResourcePermission> specificPerms = permsBySpecificResource.getOrDefault(specificKey, List.of());
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(specificPerms);
            matchingPerms.addAll(scopeAllPerms);
        }

        if (matchingPerms.isEmpty()) {
            return new GrantCheckResult(false, "NO_PERMISSION");
        }

        // 5. Check canGrant flag - at least one must have canGrant=true
        for (RoleResourcePermission perm : matchingPerms) {
            if (Boolean.TRUE.equals(perm.getCanGrant())) {
                // 6. Additional check: if granting scopeAll=true, the matching perm must have scopeAll=true
                if (key.scopeAll() && !Boolean.TRUE.equals(perm.getScopeAll())) {
                    continue;  // Cannot grant scopeAll from specific resource permission
                }
                return new GrantCheckResult(true, null);
            }
        }

        return new GrantCheckResult(false, "NO_GRANT_RIGHT");
    }

    /**
     * Build permission key for batch results.
     */
    private String buildPermissionKey(GrantCheckKey key) {
        return String.format("%s:%s:%s:%s",
            key.resourceTypeCode(),
            key.resourceCode() == null ? "*" : key.resourceCode(),
            key.operationCode(),
            key.scopeAll() ? "ALL" : "SPECIFIC");
    }
}