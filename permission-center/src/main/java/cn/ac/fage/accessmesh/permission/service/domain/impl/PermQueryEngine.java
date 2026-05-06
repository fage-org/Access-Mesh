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
 * Unified permission query engine — single entry point for all permission checks.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Resolve roles (cache L1→L2→DB)</li>
 *   <li>Resolve codes to internal IDs (batch)</li>
 *   <li>Query type-level (scopeAll=true) — 1 SQL</li>
 *   <li>Early return if scopeAll matched</li>
 *   <li>Query instance-level — 1 SQL</li>
 *   <li>Evaluate conditions / conflicts (per flags)</li>
 *   <li>Load ancillary entities (per flags)</li>
 *   <li>Build unified PermResult</li>
 * </ol>
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

    public PermResult query(PermQuery q) {
        // ── 1. Resolve roles ──
        Set<Long> roleIds = resolveRoleIds(q);
        if (roleIds.isEmpty()) {
            return PermResult.deny("NO_ROLE");
        }

        // ── 2. Resolve resource types ──
        Set<Integer> resourceTypes = resolveResourceTypes(q);

        // ── 3. Resolve operation IDs ──
        Set<Long> opIds = resolveOperationIds(q);

        // ── 4. Query type-level (scopeAll=true) ──
        List<RolePermEntry> scopeAllEntries = List.of();
        if (q.queryScopeAll() && !resourceTypes.isEmpty() && !opIds.isEmpty()) {
            scopeAllEntries = queryScopeAll(q.tenantId(), roleIds, resourceTypes, opIds);
        }

        // ── 5. Early return on scopeAll? ──
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

        // ── 6. Resolve + query instance-level ──
        Set<Long> entityIds = resolveEntityIds(q);
        List<RolePermEntry> instanceEntries = List.of();
        Map<Long, OperationPermission> opCache = Map.of();

        if (q.queryInstance() && !entityIds.isEmpty()) {
            instanceEntries = queryInstance(q.tenantId(), roleIds, entityIds, opIds);
            // matchesBit filter
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

        // ── 7. Combine scopeAll + instance results ──
        List<RolePermEntry> combined = new ArrayList<>(scopeAllEntries);
        combined.addAll(instanceEntries);
        if (combined.isEmpty()) {
            return PermResult.deny("NO_PERMISSION");
        }

        // evaluate conditions / conflicts on all results
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

    // ===== Validator-compatible API (replaces ResourcePermissionValidator) =====

    public void validate(Long tenantId, Long operatorId, String resourceTypeCode,
                          Object resourceId, String operationCode) {
        PermQuery q = PermQuery.forValidate(tenantId, operatorId,
            resourceTypeCode, resourceId != null ? String.valueOf(resourceId) : null, operationCode);
        PermResultUtils.validateOrThrow(query(q));
    }

    public boolean hasPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                                  Object resourceId, String operationCode) {
        PermQuery q = PermQuery.forValidate(tenantId, operatorId,
            resourceTypeCode, resourceId != null ? String.valueOf(resourceId) : null, operationCode);
        return query(q).allowed();
    }

    public <ID> void validateBatch(Long tenantId, Long operatorId, String resourceTypeCode,
                                    Set<ID> resourceIds, String operationCode) {
        Set<ID> denied = getDeniedIds(tenantId, operatorId, resourceTypeCode, resourceIds, operationCode);
        if (!denied.isEmpty())
            throw new SecurityException("Permission denied: " + operationCode + " on " + resourceTypeCode + ":" + denied);
    }

    /**
     * Batch permission check - optimized to minimize database queries.
     * <p>
     * Instead of N separate queries for N resourceIds, this method:
     * <ol>
     *   <li>Resolves user roles once (1 query)</li>
     *   <li>Queries type-level permissions (scopeAll=true) once (1 query)</li>
     *   <li>If type-level match, all resources are allowed</li>
     *   <li>Otherwise, queries instance-level permissions in batch (1 query)</li>
     *   <li>Computes denied IDs in memory</li>
     * </ol>
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
     * Convert ID to Long. Supports Long, Integer, String, and Number types.
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

    // ===== public convenience (for callers with pre-resolved IDs) =====

    public List<RolePermEntry> queryTypeLevelPerms(Long tenantId, Set<Long> roleIds,
                                                     Integer resourceType,
                                                     Set<Long> operationPermissionIds) {
        return queryScopeAll(tenantId, roleIds, Set.of(resourceType), operationPermissionIds);
    }

    public Set<Integer> getResourceTypesWithScopeAll(Long tenantId, Set<Long> roleIds,
                                                      Set<Long> operationIds) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildTypeLevel(tenantId, roleIds, Set.of(), operationIds));
        return perms.stream().map(RoleResourcePermission::getResourceType).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public List<RolePermEntry> queryInstancePerms(Long tenantId, Set<Long> roleIds,
                                                    Long resourceEntityId,
                                                    Long operationPermissionId,
                                                    String inheritMode) {
        Set<Long> entityIds = new HashSet<>();
        entityIds.add(resourceEntityId);
        List<RolePermEntry> entries = queryInstance(tenantId, roleIds, entityIds, Set.of(operationPermissionId));
        if (entries.isEmpty()) return entries;
        // matchesBit
        Map<Long, OperationPermission> opCache = entityBatchLoadService.batchLoadOperations(tenantId, Set.of(operationPermissionId));
        OperationPermission targetOp = opCache.get(operationPermissionId);
        if (targetOp == null) return List.of();
        return OperationPermissionUtils.filterByOperation(entries, opCache, targetOp);
    }

    public List<RolePermEntry> queryInstancePermsBatch(Long tenantId, Set<Long> roleIds,
                                                         Set<Long> resourceEntityIds) {
        return queryInstance(tenantId, roleIds, resourceEntityIds, Set.of());
    }

    // ===== private steps =====

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

    private Set<Integer> resolveResourceTypes(PermQuery q) {
        if (q.resourceTypeCodes() == null || q.resourceTypeCodes().isEmpty()) return Set.of();
        Map<String, Integer> map = typeResolutionService.batchResolveTypeValues(
            q.tenantId(), "resource_type", q.resourceTypeCodes());
        return new HashSet<>(map.values());
    }

    private Set<Long> resolveOperationIds(PermQuery q) {
        if (q.operationPermissionIds() != null && !q.operationPermissionIds().isEmpty()) {
            return q.operationPermissionIds();
        }
        if (q.operationCodes() == null || q.operationCodes().isEmpty()) return Set.of();
        // Batch resolve: pick first resourceTypeCode for operation lookup
        String rtCode = q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()
            ? q.resourceTypeCodes().iterator().next() : null;
        if (rtCode == null) return Set.of();
        Map<String, Long> map = typeResolutionService.batchResolveOperationIds(
            q.tenantId(), rtCode, q.operationCodes());
        return new HashSet<>(map.values());
    }

    private Set<Long> resolveEntityIds(PermQuery q) {
        if (q.resourceEntityIds() != null && !q.resourceEntityIds().isEmpty()) {
            return q.resourceEntityIds();
        }
        if (q.resourceCodes() == null || q.resourceCodes().isEmpty()) return Set.of();
        // Batch resolve
        String rtCode = q.resourceTypeCodes() != null && !q.resourceTypeCodes().isEmpty()
            ? q.resourceTypeCodes().iterator().next() : null;
        if (rtCode == null) return Set.of();

        // Build batch resolve requests (1 SQL query instead of N)
        List<ResourceResolveRequest> requests = q.resourceCodes().stream()
            .map(code -> new ResourceResolveRequest(rtCode, code, q.codeType(), null))
            .toList();

        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(q.tenantId(), requests);

        // Return resolved IDs (in-memory operation)
        return new HashSet<>(resolved.values());
    }

    private List<RolePermEntry> queryScopeAll(Long tenantId, Set<Long> roleIds,
                                               Set<Integer> types, Set<Long> opIds) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildTypeLevel(tenantId, roleIds, types, opIds));
        return perms.stream().map(entryMapper::toEntry).toList();
    }

    private List<RolePermEntry> queryInstance(Long tenantId, Set<Long> roleIds,
                                               Set<Long> entityIds, Set<Long> opIds) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            buildInstance(tenantId, roleIds, entityIds,
                opIds.isEmpty() ? null : opIds));
        return perms.stream().map(entryMapper::toEntry).toList();
    }

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

    // ===== inline SQL builders (was PermQueryConditions) =====

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
