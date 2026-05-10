package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.*;
import cn.ac.fage.accessmesh.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

/**
 * 统一权限查询引擎 — 所有权限校验的唯一入口。
 *
 * <h3>查询流程</h3>
 * <ol>
 *   <li>解析用户角色（缓存L1→L2→DB）</li>
 *   <li>解析资源类型码和操作码为内部ID（批量）</li>
 *   <li>查询类型级权限（scopeAll=true）— 1条SQL</li>
 *   <li>如果scopeAll匹配则提前返回</li>
 *   <li>查询实例级权限 — 1条SQL</li>
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
     */
    public PermQueryEngine(UserRoleDomainService userRoleDomainService,
                           RoleResourcePermissionMapper rolePermMapper,
                           EntityBatchLoadDomainService entityBatchLoadService,
                           PermissionConditionDomainService conditionDomainService,
                           PermissionConflictDomainService conflictDomainService,
                           RolePermEntryMapper entryMapper,
                           TypeResolutionService typeResolutionService) {
        this.userRoleDomainService = userRoleDomainService;
        this.rolePermMapper = rolePermMapper;
        this.entityBatchLoadService = entityBatchLoadService;
        this.conditionDomainService = conditionDomainService;
        this.conflictDomainService = conflictDomainService;
        this.entryMapper = entryMapper;
        this.typeResolutionService = typeResolutionService;
    }

    /**
     * 执行统一权限查询
     *
     * @param q 权限查询参数
     * @return 权限查询结果
     */
    public PermResult query(PermQuery q) {
        // ── 1. 解析角色 ──
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        // ── 2. 解析资源类型 ──
        Set<Integer> resourceTypes = resolveResourceTypes(q);

        // ── 3. 解析操作ID ──
        Set<Long> opIds = resolveOperationIds(q);

        // ── 4. 查询类型级权限（scopeAll=true） ──
        List<RolePermEntry> scopeAllEntries = List.of();
        if (q.queryScopeAll() && !resourceTypes.isEmpty() && !opIds.isEmpty()) {
            scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, resourceTypes, opIds);
        }

        // ── 5. scopeAll匹配时提前返回 ──
        if (!scopeAllEntries.isEmpty() && q.earlyReturnOnScopeAll()) {
            scopeAllEntries = evaluateIfNeeded(q, scopeAllEntries);
            if (scopeAllEntries.isEmpty()) {
                return PermResult.deny("CONDITION_NOT_MET_OR_CONFLICT");
            }
            Map<String, Object> ctx = q.context();
            if (ctx == null) ctx = Map.of();
            PermResult.Builder builder = PermResult.builder(true, null)
                .scopeAllMatched(true)
                .scopeAllEntries(scopeAllEntries);
            loadAncillary(q, builder, scopeAllEntries, List.of(), roleIds);
            return builder.build();
        }

        // ── 6. 解析并查询实例级权限 ──
        Set<Long> entityIds = resolveEntityIds(q);
        List<RolePermEntry> instanceEntries = List.of();
        Map<Long, OperationPermission> opCache = Map.of();

        if (q.queryInstance() && !entityIds.isEmpty()) {
            instanceEntries = queryInstance(q.tenantId(), roleIds, entityIds, opIds);
            // matchesBit过滤
            if (q.evaluateMatchesBit() && !opIds.isEmpty()) {
                opCache = entityBatchLoadService.batchLoadOperations(q.tenantId(), opIds);
                List<RolePermEntry> filtered = new ArrayList<>();
                for (RolePermEntry e : instanceEntries) {
                    OperationPermission granted = opCache.get(e.operationPermissionId());
                    if (granted != null) {
                        for (Long targetOpId : opIds) {
                            OperationPermission target = opCache.get(targetOpId);
                            if (OperationPermissionUtils.covers(granted, target)) {
                                filtered.add(e);
                                break;
                            }
                        }
                    }
                }
                instanceEntries = filtered;
            }
        }

        // ── 7. 合并scopeAll和实例级结果 ──
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
     * 批量权限检查 — 优化版，最小化数据库查询。
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

        // 1. Resolve user roles (1 query)
        Set<Long> roleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, operatorId, null);
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>(resourceIds); // No roles = all denied
        }

        // 2. Resolve type and operation IDs (batch)
        Integer resourceTypeValue = typeResolutionService.resolveTypeValue(tenantId, "resource_type", resourceTypeCode);
        if (resourceTypeValue == null) {
            return new LinkedHashSet<>(resourceIds); // Unknown type = all denied
        }
        Long operationId = typeResolutionService.resolveOperationId(tenantId, resourceTypeCode, operationCode);
        if (operationId == null) {
            return new LinkedHashSet<>(resourceIds); // Unknown operation = all denied
        }

        // 3. Query type-level permissions (scopeAll=true) - 1 query
        List<RolePermEntry> scopeAllEntries = queryScopeAll(tenantId, roleIds,
            Set.of(resourceTypeValue), Set.of(operationId));

        // 4. If scopeAll matched, all resources are allowed
        if (!scopeAllEntries.isEmpty()) {
            // Evaluate conditions if needed
            List<RolePermEntry> evaluated = conditionDomainService.evaluate(tenantId, scopeAllEntries, Map.of());
            if (!evaluated.isEmpty()) {
                evaluated = conflictDomainService.filterPermMutex(tenantId, evaluated);
                if (!evaluated.isEmpty()) {
                    return Set.of(); // All allowed via scopeAll
                }
            }
        }

        // 5. Convert resourceIds to Long for batch query
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
            return new LinkedHashSet<>(resourceIds); // No valid IDs = all denied
        }

        // 6. Query instance-level permissions in batch (1 query)
        List<RolePermEntry> instanceEntries = queryInstance(tenantId, roleIds, resourceEntityIds, Set.of(operationId));

        // 7. Filter by operation permission bits
        if (!instanceEntries.isEmpty()) {
            Map<Long, OperationPermission> opCache = entityBatchLoadService.batchLoadOperations(
                tenantId, Set.of(operationId));
            OperationPermission targetOp = opCache.get(operationId);
            if (targetOp != null) {
                instanceEntries = OperationPermissionUtils.filterByOperation(instanceEntries, opCache, targetOp);
            }
        }

        // 8. Evaluate conditions and conflicts on instance entries
        if (!instanceEntries.isEmpty()) {
            instanceEntries = conditionDomainService.evaluate(tenantId, instanceEntries, Map.of());
        }
        if (!instanceEntries.isEmpty()) {
            instanceEntries = conflictDomainService.filterPermMutex(tenantId, instanceEntries);
        }

        // 9. Collect allowed resource IDs
        Set<Long> allowedEntityIds = new HashSet<>();
        for (RolePermEntry entry : instanceEntries) {
            if (entry.resourceEntityId() != null) {
                allowedEntityIds.add(entry.resourceEntityId());
            }
        }

        // 10. Compute denied IDs (memory operation)
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
        return queryScopeAll(tenantId, roleIds, Set.of(resourceType), operationPermissionIds);
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
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildTypeLevel(tenantId, roleIds, Set.of(), operationIds));
        return perms.stream().map(RoleResourcePermission::getResourceType).filter(Objects::nonNull).collect(Collectors.toSet());
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
        List<RolePermEntry> entries = queryInstance(tenantId, roleIds, entityIds, Set.of(operationPermissionId));
        if (entries.isEmpty()) return entries;
        // matchesBit过滤
        Map<Long, OperationPermission> opCache = entityBatchLoadService.batchLoadOperations(tenantId, Set.of(operationPermissionId));
        OperationPermission targetOp = opCache.get(operationPermissionId);
        if (targetOp == null) return List.of();
        return OperationPermissionUtils.filterByOperation(entries, opCache, targetOp);
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
        return queryInstance(tenantId, roleIds, resourceEntityIds, Set.of());
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
            return userRoleDomainService.resolveEffectiveRoles(q.tenantId(), q.userId(), q.bizDomainId());
        }
        // bypass cache — direct batch resolve
        Map<Long, Set<Long>> batch = userRoleDomainService.batchResolveEffectiveRoles(
            q.tenantId(), Set.of(q.userId()), q.bizDomainId());
        return batch.getOrDefault(q.userId(), Set.of());
    }

    /**
     * 解析查询参数中的资源类型值
     */
    private Set<Integer> resolveResourceTypes(PermQuery q) {
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        Map<String, Integer> map = typeResolutionService.batchResolveTypeValues(
            q.tenantId(), "resource_type", q.resourceTypeCodes());
        return new HashSet<>(map.values());
    }

    /**
     * 解析查询参数中的操作ID
     */
    private Set<Long> resolveOperationIds(PermQuery q) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        // 批量解析：取第一个resourceTypeCode用于操作查找
        String rtCode = q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()
            ? q.resourceTypeCodes().iterator().next() : null;
        if (rtCode == null) return Set.of();
        Map<String, Long> map = typeResolutionService.batchResolveOperationIds(
            q.tenantId(), rtCode, q.operationCodes());
        return new HashSet<>(map.values());
    }

    /**
     * 解析查询参数中的资源实体ID
     */
    private Set<Long> resolveEntityIds(PermQuery q) {
        if (q.resourceEntityIds() != null && !q.resourceEntityIds().isEmpty()) {
            return q.resourceEntityIds();
        }
        if (q.resourceCodes() == null || q.resourceCodes().isEmpty()) return Set.of();
        // 批量解析
        String rtCode = q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()
            ? q.resourceTypeCodes().iterator().next() : null;
        if (rtCode == null) return Set.of();

        // 构建批量解析请求（1条SQL查询替代N条）
        List<ResourceResolveRequest> requests = q.resourceCodes().stream()
            .map(code -> new ResourceResolveRequest(rtCode, code, q.codeType(), null))
            .toList();

        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(q.tenantId(), requests);

        // 返回解析后的ID集合（内存操作）
        return new HashSet<>(resolved.values());
    }

    /**
     * 查询类型级权限（scopeAll=true）
     */
    private List<RolePermEntry> queryScopeAll(Long tenantId, Set<Long> roleIds,
                                               Set<Integer> types, Set<Long> opIds) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildTypeLevel(tenantId, roleIds, types, opIds));
        return perms.stream().map(entryMapper::toEntry).toList();
    }

    /**
     * 查询实例级权限
     */
    private List<RolePermEntry> queryInstance(Long tenantId, Set<Long> roleIds,
                                               Set<Long> entityIds, Set<Long> opIds) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildInstance(tenantId, roleIds, entityIds,
                opIds.isEmpty() ? null : opIds));
        return perms.stream().map(entryMapper::toEntry).toList();
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

    // ===== 内联SQL构建器（替代PermQueryConditions） =====

    /**
     * 构建类型级权限查询条件
     */
    private QueryWrapper buildTypeLevel(Long tenantId, Set<Long> roleIds,
                                         Set<Integer> resourceTypes,
                                         Set<Long> operationPermissionIds) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.SCOPE_ALL.eq(true))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        if (resourceTypes != null && !resourceTypes.isEmpty()) {
            qw.and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_TYPE.in(resourceTypes));
        }
        if (operationPermissionIds != null && !operationPermissionIds.isEmpty()) {
            qw.and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(operationPermissionIds));
        }
        return qw;
    }

    /**
     * 构建实例级权限查询条件
     */
    private QueryWrapper buildInstance(Long tenantId, Set<Long> roleIds,
                                        Set<Long> resourceEntityIds,
                                        Set<Long> operationPermissionIds) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
            .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        if (resourceEntityIds != null && !resourceEntityIds.isEmpty()) {
            qw.and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(resourceEntityIds));
        }
        if (operationPermissionIds != null && !operationPermissionIds.isEmpty()) {
            qw.and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.OPERATION_PERMISSION_ID.in(operationPermissionIds));
        }
        return qw;
    }

    /**
     * 加载辅助实体（资源、操作、角色）
     */
    private void loadAncillary(PermQuery q, PermResult.Builder builder,
                                List<RolePermEntry> scopeAll, List<RolePermEntry> instance,
                                Set<Long> roleIds) {
        Set<Long> allEntityIds = new HashSet<>();
        Set<Long> allOpIds = new HashSet<>();
        Stream.concat(scopeAll.stream(), instance.stream()).forEach(e -> {
            if (e.resourceEntityId() != null) allEntityIds.add(e.resourceEntityId());
            if (e.operationPermissionId() != null) allOpIds.add(e.operationPermissionId());
        });
        // also include resourceEntityIds from query (for scopeAll where perms have null entityId)
        if (q.resourceEntityIds() != null) allEntityIds.addAll(q.resourceEntityIds());

        if (q.includeResources()) {
            builder.resourceMap(entityBatchLoadService.batchLoadResources(q.tenantId(), allEntityIds));
        }
        if (q.includeOperations()) {
            builder.operationMap(entityBatchLoadService.batchLoadOperations(q.tenantId(), allOpIds));
        }
        if (q.includeRoles() && roleIds != null && !roleIds.isEmpty()) {
            builder.roleMap(entityBatchLoadService.batchLoadRoles(q.tenantId(), roleIds));
        }
    }
}
