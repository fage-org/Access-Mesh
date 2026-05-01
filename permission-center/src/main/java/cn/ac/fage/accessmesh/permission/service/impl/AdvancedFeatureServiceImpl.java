package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.GroupRoleExtraRolesListReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleDetectReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleReq;
import cn.ac.fage.accessmesh.permission.dto.req.ConflictRuleUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.*;
import cn.ac.fage.accessmesh.permission.entity.*;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.*;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.OperationLogTableDef.OPERATION_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionChangeLogTableDef.PERMISSION_CHANGE_LOG;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConditionTableDef.PERMISSION_CONDITION;
import static cn.ac.fage.accessmesh.permission.entity.table.PermissionConflictRuleTableDef.PERMISSION_CONFLICT_RULE;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class AdvancedFeatureServiceImpl implements AdvancedFeatureService {

    private final PermissionConditionMapper conditionMapper;
    private final PermissionConflictRuleMapper conflictRuleMapper;
    private final PermissionChangeLogMapper changeLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final UserRoleDomainService userRoleDomainService;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;

    public AdvancedFeatureServiceImpl(PermissionConditionMapper conditionMapper,
                                      PermissionConflictRuleMapper conflictRuleMapper,
                                      PermissionChangeLogMapper changeLogMapper,
                                      OperationLogMapper operationLogMapper,
                                      ResourceDependencyMapper dependencyMapper,
                                      ResourceEntityMapper resourceEntityMapper,
                                      OperationPermissionMapper operationPermissionMapper,
                                      RoleResourcePermissionMapper rolePermMapper,
                                      UserRoleMapper userRoleMapper,
                                      AbstractRoleMapper abstractRoleMapper,
                                      PermissionVersionDomainService permissionVersionDomainService,
                                      UserRoleDomainService userRoleDomainService,
                                      TypeResolutionService typeResolutionService,
                                      OperationLogDomainService operationLogDomainService) {
        this.conditionMapper = conditionMapper;
        this.conflictRuleMapper = conflictRuleMapper;
        this.changeLogMapper = changeLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.userRoleDomainService = userRoleDomainService;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
    }

    // ===== PermissionCondition =====

    @Override
    @Transactional
    public ConditionResp createCondition(Long tenantId, ConditionCreateReq req, Long operatorId) {
        PermissionCondition condition = new PermissionCondition();
        condition.setTenantId(tenantId);
        condition.setCode(req.code());
        condition.setName(req.name());
        condition.setConditionRules(req.conditionRules());
        condition.setEnabled(req.enabled() != null ? req.enabled() : true);
        condition.setDescription(req.description());
        condition.setCreatedBy(operatorId);
        condition.setCreatedAt(LocalDateTime.now());
        condition.setUpdatedAt(LocalDateTime.now());
        condition.setDeleteFlag(0L);
        conditionMapper.insert(condition);
        return toConditionResp(condition);
    }

    @Override
    public ConditionResp getCondition(Long tenantId, Long conditionId) {
        PermissionCondition condition = conditionMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.ID.eq(conditionId))
                .and(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        );
        return condition != null ? toConditionResp(condition) : null;
    }

    @Override
    @Transactional
    public ConditionResp updateCondition(Long tenantId, ConditionUpdateReq req, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(req.conditionId());
        if (condition == null || condition.getDeleteFlag() != 0L || !tenantId.equals(condition.getTenantId())) {
            throw new IllegalArgumentException("Condition not found: " + req.conditionId());
        }
        if (req.name() != null) {
            condition.setName(req.name());
        }
        if (req.conditionRules() != null) {
            condition.setConditionRules(req.conditionRules());
        }
        if (req.enabled() != null) {
            condition.setEnabled(req.enabled());
        }
        if (req.description() != null) {
            condition.setDescription(req.description());
        }
        condition.setUpdatedAt(LocalDateTime.now());
        conditionMapper.update(condition);
        return toConditionResp(condition);
    }

    @Override
    public List<ConditionResp> listConditions(Long tenantId) {
        return conditionMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONDITION.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONDITION.DELETE_FLAG.eq(0))
        ).stream().map(this::toConditionResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteCondition(Long tenantId, Long conditionId, Long operatorId) {
        PermissionCondition condition = conditionMapper.selectOneById(conditionId);
        if (condition != null && condition.getDeleteFlag() == 0L && condition.getTenantId().equals(tenantId)) {
            condition.setDeleteFlag(condition.getId());
            condition.setDeletedAt(LocalDateTime.now());
            conditionMapper.update(condition);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConditionsByIds(Long tenantId, List<Long> ids, Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            deleteCondition(tenantId, id, operatorId);
            n++;
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                "perm",
                "permission-condition-remove",
                "BATCH",
                tenantId,
                "batch soft-delete permission_condition, count=" + n + ", ids=" + ids,
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    // ===== PermissionConflictRule =====

    @Override
    @Transactional
    public ConflictRuleResp createConflictRule(Long tenantId, ConflictRuleReq req, Long operatorId) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setTenantId(tenantId);
        rule.setBizDomainId(req.bizDomainId());
        rule.setConflictType(req.conflictType());
        rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        rule.setResourceTypeValue(req.resourceTypeValue());
        rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        rule.setDescription(req.description());
        rule.setCreatedBy(operatorId);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setDeleteFlag(0L);
        conflictRuleMapper.insert(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictRuleResp getConflictRule(Long tenantId, Long ruleId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.ID.eq(ruleId))
                .and(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        return rule != null ? toConflictRuleResp(rule) : null;
    }

    @Override
    public List<ConflictRuleResp> listConflictRules(Long tenantId) {
        return conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        ).stream().map(this::toConflictRuleResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConflictRuleResp updateConflictRule(Long tenantId, ConflictRuleUpdateReq req, Long operatorId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneById(req.id());
        if (rule == null || rule.getDeleteFlag() != 0L || !tenantId.equals(rule.getTenantId())) {
            throw new IllegalArgumentException("Conflict rule not found: " + req.id());
        }
        if (req.bizDomainId() != null) rule.setBizDomainId(req.bizDomainId());
        if (req.conflictType() != null) rule.setConflictType(req.conflictType());
        if (req.firstOperationPermissionId() != null) rule.setFirstOperationPermissionId(req.firstOperationPermissionId());
        if (req.secondOperationPermissionId() != null) rule.setSecondOperationPermissionId(req.secondOperationPermissionId());
        if (req.resourceTypeValue() != null) rule.setResourceTypeValue(req.resourceTypeValue());
        if (req.firstAbstractRoleId() != null) rule.setFirstAbstractRoleId(req.firstAbstractRoleId());
        if (req.secondAbstractRoleId() != null) rule.setSecondAbstractRoleId(req.secondAbstractRoleId());
        if (req.description() != null) rule.setDescription(req.description());
        rule.setUpdatedAt(LocalDateTime.now());
        conflictRuleMapper.update(rule);
        return toConflictRuleResp(rule);
    }

    @Override
    public ConflictDetectResp detectConflictRule(Long tenantId, ConflictRuleDetectReq req) {
        java.util.List<PermissionConflictRule> rules = conflictRuleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(PERMISSION_CONFLICT_RULE.TENANT_ID.eq(tenantId))
                .and(req.resourceTypeValue() == null
                    ? PERMISSION_CONFLICT_RULE.ID.isNotNull()
                    : PERMISSION_CONFLICT_RULE.RESOURCE_TYPE_VALUE.eq(req.resourceTypeValue()))
                .and(PERMISSION_CONFLICT_RULE.DELETE_FLAG.eq(0))
        );
        List<ConflictRuleResp> matched = rules.stream().filter(rule ->
            (Objects.equals(rule.getFirstOperationPermissionId(), req.firstOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.secondOperationPermissionId()))
                || (Objects.equals(rule.getFirstOperationPermissionId(), req.secondOperationPermissionId())
                && Objects.equals(rule.getSecondOperationPermissionId(), req.firstOperationPermissionId()))
        ).map(this::toConflictRuleResp).collect(Collectors.toList());
        return new ConflictDetectResp(!matched.isEmpty(), matched);
    }

    @Override
    @Transactional
    public void deleteConflictRule(Long tenantId, Long ruleId, Long operatorId) {
        PermissionConflictRule rule = conflictRuleMapper.selectOneById(ruleId);
        if (rule != null && rule.getDeleteFlag() == 0L && rule.getTenantId().equals(tenantId)) {
            rule.setDeleteFlag(rule.getId());
            rule.setDeletedAt(LocalDateTime.now());
            conflictRuleMapper.update(rule);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConflictRulesByIds(Long tenantId, List<Long> ids, Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            deleteConflictRule(tenantId, id, operatorId);
            n++;
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                "perm",
                "conflict-rule-remove",
                "BATCH",
                tenantId,
                "batch soft-delete permission_conflict_rule, count=" + n + ", ids=" + ids,
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    // ===== PermissionChangeLog =====

    @Override
    public List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit) {
        QueryWrapper qw = changeLogBaseQuery(tenantId, entityType, entityId);
        qw.orderBy(PERMISSION_CHANGE_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return changeLogMapper.selectListByQuery(qw)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogs(Long tenantId, String entityType, Long entityId) {
        return changeLogMapper.selectCountByQuery(changeLogBaseQuery(tenantId, entityType, entityId));
    }

    private QueryWrapper changeLogBaseQuery(Long tenantId, String entityType, Long entityId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(PERMISSION_CHANGE_LOG.TENANT_ID.eq(tenantId));
        if (entityType != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_TYPE.eq(entityType));
        }
        if (entityId != null) {
            qw.and(PERMISSION_CHANGE_LOG.ENTITY_ID.eq(entityId));
        }
        return qw;
    }

    @Override
    public List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit) {
        return changeLogMapper.selectByAffectedUser(tenantId, userId, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogsForUser(Long tenantId, Long userId) {
        return changeLogMapper.countByAffectedUser(tenantId, userId);
    }

    @Override
    public List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                     LocalDateTime since, LocalDateTime until,
                                                     List<String> eventTypes, int offset, int limit) {
        return changeLogMapper.selectFiltered(tenantId, userId, roleId, since, until, eventTypes, offset, limit)
            .stream().map(this::toChangeLogResp).collect(Collectors.toList());
    }

    @Override
    public long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                       LocalDateTime since, LocalDateTime until,
                                       List<String> eventTypes) {
        return changeLogMapper.countFiltered(tenantId, userId, roleId, since, until, eventTypes);
    }

    // ===== OperationLog =====

    @Override
    public List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit) {
        QueryWrapper qw = operationLogBaseQuery(tenantId, module, action);
        qw.orderBy(OPERATION_LOG.CREATED_AT.desc())
          .limit(limit)
          .offset(offset);

        return operationLogMapper.selectListByQuery(qw)
            .stream().map(this::toOperationLogResp).collect(Collectors.toList());
    }

    @Override
    public long countOperationLogs(Long tenantId, String module, String action) {
        return operationLogMapper.selectCountByQuery(operationLogBaseQuery(tenantId, module, action));
    }

    private QueryWrapper operationLogBaseQuery(Long tenantId, String module, String action) {
        QueryWrapper qw = QueryWrapper.create()
            .where(OPERATION_LOG.TENANT_ID.eq(tenantId));
        if (module != null) {
            qw.and(OPERATION_LOG.MODULE.eq(module));
        }
        if (action != null) {
            qw.and(OPERATION_LOG.ACTION.eq(action));
        }
        return qw;
    }

    // ===== ResourceDependency =====

    @Override
    @Transactional
    public ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId) {
        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new IllegalArgumentException("Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new IllegalArgumentException("Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        Long sourceOperationBits = resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode());
        Long requiredOperationBits = resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode());

        ResourceDependency dep = new ResourceDependency();
        dep.setTenantId(tenantId);
        dep.setResourceEntityId(sourceId);
        dep.setDependsOnResourceEntityId(targetId);
        dep.setSourceOperationBits(sourceOperationBits);
        dep.setRequiredOperationBits(requiredOperationBits);
        dep.setAutoGrant(req.autoGrant() != null ? req.autoGrant() : true);
        dep.setDescription(req.description());
        dep.setCreatedBy(operatorId);
        dep.setCreatedAt(LocalDateTime.now());
        dep.setUpdatedAt(LocalDateTime.now());
        dep.setDeleteFlag(0L);
        dependencyMapper.insert(dep);
        return toDependencyResp(dep);
    }

    @Override
    public List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0));
        if (resourceEntityId != null) {
            qw.and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId));
        }
        return dependencyMapper.selectListByQuery(qw)
            .stream().map(this::toDependencyResp).collect(Collectors.toList());
    }

    @Override
    public List<ResourceDependencyResp> listAllDependencies(Long tenantId) {
        return dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        ).stream().map(this::toDependencyResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId) {
        ResourceDependency dep = dependencyMapper.selectOneById(req.id());
        if (dep == null || dep.getDeleteFlag() != 0L || !tenantId.equals(dep.getTenantId())) {
            throw new IllegalArgumentException("Dependency not found: " + req.id());
        }
        if (req.sourceOperationCodes() != null) {
            dep.setSourceOperationBits(resolveOperationBits(tenantId, req.sourceOperationCodes(), req.sourceResourceTypeCode()));
        }
        if (req.requiredOperationCodes() != null) {
            dep.setRequiredOperationBits(resolveOperationBits(tenantId, req.requiredOperationCodes(), req.targetResourceTypeCode()));
        }
        if (req.autoGrant() != null) dep.setAutoGrant(req.autoGrant());
        if (req.description() != null) dep.setDescription(req.description());
        dep.setUpdatedAt(LocalDateTime.now());
        dependencyMapper.update(dep);
        return toDependencyResp(dep);
    }

    @Override
    public boolean hasDependencyCycle(Long tenantId, ResourceDependencyCheckReq req) {
        Long sourceId = typeResolutionService.resolveResourceId(
            tenantId, req.sourceResourceTypeCode(), req.sourceResourceCode(), req.sourceCodeType(), null);
        if (sourceId == null) {
            throw new IllegalArgumentException("Source resource not found: " + req.sourceResourceTypeCode() + "/" + req.sourceResourceCode());
        }
        Long targetId = typeResolutionService.resolveResourceId(
            tenantId, req.targetResourceTypeCode(), req.targetResourceCode(), req.targetCodeType(), null);
        if (targetId == null) {
            throw new IllegalArgumentException("Target resource not found: " + req.targetResourceTypeCode() + "/" + req.targetResourceCode());
        }
        if (Objects.equals(sourceId, targetId)) {
            return true;
        }
        List<ResourceDependency> allDeps = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (ResourceDependency dep : allDeps) {
            graph.computeIfAbsent(dep.getResourceEntityId(), k -> new HashSet<>())
                .add(dep.getDependsOnResourceEntityId());
        }
        graph.computeIfAbsent(sourceId, k -> new HashSet<>()).add(targetId);
        return canReach(graph, targetId, sourceId, new HashSet<>());
    }

    private boolean canReach(Map<Long, Set<Long>> graph, Long current, Long target, Set<Long> visited) {
        if (Objects.equals(current, target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (Long next : graph.getOrDefault(current, Set.of())) {
            if (canReach(graph, next, target, visited)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void deleteDependency(Long tenantId, Long dependencyId, Long operatorId) {
        ResourceDependency dep = dependencyMapper.selectOneById(dependencyId);
        if (dep != null && dep.getDeleteFlag() == 0L && dep.getTenantId().equals(tenantId)) {
            dep.setDeleteFlag(dep.getId());
            dep.setDeletedAt(LocalDateTime.now());
            dependencyMapper.update(dep);
        }
    }

    @Override
    @Transactional
    public void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId) {
        if (dependencyIds == null || dependencyIds.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long dependencyId : dependencyIds) {
            if (dependencyId == null) {
                continue;
            }
            deleteDependency(tenantId, dependencyId, operatorId);
            n++;
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                "perm",
                "resource-dependency-remove",
                "BATCH",
                tenantId,
                "batch soft-delete resource_dependency, count=" + n + ", ids=" + dependencyIds,
                operatorId,
                null,
                null,
                tenantId
            );
        }
    }

    @Override
    @Transactional
    public void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId) {
        boolean isFullSync = "FULL".equalsIgnoreCase(req.syncMode());
        List<DependencyBatchSyncReq.DependencySyncItem> items = req.items() == null ? List.of() : req.items();
        LocalDateTime now = LocalDateTime.now();

        if (isFullSync) {
            List<ResourceDependency> existingDeps = dependencyMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_DEPENDENCY.OWNER_SERVICE_CODE.eq(req.serviceCode()))
                    .and(RESOURCE_DEPENDENCY.MAINTAIN_SOURCE.eq(req.maintainSource()))
                    .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
            );

            for (ResourceDependency existing : existingDeps) {
                ResourceEntity sourceResource = resourceEntityMapper.selectOneById(existing.getResourceEntityId());
                ResourceEntity targetResource = resourceEntityMapper.selectOneById(existing.getDependsOnResourceEntityId());
                if (sourceResource == null || targetResource == null) {
                    continue;
                }
                String sourceCode = sourceResource.getCode();
                String targetCode = targetResource.getCode();

                boolean stillPresent = items.stream().anyMatch(item ->
                    Objects.equals(item.sourceResourceCode(), sourceCode)
                        && Objects.equals(item.targetResourceCode(), targetCode));
                if (!stillPresent) {
                    existing.setDeleteFlag(existing.getId());
                    existing.setDeletedAt(now);
                    dependencyMapper.update(existing);
                }
            }
        }

        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            Long sourceResourceId = typeResolutionService.resolveResourceId(
                tenantId, item.sourceResourceTypeCode(), item.sourceResourceCode(),
                item.sourceCodeType(), null);
            Long targetResourceId = typeResolutionService.resolveResourceId(
                tenantId, item.targetResourceTypeCode(), item.targetResourceCode(),
                item.targetCodeType(), null);
            if (sourceResourceId == null || targetResourceId == null) {
                continue;
            }

            Long sourceOperationBits = resolveOperationBits(tenantId, item.sourceOperationCodes(), item.sourceResourceTypeCode());
            Long requiredOperationBits = resolveOperationBits(tenantId, item.requiredOperationCodes(), item.targetResourceTypeCode());

            ResourceDependency existing = dependencyMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(sourceResourceId))
                    .and(RESOURCE_DEPENDENCY.DEPENDS_ON_RESOURCE_ENTITY_ID.eq(targetResourceId))
                    .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
            );

            if (existing != null) {
                if (sourceOperationBits != null) {
                    existing.setSourceOperationBits(sourceOperationBits);
                }
                if (requiredOperationBits != null) {
                    existing.setRequiredOperationBits(requiredOperationBits);
                }
                if (item.autoGrant() != null) {
                    existing.setAutoGrant(item.autoGrant());
                }
                if (item.description() != null) {
                    existing.setDescription(item.description());
                }
                existing.setOwnerServiceCode(req.serviceCode());
                existing.setMaintainSource(req.maintainSource());
                existing.setUpdatedAt(now);
                dependencyMapper.update(existing);
            } else {
                ResourceDependency dep = new ResourceDependency();
                dep.setTenantId(tenantId);
                dep.setResourceEntityId(sourceResourceId);
                dep.setDependsOnResourceEntityId(targetResourceId);
                dep.setSourceOperationBits(sourceOperationBits);
                dep.setRequiredOperationBits(requiredOperationBits);
                dep.setAutoGrant(item.autoGrant() != null ? item.autoGrant() : true);
                dep.setDescription(item.description());
                dep.setOwnerServiceCode(req.serviceCode());
                dep.setMaintainSource(req.maintainSource());
                dep.setCreatedBy(operatorId);
                dep.setCreatedAt(now);
                dep.setUpdatedAt(now);
                dep.setDeleteFlag(0L);
                dependencyMapper.insert(dep);
            }
        }
    }

    private Long resolveOperationBits(Long tenantId, List<String> operationCodes, String resourceTypeCode) {
        if (operationCodes == null || operationCodes.isEmpty()) {
            return null;
        }
        Long bits = 0L;
        for (String code : operationCodes) {
            Long opId = typeResolutionService.resolveOperationId(tenantId, code, resourceTypeCode);
            if (opId == null) {
                continue;
            }
            OperationPermission op = operationPermissionMapper.selectOneById(opId);
            if (op != null && op.getBinaryBit() != null) {
                bits |= op.getBinaryBit();
            }
        }
        return bits;
    }

    // ===== GroupRole extra-roles =====

    @Override
    @Transactional
    public void addGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new IllegalArgumentException("Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        AbstractRole groupRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(groupId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        Integer groupRoleTypeValue = typeResolutionService.resolveTypeValue(tenantId, "role_type", "GROUP_ROLE");
        if (groupRole == null || groupRoleTypeValue == null || !groupRoleTypeValue.equals(groupRole.getRoleType())) {
            throw new IllegalArgumentException("Not a valid GROUP_ROLE: " + req.groupRoleExternalId());
        }

        AbstractRole basicRole = abstractRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.ID.eq(basicRoleId))
                .and(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        );
        if (basicRole == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        // Create user_role link: the group role "contains" the basic role
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(null); // Not assigned to a specific user
        ur.setTargetType("GROUP_ROLE");
        ur.setTargetId(groupId);
        ur.setRelationId(basicRoleId);
        ur.setCreatedAt(LocalDateTime.now());
        ur.setUpdatedAt(LocalDateTime.now());
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        // Invalidate caches for all users with this group role
        userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupId);
    }

    @Override
    @Transactional
    public void removeGroupRoleExtraRole(Long tenantId, GroupRoleExtraRoleReq req, Long operatorId) {
        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.groupDomainCode());
        if (groupId == null) {
            throw new IllegalArgumentException("Group role not found: " + req.groupRoleExternalId());
        }
        Long basicRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.basicRoleTypeCode(), req.basicRoleExternalId(), req.basicDomainCode());
        if (basicRoleId == null) {
            throw new IllegalArgumentException("Basic role not found: " + req.basicRoleExternalId());
        }

        UserRole ur = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .where(USER_ROLE.TARGET_TYPE.eq("GROUP_ROLE"))
                .and(USER_ROLE.TARGET_ID.eq(groupId))
                .and(USER_ROLE.RELATION_ID.eq(basicRoleId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        );
        if (ur != null) {
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(LocalDateTime.now());
            userRoleMapper.update(ur);
            userRoleDomainService.invalidateRoleCacheByRole(tenantId, groupId);
        }
    }

    @Override
    public List<RoleSummaryResp> listGroupRoleExtraRoles(Long tenantId, GroupRoleExtraRolesListReq req) {
        Long groupId = typeResolutionService.resolveRoleId(
            tenantId, req.groupRoleTypeCode(), req.groupRoleExternalId(), req.domainCode());
        if (groupId == null) {
            return List.of();
        }
        Set<Long> basicRoleIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .where(USER_ROLE.TARGET_TYPE.eq("GROUP_ROLE"))
                .where(USER_ROLE.TARGET_ID.eq(groupId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(UserRole::getRelationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (basicRoleIds.isEmpty()) {
            return List.of();
        }
        return abstractRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                .where(ABSTRACT_ROLE.ID.in(basicRoleIds))
                .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
        ).stream()
            .map(r -> new RoleSummaryResp(
                r.getId(),
                typeResolutionService.resolveTypeCode(tenantId, "role_type", r.getRoleType()),
                r.getExternalId(),
                r.getName()))
            .collect(Collectors.toList());
    }

    // ===== Converters =====

    private ConditionResp toConditionResp(PermissionCondition c) {
        return new ConditionResp(
            c.getId(), c.getTenantId(), c.getCode(), c.getName(),
            c.getConditionRules(), c.getEnabled(), c.getDescription(), c.getCreatedAt()
        );
    }

    private ConflictRuleResp toConflictRuleResp(PermissionConflictRule r) {
        return new ConflictRuleResp(
            r.getId(), r.getTenantId(), r.getBizDomainId(), r.getConflictType(),
            r.getFirstOperationPermissionId(), r.getSecondOperationPermissionId(),
            r.getResourceTypeValue(), r.getFirstAbstractRoleId(), r.getSecondAbstractRoleId(),
            r.getDescription(), r.getCreatedAt()
        );
    }

    private ChangeLogResp toChangeLogResp(PermissionChangeLog c) {
        return new ChangeLogResp(
            c.getId(), c.getTenantId(), c.getBizDomainId(), c.getEntityType(),
            c.getEntityId(), c.getOperation(), c.getOldSnapshot(), c.getNewSnapshot(),
            c.getDiffSnapshot(), c.getAffectedAbstractUserIds(), c.getAffectedAbstractRoleIds(),
            c.getChangeReason(), c.getChangeSource(), c.getRequestId(), c.getCreatedAt()
        );
    }

    private OperationLogResp toOperationLogResp(OperationLog l) {
        return new OperationLogResp(
            l.getId(), l.getTenantId(), l.getModule(), l.getAction(),
            l.getTargetType(), l.getTargetId(), l.getSummary(), l.getOperatorId(),
            l.getOperatorName(), l.getIpAddress(), l.getRequestId(), l.getCreatedAt()
        );
    }

    private ResourceDependencyResp toDependencyResp(ResourceDependency d) {
        ResourceEntity src = resourceEntityMapper.selectOneById(d.getResourceEntityId());
        ResourceEntity dep = resourceEntityMapper.selectOneById(d.getDependsOnResourceEntityId());
        return new ResourceDependencyResp(
            d.getId(), d.getTenantId(), d.getResourceEntityId(),
            src != null ? src.getCode() : null,
            d.getDependsOnResourceEntityId(),
            dep != null ? dep.getCode() : null,
            d.getSourceOperationBits(), d.getRequiredOperationBits(),
            d.getAutoGrant(), d.getDescription(), d.getCreatedAt()
        );
    }
}
