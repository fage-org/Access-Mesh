package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
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

import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

/**
 * Implementation of canGrant authorization checks.
 *
 * <p>General permission checks should use ResourcePermissionValidator.
 * This service only handles canGrant validation for permission delegation.
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServiceImpl.class);

    private final TypeResolutionService typeResolutionService;
    private final UserRoleDomainService userRoleDomainService;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;

    public AuthorizationServiceImpl(TypeResolutionService typeResolutionService,
                                     UserRoleDomainService userRoleDomainService,
                                     OperationPermissionMapper operationPermissionMapper,
                                     RoleResourcePermissionMapper roleResourcePermissionMapper) {
        this.typeResolutionService = typeResolutionService;
        this.userRoleDomainService = userRoleDomainService;
        this.operationPermissionMapper = operationPermissionMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
    }

    @Override
    public boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                       String resourceCode, String operationCode, boolean scopeAll, String domainCode) {
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

        Set<Long> operatorRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (operatorRoleIds.isEmpty()) {
            Map<String, GrantCheckResult> results = new HashMap<>();
            for (GrantCheckKey key : permissions) {
                String permKey = buildPermissionKey(key);
                results.put(permKey, new GrantCheckResult(false, "NO_ROLE"));
            }
            return results;
        }

        // ===== Batch optimization =====

        // 1. Collect resource type codes and operation codes
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

        // 3. Batch query operation permissions
        Set<Integer> resourceTypeValues = new HashSet<>(resourceTypeByCode.values());
        List<OperationPermission> allOpPerms = operationPermissionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(OPERATION_PERMISSION.TENANT_ID.eq(tenantId))
                .and(OPERATION_PERMISSION.RESOURCE_TYPE.in(resourceTypeValues))
                .and(OPERATION_PERMISSION.CODE.in(operationCodes.stream().map(String::toUpperCase).collect(Collectors.toSet())))
                .and(OPERATION_PERMISSION.DELETE_FLAG.eq(0))
        );

        Map<String, OperationPermission> opPermByKey = new HashMap<>();
        Map<Long, OperationPermission> opPermById = new HashMap<>();
        for (OperationPermission op : allOpPerms) {
            String key = op.getResourceType() + ":" + op.getCode().toUpperCase();
            opPermByKey.put(key, op);
            opPermById.put(op.getId(), op);
        }

        // 4. Batch resolve resource entity IDs
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

        // 5. Batch query role_resource_permissions
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

        // 6. Build lookup maps
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

    private GrantCheckResult evaluateGrantPermission(GrantCheckKey key,
                                                      Map<String, Integer> resourceTypeByCode,
                                                      Map<String, OperationPermission> opPermByKey,
                                                      Map<String, Long> resourceEntityIdByCode,
                                                      Map<String, List<RoleResourcePermission>> permsBySpecificResource,
                                                      Map<String, List<RoleResourcePermission>> permsByScopeAll) {
        String resTypeCodeUpper = key.resourceTypeCode().toUpperCase();
        String opCodeUpper = key.operationCode().toUpperCase();

        Integer resourceTypeValue = resourceTypeByCode.get(resTypeCodeUpper);
        if (resourceTypeValue == null) {
            return new GrantCheckResult(false, "INVALID_RESOURCE_TYPE");
        }

        String opPermKey = resourceTypeValue + ":" + opCodeUpper;
        OperationPermission opPerm = opPermByKey.get(opPermKey);
        if (opPerm == null) {
            return new GrantCheckResult(false, "INVALID_OPERATION");
        }

        Long resourceEntityId = null;
        if (!key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank()) {
            String resKey = resTypeCodeUpper + ":" + key.resourceCode();
            resourceEntityId = resourceEntityIdByCode.get(resKey);
            if (resourceEntityId == null) {
                return new GrantCheckResult(false, "RESOURCE_NOT_FOUND");
            }
        }

        String baseKey = resourceTypeValue + ":" + opCodeUpper;
        List<RoleResourcePermission> matchingPerms = new ArrayList<>();

        if (key.scopeAll()) {
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(scopeAllPerms);
        } else {
            String specificKey = baseKey + ":" + resourceEntityId;
            List<RoleResourcePermission> specificPerms = permsBySpecificResource.getOrDefault(specificKey, List.of());
            List<RoleResourcePermission> scopeAllPerms = permsByScopeAll.getOrDefault(baseKey, List.of());
            matchingPerms.addAll(specificPerms);
            matchingPerms.addAll(scopeAllPerms);
        }

        if (matchingPerms.isEmpty()) {
            return new GrantCheckResult(false, "NO_PERMISSION");
        }

        for (RoleResourcePermission perm : matchingPerms) {
            if (Boolean.TRUE.equals(perm.getCanGrant())) {
                if (key.scopeAll() && !Boolean.TRUE.equals(perm.getScopeAll())) {
                    continue;
                }
                return new GrantCheckResult(true, null);
            }
        }

        return new GrantCheckResult(false, "NO_GRANT_RIGHT");
    }

    private String buildPermissionKey(GrantCheckKey key) {
        return String.format("%s:%s:%s:%s",
            key.resourceTypeCode(),
            key.resourceCode() == null ? "*" : key.resourceCode(),
            key.operationCode(),
            key.scopeAll() ? "ALL" : "SPECIFIC");
    }
}