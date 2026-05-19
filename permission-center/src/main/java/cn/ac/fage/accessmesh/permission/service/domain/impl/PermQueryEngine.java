package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.service.domain.ResolveContext;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一权限查询引擎 -- 所有权限校验的唯一入口。
 *
 * <h3>查询流程</h3>
 * <ol>
 *   <li>解析用户角色（缓存L1->L2->DB）</li>
 *   <li>解析资源类型码和操作码为内部ID（批量）</li>
 *   <li>查询类型级权限（scopeAll=true）-- 1条SQL</li>
 *   <li>如果scopeAll匹配则提前返回</li>
 *   <li>查询实例级权限 -- 1条SQL</li>
 *   <li>评估条件和冲突（按参数标志）</li>
 *   <li>加载辅助实体（按参数标志）</li>
 *   <li>构建统一PermResult结果</li>
 * </ol>
 *
 * <h3>优化策略</h3>
 * <ul>
 *   <li>批量ID解析：避免N次单查询</li>
 *   <li>scopeAll优先匹配：匹配后跳过实例级查询</li>
 *   <li>内存筛选：matchesBit过滤、条件评估、冲突过滤</li>
 *   <li>双索引缓存：OperationPermissionCacheService提供ID索引和binaryBit反向索引</li>
 * </ul>
 */
@Component
public class PermQueryEngine {

    private static final Logger log = LoggerFactory.getLogger(PermQueryEngine.class);

    private final UserRoleDomainService userRoleDomainService;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final EntityBatchLoadDomainService entityBatchLoadService;
    private final PermissionConditionDomainService conditionDomainService;
    private final PermissionConflictDomainService conflictDomainService;
    private final RolePermEntryMapper entryMapper;
    private final TypeResolutionService typeResolutionService;
    private final OperationPermissionCacheService operationPermissionCacheService;

    /**
     * 构造函数注入依赖服务
     *
     * @param userRoleDomainService     用户角色解析服务
     * @param rolePermMapper            角色权限映射器
     * @param entityBatchLoadService    实体批量加载服务
     * @param conditionDomainService    权限条件评估服务
     * @param conflictDomainService     权限冲突处理服务
     * @param entryMapper               权限条目映射器
     * @param typeResolutionService     类型解析服务
     * @param operationPermissionCacheService 操作权限缓存服务（双索引：ID和binaryBit）
     */
    public PermQueryEngine(UserRoleDomainService userRoleDomainService,
                           RoleResourcePermissionMapper rolePermMapper,
                           EntityBatchLoadDomainService entityBatchLoadService,
                           PermissionConditionDomainService conditionDomainService,
                           PermissionConflictDomainService conflictDomainService,
                           RolePermEntryMapper entryMapper,
                           TypeResolutionService typeResolutionService,
                           OperationPermissionCacheService operationPermissionCacheService) {
        this.userRoleDomainService = userRoleDomainService;
        this.rolePermMapper = rolePermMapper;
        this.entityBatchLoadService = entityBatchLoadService;
        this.conditionDomainService = conditionDomainService;
        this.conflictDomainService = conflictDomainService;
        this.entryMapper = entryMapper;
        this.typeResolutionService = typeResolutionService;
        this.operationPermissionCacheService = operationPermissionCacheService;
    }

    /**
     * 执行统一权限查询
     *
     * @param q 权限查询参数
     * @return 权限查询结果
     */
    public PermResult query(PermQuery q) {
        // -- 0. 创建 ResolveContext，预解析所有类型 --
        ResolveContext ctx = new ResolveContext(q.tenantId(), typeResolutionService);
        if (q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareResourceTypes(q.resourceTypeCodes());
        }
        if (q.operationCodes() != null && !q.operationCodes().isEmpty()
            && q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()) {
            ctx.prepareOperations(q.resourceTypeCodes(), q.operationCodes());
        }

        // -- 1. 解析角色 --
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        // -- 2. 解析资源类型（使用 ResolveContext）--
        Set<Integer> resourceTypes = resolveResourceTypes(q, ctx);

        // -- 3. 解析操作ID（使用 ResolveContext）--
        Set<Long> opIds = resolveOperationIds(q, ctx);
        Map<Integer, Long> bitMasks = resolveBitMasks(q.tenantId(), resourceTypes, opIds);

        // -- 4. 查询类型级权限（scopeAll=true） --
        List<RolePermEntry> scopeAllEntries = List.of();
        if (q.queryScopeAll() && !bitMasks.isEmpty()) {
            scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, bitMasks);
        }

        // -- 5. scopeAll匹配时提前返回 --
        if (!scopeAllEntries.isEmpty() && q.earlyReturnOnScopeAll()) {
            scopeAllEntries = evaluateIfNeeded(q, scopeAllEntries);
            if (scopeAllEntries.isEmpty()) {
                return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
            }
            Map<String, Object> evalCtx = q.context();
            if (evalCtx == null) evalCtx = Map.of();
            PermResult.Builder builder = PermResult.builder(true, null)
                .scopeAllMatched(true)
                .scopeAllEntries(scopeAllEntries);
            loadAncillary(q, builder, scopeAllEntries, List.of(), roleIds);
            return builder.build();
        }

        // -- 6. 解析并查询实例级权限 --
        Set<Long> entityIds = resolveEntityIds(q);
        List<RolePermEntry> instanceEntries = List.of();

        if (q.queryInstance() && !entityIds.isEmpty()) {
            instanceEntries = queryInstance(q.tenantId(), roleIds, entityIds, bitMasks);
        }

        // -- 7. 合并scopeAll和实例级结果 --
        List<RolePermEntry> combined = new ArrayList<>(scopeAllEntries);
        combined.addAll(instanceEntries);
        if (combined.isEmpty()) {
            return PermResult.deny("NO_PERMISSION");
        }

        // 评估条件和冲突
        combined = evaluateIfNeeded(q, combined);
        if (combined.isEmpty()) {
            return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
        }

        PermResult.Builder builder = PermResult.builder(true, null)
            .scopeAllMatched(!scopeAllEntries.isEmpty())
            .scopeAllEntries(scopeAllEntries)
            .instanceEntries(instanceEntries);
        loadAncillary(q, builder, scopeAllEntries, instanceEntries, roleIds);
        return builder.build();
    }

    // ===== 校验器兼容API（替代ResourcePermissionValidator） =====

    /**
     * 校验单个权限（无权限时抛异常）
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型码
     * @param resourceId       资源ID（可为null表示类型级）
     * @param operationCode    操作码
     * @throws SecurityException 无权限时抛出异常
     */
    public void validate(Long tenantId, Long operatorId, String resourceTypeCode,
                          Object resourceId, String operationCode) {
        PermQuery q = PermQuery.forValidate(tenantId, operatorId,
            resourceTypeCode, resourceId != null ? String.valueOf(resourceId) : null, operationCode);
        PermResultUtils.validateOrThrow(query(q));
    }

    /**
     * 检查是否有权限
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型码
     * @param resourceId       资源ID（可为null）
     * @param operationCode    操作码
     * @return 是否有权限
     */
    public boolean hasPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                  Object resourceId, String operationCode) {
        PermQuery q = PermQuery.forValidate(tenantId, operatorId,
            resourceTypeCode, resourceId != null ? String.valueOf(resourceId) : null, operationCode);
        return query(q).allowed();
    }

    /**
     * 批量校验权限（有拒绝ID时抛异常）
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型码
     * @param resourceIds      资源ID集合
     * @param operationCode    操作码
     * @throws SecurityException 有拒绝ID时抛出异常
     */
    public <ID> void validateBatch(Long tenantId, Long operatorId, String resourceTypeCode,
                                    Set<ID> resourceIds, String operationCode) {
        Set<ID> denied = getDeniedIds(tenantId, operatorId, resourceTypeCode, resourceIds, operationCode);
        if (!denied.isEmpty())
            throw new SecurityException("Permission denied: " + operationCode + " on " + resourceTypeCode + ":" + denied);
    }

    /**
     * 批量权限检查 -- 优化版，最小化数据库查询。
     * <p>
     * 相比N次单独查询，此方法：
     * <ol>
     *   <li>一次查询解析用户角色</li>
     *   <li>一次查询类型级权限（scopeAll=true）</li>
     *   <li>如果类型级匹配，所有资源都被允许</li>
     *   <li>否则，一次批量查询实例级权限</li>
     *   <li>内存计算拒绝ID集合</li>
     * </ol>
     *
     * @param tenantId         租户ID
     * @param operatorId       操作者ID
     * @param resourceTypeCode 资源类型码
     * @param resourceIds      资源ID集合
     * @param operationCode    操作码
     * @return 被拒绝的资源ID集合
     */
    public <ID> Set<ID> getDeniedIds(Long tenantId, Long operatorId, String resourceTypeCode,
                                      Set<ID> resourceIds, String operationCode) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Set.of();
        }

        // 1. 解析用户角色（1次查询）
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId);
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>(resourceIds); // 无角色 = 全部拒绝
        }

        // 2. 创建 ResolveContext，预解析类型和操作ID（避免重复调用）
        ResolveContext ctx = new ResolveContext(tenantId, typeResolutionService);
        ctx.prepareResourceTypes(Set.of(resourceTypeCode));
        ctx.prepareOperations(resourceTypeCode, Set.of(operationCode));

        Integer resourceTypeValue = ctx.getResourceTypeValue(resourceTypeCode);
        if (resourceTypeValue == null) {
            return new LinkedHashSet<>(resourceIds); // 未知类型 = 全部拒绝
        }
        Long operationId = ctx.getOperationId(resourceTypeCode, operationCode);
        if (operationId == null) {
            return new LinkedHashSet<>(resourceIds); // 未知操作 = 全部拒绝
        }

        // 3. 查询类型级权限（scopeAll=true）— 1次查询
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId));
        List<RolePermEntry> scopeAllEntries = queryScopeAll(tenantId, roleIds, bitMasks);

        // 4. 若scopeAll匹配，则所有资源均允许
        if (!scopeAllEntries.isEmpty()) {
            // 评估条件（如有需要）
            List<RolePermEntry> evaluated = conditionDomainService.evaluate(tenantId, scopeAllEntries, Map.of());
            if (!evaluated.isEmpty()) {
                evaluated = conflictDomainService.filterPermMutex(tenantId, evaluated);
                if (!evaluated.isEmpty()) {
                    return Set.of(); // 通过scopeAll全部允许
                }
            }
        }

        // 5. 将resourceIds转换为Long以批量查询
        Set<Long> resourceEntityIds = new HashSet<>();
        Map<Long, ID> entityIdToOriginalId = new HashMap<>();
        for (ID id : resourceIds) {
            Long entityId = toLongId(id);
            if (entityId != null) {
                resourceEntityIds.add(entityId);
                entityIdToOriginalId.put(entityId, id);
            }
        }
        if (resourceEntityIds.isEmpty()) {
            return new LinkedHashSet<>(resourceIds); // 无有效ID = 全部拒绝
        }

        // 6. 批量查询实例级权限（1次查询）
        List<RolePermEntry> instanceEntries = queryInstance(
            tenantId,
            roleIds,
            resourceEntityIds,
            resolveBitMasks(tenantId, Set.of(resourceTypeValue), Set.of(operationId))
        );

        // 8. 评估实例级权限的条件和冲突
        if (!instanceEntries.isEmpty()) {
            instanceEntries = conditionDomainService.evaluate(tenantId, instanceEntries, Map.of());
        }
        if (!instanceEntries.isEmpty()) {
            instanceEntries = conflictDomainService.filterPermMutex(tenantId, instanceEntries);
        }

        // 9. 收集允许的资源ID
        Set<Long> allowedEntityIds = new HashSet<>();
        for (RolePermEntry entry : instanceEntries) {
            if (entry.resourceEntityId() != null) {
                allowedEntityIds.add(entry.resourceEntityId());
            }
        }

        // 10. 计算被拒绝的ID（内存操作）
        Set<ID> denied = new LinkedHashSet<>();
        for (ID id : resourceIds) {
            Long entityId = toLongId(id);
            if (entityId == null || !allowedEntityIds.contains(entityId)) {
                denied.add(id);
            }
        }
        return denied;
    }

    /**
     * 将ID转换为Long类型。支持Long、Integer、String、Number类型。
     *
     * @param id 原始ID
     * @return Long类型ID，转换失败返回null
     */
    private <ID> Long toLongId(ID id) {
        if (id == null) {
            return null;
        }
        if (id instanceof Long) {
            return (Long) id;
        }
        if (id instanceof Integer) {
            return ((Integer) id).longValue();
        }
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===== 公共便捷方法（适用于已解析ID的调用者） =====

    /**
     * 查询类型级权限
     *
     * @param tenantId              租户ID
     * @param roleIds               角色ID集合
     * @param resourceType          资源类型值
     * @param operationPermissionIds 操作权限ID集合
     * @return 权限条目列表
     */
    public List<RolePermEntry> queryTypeLevelPerms(Long tenantId, Set<Long> roleIds,
                                                 Integer resourceType,
                                                     Set<Long> operationPermissionIds) {
        return queryScopeAll(
            tenantId,
            roleIds,
            resolveBitMasks(tenantId, Set.of(resourceType), operationPermissionIds)
        );
    }

    /**
     * 获取拥有scopeAll权限的资源类型集合
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @param operationIds 操作ID集合
     * @return 资源类型值集合
     */
    public Set<Integer> getResourceTypesWithScopeAll(Long tenantId, Set<Long> roleIds,
                                                      Set<Long> operationIds) {
        Map<Integer, Long> bitMasks = resolveBitMasks(tenantId, entityBatchLoadService.batchLoadOperations(tenantId, operationIds)
            .values()
            .stream()
            .map(OperationPermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()), operationIds);
        return queryScopeAll(tenantId, roleIds, bitMasks).stream()
            .map(RolePermEntry::resourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    /**
     * 查询实例级权限（单实体）
     *
     * @param tenantId              租户ID
     * @param roleIds               角色ID集合
     * @param resourceEntityId      资源实体ID
     * @param operationPermissionId 操作权限ID
     * @param inheritMode           继承模式
     * @return 权限条目列表（已过滤matchesBit）
     */
    public List<RolePermEntry> queryInstancePerms(Long tenantId, Set<Long> roleIds,
                                                    Long resourceEntityId,
                                                    Long operationPermissionId,
                                                    String inheritMode) {
        Set<Long> entityIds = new HashSet<>();
        entityIds.add(resourceEntityId);
        OperationPermission targetOp = entityBatchLoadService.batchLoadOperations(tenantId, Set.of(operationPermissionId)).get(operationPermissionId);
        if (targetOp == null || targetOp.getResourceType() == null) {
            return List.of();
        }
        return queryInstance(
            tenantId,
            roleIds,
            entityIds,
            resolveBitMasks(tenantId, Set.of(targetOp.getResourceType()), Set.of(operationPermissionId))
        );
    }

    /**
     * 查询实例级权限（批量实体）
     *
     * @param tenantId        租户ID
     * @param roleIds         角色ID集合
     * @param resourceEntityIds 资源实体ID集合
     * @return 权限条目列表
     */
    public List<RolePermEntry> queryInstancePermsBatch(Long tenantId, Set<Long> roleIds,
                                                         Set<Long> resourceEntityIds) {
        return rolePermMapper.selectInstancePerms(tenantId, roleIds, resourceEntityIds, null)
            .stream()
            .map(entryMapper::toEntry)
            .toList();
    }

    // ===== 私有步骤方法 =====

    /**
     * 解析查询参数中的角色ID
     */
    private Set<Long> resolveRoleIds(PermQuery q) {
        if (q.roleIds() != null && !q.roleIds().isEmpty()) {
            return q.roleIds();
        }
        if (q.userId() == null) {
            return Set.of();
        }
        if (q.useRoleCache()) {
            return userRoleDomainService.resolveEffectiveRoles(q.tenantId(), q.userId());
        }
        // 绕过缓存，直接批量解析
        Map<Long, Set<Long>> batch = userRoleDomainService.batchResolveEffectiveRoles(
            q.tenantId(), Set.of(q.userId()));
        return batch.getOrDefault(q.userId(), Set.of());
    }

    /**
     * 解析查询参数中的资源类型值（使用 ResolveContext）
     */
    private Set<Integer> resolveResourceTypes(PermQuery q, ResolveContext ctx) {
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        return new HashSet<>(ctx.getResourceTypeValues(q.resourceTypeCodes()).values());
    }

    /**
     * 解析查询参数中的操作ID（使用 ResolveContext）
     */
    private Set<Long> resolveOperationIds(PermQuery q, ResolveContext ctx) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        return ctx.getOperationIds(q.resourceTypeCodes(), q.operationCodes());
    }

    /**
     * 解析查询参数中的资源类型值（旧方法，保留兼容）
     */
    private Set<Integer> resolveResourceTypes(PermQuery q) {
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        Map<String, Integer> map = typeResolutionService.batchResolveTypeValues(
            q.tenantId(), "resource_type", q.resourceTypeCodes());
        return new HashSet<>(map.values());
    }

    /**
     * 解析查询参数中的操作ID（旧方法，保留兼容）
     */
    private Set<Long> resolveOperationIds(PermQuery q) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        // 批量解析：遍历所有resourceTypeCode，合并结果
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        Set<Long> result = new HashSet<>();
        for (String rtCode : q.resourceTypeCodes()) {
            Map<String, Long> map = typeResolutionService.batchResolveOperationIds(
                q.tenantId(), rtCode, q.operationCodes());
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 解析查询参数中的资源实体ID
     */
    private Set<Long> resolveEntityIds(PermQuery q) {
        if (q.resourceEntityIds() != null && !q.resourceEntityIds().isEmpty()) {
            return q.resourceEntityIds();
        }
        if (q.resourceCodes() == null || q.resourceCodes().isEmpty()) return Set.of();
        // 批量解析：遍历所有resourceTypeCode，合并结果
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();

        Set<Long> allResolved = new HashSet<>();
        for (String rtCode : q.resourceTypeCodes()) {
            List<ResourceResolveRequest> requests = q.resourceCodes().stream()
                .map(code -> new ResourceResolveRequest(rtCode, code, q.codeType(), null))
                .toList();

            Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(q.tenantId(), requests);
            allResolved.addAll(resolved.values());
        }

        return allResolved;
    }

    /**
     * 查询类型级权限（scopeAll=true）
     */
    private List<RolePermEntry> queryScopeAll(Long tenantId, Set<Long> roleIds,
                                               Map<Integer, Long> bitMasks) {
        if (bitMasks == null || bitMasks.isEmpty()) {
            return List.of();
        }
        List<RolePermEntry> result = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : bitMasks.entrySet()) {
            result.addAll(rolePermMapper.selectScopeAllPermsByBits(
                tenantId,
                roleIds,
                Set.of(entry.getKey()),
                entry.getValue()
            ).stream().map(entryMapper::toEntry).toList());
        }
        return result;
    }

    /**
     * 查询实例级权限
     */
    private List<RolePermEntry> queryInstance(Long tenantId, Set<Long> roleIds,
                                               Set<Long> entityIds, Map<Integer, Long> bitMasks) {
        if (bitMasks == null || bitMasks.isEmpty()) {
            return List.of();
        }
        List<RolePermEntry> result = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : bitMasks.entrySet()) {
            result.addAll(rolePermMapper.selectInstancePermsByBits(
                tenantId,
                roleIds,
                entityIds,
                Set.of(entry.getKey()),
                entry.getValue()
            ).stream().map(entryMapper::toEntry).toList());
        }
        return result;
    }

    /**
     * 按需评估条件和冲突
     */
    private List<RolePermEntry> evaluateIfNeeded(PermQuery q, List<RolePermEntry> entries) {
        if (entries.isEmpty()) return entries;
        Map<String, Object> ctx = q.context();
        if (ctx == null) ctx = Map.of();
        if (q.evaluateConditions()) {
            entries = conditionDomainService.evaluate(q.tenantId(), entries, ctx);
        }
        if (q.evaluateConflicts() && !entries.isEmpty()) {
            entries = conflictDomainService.filterPermMutex(q.tenantId(), entries);
        }
        return entries;
    }

    /**
     * 加载辅助实体（资源、操作、角色）
     */
    private void loadAncillary(PermQuery q, PermResult.Builder builder,
                                List<RolePermEntry> scopeAll, List<RolePermEntry> instance,
                                Set<Long> roleIds) {
        Set<Long> allEntityIds = new HashSet<>();
        Set<Long> targetOpIds = resolveOperationIds(q);
        Map<Integer, Set<Long>> grantedBitsByType = new LinkedHashMap<>();
        Stream.concat(scopeAll.stream(), instance.stream()).forEach(e -> {
            if (e.resourceEntityId() != null) allEntityIds.add(e.resourceEntityId());
            if (e.resourceType() != null && e.grantedBits() != null) {
                grantedBitsByType.computeIfAbsent(e.resourceType(), _unused -> new LinkedHashSet<>()).add(e.grantedBits());
            }
        });
        // 同时包含查询参数中的resourceEntityIds（scopeAll权限的entityId可能为null）
        if (q.resourceEntityIds() != null) allEntityIds.addAll(q.resourceEntityIds());

        if (q.includeResources()) {
            builder.resourceMap(entityBatchLoadService.batchLoadResources(q.tenantId(), allEntityIds));
        }
        if (q.includeOperations()) {
            Map<Long, OperationPermission> operationMap = new LinkedHashMap<>(entityBatchLoadService.batchLoadOperations(q.tenantId(), targetOpIds));
            Map<Integer, List<OperationPermission>> operationsByType = entityBatchLoadService.batchLoadOperationsByResourceTypes(
                q.tenantId(),
                grantedBitsByType.keySet()
            );
            for (Map.Entry<Integer, Set<Long>> entry : grantedBitsByType.entrySet()) {
                for (OperationPermission operation : operationsByType.getOrDefault(entry.getKey(), List.of())) {
                    if (entry.getValue().contains(operation.getBinaryBit())) {
                        operationMap.put(operation.getId(), operation);
                    }
                }
            }
            builder.operationMap(operationMap);
        }
        if (q.includeRoles() && roleIds != null && !roleIds.isEmpty()) {
            builder.roleMap(entityBatchLoadService.batchLoadRoles(q.tenantId(), roleIds));
        }
    }

    /**
     * 解析位掩码映射
     * <p>
     * 为每个资源类型计算位掩码，用于SQL位操作查询。
     * 使用 OperationPermissionCacheService 的双索引缓存优化查找效率。
     * </p>
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @param opIds         操作权限ID集合
     * @return resourceType → bitMask 映射
     */
    private Map<Integer, Long> resolveBitMasks(Long tenantId, Set<Integer> resourceTypes, Set<Long> opIds) {
        if (resourceTypes == null || resourceTypes.isEmpty() || opIds == null || opIds.isEmpty()) {
            return Map.of();
        }
        // 使用 EntityBatchLoadService 获取目标操作权限（按ID）
        Map<Long, OperationPermission> targetOps = entityBatchLoadService.batchLoadOperations(tenantId, opIds);
        if (targetOps.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Integer resourceType : resourceTypes) {
            // 使用 OperationPermissionCacheService 获取该资源类型的所有操作权限
            // 该方法会缓存到双索引（ID索引和binaryBit反向索引）
            Map<Long, OperationPermission> opMap = operationPermissionCacheService.loadByResourceType(tenantId, resourceType);
            if (opMap.isEmpty()) {
                continue;
            }

            long mask = 0L;
            for (OperationPermission targetOp : targetOps.values()) {
                if (!Objects.equals(resourceType, targetOp.getResourceType())) {
                    continue;
                }
                // 计算覆盖目标操作的位掩码
                mask |= OperationPermissionUtils.computeCoveringBitMask(opMap.values(), targetOp.getBinaryBit());
            }
            if (mask != 0L) {
                result.put(resourceType, mask);
            }
        }
        return result;
    }
}