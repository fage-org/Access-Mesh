package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcPermissionVersion;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.dto.ChangeLogParam;
import org.dromara.permission.domain.dto.DependencyCheckReq;
import org.dromara.permission.domain.dto.IdsReq;
import org.dromara.permission.domain.dto.ResourceDependencyListReq;
import org.dromara.permission.domain.dto.ResourceDependencySaveReq;
import org.dromara.permission.domain.vo.ResourceDependencyVo;
import org.dromara.permission.event.PermissionGovernanceEventPublisher;
import org.dromara.permission.event.PermissionWriteRefreshEventPublisher;
import org.dromara.permission.handler.ResourceTypeHandlerRegistry;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.model.permission.DependencyCheckResult;
import org.dromara.permission.model.permission.DependencyGap;
import org.dromara.permission.model.permission.InheritMode;
import org.dromara.permission.model.permission.PermissionContext;
import org.dromara.permission.model.permission.PermissionErrorCode;
import org.dromara.permission.model.permission.PermissionServiceException;
import org.dromara.permission.model.permission.ResolvedRole;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.PermissionVersionService;
import org.dromara.permission.service.ResourceDependencyService;
import org.dromara.permission.service.RoleResolverService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.dromara.permission.service.support.PermissionBridgeSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceDependencyServiceImpl implements ResourceDependencyService {

    private final PcResourceDependencyMapper mapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final RoleResolverService roleResolverService;
    private final PermissionBridgeSupport permissionBridgeSupport;
    private final ResourceTypeHandlerRegistry resourceTypeHandlerRegistry;
    private final PermissionGovernanceEventPublisher governanceEventPublisher;
    private final PermissionVersionService permissionVersionService;
    private final PermissionWriteRefreshEventPublisher permissionWriteRefreshEventPublisher;
    private final PermissionChangeLogService permissionChangeLogService;

    @Override
    public List<ResourceDependencyVo> list(ResourceDependencyListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        return selectDependencies(req, true).stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public List<ResourceDependencyVo> graph(ResourceDependencyListReq req) {
        if (req == null || req.getTenantId() == null) {
            return new ArrayList<>();
        }
        List<PcResourceDependency> dependencies = selectDependencies(req, false);
        if (dependencies.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> seeds = new LinkedHashSet<>();
        if (req.getResourceEntityId() != null) {
            seeds.add(req.getResourceEntityId());
        }
        if (req.getDependsOnResourceEntityId() != null) {
            seeds.add(req.getDependsOnResourceEntityId());
        }
        if (seeds.isEmpty()) {
            return dependencies.stream().map(this::toVo).collect(Collectors.toList());
        }
        String graphMode = normalizeGraphMode(req.getGraphMode());
        if ("UPSTREAM".equals(graphMode) || "DOWNSTREAM".equals(graphMode)) {
            return directionalGraph(dependencies, seeds, graphMode).stream().map(this::toVo).collect(Collectors.toList());
        }
        Map<Long, Set<Long>> relatedNodes = buildRelatedNodes(dependencies);
        Set<Long> visited = traverseGraph(seeds, relatedNodes);
        return dependencies.stream()
            .filter(dependency -> visited.contains(dependency.getResourceEntityId())
                || visited.contains(dependency.getDependsOnResourceEntityId()))
            .map(this::toVo)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ResourceDependencySaveReq req) {
        if (req == null || req.getTenantId() == null || req.getResourceEntityId() == null
            || req.getDependsOnResourceEntityId() == null || req.getRequiredOperationPermissionId() == null) {
            return;
        }
        if (Objects.equals(req.getResourceEntityId(), req.getDependsOnResourceEntityId())) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "resource dependency cannot depend on itself");
        }
        PcResourceEntity sourceResource = permissionBridgeSupport.loadResource(req.getTenantId(), req.getResourceEntityId());
        PcResourceEntity dependsOnResource = permissionBridgeSupport.loadResource(req.getTenantId(), req.getDependsOnResourceEntityId());
        PcOperationPermission requiredOperation =
            permissionBridgeSupport.loadOperation(req.getTenantId(), req.getRequiredOperationPermissionId());
        permissionBridgeSupport.validateResourceOperationType(dependsOnResource, requiredOperation);
        if (req.getSourceOperationPermissionId() != null) {
            PcOperationPermission sourceOperation =
                permissionBridgeSupport.loadOperation(req.getTenantId(), req.getSourceOperationPermissionId());
            permissionBridgeSupport.validateResourceOperationType(sourceResource, sourceOperation);
        }
        assertNoCycle(req);
        LocalDateTime now = LocalDateTime.now();
        PcResourceDependency duplicated = findDuplicate(req);
        if (duplicated != null && (req.getId() == null || !Objects.equals(duplicated.getId(), req.getId()))) {
            return;
        }
        if (req.getId() != null) {
            PcResourceDependency entity = mapper.selectOne(new LambdaQueryWrapper<PcResourceDependency>()
                .eq(PcResourceDependency::getTenantId, req.getTenantId())
                .eq(PcResourceDependency::getId, req.getId())
                .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity == null) {
                return;
            }
            PcResourceDependency oldSnapshot = copyDependency(entity);
            entity.setResourceEntityId(req.getResourceEntityId());
            entity.setDependsOnResourceEntityId(req.getDependsOnResourceEntityId());
            entity.setSourceOperationPermissionId(req.getSourceOperationPermissionId());
            entity.setRequiredOperationPermissionId(req.getRequiredOperationPermissionId());
            entity.setUpdatedAt(now);
            mapper.updateById(entity);
            recordWriteChain(buildWriteContext(req.getTenantId(), sourceResource.getBizDomainId(),
                    req.getRequestId(), req.getChangeSource(), "saveResourceDependency", "save-resource-dependency"),
                entity.getId(),
                new ChangeLogParam()
                    .setEntityType("resource_dependency")
                    .setEntityId(entity.getId())
                    .setOperation("UPDATE")
                    .setOldSnapshot(oldSnapshot)
                    .setNewSnapshot(copyDependency(entity))
                    .setChangeReason(req.getChangeReason()));
            return;
        }
        PcResourceDependency entity = new PcResourceDependency();
        entity.setTenantId(req.getTenantId());
        entity.setResourceEntityId(req.getResourceEntityId());
        entity.setDependsOnResourceEntityId(req.getDependsOnResourceEntityId());
        entity.setSourceOperationPermissionId(req.getSourceOperationPermissionId());
        entity.setRequiredOperationPermissionId(req.getRequiredOperationPermissionId());
        entity.setDeleteFlag(PermissionConstants.NOT_DELETED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        recordWriteChain(buildWriteContext(req.getTenantId(), sourceResource.getBizDomainId(),
                req.getRequestId(), req.getChangeSource(), "saveResourceDependency", "save-resource-dependency"),
            entity.getId(),
            new ChangeLogParam()
                .setEntityType("resource_dependency")
                .setEntityId(entity.getId())
                .setOperation("INSERT")
                .setNewSnapshot(copyDependency(entity))
                .setChangeReason(req.getChangeReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(IdsReq req) {
        if (req == null || req.getTenantId() == null || req.getIds() == null || req.getIds().isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PcResourceDependency> changed = new ArrayList<>();
        for (Long id : req.getIds()) {
            PcResourceDependency entity = mapper.selectOne(new LambdaQueryWrapper<PcResourceDependency>()
                .eq(PcResourceDependency::getTenantId, req.getTenantId())
                .eq(PcResourceDependency::getId, id)
                .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (entity != null && PermissionConstants.NOT_DELETED.equals(entity.getDeleteFlag())) {
                PcResourceDependency oldSnapshot = copyDependency(entity);
                PermissionAuditSupport.markDeleted(entity, entity.getId(), now);
                mapper.updateById(entity);
                changed.add(copyDependency(entity));
                permissionChangeLogService.writeChangeLog(new ChangeLogParam()
                    .setTenantId(req.getTenantId())
                    .setEntityType("resource_dependency")
                    .setEntityId(entity.getId())
                    .setOperation("DELETE")
                    .setOldSnapshot(oldSnapshot)
                    .setNewSnapshot(copyDependency(entity))
                    .setChangeReason(req.getChangeReason())
                    .setRequestId(req.getRequestId())
                    .setChangeSource(resolveChangeSource(req.getChangeSource())));
            }
        }
        if (!changed.isEmpty()) {
            Long triggerEntityId = changed.size() == 1 ? changed.get(0).getId() : null;
            recordVersionRefresh(buildWriteContext(req.getTenantId(), null,
                    req.getRequestId(), req.getChangeSource(), "removeResourceDependency", "remove-resource-dependency"),
                triggerEntityId);
        }
    }

    @Override
    public DependencyCheckResult check(DependencyCheckReq req) {
        if (req == null || req.getTenantId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST);
        }
        if (req.getAbstractUserId() == null && req.getAbstractRoleId() == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST,
                "dependency check requires abstractUserId or abstractRoleId");
        }
        PermissionContext ctx = new PermissionContext(req.getTenantId(), req.getAbstractUserId(), req.getBizDomainId(), null, null);
        ctx.setAction("dependency-check");
        ctx.setRequestId(req.getRequestId());
        ctx.setChangeSource("API");
        ctx.setConditionEvaluatorResolver(resourceType ->
            resourceTypeHandlerRegistry.getHandler(req.getTenantId(), resourceType).getConditionEvaluator());
        List<ResolvedRole> resolvedRoles = new ArrayList<>();
        Set<Long> roleIds = new LinkedHashSet<>();
        if (req.getAbstractUserId() != null) {
            List<ResolvedRole> userRoles = roleResolverService.resolve(req.getTenantId(), req.getAbstractUserId(), req.getBizDomainId());
            resolvedRoles.addAll(userRoles);
            roleIds.addAll(userRoles.stream().map(ResolvedRole::getRoleId).collect(Collectors.toSet()));
        }
        if (req.getAbstractRoleId() != null) {
            PcAbstractRole role = loadRole(req.getTenantId(), req.getAbstractRoleId());
            if (req.getBizDomainId() == null || role.getBizDomainId() == null || Objects.equals(role.getBizDomainId(), req.getBizDomainId())) {
                roleIds.add(role.getId());
                resolvedRoles.add(toResolvedRole(role));
            }
        }
        ctx.setRoles(resolvedRoles);
        DependencyCheckResult result;
        List<Long> roleIdList = new ArrayList<>(roleIds);
        if (req.getResourceEntityId() != null && req.getOperationPermissionId() != null) {
            result = checkSingleDependency(ctx, req.getResourceEntityId(), req.getOperationPermissionId(), roleIdList);
        } else {
            result = scanDependencies(ctx, req, roleIdList);
        }
        if (!result.isSatisfied() && shouldPublishDependencyBroken(req, result)) {
            governanceEventPublisher.publishDependencyBroken(req, result);
        }
        return result;
    }

    private List<PcResourceDependency> selectDependencies(ResourceDependencyListReq req, boolean applyNodeFilters) {
        LambdaQueryWrapper<PcResourceDependency> query = new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, req.getTenantId())
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED);
        if (applyNodeFilters) {
            if (req.getResourceEntityId() != null) {
                query.eq(PcResourceDependency::getResourceEntityId, req.getResourceEntityId());
            }
            if (req.getDependsOnResourceEntityId() != null) {
                query.eq(PcResourceDependency::getDependsOnResourceEntityId, req.getDependsOnResourceEntityId());
            }
        }
        if (req.getSourceOperationPermissionId() != null) {
            query.eq(PcResourceDependency::getSourceOperationPermissionId, req.getSourceOperationPermissionId());
        }
        if (req.getRequiredOperationPermissionId() != null) {
            query.eq(PcResourceDependency::getRequiredOperationPermissionId, req.getRequiredOperationPermissionId());
        }
        query.orderByAsc(PcResourceDependency::getId);
        return mapper.selectList(query);
    }

    private Map<Long, Set<Long>> buildRelatedNodes(List<PcResourceDependency> dependencies) {
        Map<Long, Set<Long>> relatedNodes = new HashMap<>();
        for (PcResourceDependency dependency : dependencies) {
            relatedNodes.computeIfAbsent(dependency.getResourceEntityId(), ignored -> new LinkedHashSet<>())
                .add(dependency.getDependsOnResourceEntityId());
            relatedNodes.computeIfAbsent(dependency.getDependsOnResourceEntityId(), ignored -> new LinkedHashSet<>())
                .add(dependency.getResourceEntityId());
        }
        return relatedNodes;
    }

    private Set<Long> traverseGraph(Set<Long> seeds, Map<Long, Set<Long>> relatedNodes) {
        Set<Long> visited = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (Long next : relatedNodes.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private List<PcResourceDependency> directionalGraph(List<PcResourceDependency> dependencies, Set<Long> seeds, String graphMode) {
        Map<Long, List<PcResourceDependency>> adjacency = new LinkedHashMap<>();
        for (PcResourceDependency dependency : dependencies) {
            Long key = "UPSTREAM".equals(graphMode)
                ? dependency.getResourceEntityId()
                : dependency.getDependsOnResourceEntityId();
            adjacency.computeIfAbsent(key, ignored -> new ArrayList<>()).add(dependency);
        }
        List<PcResourceDependency> edges = new ArrayList<>();
        Set<Long> visitedNodes = new LinkedHashSet<>();
        Set<Long> visitedEdges = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!visitedNodes.add(current)) {
                continue;
            }
            for (PcResourceDependency dependency : adjacency.getOrDefault(current, Collections.emptyList())) {
                if (!visitedEdges.add(dependency.getId())) {
                    continue;
                }
                edges.add(dependency);
                Long next = "UPSTREAM".equals(graphMode)
                    ? dependency.getDependsOnResourceEntityId()
                    : dependency.getResourceEntityId();
                if (!visitedNodes.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return edges;
    }

    private PcResourceDependency findDuplicate(ResourceDependencySaveReq req) {
        List<PcResourceDependency> dependencies = mapper.selectList(new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, req.getTenantId())
            .eq(PcResourceDependency::getResourceEntityId, req.getResourceEntityId())
            .eq(PcResourceDependency::getDependsOnResourceEntityId, req.getDependsOnResourceEntityId())
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcResourceDependency dependency : dependencies) {
            if (Objects.equals(dependency.getSourceOperationPermissionId(), req.getSourceOperationPermissionId())
                && Objects.equals(dependency.getRequiredOperationPermissionId(), req.getRequiredOperationPermissionId())) {
                return dependency;
            }
        }
        return null;
    }

    private void assertNoCycle(ResourceDependencySaveReq req) {
        List<PcResourceDependency> dependencies = mapper.selectList(new LambdaQueryWrapper<PcResourceDependency>()
            .eq(PcResourceDependency::getTenantId, req.getTenantId())
            .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        for (PcResourceDependency dependency : dependencies) {
            if (Objects.equals(dependency.getId(), req.getId())) {
                continue;
            }
            adjacency.computeIfAbsent(dependency.getResourceEntityId(), ignored -> new HashSet<>())
                .add(dependency.getDependsOnResourceEntityId());
        }
        if (hasPath(req.getDependsOnResourceEntityId(), req.getResourceEntityId(), adjacency)) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "resource dependency cycle detected");
        }
    }

    private boolean hasPath(Long start, Long target, Map<Long, Set<Long>> adjacency) {
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (Objects.equals(current, target)) {
                return true;
            }
            for (Long next : adjacency.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private PcAbstractRole loadRole(Long tenantId, Long roleId) {
        PcAbstractRole role = abstractRoleMapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
            .eq(PcAbstractRole::getTenantId, tenantId)
            .eq(PcAbstractRole::getId, roleId)
            .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (role == null) {
            throw new PermissionServiceException(PermissionErrorCode.INVALID_REQUEST, "role not found");
        }
        return role;
    }

    private ResolvedRole toResolvedRole(PcAbstractRole role) {
        ResolvedRole resolvedRole = new ResolvedRole();
        resolvedRole.setRoleId(role.getId());
        resolvedRole.setBizDomainId(role.getBizDomainId());
        resolvedRole.setRoleType(role.getRoleType());
        return resolvedRole;
    }

    private DependencyCheckResult checkSingleDependency(PermissionContext ctx, Long resourceEntityId,
                                                        Long operationPermissionId, List<Long> roleIds) {
        List<org.dromara.permission.model.permission.MatchedPermission> effectivePermissions =
            permissionBridgeSupport.resolveCurrentEffectivePermissions(ctx, roleIds, Set.of(resourceEntityId), operationPermissionId);
        if (effectivePermissions.isEmpty()) {
            return DependencyCheckResult.fail(List.of(new DependencyGap(resourceEntityId, operationPermissionId)));
        }
        PcResourceEntity resource = permissionBridgeSupport.loadResource(ctx.getTenantId(), resourceEntityId);
        PcOperationPermission operation = permissionBridgeSupport.loadOperation(ctx.getTenantId(), operationPermissionId);
        permissionBridgeSupport.validateResourceOperationType(resource, operation);
        ctx.setResource(resource);
        ctx.setOperation(operation);
        ctx.bindResourceContext();
        return permissionBridgeSupport.checkDependencies(ctx, resourceEntityId, operationPermissionId, roleIds);
    }

    private DependencyCheckResult scanDependencies(PermissionContext ctx, DependencyCheckReq req, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return DependencyCheckResult.ok();
        }
        Set<Long> resourceFilter = req.getResourceEntityId() == null ? null : Set.of(req.getResourceEntityId());
        List<org.dromara.permission.model.permission.MatchedPermission> permissions =
            permissionBridgeSupport.resolveCurrentEffectivePermissions(ctx, roleIds, resourceFilter, req.getOperationPermissionId());
        if (permissions.isEmpty()) {
            return DependencyCheckResult.ok();
        }
        List<DependencyGap> gaps = new ArrayList<>();
        Set<String> visitedPairs = new LinkedHashSet<>();
        for (org.dromara.permission.model.permission.MatchedPermission permission : permissions) {
            String pairKey = permission.getResourceId() + ":" + permission.getOperationId();
            if (!visitedPairs.add(pairKey)) {
                continue;
            }
            PcResourceEntity resource = permissionBridgeSupport.loadResource(ctx.getTenantId(), permission.getResourceId());
            PcOperationPermission operation = permissionBridgeSupport.loadOperation(ctx.getTenantId(), permission.getOperationId());
            permissionBridgeSupport.validateResourceOperationType(resource, operation);
            ctx.setResource(resource);
            ctx.setOperation(operation);
            ctx.bindResourceContext();
            DependencyCheckResult result = permissionBridgeSupport.checkDependencies(
                ctx, permission.getResourceId(), permission.getOperationId(), roleIds);
            if (!result.isSatisfied()) {
                gaps.addAll(result.getGaps());
            }
        }
        if (gaps.isEmpty()) {
            return DependencyCheckResult.ok();
        }
        return DependencyCheckResult.fail(normalizeGaps(gaps));
    }

    private List<DependencyGap> normalizeGaps(List<DependencyGap> gaps) {
        Map<String, DependencyGap> deduplicated = new LinkedHashMap<>();
        for (DependencyGap gap : gaps) {
            String pathKey = gap.getPath() == null ? "" : gap.getPath().stream()
                .map(node -> node.getResourceEntityId() + ":" + node.getOperationPermissionId())
                .collect(Collectors.joining("->"));
            String key = gap.getResourceEntityId() + ":" + gap.getOperationPermissionId() + ":" + pathKey;
            deduplicated.putIfAbsent(key, gap);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private boolean shouldPublishDependencyBroken(DependencyCheckReq req, DependencyCheckResult result) {
        return result != null
            && !result.isSatisfied()
            && result.getGaps() != null
            && !result.getGaps().isEmpty()
            && !(req.getResourceEntityId() != null
            && req.getOperationPermissionId() != null
            && result.getGaps().size() == 1
            && Objects.equals(result.getGaps().get(0).getResourceEntityId(), req.getResourceEntityId())
            && Objects.equals(result.getGaps().get(0).getOperationPermissionId(), req.getOperationPermissionId())
            && (result.getGaps().get(0).getPath() == null || result.getGaps().get(0).getPath().isEmpty()));
    }

    private String normalizeGraphMode(String graphMode) {
        if (graphMode == null || graphMode.isBlank()) {
            return "AROUND";
        }
        String normalized = graphMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "UPSTREAM", "DOWNSTREAM", "AROUND" -> normalized;
            default -> "AROUND";
        };
    }

    private PermissionContext buildWriteContext(Long tenantId, Long bizDomainId, String requestId,
                                                String changeSource, String action, String versionRemark) {
        PermissionContext ctx = new PermissionContext(tenantId, null, bizDomainId, InheritMode.NONE, null);
        ctx.setAction(action);
        ctx.setRequestId(requestId);
        ctx.setChangeSource(resolveChangeSource(changeSource));
        ctx.setVersionRemark(versionRemark);
        return ctx;
    }

    private void recordWriteChain(PermissionContext ctx, Long triggerEntityId, ChangeLogParam changeLogParam) {
        permissionChangeLogService.writeChangeLog(changeLogParam
            .setTenantId(ctx.getTenantId())
            .setBizDomainId(changeLogParam.getBizDomainId() == null ? ctx.getBizDomainId() : changeLogParam.getBizDomainId())
            .setRequestId(changeLogParam.getRequestId() == null ? ctx.getRequestId() : changeLogParam.getRequestId())
            .setChangeSource(changeLogParam.getChangeSource() == null ? ctx.getChangeSource() : changeLogParam.getChangeSource()));
        recordVersionRefresh(ctx, triggerEntityId);
    }

    private void recordVersionRefresh(PermissionContext ctx, Long triggerEntityId) {
        PcPermissionVersion version = permissionVersionService.bumpVersion(
            ctx.getTenantId(), "resource_dependency", triggerEntityId, ctx.getVersionRemark());
        permissionWriteRefreshEventPublisher.publish(ctx, version);
    }

    private String resolveChangeSource(String changeSource) {
        return changeSource == null || changeSource.isBlank() ? "API" : changeSource;
    }

    private PcResourceDependency copyDependency(PcResourceDependency source) {
        PcResourceDependency target = new PcResourceDependency();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private ResourceDependencyVo toVo(PcResourceDependency entity) {
        ResourceDependencyVo vo = new ResourceDependencyVo();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
