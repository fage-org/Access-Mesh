package org.dromara.permission.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcPermissionConflictRule;
import org.dromara.permission.domain.PcResourceApiMapping;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcPermissionConflictRuleMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.model.permission.ConflictDetail;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.model.permission.GrantPermissionRequest;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.MatchedPermission;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.SnapshotEntry;
import org.dromara.permission.operation.ConditionEvaluator;
import org.dromara.permission.service.OperationInheritanceService;
import org.dromara.permission.service.ResourceApiMappingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PermissionBridgeSupport {

    private static final int DEPTH_LIMIT = 5;

    private final OperationInheritanceService operationInheritanceService;
    private final ResourceApiMappingService resourceApiMappingService;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcOperationPermissionMapper operationPermissionMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcPermissionConditionMapper permissionConditionMapper;
    private final PcPermissionConflictRuleMapper permissionConflictRuleMapper;
    private final PcResourceDependencyMapper resourceDependencyMapper;

    public PcResourceEntity loadResource(Long tenantId, Long resourceId) {
        PcResourceEntity resource = resourceEntityMapper.selectOne(new LambdaQueryWrapper<PcResourceEntity>()
            .eq(PcResourceEntity::getTenantId, tenantId)
            .eq(PcResourceEntity::getId, resourceId)
            .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (resource == null) {
            throw new PermissionServiceException(PermissionErrorCode.RESOURCE_NOT_FOUND);
        }
        return resource;
    }

    public PcOperationPermission loadOperation(Long tenantId, Long operationId) {
        PcOperationPermission operation = operationPermissionMapper.selectOne(new LambdaQueryWrapper<PcOperationPermission>()
            .eq(PcOperationPermission::getTenantId, tenantId)
            .eq(PcOperationPermission::getId, operationId)
            .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (operation == null) {
            throw new PermissionServiceException(PermissionErrorCode.OPERATION_NOT_FOUND);
        }
        return operation;
    }

    public void validateResourceOperationType(PcResourceEntity resource, PcOperationPermission operation) {
        if (operation.getResourceType() != null && !Objects.equals(operation.getResourceType(), resource.getResourceType())) {
            throw new PermissionServiceException(PermissionErrorCode.RESOURCE_OPERATION_TYPE_MISMATCH);
        }
    }

    public void assertGrantConditionApproved(GrantPermissionRequest request) {
        if (request.getConditionId() == null) {
            return;
        }
        PcPermissionCondition condition = permissionConditionMapper.selectOne(new LambdaQueryWrapper<PcPermissionCondition>()
            .eq(PcPermissionCondition::getTenantId, request.getTenantId())
            .eq(PcPermissionCondition::getId, request.getConditionId())
            .eq(PcPermissionCondition::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (condition == null || !PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())) {
            throw new PermissionServiceException(PermissionErrorCode.CONDITION_NOT_APPROVED);
        }
    }

    public Set<Long> expandResourceIds(PcResourceEntity resource, InheritMode inheritMode) {
        Set<Long> ids = new HashSet<>();
        ids.add(resource.getId());
        if (inheritMode == InheritMode.NONE || resource.getPath() == null || resource.getPath().isBlank()) {
            return ids;
        }
        if (inheritMode == InheritMode.CHILDREN || inheritMode == InheritMode.BOTH) {
            resourceEntityMapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
                    .eq(PcResourceEntity::getTenantId, resource.getTenantId())
                    .likeRight(PcResourceEntity::getPath, resource.getPath() + "/")
                    .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED))
                .forEach(entity -> ids.add(entity.getId()));
        }
        if (inheritMode == InheritMode.PARENT || inheritMode == InheritMode.BOTH) {
            String[] pathIds = resource.getPath().split("/");
            for (String pathId : pathIds) {
                if (!pathId.isBlank()) {
                    ids.add(Long.valueOf(pathId));
                }
            }
        }
        return ids;
    }

    public List<MatchedPermission> loadMatchedPermissions(Long tenantId, Collection<Long> roleIds, Set<Long> resourceIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<PcRoleResourcePermission> query = new LambdaQueryWrapper<PcRoleResourcePermission>()
            .eq(PcRoleResourcePermission::getTenantId, tenantId)
            .in(PcRoleResourcePermission::getAbstractRoleId, roleIds)
            .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (resourceIds != null && !resourceIds.isEmpty()) {
            query.in(PcRoleResourcePermission::getResourceEntityId, resourceIds);
        }
        return toMatchedPermissions(roleResourcePermissionMapper.selectList(query));
    }

    public List<MatchedPermission> toMatchedPermissions(List<PcRoleResourcePermission> grants) {
        return grants.stream().map(grant -> {
            MatchedPermission permission = new MatchedPermission();
            permission.setPermissionId(grant.getId());
            permission.setRoleId(grant.getAbstractRoleId());
            permission.setResourceId(grant.getResourceEntityId());
            permission.setOperationId(grant.getOperationPermissionId());
            permission.setConditionId(grant.getConditionId());
            permission.setCanManage(grant.getCanManage());
            return permission;
        }).collect(Collectors.toList());
    }

    public Map<Long, PcOperationPermission> loadOperationsByIds(Set<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return operationPermissionMapper.selectList(new LambdaQueryWrapper<PcOperationPermission>()
                .eq(PcOperationPermission::getTenantId, tenantId)
                .in(PcOperationPermission::getId, ids)
                .eq(PcOperationPermission::getDeleteFlag, PermissionConstants.NOT_DELETED))
            .stream().collect(Collectors.toMap(PcOperationPermission::getId, operation -> operation));
    }

    public Map<Long, PcResourceEntity> loadResourcesByIds(Set<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return resourceEntityMapper.selectList(new LambdaQueryWrapper<PcResourceEntity>()
                .eq(PcResourceEntity::getTenantId, tenantId)
                .in(PcResourceEntity::getId, ids)
                .eq(PcResourceEntity::getDeleteFlag, PermissionConstants.NOT_DELETED))
            .stream().collect(Collectors.toMap(PcResourceEntity::getId, resource -> resource));
    }

    public Map<Long, PcPermissionCondition> loadConditionsByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return permissionConditionMapper.selectBatchIds(ids).stream()
            .filter(condition -> PermissionConstants.NOT_DELETED.equals(condition.getDeleteFlag()))
            .collect(Collectors.toMap(PcPermissionCondition::getId, condition -> condition));
    }

    public void attachPermissionMetadata(List<MatchedPermission> matchedPermissions, Map<Long, PcResourceEntity> resources,
                                         Map<Long, PcOperationPermission> operations, Map<Long, PcPermissionCondition> conditions) {
        for (MatchedPermission permission : matchedPermissions) {
            PcResourceEntity resource = resources.get(permission.getResourceId());
            if (resource != null) {
                permission.setResourceCode(resource.getCode());
                permission.setResourceType(resource.getResourceType());
            }
            PcOperationPermission operation = operations.get(permission.getOperationId());
            if (operation != null) {
                permission.setOperationCode(operation.getCode());
            }
            if (permission.getConditionId() != null) {
                permission.setCondition(conditions.get(permission.getConditionId()));
            }
        }
    }

    public List<MatchedPermission> filterSnapshotPermissions(List<MatchedPermission> matchedPermissions, Boolean includeConditional) {
        return matchedPermissions.stream()
            .filter(permission -> includeInSnapshot(permission, includeConditional))
            .collect(Collectors.toList());
    }

    public boolean includeInSnapshot(MatchedPermission permission, Boolean includeConditional) {
        if (permission.getConditionId() == null) {
            return true;
        }
        if (!Boolean.TRUE.equals(includeConditional)) {
            return false;
        }
        PcPermissionCondition condition = permission.getCondition();
        return condition != null && PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus());
    }

    public List<ConflictDetail> detectConflicts(Long tenantId, PcResourceEntity resource, List<MatchedPermission> matchedPermissions) {
        if (matchedPermissions.isEmpty()) {
            return new ArrayList<>();
        }
        List<PcPermissionConflictRule> rules = permissionConflictRuleMapper.selectByTenantAndResourceType(tenantId, resource.getResourceType());
        Set<Long> matchedOpIds = matchedPermissions.stream().map(MatchedPermission::getOperationId).collect(Collectors.toSet());
        List<ConflictDetail> details = new ArrayList<>();
        for (PcPermissionConflictRule rule : rules) {
            if (matchedOpIds.contains(rule.getFirstOperationPermissionId()) && matchedOpIds.contains(rule.getSecondOperationPermissionId())) {
                ConflictDetail detail = new ConflictDetail();
                detail.setConflictRuleId(rule.getId());
                detail.setResourceId(resource.getId());
                detail.setFirstOperationId(rule.getFirstOperationPermissionId());
                detail.setSecondOperationId(rule.getSecondOperationPermissionId());
                details.add(detail);
            }
        }
        return details;
    }

    public List<ConflictDetail> detectConflictsForSnapshot(Long tenantId, List<MatchedPermission> matchedPermissions,
                                                           Map<Long, PcResourceEntity> resources) {
        Map<Long, List<MatchedPermission>> byResource = matchedPermissions.stream()
            .collect(Collectors.groupingBy(MatchedPermission::getResourceId, LinkedHashMap::new, Collectors.toList()));
        List<ConflictDetail> conflicts = new ArrayList<>();
        for (Map.Entry<Long, List<MatchedPermission>> entry : byResource.entrySet()) {
            PcResourceEntity resource = resources.get(entry.getKey());
            if (resource == null) {
                continue;
            }
            conflicts.addAll(detectConflicts(tenantId, resource, entry.getValue()));
        }
        return conflicts;
    }

    public List<MatchedPermission> removeConflicted(List<MatchedPermission> matchedPermissions, List<ConflictDetail> conflicts,
                                                    Long targetOperationId) {
        if (conflicts.isEmpty()) {
            return matchedPermissions;
        }
        Set<String> deniedKeys = conflicts.stream()
            .filter(conflict -> Objects.equals(conflict.getFirstOperationId(), targetOperationId)
                || Objects.equals(conflict.getSecondOperationId(), targetOperationId))
            .flatMap(conflict -> List.of(
                conflict.getResourceId() + ":" + conflict.getFirstOperationId(),
                conflict.getResourceId() + ":" + conflict.getSecondOperationId()).stream())
            .collect(Collectors.toSet());
        return matchedPermissions.stream()
            .filter(permission -> !deniedKeys.contains(permission.getResourceId() + ":" + permission.getOperationId()))
            .collect(Collectors.toList());
    }

    public Set<String> buildConflictKeys(List<ConflictDetail> conflicts) {
        return conflicts.stream()
            .flatMap(conflict -> List.of(
                conflict.getResourceId() + ":" + conflict.getFirstOperationId(),
                conflict.getResourceId() + ":" + conflict.getSecondOperationId()).stream())
            .collect(Collectors.toSet());
    }

    public DependencyCheckResult checkDependencies(PermissionContext ctx, Long resourceEntityId, Long operationPermissionId, List<Long> roleIds) {
        return checkDependencies(ctx, resourceEntityId, operationPermissionId, roleIds, new HashSet<>(), 0);
    }

    public List<PcResourceApiMapping> loadApiMappings(Long tenantId, Collection<Long> resourceIds) {
        return resourceApiMappingService.listEnabledMappings(tenantId, resourceIds);
    }

    public List<SnapshotEntry> assembleDefaultSnapshotEntries(List<MatchedPermission> permissions) {
        List<SnapshotEntry> entries = new ArrayList<>();
        for (MatchedPermission permission : permissions) {
            SnapshotEntry entry = new SnapshotEntry();
            entry.setRoleId(permission.getRoleId());
            entry.setResourceId(permission.getResourceId());
            entry.setResourceCode(permission.getResourceCode());
            entry.setResourceType(permission.getResourceType());
            entry.setOperationId(permission.getOperationId());
            entry.setOperationCode(permission.getOperationCode());
            entry.setConditionId(permission.getConditionId());
            entry.setCanManage(permission.getCanManage());
            entries.add(entry);
        }
        return entries;
    }

    public List<SnapshotEntry> assembleApiSnapshotEntries(List<MatchedPermission> permissions, PermissionContext ctx) {
        List<MatchedPermission> unconditional = permissions.stream()
            .filter(permission -> permission.getConditionId() == null)
            .collect(Collectors.toList());
        if (unconditional.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PcResourceApiMapping>> mappings = loadApiMappings(ctx.getTenantId(),
            unconditional.stream().map(MatchedPermission::getResourceId).collect(Collectors.toSet()))
            .stream().collect(Collectors.groupingBy(PcResourceApiMapping::getResourceEntityId, LinkedHashMap::new, Collectors.toList()));
        List<SnapshotEntry> entries = new ArrayList<>();
        for (MatchedPermission permission : unconditional) {
            List<PcResourceApiMapping> apiMappings = mappings.get(permission.getResourceId());
            if (apiMappings == null || apiMappings.isEmpty()) {
                continue;
            }
            for (PcResourceApiMapping mapping : apiMappings) {
                SnapshotEntry entry = new SnapshotEntry();
                entry.setRoleId(permission.getRoleId());
                entry.setResourceId(permission.getResourceId());
                entry.setResourceCode(permission.getResourceCode());
                entry.setResourceType(permission.getResourceType());
                entry.setOperationId(permission.getOperationId());
                entry.setOperationCode(permission.getOperationCode());
                entry.setConditionId(permission.getConditionId());
                entry.setCanManage(permission.getCanManage());
                entry.setServiceCode(mapping.getServiceCode());
                entry.setHttpMethod(mapping.getHttpMethod());
                entry.setPathPattern(mapping.getPathPattern());
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("mappingId", mapping.getId());
                extra.put("matchOrder", mapping.getMatchOrder());
                if (mapping.getExtra() != null && !mapping.getExtra().isBlank()) {
                    extra.put("mappingExtra", mapping.getExtra());
                }
                entry.setExtra(extra);
                entries.add(entry);
            }
        }
        return entries;
    }

    private DependencyCheckResult checkDependencies(PermissionContext ctx, Long resourceEntityId, Long operationPermissionId,
                                                    List<Long> roleIds, Set<String> visited, int depth) {
        if (depth >= DEPTH_LIMIT) {
            return DependencyCheckResult.fail(List.of(new DependencyGap(resourceEntityId, operationPermissionId)));
        }
        String visitKey = resourceEntityId + ":" + operationPermissionId;
        if (!visited.add(visitKey)) {
            return DependencyCheckResult.ok();
        }
        List<PcResourceDependency> dependencies = resourceDependencyMapper.selectList(new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, ctx.getTenantId())
            .eq(PcResourceDependency::getResourceEntityId, resourceEntityId)
            .and(wrapper -> wrapper.isNull(PcResourceDependency::getSourceOperationPermissionId)
                .or().eq(PcResourceDependency::getSourceOperationPermissionId, operationPermissionId))
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (dependencies.isEmpty()) {
            return DependencyCheckResult.ok();
        }
        List<DependencyGap> gaps = new ArrayList<>();
        for (PcResourceDependency dependency : dependencies) {
            PcOperationPermission requiredOperation = loadOperation(ctx.getTenantId(), dependency.getRequiredOperationPermissionId());
            List<MatchedPermission> grants = loadMatchedPermissions(ctx.getTenantId(), roleIds,
                Collections.singleton(dependency.getDependsOnResourceEntityId()));
            Map<Long, PcOperationPermission> operations = loadOperationsByIds(
                grants.stream().map(MatchedPermission::getOperationId).collect(Collectors.toSet()), ctx.getTenantId());
            Map<Long, PcPermissionCondition> conditions = loadConditionsByIds(grants.stream()
                .map(MatchedPermission::getConditionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
            Map<Long, PcResourceEntity> resources = loadResourcesByIds(grants.stream()
                .map(MatchedPermission::getResourceId)
                .collect(Collectors.toSet()), ctx.getTenantId());
            attachPermissionMetadata(grants, resources, operations, conditions);
            List<MatchedPermission> matched = operationInheritanceService.filterByInheritance(grants, requiredOperation, operations).stream()
                .filter(permission -> isConditionSatisfied(permission, ctx))
                .collect(Collectors.toList());
            if (matched.isEmpty()) {
                gaps.add(new DependencyGap(dependency.getDependsOnResourceEntityId(), dependency.getRequiredOperationPermissionId()));
                continue;
            }
            DependencyCheckResult nested = checkDependencies(ctx, dependency.getDependsOnResourceEntityId(),
                dependency.getRequiredOperationPermissionId(), roleIds, visited, depth + 1);
            if (!nested.isSatisfied()) {
                gaps.addAll(nested.getGaps());
            }
        }
        return gaps.isEmpty() ? DependencyCheckResult.ok() : DependencyCheckResult.fail(gaps);
    }

    private boolean isConditionSatisfied(MatchedPermission permission, PermissionContext ctx) {
        if (permission.getConditionId() == null) {
            return true;
        }
        if (ctx.getConditionEvaluatorResolver() == null) {
            return permission.getCondition() != null
                && PermissionConstants.CONDITION_STATUS_APPROVED.equals(permission.getCondition().getStatus());
        }
        ConditionEvaluator evaluator = ctx.getConditionEvaluatorResolver().apply(permission.getResourceType());
        return evaluator != null && evaluator.evaluate(permission, ctx);
    }
}
