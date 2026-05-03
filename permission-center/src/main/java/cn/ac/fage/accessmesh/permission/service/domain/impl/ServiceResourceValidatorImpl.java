package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ServiceResourceValidator;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

/**
 * Implementation of service-level permission validation.
 * Checks if operator has MANAGE_API_MAPPING or SYNC_INTERFACE permission on SERVICE resources.
 */
@Component
public class ServiceResourceValidatorImpl implements ServiceResourceValidator {

    private static final Logger log = LoggerFactory.getLogger(ServiceResourceValidatorImpl.class);

    private final ResourceEntityDomainService resourceEntityDomainService;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final ResourceEntityMapper resourceEntityMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final OperationPermissionMapper operationPermissionMapper;

    public ServiceResourceValidatorImpl(ResourceEntityDomainService resourceEntityDomainService,
                                         TypeResolutionService typeResolutionService,
                                         UserRoleDomainService userRoleDomainService,
                                         ResourceEntityMapper resourceEntityMapper,
                                         RoleResourcePermissionMapper roleResourcePermissionMapper,
                                         OperationPermissionMapper operationPermissionMapper) {
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.resourceEntityMapper = resourceEntityMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    @Override
    public void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode) {
        validatePermission(tenantId, operatorId, serviceCode, "MANAGE_API_MAPPING");
    }

    @Override
    public void validateInterfaceSyncPermission(Long tenantId, Long operatorId, String serviceCode) {
        validatePermission(tenantId, operatorId, serviceCode, "SYNC_INTERFACE");
    }

    @Override
    public void validateApiMappingPermissionBatch(Long tenantId, Long operatorId, Set<String> serviceCodes) {
        if (tenantId == null || operatorId == null || serviceCodes == null || serviceCodes.isEmpty()) {
            throw new IllegalArgumentException("Invalid parameters for batch permission check: tenantId, operatorId, and serviceCodes must be non-null and non-empty");
        }

        // Filter out null/blank service codes
        Set<String> validServiceCodes = serviceCodes.stream()
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());

        if (validServiceCodes.isEmpty()) {
            return;
        }

        // 1. Resolve SERVICE resource type value
        Integer serviceResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "SERVICE");
        if (serviceResourceType == null) {
            log.warn("SERVICE resource type not defined in tenant {}", tenantId);
            throw new IllegalArgumentException("SERVICE resource type not defined in the system");
        }

        // 2. Batch query all SERVICE resources by codes (single query)
        List<ResourceEntity> serviceResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(serviceResourceType))
                .and(RESOURCE_ENTITY.CODE.in(validServiceCodes))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );

        // Build map: serviceCode -> resourceId
        Map<String, Long> serviceCodeToId = new HashMap<>();
        for (ResourceEntity entity : serviceResources) {
            serviceCodeToId.put(entity.getCode(), entity.getId());
        }

        // 3. Check for missing service resources
        Set<String> missingServices = new HashSet<>();
        for (String code : validServiceCodes) {
            if (!serviceCodeToId.containsKey(code)) {
                missingServices.add(code);
            }
        }

        if (!missingServices.isEmpty()) {
            log.warn("Service resources not registered: serviceCodes={}, tenantId={}", missingServices, tenantId);
            throw new IllegalArgumentException("Services not registered as resources: " + missingServices);
        }

        // 4. Get operator's active roles (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            log.warn("Permission denied: operator={} has no roles for MANAGE_API_MAPPING on services={}, tenantId={}",
                operatorId, validServiceCodes, tenantId);
            throw new SecurityException("Permission denied: operator has no active roles");
        }

        // 5. Find MANAGE_API_MAPPING operation permission for SERVICE type
        OperationPermission manageApiMappingPerm = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(serviceResourceType))
                .and(OPERATION_PERMISSION.CODE.eq("MANAGE_API_MAPPING"))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (manageApiMappingPerm == null) {
            log.warn("MANAGE_API_MAPPING operation permission not defined for SERVICE type in tenant {}", tenantId);
            throw new SecurityException("MANAGE_API_MAPPING permission not defined for SERVICE resources");
        }

        // 6. Batch query role_resource_permissions for all service resources
        Set<Long> serviceResourceIds = new HashSet<>(serviceCodeToId.values());
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(serviceResourceIds))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(manageApiMappingPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Build set of permitted resource IDs
        Set<Long> permittedResourceIds = perms.stream()
            .map(RoleResourcePermission::getResourceEntityId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

        // 7. Check each service for permission
        Set<String> deniedServices = new HashSet<>();
        for (String code : validServiceCodes) {
            Long resourceId = serviceCodeToId.get(code);
            if (resourceId != null && !permittedResourceIds.contains(resourceId)) {
                deniedServices.add(code);
            }
        }

        // 8. Handle permission denial
        if (!deniedServices.isEmpty()) {
            log.warn("Permission denied: operator={}, services={}, operation=MANAGE_API_MAPPING, tenantId={}",
                operatorId, deniedServices, tenantId);
            throw new SecurityException(
                "Permission denied: cannot perform MANAGE_API_MAPPING on services: " + deniedServices);
        }

        log.debug("Batch permission granted: operator={}, services={}, operation=MANAGE_API_MAPPING",
            operatorId, validServiceCodes);
    }

    /**
     * Common validation logic for service-level permissions.
     *
     * @param tenantId       the tenant ID
     * @param operatorId     the operator's user ID
     * @param serviceCode    the service code
     * @param operationCode  the operation code to check
     * @throws IllegalArgumentException if service resource not found
     * @throws SecurityException if permission denied
     */
    private void validatePermission(Long tenantId, Long operatorId, String serviceCode, String operationCode) {
        // 1. Parameter validation
        if (tenantId == null || operatorId == null || serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalArgumentException("Invalid parameters for permission check: tenantId, operatorId, and serviceCode must be non-null");
        }

        // 2. Resolve SERVICE resource type value
        Integer serviceResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "SERVICE");
        if (serviceResourceType == null) {
            log.warn("SERVICE resource type not defined in tenant {}", tenantId);
            throw new IllegalArgumentException("SERVICE resource type not defined in the system");
        }

        // 3. Find the service resource instance by type and code
        Long serviceResourceId = resourceEntityDomainService.findByTypeAndCode(tenantId, serviceResourceType, serviceCode);
        if (serviceResourceId == null) {
            log.warn("Service resource not registered: serviceCode={}, tenantId={}", serviceCode, tenantId);
            throw new IllegalArgumentException("Service not registered as resource: " + serviceCode);
        }

        // 4. Get operator's active roles (with L1/L2 cache)
        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            log.warn("Permission denied: operator={} has no roles for {} on service={}, tenantId={}",
                operatorId, operationCode, serviceCode, tenantId);
            throw new SecurityException("Permission denied: operator has no active roles");
        }

        // 5. Find operation permission for SERVICE type
        OperationPermission operationPerm = operationPermissionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.eq(serviceResourceType))
                .and(OPERATION_PERMISSION.CODE.eq(operationCode))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (operationPerm == null) {
            log.warn("{} operation permission not defined for SERVICE type in tenant {}", operationCode, tenantId);
            throw new SecurityException(operationCode + " permission not defined for SERVICE resources");
        }

        // 6. Check permission on the specific service resource
        List<RoleResourcePermission> perms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(serviceResourceId))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // 7. Handle permission denial
        if (perms.isEmpty()) {
            log.warn("Permission denied: operator={}, service={}, operation={}, tenantId={}",
                operatorId, serviceCode, operationCode, tenantId);
            throw new SecurityException(
                "Permission denied: cannot perform " + operationCode + " on service " + serviceCode);
        }

        log.debug("Permission granted: operator={}, service={}, operation={}", operatorId, serviceCode, operationCode);
    }
}

