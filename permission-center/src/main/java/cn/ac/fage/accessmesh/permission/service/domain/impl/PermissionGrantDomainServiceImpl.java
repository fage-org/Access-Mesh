package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
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
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @param operationCode    操作码
     * @param scopeAll         是否全局作用域
     * @param domainCode       业务域编码，可选
     * @return 是否有权限授予
     */
    @Override
    public boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                       String resourceCode, String codeType, String operationCode,
                                       boolean scopeAll, String domainCode) {
        Set<GrantCheckKey> keys = Set.of(new GrantCheckKey(resourceTypeCode, resourceCode, codeType, operationCode, scopeAll));
        Map<String, GrantCheckResult> results = checkCanGrant(tenantId, operatorId, keys, domainCode);
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
     * </p>
     *
     * @param tenantId    租户ID
     * @param operatorId  操作者ID
     * @param permissions 待检查的权限键集合
     * @param domainCode  业务域编码，可选
     * @return 权限键到检查结果的映射
     */
    @Override
    public Map<String, GrantCheckResult> checkCanGrant(Long tenantId, Long operatorId,
                                                        Set<GrantCheckKey> permissions, String domainCode) {
        if (tenantId == null || operatorId == null || permissions == null || permissions.isEmpty()) {
            return Map.of();
        }

        Set<Long> operatorRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, operatorId);
        if (operatorRoleIds.isEmpty()) {
            Map<String, GrantCheckResult> results = new HashMap<>();
            for (GrantCheckKey key : permissions) {
                String permKey = buildPermissionKey(key);
                results.put(permKey, new GrantCheckResult(false, "NO_ROLE"));
            }
            return results;
        }

        // ===== 批量优化策略 =====

        // 1. 收集资源类型编码和操作码
        Set<String> resourceTypeCodes = permissions.stream()
            .map(GrantCheckKey::resourceTypeCode)
            .collect(Collectors.toSet());
        Set<String> operationCodes = permissions.stream()
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
        List<OperationPermission> allOpPerms = operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            tenantId, resourceTypeValues, operationCodes);

        Map<String, OperationPermission> opPermByKey = new HashMap<>();
        Map<Integer, List<OperationPermission>> operationsByType = new LinkedHashMap<>();
        for (OperationPermission op : allOpPerms) {
            String key = op.getResourceType() + ":" + op.getCode().toUpperCase();
            opPermByKey.put(key, op);
        }
        for (Integer resourceTypeValue : resourceTypeValues) {
            operationsByType.put(resourceTypeValue, operationPermissionMapper.selectByTenantAndResourceType(tenantId, resourceTypeValue));
        }
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(
            operationsByType.values().stream().flatMap(List::stream).toList()
        );
        Map<Integer, List<OperationPermission>> targetOpsByType = allOpPerms.stream()
            .collect(Collectors.groupingBy(OperationPermission::getResourceType));

        // 4. 批量解析资源实体ID
        List<ResourceResolveRequest> resourceRequests = permissions.stream()
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
            OperationPermission grantedOp = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
                grantedOpIndex,
                perm.getResourceType(),
                perm.getGrantedBits()
            );
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
        Map<String, GrantCheckResult> results = new HashMap<>();
        for (GrantCheckKey key : permissions) {
            GrantCheckResult result = evaluateGrantPermission(key, resourceTypeByCode, opPermByKey,
                resourceEntityIdByKey, permsBySpecificResource, permsByScopeAll);
            results.put(buildPermissionKey(key), result);
        }
        return results;
    }

    // ===== 权限撤销 =====

    /**
     * 批量撤销角色权限
     * <p>
     * 批量软删除权限，同时级联删除依赖该权限的子权限。
     * 注意：缓存失效和广播由调用方通过 PermissionChangeContext + @PermissionChange afterCommit 统一处理。
     * </p>
     *
     * @param tenantId      租户ID
     * @param roleId        角色ID
     * @param permissionIds 待撤销的权限ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 查询实际属于该角色的有效权限ID
        List<RoleResourcePermission> validPerms = roleResourcePermissionMapper.selectValidByIds(tenantId, roleId, permissionIds);
        if (validPerms.isEmpty()) {
            return;
        }
        List<Long> validIds = validPerms.stream().map(RoleResourcePermission::getId).collect(Collectors.toList());

        // 批量软删除权限（仅删除属于该角色的有效权限）
        roleResourcePermissionMapper.softDeleteBatch(tenantId, validIds, now);

        // 批量级联删除子权限（仅基于有效权限ID）
        roleResourcePermissionMapper.cascadeSoftDeleteChildren(tenantId, validIds, now);

        // 注意：缓存失效和广播由调用方在 @PermissionChange afterCommit 中统一处理（避免与 batchGrant 重复登记）
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
            if (Boolean.TRUE.equals(perm.getCanGrant())) {
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
