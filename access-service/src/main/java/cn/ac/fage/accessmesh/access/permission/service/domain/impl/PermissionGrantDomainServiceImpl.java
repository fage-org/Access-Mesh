package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限授予领域服务实现类
 * <p>
 * 实现权限授予相关的核心领域逻辑：
 * - 授权传递检查（canGrant验证）：操作者必须拥有该权限且canGrant=true才能授权给他人
 * - 权限撤销：批量软删除权限并级联删除子权限
 * TODO: 自动授权解析（resolveAutoGrants）——依赖资源的自动授权尚未实现，当前仅使用 GrantSource.MANUAL
 * 采用批量处理策略避免N+1查询问题。
 * </p>
 */
@Service
public class PermissionGrantDomainServiceImpl implements PermissionGrantDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantDomainServiceImpl.class);

    private final TypeResolutionService typeResolutionService;
    private final SubjectDomainService subjectDomainService;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper roleResourcePermissionMapper;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService        类型解析服务
     * @param subjectDomainService        主体领域服务
     * @param operationPermissionMapper    操作权限数据访问层
     * @param roleResourcePermissionMapper 角色资源权限数据访问层
     */
    public PermissionGrantDomainServiceImpl(TypeResolutionService typeResolutionService,
                                            SubjectDomainService subjectDomainService,
                                            OperationPermissionMapper operationPermissionMapper,
                                            RoleResourcePermissionMapper roleResourcePermissionMapper) {
        this.typeResolutionService = typeResolutionService;
        this.subjectDomainService = subjectDomainService;
        this.operationPermissionMapper = operationPermissionMapper;
        this.roleResourcePermissionMapper = roleResourcePermissionMapper;
    }

    // ===== canGrant 权限检查 =====

    /**
     * 检查是否有权限授予指定权限
     * <p>
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * （T-ORG-001 统一后操作者 ID 即主体 ID，无转换层）。
     * </p>
     *
     * @param tenantId         租户ID
     * @param subjectId        权限域投影主体ID（abstract_user.id）
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @param operationCode    操作码
     * @param scopeAll         是否全局作用域
     * @param domainCode       业务域编码，可选
     * @return 是否有权限授予
     */
    @Override
    public boolean canGrantPermission(Long tenantId, Long subjectId, String resourceTypeCode,
                                       String resourceCode, String codeType, String operationCode,
                                       boolean scopeAll, String domainCode) {
        Set<GrantCheckKey> keys = Set.of(new GrantCheckKey(resourceTypeCode, resourceCode, codeType, operationCode, scopeAll));
        Map<String, GrantCheckResult> results = checkCanGrant(tenantId, subjectId, keys, domainCode);
        String key = buildPermissionKey(new GrantCheckKey(resourceTypeCode, resourceCode, codeType, operationCode, scopeAll));
        GrantCheckResult result = results.get(key);
        return result != null && result.canGrant();
    }

    /**
     * 批量检查授权权限（canGrant验证）
     * <p>
     * 采用批量处理策略避免N+1查询：
     * 1. 批量解析资源类型值
     * 2. 批量查询操作权限
     * 3. 批量解析资源实体ID
     * 4. 批量查询角色资源权限
     * 5. 构建查找映射并逐个评估
     * 主体必须是权限域投影主体（{@code abstract_user.id}），禁止直接传 admin 域
     * （T-ORG-001 统一后操作者 ID 即主体 ID，无转换层）。
     * </p>
     *
     * @param tenantId    租户ID
     * @param subjectId   权限域投影主体ID（abstract_user.id）
     * @param permissions 待检查的权限键集合
     * @param domainCode  业务域编码，可选
     * @return 权限键到检查结果的映射
     */
    @Override
    public Map<String, GrantCheckResult> checkCanGrant(Long tenantId, Long subjectId,
                                                        Set<GrantCheckKey> permissions, String domainCode) {
        if (tenantId == null || subjectId == null || permissions == null || permissions.isEmpty()) {
            return Map.of();
        }

        Map<String, GrantCheckResult> results = new HashMap<>();
        Set<GrantCheckKey> validPermissions = permissions.stream()
            .filter(Objects::nonNull)
            .filter(key -> {
                boolean valid = key.resourceTypeCode() != null && !key.resourceTypeCode().isBlank()
                    && key.operationCode() != null && !key.operationCode().isBlank();
                if (!valid) {
                    results.put(buildPermissionKey(key), new GrantCheckResult(false, "INVALID_PERMISSION_KEY"));
                }
                return valid;
            })
            .collect(Collectors.toSet());
        if (validPermissions.isEmpty()) {
            return results;
        }

        Set<Long> operatorRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, subjectId);
        if (operatorRoleIds.isEmpty()) {
            for (GrantCheckKey key : validPermissions) {
                String permKey = buildPermissionKey(key);
                results.put(permKey, new GrantCheckResult(false, "NO_ROLE"));
            }
            return results;
        }

        // ===== 批量优化策略 =====

        // 1. 收集资源类型编码和操作码
        Set<String> resourceTypeCodes = validPermissions.stream()
            .map(GrantCheckKey::resourceTypeCode)
            .collect(Collectors.toSet());
        Set<String> operationCodes = validPermissions.stream()
            .map(GrantCheckKey::operationCode)
            .map(String::toUpperCase)
            .collect(Collectors.toSet());

        // 2. 批量解析资源类型，避免N+1查询
        Map<String, Integer> rawResourceTypeByCode = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", resourceTypeCodes);
        // 键转大写以保持一致的查找
        Map<String, Integer> resourceTypeByCode = new HashMap<>();
        for (Map.Entry<String, Integer> entry : rawResourceTypeByCode.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                resourceTypeByCode.put(entry.getKey().toUpperCase(), entry.getValue());
            }
        }

        // 3. 批量查询操作权限
        Set<Integer> resourceTypeValues = new HashSet<>(resourceTypeByCode.values());
        if (resourceTypeValues.isEmpty()) {
            for (GrantCheckKey key : validPermissions) {
                results.put(buildPermissionKey(key),
                    new GrantCheckResult(false, "INVALID_RESOURCE_TYPE"));
            }
            return results;
        }
        List<OperationPermission> specificTargetOperations = operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            tenantId, resourceTypeValues, operationCodes);

        Map<String, OperationPermission> opPermByKey = new HashMap<>();
        Map<Integer, List<OperationPermission>> targetOpsByType = new LinkedHashMap<>();
        for (Integer resourceTypeValue : resourceTypeValues) {
            List<OperationPermission> merged = specificTargetOperations.stream()
                .filter(operation -> Objects.equals(operation.getResourceType(), resourceTypeValue))
                .collect(Collectors.toList());
            targetOpsByType.put(resourceTypeValue, merged);
            for (OperationPermission operation : merged) {
                opPermByKey.put(resourceTypeValue + ":" + operation.getCode().toUpperCase(), operation);
            }
        }
        Map<String, OperationPermission> grantedOpIndex = buildOperationIndex(
            operationPermissionMapper.selectByTenantAndResourceType(tenantId, null), resourceTypeValues);

        // 4. 批量解析资源实体ID
        List<ResourceResolveRequest> resourceRequests = validPermissions.stream()
            .filter(key -> !key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank())
            .map(key -> new ResourceResolveRequest(key.resourceTypeCode(), key.resourceCode(), key.codeType(), domainCode))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resolvedResourceIds = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);

        Map<String, Long> resourceEntityIdByKey = new HashMap<>();
        for (Map.Entry<ResourceResolveKey, Long> entry : resolvedResourceIds.entrySet()) {
            ResourceResolveKey key = entry.getKey();
            resourceEntityIdByKey.put(buildResourceKey(key.resourceTypeCode(), key.resourceCode(), key.codeType()), entry.getValue());
        }

        // 5. 批量查询角色资源权限（按角色和资源类型过滤）
        List<RoleResourcePermission> allPerms = roleResourcePermissionMapper.selectValidByRoleIds(
            tenantId, operatorRoleIds);

        allPerms = allPerms.stream()
            .filter(p -> p.getResourceType() != null && resourceTypeValues.contains(p.getResourceType()))
            .collect(Collectors.toList());

        // 6. 构建查找映射
        Map<String, List<RoleResourcePermission>> permsBySpecificResource = new HashMap<>();
        Map<String, List<RoleResourcePermission>> permsByScopeAll = new HashMap<>();

        for (RoleResourcePermission perm : allPerms) {
            OperationPermission grantedOp = grantedOpIndex.get(
                operationIndexKey(perm.getResourceType(), perm.getGrantedBits()));
            if (grantedOp == null) {
                continue;
            }
            for (OperationPermission targetOp : targetOpsByType.getOrDefault(perm.getResourceType(), List.of())) {
                if (!OperationPermissionUtils.covers(grantedOp, targetOp)) {
                    continue;
                }
                String baseKey = perm.getResourceType() + ":" + targetOp.getCode().toUpperCase();
                if (Boolean.TRUE.equals(perm.getScopeAll())) {
                    permsByScopeAll.computeIfAbsent(baseKey, _unused -> new ArrayList<>()).add(perm);
                } else if (perm.getResourceEntityId() != null) {
                    permsBySpecificResource.computeIfAbsent(baseKey + ":" + perm.getResourceEntityId(), _unused -> new ArrayList<>()).add(perm);
                }
            }
        }

        // ===== 逐个评估权限 =====
        for (GrantCheckKey key : validPermissions) {
            GrantCheckResult result = evaluateGrantPermission(key, resourceTypeByCode, opPermByKey,
                resourceEntityIdByKey, permsBySpecificResource, permsByScopeAll);
            results.put(buildPermissionKey(key), result);
        }
        return results;
    }

    @Override
    public void validateSingleManualGrants(List<RoleResourcePermission> existingPermissions,
                                           List<RoleResourcePermission> newPermissions,
                                           Set<Long> removedPermissionIds) {
        Set<Long> removedIds = removedPermissionIds == null ? Set.of() : removedPermissionIds;
        Set<ManualGrantKey> occupied = new HashSet<>();

        for (RoleResourcePermission permission : existingPermissions == null ? List.<RoleResourcePermission>of() : existingPermissions) {
            if (removedIds.contains(permission.getId()) || !isManual(permission)) {
                continue;
            }
            occupied.add(ManualGrantKey.of(permission));
        }

        for (RoleResourcePermission permission : newPermissions == null ? List.<RoleResourcePermission>of() : newPermissions) {
            if (!isManual(permission)) {
                continue;
            }
            validateGrantAttributes(permission);
            if (!isSingleOperationBit(permission.getGrantedBits())) {
                throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                    "MANUAL permission must contain exactly one operation bit");
            }
            if (!occupied.add(ManualGrantKey.of(permission))) {
                throw new BizException(PermissionErrorCode.DIRECT_PERMISSION_CONFLICT.getCode(),
                    "Direct permission already exists for the same role, resource, operation, scope and parent");
            }
        }
    }

    @Override
    public void validateGrantAttributes(RoleResourcePermission permission) {
        if (permission != null && permission.getConditionId() != null
            && Boolean.TRUE.equals(permission.getCanGrant())) {
            throw new BizException(PermissionErrorCode.CONDITIONAL_PERMISSION_CANNOT_DELEGATE.getCode(),
                PermissionErrorCode.CONDITIONAL_PERMISSION_CANNOT_DELEGATE.getMessage());
        }
    }

    // ===== 私有辅助方法 =====

    /**
     * 评估单个权限的授权资格
     * <p>
     * 根据预加载的数据评估操作者是否有canGrant权限。
     * 检查步骤：
     * 1. 验证资源类型有效性
     * 2. 验证操作权限有效性
     * 3. 解析资源实体ID（非scopeAll时）
     * 4. 查找匹配的权限记录
     * 5. 检查canGrant标记
     * </p>
     */
    private GrantCheckResult evaluateGrantPermission(GrantCheckKey key,
                                                      Map<String, Integer> resourceTypeByCode,
                                                      Map<String, OperationPermission> opPermByKey,
                                                      Map<String, Long> resourceEntityIdByKey,
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
            String resKey = buildResourceKey(resTypeCodeUpper, key.resourceCode(), key.codeType());
            resourceEntityId = resourceEntityIdByKey.get(resKey);
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
            if (Boolean.TRUE.equals(perm.getCanGrant()) && perm.getConditionId() == null) {
                if (key.scopeAll() && !Boolean.TRUE.equals(perm.getScopeAll())) {
                    continue;
                }
                return new GrantCheckResult(true, null);
            }
        }

        return new GrantCheckResult(false, "NO_GRANT_RIGHT");
    }

    private String buildResourceKey(String resourceTypeCode, String resourceCode, String codeType) {
        return String.format("%s:%s:%s",
            resourceTypeCode == null ? "" : resourceTypeCode.toUpperCase(),
            resourceCode == null ? "" : resourceCode,
            codeType == null ? "" : codeType);
    }

    private boolean isManual(RoleResourcePermission permission) {
        return permission != null && (permission.getGrantSource() == null
            || GrantSource.MANUAL.getValue().equals(permission.getGrantSource()));
    }

    private boolean isSingleOperationBit(Long grantedBits) {
        return grantedBits != null && grantedBits > 0 && (grantedBits & (grantedBits - 1)) == 0;
    }

    private Map<String, OperationPermission> buildOperationIndex(
            List<OperationPermission> operations, Set<Integer> resourceTypes) {
        Map<String, OperationPermission> result = new HashMap<>();
        for (Integer resourceType : resourceTypes) {
            for (OperationPermission operation : operations.stream()
                    .filter(op -> Objects.equals(op.getResourceType(), resourceType))
                    .toList()) {
                result.put(operationIndexKey(resourceType, operation.getBinaryBit()), operation);
            }
        }
        return result;
    }

    private String operationIndexKey(Integer resourceType, Long binaryBit) {
        return resourceType + ":" + binaryBit;
    }

    private record ManualGrantKey(
        Long tenantId,
        Long roleId,
        Long resourceEntityId,
        Integer resourceType,
        Long grantedBits,
        Long dependOn,
        boolean scopeAll
    ) {
        private static ManualGrantKey of(RoleResourcePermission permission) {
            return new ManualGrantKey(
                permission.getTenantId(),
                permission.getAbstractRoleId(),
                permission.getResourceEntityId(),
                permission.getResourceType(),
                permission.getGrantedBits(),
                permission.getDependOn(),
                Boolean.TRUE.equals(permission.getScopeAll())
            );
        }
    }

    /**
     * 构建权限键字符串
     * <p>
     * 格式：resourceTypeCode:resourceCode:codeType:operationCode:scopeType
     * </p>
     */
    private String buildPermissionKey(GrantCheckKey key) {
        return String.format("%s:%s:%s:%s:%s",
            key.resourceTypeCode(),
            key.resourceCode() == null ? "*" : key.resourceCode(),
            key.codeType() == null ? "*" : key.codeType(),
            key.operationCode(),
            key.scopeAll() ? "ALL" : "SPECIFIC");
    }
}
