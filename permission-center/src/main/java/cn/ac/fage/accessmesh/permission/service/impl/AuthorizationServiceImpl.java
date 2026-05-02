package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

/**
 * Implementation of authorization checks.
 *
 * Authorization logic:
 * 1. Query operator's roles via UserRole table (targetType = 'ROLE')
 * 2. Check if operator has canManage=true permission on the target role
 *    via RoleResourcePermission table (where resourceEntityId = targetRoleId)
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServiceImpl.class);

    private final UserRoleMapper userRoleMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;
    private final TypeResolutionService typeResolutionService;
    private final OperationPermissionMapper operationPermissionMapper;

    public AuthorizationServiceImpl(UserRoleMapper userRoleMapper,
                                     RoleResourcePermissionMapper roleResourcePermissionMapper,
                                     TypeResolutionService typeResolutionService,
                                     OperationPermissionMapper operationPermissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
        this.typeResolutionService = typeResolutionService;
        this.operationPermissionMapper = operationPermissionMapper;
    }

    @Override
    public boolean canManageRole(Long tenantId, Long operatorId, Long targetRoleId) {
        if (tenantId == null || operatorId == null || targetRoleId == null) {
            return false;
        }

        // Step 1: Get operator's active roles
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.eq(operatorId))
                .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
                .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
                .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
        );

        if (userRoles.isEmpty()) {
            log.debug("Operator {} has no active roles in tenant {}", operatorId, tenantId);
            return false;
        }

        Set<Long> operatorRoleIds = userRoles.stream()
            .map(UserRole::getTargetId)
            .collect(Collectors.toSet());

        // Step 2: Check if any of operator's roles has canManage=true on target role
        // Query RoleResourcePermission where abstractRoleId IN (operator's roles)
        // AND resourceEntityId = targetRoleId AND canManage = true
        List<RoleResourcePermission> permissions = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(targetRoleId))
                .and(ROLE_RESOURCE_PERMISSION.CAN_MANAGE.eq(true))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        boolean hasPermission = !permissions.isEmpty();

        if (!hasPermission) {
            log.debug("Operator {} lacks MANAGE permission for role {} in tenant {}",
                operatorId, targetRoleId, tenantId);
        }

        return hasPermission;
    }

    @Override
    public boolean hasPermission(Long tenantId, Long operatorId, String resourceTypeCode, String operationCode) {
        if (tenantId == null || operatorId == null || resourceTypeCode == null || operationCode == null) {
            return false;
        }

        // 1. Resolve resource type and operation type to internal values
        Integer resourceTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceTypeValue == null) {
            log.debug("Cannot resolve resource type: {}", resourceTypeCode);
            return false;
        }

        // 2. Find the operation permission ID for this resource type and operation code
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

        // 3. Query operator's active roles
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.eq(operatorId))
                .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
                .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
                .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
        );

        if (userRoles.isEmpty()) {
            log.debug("Operator {} has no active roles in tenant {}", operatorId, tenantId);
            return false;
        }

        Set<Long> operatorRoleIds = userRoles.stream()
            .map(UserRole::getTargetId)
            .collect(Collectors.toSet());

        // 4. Check if operator has permission on the resource type
        // Two scenarios:
        // a) If canManage=true, operator has full manage permission
        // b) If operationPermissionId matches, operator has specific operation permission
        Long count = roleResourcePermissionMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.eq(resourceTypeValue))
                .and(
                    ROLE_RESOURCE_PERMISSION.CAN_MANAGE.eq(true)
                    .or(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPermission.getId()))
                )
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

        // Step 1: Get operator's active roles
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.eq(operatorId))
                .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
                .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
                .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
        );

        if (userRoles.isEmpty()) {
            log.debug("Operator {} has no active roles in tenant {}", operatorId, tenantId);
            return false;
        }

        Set<Long> operatorRoleIds = userRoles.stream()
            .map(UserRole::getTargetId)
            .collect(Collectors.toSet());

        // Step 2: Check canManage first - MANAGE implies all other permissions
        List<RoleResourcePermission> managePerms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(targetRoleId))
                .and(ROLE_RESOURCE_PERMISSION.CAN_MANAGE.eq(true))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (!managePerms.isEmpty()) {
            return true;
        }

        // Step 3: If permissionType is null or "MANAGE", we already checked above
        if (permissionType == null || "MANAGE".equalsIgnoreCase(permissionType)) {
            return false;
        }

        // Step 4: Check specific permission type via operation_permission
        // Resolve ROLE resource type
        Integer roleResourceType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "ROLE");
        if (roleResourceType == null) {
            log.debug("Cannot resolve ROLE resource type");
            return false;
        }

        // Find operation_permission for the specified permission type
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

        // Check if operator has this specific operation permission on the target role
        List<RoleResourcePermission> specificPerms = roleResourcePermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(operatorRoleIds))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(targetRoleId))
                .and(ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.eq(operationPerm.getId()))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        return !specificPerms.isEmpty();
    }
}
