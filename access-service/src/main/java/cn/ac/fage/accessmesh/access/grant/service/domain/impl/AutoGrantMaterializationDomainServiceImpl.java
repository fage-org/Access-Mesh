package cn.ac.fage.accessmesh.access.grant.service.domain.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.enums.GrantSource;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.DependencyEdge;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.Fact;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantMaterializationDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.grant.util.DatabaseExceptionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 自动授权物化领域服务实现（T-PERM-072）。
 * <p>
 * 一致视图批量装载种子、编译图与操作定义，多角色重算共享图；按角色 ID 升序逐角色
 * 完整重算 desired 并与 actual 精确 diff（完整事实键 = 资源 + canonical 操作位 + 条件身份）。
 * 重算始终从当前全部 MANUAL 种子开始，旧 AUTO_DEP 不作种子；desired 为存续依据，
 * 无来源的自动行同事务删除，独立 MANUAL 行不由物化删除。
 * </p>
 * <p>
 * 不按资源启停过滤（M1 定案）；不做操作覆盖或条件支配压缩（M2 定案）；条件按推导变体
 * 直传保留（含 NULL）。物化行 grant_source=AUTO_DEP、单 canonical 操作位、can_grant=false、
 * depend_on=NULL、scope_all=false。
 * </p>
 */
@Service
public class AutoGrantMaterializationDomainServiceImpl implements AutoGrantMaterializationDomainService {

    private static final Logger log = LoggerFactory.getLogger(AutoGrantMaterializationDomainServiceImpl.class);

    private final RoleResourcePermissionMapper rolePermissionMapper;
    private final AutoGrantDerivation derivation;
    private final DependencyCompilationDomainService compilation;
    private final SubjectDomainService subjectDomainService;
    private final ResourceEntityDomainService resourceEntities;
    private final OperationPermissionDomainService operations;
    private final PermissionConditionDomainService conditionDomainService;
    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;

    public AutoGrantMaterializationDomainServiceImpl(RoleResourcePermissionMapper rolePermissionMapper,
            AutoGrantDerivation derivation, DependencyCompilationDomainService compilation, SubjectDomainService subjectDomainService,
            ResourceEntityDomainService resourceEntities, OperationPermissionDomainService operations,
            PermissionConditionDomainService conditionDomainService, AuditDomainService auditDomainService,
            ObjectMapper objectMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.derivation = derivation;
        this.compilation = compilation;
        this.subjectDomainService = subjectDomainService;
        this.resourceEntities = resourceEntities;
        this.operations = operations;
        this.conditionDomainService = conditionDomainService;
        this.auditDomainService = auditDomainService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<Long> recompute(Long tenantId, Collection<Long> roleIds, Set<Long> inlineRecycleCandidates) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> candidates = new HashSet<>();
        if (inlineRecycleCandidates != null) {
            candidates.addAll(inlineRecycleCandidates);
        }
        if (roleIds == null || roleIds.isEmpty()) {
            recycleQuietly(tenantId, candidates);
            return Set.of();
        }
        TreeSet<Long> orderedRoleIds = new TreeSet<>(roleIds);
        // 防御层（T-PERM-072 外评 P2）：已删角色的滞留 MANUAL 行不作物种（不推导、不重建），
        // 其 AUTO_DEP 行按 desired 恒空在 diff 中整体回收——正常流删除通道已同事务回收，
        // 此处兜底 DB 直写/历史脏数据形态。
        // 角色维度分批：受影响角色集合与 manifest 条目数无关（一条声明×一个源资源即可波及
        // 全租户持有角色），有效角色与授权行装载同按 SqlBatches 分批防 IN 参数上限
        //（对齐本类 recycleRoleGrants 与 071 大清单先例）
        Set<Long> validRoleIds = new HashSet<>();
        List<RoleResourcePermission> rows = new ArrayList<>();
        SqlBatches.forEach(new ArrayList<>(orderedRoleIds), batch -> {
            validRoleIds.addAll(subjectDomainService
                .selectValidRolesByIds(tenantId, new LinkedHashSet<>(batch)).stream()
                .map(cn.ac.fage.accessmesh.access.role.entity.AbstractRole::getId)
                .collect(java.util.stream.Collectors.toSet()));
            rows.addAll(rolePermissionMapper.selectValidByRoleIds(tenantId, new LinkedHashSet<>(batch)));
        });
        Map<Long, List<RoleResourcePermission>> seedsByRole = new HashMap<>();
        Map<Long, Map<Fact, RoleResourcePermission>> actualAutoByRole = new HashMap<>();
        Set<Long> involvedResourceIds = new LinkedHashSet<>();
        for (RoleResourcePermission row : rows) {
            if (isAutoDep(row)) {
                actualAutoByRole.computeIfAbsent(row.getAbstractRoleId(), key -> new LinkedHashMap<>())
                    .put(factOf(row), row);
            } else if (isSeed(row) && validRoleIds.contains(row.getAbstractRoleId())) {
                seedsByRole.computeIfAbsent(row.getAbstractRoleId(), key -> new ArrayList<>()).add(row);
                involvedResourceIds.add(row.getResourceEntityId());
            }
        }

        List<DependencyEdge> edges = List.of();
        if (!seedsByRole.isEmpty() || !actualAutoByRole.isEmpty()) {
            edges = compilation.loadCompiledEdges(tenantId).stream()
                .map(e -> new DependencyEdge(e.sourceId(), e.targetId(), e.sourceOperationBits(), e.requiredOperationBits()))
                .toList();
            edges.forEach(edge -> {
                involvedResourceIds.add(edge.sourceId());
                involvedResourceIds.add(edge.targetId());
            });
        }

        Map<Long, Integer> resourceTypes = new HashMap<>();
        List<OperationPermission> operationRows = List.of();
        if (!involvedResourceIds.isEmpty()) {
            List<Long> involved = new ArrayList<>(involvedResourceIds);
            // 分批装载（种子资源 ∪ 全图边端点可超数据库参数上限——对齐 recomputeByResourceEntities 同款修复）
            SqlBatches.forEach(involved, batch -> {
                for (var entity : resourceEntities.selectValidByIds(tenantId, new LinkedHashSet<>(batch))) {
                    resourceTypes.put(entity.getId(), entity.getResourceType());
                }
            });
            if (!edges.isEmpty()) {
                operationRows = operations.selectByTenantAndResourceTypes(tenantId, new HashSet<>(resourceTypes.values()));
            }
        }

        Set<Long> changedRoles = new LinkedHashSet<>();
        for (Long roleId : orderedRoleIds) {
            List<RoleResourcePermission> roleSeeds = seedsByRole.getOrDefault(roleId, List.of());
            Map<Fact, RoleResourcePermission> actual = actualAutoByRole.getOrDefault(roleId, Map.of());
            List<Fact> seedFacts = roleSeeds.stream().map(AutoGrantMaterializationDomainServiceImpl::factOf).toList();
            List<Fact> desiredList = derivation.derive(seedFacts, resourceTypes, edges, operationRows).desiredFacts();
            Set<Fact> desired = new HashSet<>(desiredList);

            List<RoleResourcePermission> toInsert = new ArrayList<>();
            for (Fact fact : desired) {
                if (actual.containsKey(fact)) continue;
                Integer resourceType = resourceTypes.get(fact.resourceEntityId());
                // 实体已软删（防御面：资源删除触发面同事务收缩编译贡献后推导不可达；历史脏数据跳过）
                if (resourceType == null) continue;
                toInsert.add(toRow(tenantId, roleId, fact, resourceType, now));
            }
            List<Long> toDeleteIds = new ArrayList<>();
            Set<Long> toDeleteConditions = new HashSet<>();
            for (var entry : actual.entrySet()) {
                if (!desired.contains(entry.getKey())) {
                    toDeleteIds.add(entry.getValue().getId());
                    if (entry.getValue().getConditionId() != null) {
                        toDeleteConditions.add(entry.getValue().getConditionId());
                    }
                }
            }
            if (toInsert.isEmpty() && toDeleteIds.isEmpty()) continue;

            changedRoles.add(roleId);
            insertBatch(tenantId, toInsert);
            softDeleteBatch(tenantId, toDeleteIds, now);
            candidates.addAll(toDeleteConditions);
            recordRecomputeAudit(tenantId, roleId, toInsert, actual, desired);
        }

        recycleQuietly(tenantId, candidates);
        return changedRoles;
    }

    @Override
    public Set<Long> recomputeByResourceEntities(Long tenantId, Collection<Long> resourceEntityIds) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Set.of();
        }
        // 分批定位受影响角色（对齐 071 大清单先例——大 scope 清理的实体集可能超出数据库参数上限）
        List<Long> orderedIds = new ArrayList<>(resourceEntityIds);
        Set<Long> roleIds = new LinkedHashSet<>();
        SqlBatches.forEach(orderedIds,
                batch -> roleIds.addAll(rolePermissionMapper.selectRoleIdsByResourceIds(tenantId, batch)));
        return recompute(tenantId, roleIds, Set.of());
    }

    @Override
    public void recycleRoleGrants(Long tenantId, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RoleResourcePermission> rows = new ArrayList<>();
        List<Long> orderedRoleIds = new ArrayList<>(roleIds);
        SqlBatches.forEach(orderedRoleIds, batch ->
                rows.addAll(rolePermissionMapper.selectValidByRoleIds(tenantId, new LinkedHashSet<>(batch))));
        if (rows.isEmpty()) {
            return;
        }
        Set<Long> inlineCandidates = new HashSet<>();
        List<Long> rowIds = new ArrayList<>(rows.size());
        for (RoleResourcePermission row : rows) {
            rowIds.add(row.getId());
            if (row.getConditionId() != null) {
                inlineCandidates.add(row.getConditionId());
            }
        }
        softDeleteBatch(tenantId, rowIds, now);
        recycleQuietly(tenantId, inlineCandidates);
    }

    /** 种子口径（设计 §6.1）：有效 MANUAL 行、scope_all=false、实例 ID 非空、depend_on=NULL，条件不限。 */
    private boolean isSeed(RoleResourcePermission row) {
        return !Boolean.TRUE.equals(row.getScopeAll())
            && row.getResourceEntityId() != null
            && row.getDependOn() == null
            && !isAutoDep(row) && !isAuthorityRoot(row);
    }

    private boolean isAutoDep(RoleResourcePermission row) {
        return GrantSource.AUTO_DEP.getValue().equals(row.getGrantSource());
    }

    private boolean isAuthorityRoot(RoleResourcePermission row) {
        return GrantSource.AUTHORITY_ROOT.getValue().equals(row.getGrantSource());
    }

    private static Fact factOf(RoleResourcePermission row) {
        return new Fact(row.getResourceEntityId(), row.getGrantedBits(), row.getConditionId());
    }

    private RoleResourcePermission toRow(Long tenantId, Long roleId, Fact fact, Integer resourceType, LocalDateTime now) {
        RoleResourcePermission row = new RoleResourcePermission();
        row.setTenantId(tenantId);
        row.setAbstractRoleId(roleId);
        row.setResourceEntityId(fact.resourceEntityId());
        row.setGrantedBits(fact.operationBit());
        row.setResourceType(resourceType);
        row.setDependOn(null);
        row.setScopeAll(false);
        row.setCanGrant(false);
        row.setConditionId(fact.conditionId());
        row.setGrantSource(GrantSource.AUTO_DEP.getValue());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setDeleteFlag(0L);
        return row;
    }

    private void insertBatch(Long tenantId, List<RoleResourcePermission> rows) {
        SqlBatches.forEach(rows, batch -> {
            try {
                if (rolePermissionMapper.insertBatch(batch) != batch.size()) {
                    throw new BizException(AccessErrorCode.PERMISSION_NOT_FOUND.getCode(),
                        "AUTO_DEP create count does not match the derived plan");
                }
            } catch (DataIntegrityViolationException exception) {
                if (DatabaseExceptionSupport.isUniqueViolation(exception, "uk_role_resource_permission")) {
                    throw new BizException(AccessErrorCode.DIRECT_PERMISSION_CONFLICT.getCode(),
                        "AUTO_DEP row conflicts with an existing permission of the same fact key");
                }
                throw exception;
            }
        });
    }

    private void softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime now) {
        SqlBatches.forEach(ids, batch -> {
            int affected = rolePermissionMapper.softDeleteBatch(tenantId, batch, now);
            if (affected != batch.size()) {
                throw new BizException(AccessErrorCode.PERMISSION_NOT_FOUND.getCode(),
                    "AUTO_DEP remove count does not match the derived plan");
            }
        });
    }

    private void recordRecomputeAudit(Long tenantId, Long roleId, List<RoleResourcePermission> inserted,
                                      Map<Fact, RoleResourcePermission> actual, Set<Fact> desired) {
        ObjectNode diffRoot = objectMapper.createObjectNode();
        diffRoot.put("eventType", "AUTO_DEP_DIFF");
        diffRoot.put("roleId", roleId);
        ArrayNode insertedNode = diffRoot.putArray("inserted");
        inserted.forEach(row -> insertedNode.add(factNode(row.getResourceEntityId(), row.getGrantedBits(), row.getConditionId())));
        ArrayNode removedNode = diffRoot.putArray("removed");
        actual.entrySet().stream()
            .filter(entry -> !desired.contains(entry.getKey()))
            .forEach(entry -> removedNode.add(factNode(entry.getKey().resourceEntityId(),
                entry.getKey().operationBit(), entry.getKey().conditionId())));
        String diffSnapshot;
        try {
            diffSnapshot = objectMapper.writeValueAsString(diffRoot);
        } catch (Exception e) {
            diffSnapshot = "{}";
        }
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                // 服务身份触发的重算（资源/manifest 通道）无用户操作者，取可空读取（getOperatorId 会 fail-closed 拒绝）；
                // changeSource 按触发上下文区分：SERVICE 上下文=SERVICE_SYNC，用户操作入口=MANUAL
                tenantId, AccessRequestContext.getOperatorId(), OperatorContext.getRequestId(),
                AccessRequestContext.getServiceCode() != null
                    ? PermConstants.MaintainSource.SERVICE_SYNC : PermConstants.MaintainSource.MANUAL,
                "auto-grant-materialization"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "role_resource_permission", roleId, "AUTO_DEP_DIFF", null, null, diffSnapshot,
                new Long[0], new Long[] {roleId})));
    }

    private ObjectNode factNode(long resourceEntityId, long grantedBits, Long conditionId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("resourceEntityId", resourceEntityId);
        node.put("grantedBits", grantedBits);
        if (conditionId != null) node.put("conditionId", conditionId);
        else node.putNull("conditionId");
        return node;
    }

    private void recycleQuietly(Long tenantId, Set<Long> candidates) {
        if (candidates.isEmpty()) return;
        Set<Long> recycled = conditionDomainService.recycleOrphanInlineConditions(tenantId, candidates);
        if (!recycled.isEmpty()) {
            log.info("AUTO_DEP recompute recycled {} orphan inline condition(s)", recycled.size());
        }
    }
}
