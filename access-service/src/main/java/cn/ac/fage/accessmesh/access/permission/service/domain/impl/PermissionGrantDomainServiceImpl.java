package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 *   （T-PERM-057 第五套形态收编：授权事实经统一引擎 LIST 管线获取，不再直查
 *   role_resource_permission；本服务只保留 canGrant 转授资格的领域判定——
 *   canGrant=true 且无条件挂载（20041 条件权限不可转授同源））
 * - 权限撤销：批量软删除权限并级联删除子权限
 * TODO: 自动授权解析（resolveAutoGrants）——依赖资源的自动授权尚未实现，当前仅使用 GrantSource.MANUAL
 * 采用批量处理策略避免N+1查询问题。
 * </p>
 */
@Service
public class PermissionGrantDomainServiceImpl implements PermissionGrantDomainService {

    private static final Logger log = LoggerFactory.getLogger(PermissionGrantDomainServiceImpl.class);

    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine permQueryEngine;
    private final OperationPermissionMapper operationPermissionMapper;

    /**
     * 构造函数注入依赖服务
     *
     * @param typeResolutionService     类型解析服务
     * @param permQueryEngine           统一权限查询引擎（授权事实唯一来源）
     * @param operationPermissionMapper 操作权限数据访问层（目标操作定义加载，非权限判定）
     */
    public PermissionGrantDomainServiceImpl(TypeResolutionService typeResolutionService,
                                            PermQueryEngine permQueryEngine,
                                            OperationPermissionMapper operationPermissionMapper) {
        this.typeResolutionService = typeResolutionService;
        this.permQueryEngine = permQueryEngine;
        this.operationPermissionMapper = operationPermissionMapper;
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
        GrantCheckKey checkKey = new GrantCheckKey(resourceTypeCode, resourceCode, codeType, operationCode, scopeAll);
        String key = grantCheckKeyText(checkKey);
        GrantCheckResult result = results.get(key);
        return result != null && result.canGrant();
    }

    /**
     * 批量检查授权权限（canGrant验证）
     * <p>
     * 授权事实一次经统一引擎 LIST 管线拉取（T-PERM-057 收编；配置面口径：不评估条件与
     * 条目互斥——转授资格看原始授权行），本方法内存完成 canGrant 领域判定：
     * 批量解析资源实体ID → 逐键匹配授权条目（位覆盖 covers）→ canGrant=true 且
     * conditionId=null（20041 同源：条件权限不可转授）。
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
                    results.put(grantCheckKeyText(key), new GrantCheckResult(false, "INVALID_PERMISSION_KEY"));
                }
                return valid;
            })
            .collect(Collectors.toSet());
        if (validPermissions.isEmpty()) {
            return results;
        }

        // 1. 授权事实：引擎 LIST 全量（配置面口径——不评估条件/互斥；评估与否不影响
        //    canGrant 判定：转授资格只认 canGrant=true 且无条件挂载的原始行）
        PermQuery query = PermQuery.forUserView(tenantId, subjectId);
        query.setEvaluateConditions(false);
        query.setEvaluateConflicts(false);
        query.setEvaluateMatchesBit(false);
        query.setIncludeResources(false);
        query.setIncludeOperations(true);
        query.setIncludeRoles(false);
        // 写校验面绕过 ROLE_PERM_SNAPSHOT（codex 四轮 P1）：直查恢复收编前新鲜度——
        // 撤权后 TTL 陈旧/旧读回填竞态可放行已撤销的转授资格（权限提升）
        query.setBypassPermSnapshot(true);
        PermResult permResult = permQueryEngine.query(query);
        List<RolePermEntry> operatorEntries = permResult.allowed()
            ? permResult.instanceEntries() : List.of();
        if (operatorEntries.isEmpty()) {
            // reason 区分（异常消息运维归因通道）：无角色=NO_ROLE、有角色零授权行=NO_PERMISSION
            String noEntryReason = "NO_ROLE".equals(permResult.reason()) ? "NO_ROLE" : "NO_PERMISSION";
            for (GrantCheckKey key : validPermissions) {
                results.put(grantCheckKeyText(key), new GrantCheckResult(false, noEntryReason));
            }
            return results;
        }

        // 2. 类型值与目标操作解析（批量）
        Map<String, Integer> rawResourceTypeByCode = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type",
            validPermissions.stream().map(GrantCheckKey::resourceTypeCode).collect(Collectors.toSet()));
        Map<String, Integer> resourceTypeByCode = new HashMap<>();
        for (Map.Entry<String, Integer> entry : rawResourceTypeByCode.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                resourceTypeByCode.put(entry.getKey().toUpperCase(), entry.getValue());
            }
        }
        if (resourceTypeByCode.isEmpty()) {
            for (GrantCheckKey key : validPermissions) {
                results.put(grantCheckKeyText(key),
                    new GrantCheckResult(false, "INVALID_RESOURCE_TYPE"));
            }
            return results;
        }

        // 目标操作索引（键=类型值+操作码大写）。目标操作独立于引擎辅助 map 加载——
        // 操作者无该类型授权行时该类型不进引擎 operationMap，目标操作仍须可解析（→NO_PERMISSION 而非误报 INVALID_OPERATION）
        Map<String, OperationPermission> targetOpByKey = new HashMap<>();
        Map<String, Set<String>> opCodesByTypeCode = new HashMap<>();
        for (GrantCheckKey key : validPermissions) {
            opCodesByTypeCode.computeIfAbsent(key.resourceTypeCode().toUpperCase(), _unused -> new LinkedHashSet<>())
                .add(key.operationCode().toUpperCase());
        }
        Set<Integer> targetTypeValues = new LinkedHashSet<>(resourceTypeByCode.values());
        Set<String> allTargetOpCodes = opCodesByTypeCode.values().stream()
            .flatMap(Set::stream).collect(Collectors.toCollection(LinkedHashSet::new));
        List<OperationPermission> targetOperations = operationPermissionMapper
            .selectByTenantResourceTypesAndOpCodes(tenantId, targetTypeValues, allTargetOpCodes);
        for (OperationPermission target : targetOperations) {
            if (target.getResourceType() == null || target.getCode() == null) {
                continue;
            }
            targetOpByKey.put(BusinessKeys.operationCodeKey(
                target.getResourceType(), target.getCode().toUpperCase()), target);
        }

        // 3. 实例目标批量解析（非 scopeAll 键）
        List<ResourceResolveRequest> resourceRequests = validPermissions.stream()
            .filter(key -> !key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank())
            .map(key -> new ResourceResolveRequest(key.resourceTypeCode(), key.resourceCode(), key.codeType(), domainCode))
            .distinct()
            .collect(Collectors.toList());
        Map<ResourceResolveKey, Long> resolvedResourceIds = typeResolutionService.batchResolveResourceIds(tenantId, resourceRequests);
        Map<String, Long> resourceEntityIdByKey = new HashMap<>();
        for (Map.Entry<ResourceResolveKey, Long> entry : resolvedResourceIds.entrySet()) {
            ResourceResolveKey key = entry.getKey();
            resourceEntityIdByKey.put(BusinessKeys.resourceTripleCodeKey(key.resourceTypeCode(), key.resourceCode(), key.codeType()), entry.getValue());
        }

        // 4. 授权条目按（类型值×操作码）与（类型值×操作码×实体）索引（位覆盖语义：授予操作覆盖目标操作即匹配）
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils
            .indexByResourceTypeAndBinaryBit(permResult.operationMap() != null
                ? permResult.operationMap().values() : List.of());
        Map<String, List<RolePermEntry>> entriesByOpKey = new HashMap<>();
        Map<String, List<RolePermEntry>> entriesByOpAndEntity = new HashMap<>();
        for (RolePermEntry perm : operatorEntries) {
            if (perm.resourceType() == null || perm.grantedBits() == null) {
                continue;
            }
            OperationPermission grantedOp = grantedOpIndex.get(
                BusinessKeys.operationBitKey(perm.resourceType(), perm.grantedBits()));
            if (grantedOp == null) {
                continue;
            }
            for (OperationPermission targetOp : targetOpByKey.values()) {
                if (!Objects.equals(targetOp.getResourceType(), perm.resourceType())
                    || !OperationPermissionUtils.covers(grantedOp, targetOp)) {
                    continue;
                }
                String opKey = BusinessKeys.operationCodeKey(perm.resourceType(), targetOp.getCode().toUpperCase());
                if (Boolean.TRUE.equals(perm.scopeAll())) {
                    entriesByOpKey.computeIfAbsent(opKey, _unused -> new ArrayList<>()).add(perm);
                } else if (perm.resourceEntityId() != null) {
                    entriesByOpAndEntity.computeIfAbsent(
                        BusinessKeys.grantEntryKey(perm.resourceType(), targetOp.getCode().toUpperCase(), perm.resourceEntityId()),
                        _unused -> new ArrayList<>()).add(perm);
                    // scopeAll=false 的实例行同样满足类型级转授键？否——scopeAll 键只认 scopeAll 行（与既有语义一致）
                }
            }
        }

        // 5. 逐键评估转授资格
        for (GrantCheckKey key : validPermissions) {
            GrantCheckResult result = evaluateGrantPermission(key, resourceTypeByCode, targetOpByKey,
                resourceEntityIdByKey, entriesByOpAndEntity, entriesByOpKey);
            results.put(grantCheckKeyText(key), result);
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
     * 评估单个权限的授权资格（T-PERM-057 收编后基于引擎 RolePermEntry 条目）
     * <p>
     * 根据预加载的数据评估操作者是否有canGrant权限。
     * 检查步骤：
     * 1. 验证资源类型有效性
     * 2. 验证操作权限有效性
     * 3. 解析资源实体ID（非scopeAll时）
     * 4. 查找匹配的权限条目
     * 5. 检查canGrant标记（可转授行须 canGrant=true 且无挂载条件，20041 同源）
     * </p>
     */
    private GrantCheckResult evaluateGrantPermission(GrantCheckKey key,
                                                      Map<String, Integer> resourceTypeByCode,
                                                      Map<String, OperationPermission> opPermByKey,
                                                      Map<String, Long> resourceEntityIdByKey,
                                                      Map<String, List<RolePermEntry>> permsBySpecificResource,
                                                      Map<String, List<RolePermEntry>> permsByScopeAll) {
        String resTypeCodeUpper = key.resourceTypeCode().toUpperCase();
        String opCodeUpper = key.operationCode().toUpperCase();

        Integer resourceTypeValue = resourceTypeByCode.get(resTypeCodeUpper);
        if (resourceTypeValue == null) {
            return new GrantCheckResult(false, "INVALID_RESOURCE_TYPE");
        }

        String opPermKey = BusinessKeys.operationCodeKey(resourceTypeValue, opCodeUpper);
        OperationPermission opPerm = opPermByKey.get(opPermKey);
        if (opPerm == null) {
            return new GrantCheckResult(false, "INVALID_OPERATION");
        }

        Long resourceEntityId = null;
        if (!key.scopeAll() && key.resourceCode() != null && !key.resourceCode().isBlank()) {
            String resKey = BusinessKeys.resourceTripleCodeKey(resTypeCodeUpper, key.resourceCode(), key.codeType());
            resourceEntityId = resourceEntityIdByKey.get(resKey);
            if (resourceEntityId == null) {
                return new GrantCheckResult(false, "RESOURCE_NOT_FOUND");
            }
        }

        String baseKey = BusinessKeys.operationCodeKey(resourceTypeValue, opCodeUpper);
        List<RolePermEntry> matchingPerms = new ArrayList<>();

        if (key.scopeAll()) {
            matchingPerms.addAll(permsByScopeAll.getOrDefault(baseKey, List.of()));
        } else {
            String specificKey = BusinessKeys.grantEntryKey(resourceTypeValue, opCodeUpper, resourceEntityId);
            matchingPerms.addAll(permsBySpecificResource.getOrDefault(specificKey, List.of()));
            matchingPerms.addAll(permsByScopeAll.getOrDefault(baseKey, List.of()));
        }

        if (matchingPerms.isEmpty()) {
            return new GrantCheckResult(false, "NO_PERMISSION");
        }

        for (RolePermEntry perm : matchingPerms) {
            if (Boolean.TRUE.equals(perm.canGrant()) && perm.conditionId() == null) {
                if (key.scopeAll() && !Boolean.TRUE.equals(perm.scopeAll())) {
                    continue;
                }
                return new GrantCheckResult(true, null);
            }
        }

        return new GrantCheckResult(false, "NO_GRANT_RIGHT");
    }

    /** K8 转授检查五段键（经 BusinessKeys 构造，格式 golden 锁定）。 */
    private static String grantCheckKeyText(GrantCheckKey key) {
        return BusinessKeys.grantCheckKey(key.resourceTypeCode(), key.resourceCode(),
            key.codeType(), key.operationCode(), key.scopeAll());
    }

    private boolean isManual(RoleResourcePermission permission) {
        return permission != null && (permission.getGrantSource() == null
            || GrantSource.MANUAL.getValue().equals(permission.getGrantSource()));
    }

    private boolean isSingleOperationBit(Long grantedBits) {
        return grantedBits != null && grantedBits > 0 && (grantedBits & (grantedBits - 1)) == 0;
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

}
