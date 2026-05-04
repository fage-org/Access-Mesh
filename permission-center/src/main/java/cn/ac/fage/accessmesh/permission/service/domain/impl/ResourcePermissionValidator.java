package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourcePermissionStrategy;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

/**
 * Universal resource permission validator.
 * Handles all resource types with operation type as enum parameter.
 *
 * <p>Usage:
 * <pre>
 * validator.validate(tenantId, operatorId, "SERVICE", "admin-service", OperationType.MANAGE_API_MAPPING);
 * validator.validate(tenantId, operatorId, "DOMAIN", domainId, OperationType.VIEW);
 * validator.hasPermission(tenantId, operatorId, "USER", userId, OperationType.MANAGE);
 * </pre>
 *
 * <p>Business rules (like isSystem cannot be deleted) should be handled in business layer.
 */
@Component
public class ResourcePermissionValidator {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionValidator.class);
    private static final String RESOURCE_TYPE_KEY = "resource_type";
    private static final String FALLBACK_TYPE_CODE = ResourceTypeCode.SYSTEM_CONFIG;

    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;

    // Strategy registry: resourceTypeCode -> strategy (for ID conversion only)
    private final Map<String, ResourcePermissionStrategy<?>> strategies = new HashMap<>();

    public ResourcePermissionValidator(
            TypeResolutionService typeResolutionService,
            UserRoleDomainService userRoleDomainService,
            ResourceEntityMapper resourceEntityMapper,
            OperationPermissionMapper operationPermissionMapper,
            RoleResourcePermissionMapper roleResourcePermissionMapper,
            List<ResourcePermissionStrategy<?>> strategyList) {
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;

        // Auto-register all strategies via Spring DI
        for (ResourcePermissionStrategy<?> strategy : strategyList) {
            strategies.put(strategy.getResourceTypeCode(), strategy);
            log.info("Registered permission strategy for resource type: {}", strategy.getResourceTypeCode());
        }
    }

    // ===== Core validation methods =====

    /**
     * Validate permission on a single resource.
     * Throws SecurityException if permission denied.
     */
    public void validate(Long tenantId, Long operatorId,
                         String resourceTypeCode, Object resourceId, OperationType operation) {
        if (!hasPermission(tenantId, operatorId, resourceTypeCode, resourceId, operation)) {
            throw new SecurityException(String.format(
                "Permission denied: cannot perform %s on %s:%s",
                operation.getCode(), resourceTypeCode, resourceId));
        }
    }

    /**
     * Validate permission on multiple resources (batch).
     * Throws SecurityException if permission denied on any resource.
     */
    public <ID> void validateBatch(Long tenantId, Long operatorId,
                                    String resourceTypeCode, Set<ID> resourceIds, OperationType operation) {
        Set<ID> deniedIds = getDeniedIds(tenantId, operatorId, resourceTypeCode, resourceIds, operation);
        if (!deniedIds.isEmpty()) {
            throw new SecurityException(String.format(
                "Permission denied: cannot perform %s on %s:%s",
                operation.getCode(), resourceTypeCode, deniedIds));
        }
    }

    /**
     * Check permission without throwing.
     *
     * @return true if permission granted, false otherwise
     */
    public boolean hasPermission(Long tenantId, Long operatorId,
                                  String resourceTypeCode, Object resourceId, OperationType operation) {
        // Parameter validation
        if (tenantId == null || operatorId == null ||
            resourceTypeCode == null || resourceId == null || operation == null) {
            throw new IllegalArgumentException("Invalid parameters for permission check");
        }

        // Resolve resource type
        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, RESOURCE_TYPE_KEY, resourceTypeCode);
        if (resourceType == null) {
            log.debug("Resource type {} not defined, using fallback", resourceTypeCode);
            return checkFallbackPermission(tenantId, operatorId, operation);
        }

        // Get operator's roles
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (roleIds.isEmpty()) {
            log.warn("Operator {} has no roles", operatorId);
            return false;
        }

        // Type-level operation (CREATE - no instance yet)
        if (operation.isTypeLevelOperation()) {
            return checkTypeLevelPermission(tenantId, roleIds, resourceType, operation);
        }

        // Resolve resource_entity.id via strategy or default
        Long resourceEntityId = resolveResourceEntityId(tenantId, resourceType, resourceId, resourceTypeCode);
        if (resourceEntityId == null) {
            log.debug("Resource entity not found for {}:{}, fallback to type-level", resourceTypeCode, resourceId);
            return checkTypeLevelPermission(tenantId, roleIds, resourceType, operation);
        }

        // Instance-level permission check
        if (checkInstancePermission(tenantId, roleIds, resourceEntityId, resourceType, operation)) {
            return true;
        }

        // Fallback to type-level permission
        return checkTypeLevelPermission(tenantId, roleIds, resourceType, operation);
    }

    /**
     * Get IDs that are denied permission (batch check, non-throwing).
     *
     * @return set of denied IDs, empty if all allowed
     */
    public <ID> Set<ID> getDeniedIds(Long tenantId, Long operatorId,
                                      String resourceTypeCode, Set<ID> resourceIds, OperationType operation) {
        if (tenantId == null || operatorId == null ||
            resourceTypeCode == null || resourceIds == null || resourceIds.isEmpty() || operation == null) {
            throw new IllegalArgumentException("Invalid parameters for batch permission check");
        }

        Set<ID> validIds = resourceIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Set.of();
        }

        Integer resourceType = typeResolutionService.resolveTypeValue(tenantId, RESOURCE_TYPE_KEY, resourceTypeCode);
        if (resourceType == null) {
            log.debug("Resource type {} not defined, using fallback", resourceTypeCode);
            boolean hasFallback = checkFallbackPermission(tenantId, operatorId, operation);
            return hasFallback ? Set.of() : validIds;
        }

        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (roleIds.isEmpty()) {
            log.warn("Operator {} has no roles", operatorId);
            return validIds;
        }

        // Type-level operation (CREATE)
        if (operation.isTypeLevelOperation()) {
            boolean hasTypePerm = checkTypeLevelPermission(tenantId, roleIds, resourceType, operation);
            return hasTypePerm ? Set.of() : validIds;
        }

        // Check type-level first - if granted, all are allowed
        if (checkTypeLevelPermission(tenantId, roleIds, resourceType, operation)) {
            log.debug("Type-level {} permission granted for operator {}", operation, operatorId);
            return Set.of();
        }

        // Batch resolve resource_entity.ids
        Map<ID, Long> idToResourceEntityId = batchResolveResourceEntityIds(
            tenantId, resourceType, validIds, resourceTypeCode);

        // Find operation permission
        OperationPermission opPerm = findOperationPermission(tenantId, resourceType, operation);
        if (opPerm == null) {
            log.warn("Operation {} not defined for type {}", operation, resourceTypeCode);
            return validIds;
        }

        // Batch check instance permissions
        Set<Long> resourceEntityIds = new HashSet<>(idToResourceEntityId.values());
        Set<Long> allowedResourceEntityIds = batchCheckInstancePermissions(
            tenantId, roleIds, resourceEntityIds, opPerm.getId());

        // Determine denied IDs
        Set<ID> deniedIds = new HashSet<>();
        for (ID id : validIds) {
            Long reId = idToResourceEntityId.get(id);
            if (reId == null || !allowedResourceEntityIds.contains(reId)) {
                deniedIds.add(id);
            }
        }

        return deniedIds;
    }

    // ===== Helper methods =====

    @SuppressWarnings("unchecked")
    private <ID> ResourcePermissionStrategy<ID> getStrategy(String resourceTypeCode) {
        return (ResourcePermissionStrategy<ID>) strategies.get(resourceTypeCode);
    }

    /**
     * Resolve resource_entity.id from business identifier.
     */
    private Long resolveResourceEntityId(Long tenantId, Integer resourceType,
                                          Object resourceId, String resourceTypeCode) {
        ResourcePermissionStrategy<Object> strategy = getStrategy(resourceTypeCode);

        if (strategy != null) {
            String code = strategy.toResourceEntityCode(tenantId, resourceId);
            if (code != null) {
                return findResourceEntityIdByCode(tenantId, resourceType, code);
            }
            return null;
        }

        // Default: resourceId IS resource_entity.id (for Long/Number)
        if (resourceId instanceof Long) {
            return (Long) resourceId;
        }
        if (resourceId instanceof Number) {
            return ((Number) resourceId).longValue();
        }
        // String resourceId - treat as code
        if (resourceId instanceof String) {
            return findResourceEntityIdByCode(tenantId, resourceType, (String) resourceId);
        }

        log.warn("Cannot resolve resource_entity.id for resourceId type: {}", resourceId.getClass());
        return null;
    }

    /**
     * Batch resolve resource_entity.ids.
     */
    private <ID> Map<ID, Long> batchResolveResourceEntityIds(Long tenantId, Integer resourceType,
                                                              Set<ID> ids, String resourceTypeCode) {
        ResourcePermissionStrategy<ID> strategy = getStrategy(resourceTypeCode);
        Map<ID, Long> result = new HashMap<>();

        if (strategy != null) {
            // Collect codes for batch lookup
            Set<String> codes = new HashSet<>();
            Map<String, ID> codeToId = new HashMap<>();

            for (ID id : ids) {
                String code = strategy.toResourceEntityCode(tenantId, id);
                if (code != null) {
                    codes.add(code);
                    codeToId.put(code, id);
                }
            }

            if (!codes.isEmpty()) {
                Map<String, Long> codeToEntityId = batchFindResourceEntityIdsByCode(tenantId, resourceType, codes);
                for (Map.Entry<String, ID> entry : codeToId.entrySet()) {
                    Long entityId = codeToEntityId.get(entry.getKey());
                    if (entityId != null) {
                        result.put(entry.getValue(), entityId);
                    }
                }
            }
        } else {
            // Default: id IS resource_entity.id
            for (ID id : ids) {
                if (id instanceof Long) {
                    result.put(id, (Long) id);
                } else if (id instanceof Number) {
                    result.put(id, ((Number) id).longValue());
                } else if (id instanceof String) {
                    Long entityId = findResourceEntityIdByCode(tenantId, resourceType, (String) id);
                    if (entityId != null) {
                        result.put(id, entityId);
                    }
                }
            }
        }

        return result;
    }

    private Long findResourceEntityIdByCode(Long tenantId, Integer resourceType, String code) {
        ResourceEntity entity = resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                .and(RESOURCE_ENTITY.CODE.eq(code))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        return entity != null ? entity.getId() : null;
    }

    private Map<String, Long> batchFindResourceEntityIdsByCode(Long tenantId, Integer resourceType, Set<String> codes) {
        List<ResourceEntity> entities = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                .and(RESOURCE_ENTITY.CODE.in(codes))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        Map<String, Long> result = new HashMap<>();
        for (ResourceEntity entity : entities) {
            result.put(entity.getCode(), entity.getId());
        }
        return result;
    }

    private OperationPermission findOperationPermission(Long tenantId, Integer resourceType, OperationType operation) {
        return operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(resourceType))
                .and(OPERATION_PERMISSION.CODE.eq(operation.getCode()))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );
    }

    // 类型级权限：scopeAll=true 表示用户对该资源类型的所有实例都有操作权限
    // 实例级权限：scopeAll=false + resourceEntityId=具体ID 表示用户只对特定实例有权限
    private boolean checkTypeLevelPermission(Long tenantId, Set<Long> roleIds,
                                              Integer resourceType, OperationType operation) {
        OperationPermission opPerm = findOperationPermission(tenantId, resourceType, operation);
        if (opPerm == null) {
            return false;
        }

        // 类型级权限定义：scopeAll=true 表示对该资源类型的所有实例都有权限
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(resourceType))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(opPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.SCOPE_ALL.eq(true))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return !perms.isEmpty();
    }

    private boolean checkInstancePermission(Long tenantId, Set<Long> roleIds,
                                             Long resourceEntityId, Integer resourceType, OperationType operation) {
        OperationPermission opPerm = findOperationPermission(tenantId, resourceType, operation);
        if (opPerm == null) {
            return false;
        }

        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(opPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return !perms.isEmpty();
    }

    private Set<Long> batchCheckInstancePermissions(Long tenantId, Set<Long> roleIds,
                                                     Set<Long> resourceEntityIds, Long operationPermissionId) {
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(resourceEntityIds))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPermissionId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        return perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private boolean checkFallbackPermission(Long tenantId, Long operatorId, OperationType operation) {
        Integer fallbackType = typeResolutionService.resolveTypeValue(tenantId, RESOURCE_TYPE_KEY, FALLBACK_TYPE_CODE);
        if (fallbackType == null) {
            log.warn("SYSTEM_CONFIG resource type not defined");
            return false;
        }

        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (roleIds.isEmpty()) {
            return false;
        }

        // Try specific operation first
        if (checkTypeLevelPermission(tenantId, roleIds, fallbackType, operation)) {
            return true;
        }

        // Fall back to MANAGE
        return checkTypeLevelPermission(tenantId, roleIds, fallbackType, OperationType.MANAGE);
    }
}