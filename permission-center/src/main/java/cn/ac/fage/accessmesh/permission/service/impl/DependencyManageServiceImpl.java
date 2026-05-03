package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.enums.OperationType;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.service.DependencyManageService;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.ResourcePermissionValidator;
import cn.ac.fage.accessmesh.permission.util.OperatorUtil;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;

@Service
public class DependencyManageServiceImpl implements DependencyManageService {

    private final ResourceDependencyMapper dependencyMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final TypeResolutionService typeResolutionService;
    private final EntityBatchLoadDomainService entityBatchLoadDomainService;
    private final OperationLogDomainService operationLogDomainService;
    private final ResourcePermissionValidator permissionValidator;

    public DependencyManageServiceImpl(ResourceDependencyMapper dependencyMapper,
                                        ResourceEntityMapper resourceEntityMapper,
                                        TypeResolutionService typeResolutionService,
                                        EntityBatchLoadDomainService entityBatchLoadDomainService,
                                        OperationLogDomainService operationLogDomainService,
                                        ResourcePermissionValidator permissionValidator) {
        this.dependencyMapper = dependencyMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.typeResolutionService = typeResolutionService;
        this.entityBatchLoadDomainService = entityBatchLoadDomainService;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDependencyResp createDependency(Long tenantId, ResourceDependencyCreateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationType.CREATE);

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

        // 批量加载 ResourceEntity
        Set<Long> resourceIds = Set.of(sourceId, targetId);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        return toDependencyResp(dep, entityMap);
    }

    @Override
    public List<ResourceDependencyResp> listDependencies(Long tenantId, Long resourceEntityId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0));
        if (resourceEntityId != null) {
            qw.and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId));
        }
        List<ResourceDependency> dependencies = dependencyMapper.selectListByQuery(qw);

        // 批量加载 ResourceEntity 避免 N+1 查询
        Set<Long> resourceIds = extractResourceIds(dependencies);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);

        return dependencies.stream()
            .map(d -> toDependencyResp(d, entityMap))
            .collect(Collectors.toList());
    }

    @Override
    public List<ResourceDependencyResp> listAllDependencies(Long tenantId) {
        List<ResourceDependency> dependencies = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );

        // 批量加载 ResourceEntity 避免 N+1 查询
        Set<Long> resourceIds = extractResourceIds(dependencies);
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);

        return dependencies.stream()
            .map(d -> toDependencyResp(d, entityMap))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDependencyResp updateDependency(Long tenantId, ResourceDependencyUpdateReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, req.id(), OperationType.UPDATE);

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

        // 批量加载 ResourceEntity
        Set<Long> resourceIds = Stream.of(dep.getResourceEntityId(), dep.getDependsOnResourceEntityId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, ResourceEntity> entityMap = loadResourceEntityMap(tenantId, resourceIds);
        return toDependencyResp(dep, entityMap);
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
        if (Objects.equals(current, target)) return true;
        if (!visited.add(current)) return false;
        for (Long next : graph.getOrDefault(current, Set.of())) {
            if (canReach(graph, next, target, visited)) return true;
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDependency(Long tenantId, Long dependencyId, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, dependencyId, OperationType.DELETE);

        ResourceDependency dep = dependencyMapper.selectOneById(dependencyId);
        if (dep != null && dep.getDeleteFlag() == 0L && dep.getTenantId().equals(tenantId)) {
            dep.setDeleteFlag(dep.getId());
            dep.setDeletedAt(LocalDateTime.now());
            dependencyMapper.update(dep);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDependencies(Long tenantId, List<Long> dependencyIds, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);

        if (dependencyIds == null || dependencyIds.isEmpty()) return;

        Set<Long> validInputIds = dependencyIds.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (validInputIds.isEmpty()) return;

        permissionValidator.validateBatch(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, validInputIds, OperationType.DELETE);

        List<ResourceDependency> entities = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.ID.in(validInputIds))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );
        if (entities.isEmpty()) return;

        Set<Long> validIds = entities.stream().map(ResourceDependency::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (Long id : validIds) {
            ResourceDependency updateEntity = new ResourceDependency();
            updateEntity.setId(id);
            updateEntity.setDeleteFlag(id);
            updateEntity.setDeletedAt(now);
            dependencyMapper.update(updateEntity);
        }

        operationLogDomainService.asyncRecord(
            "perm", "resource-dependency-remove", "BATCH", tenantId,
            "soft-deleted " + validIds.size() + " resource_dependency row(s), ids=" + validIds,
            operatorId, null, null, tenantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSyncDependencies(Long tenantId, DependencyBatchSyncReq req, Long operatorId) {
        operatorId = OperatorUtil.resolveOrDefault(operatorId);
        permissionValidator.validate(tenantId, operatorId, ResourceTypeCode.DEPENDENCY, null, OperationType.SYNC);

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

            Set<Long> allResourceIds = existingDeps.stream()
                .flatMap(dep -> Stream.of(dep.getResourceEntityId(), dep.getDependsOnResourceEntityId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, ResourceEntity> resourceMap = allResourceIds.isEmpty() ? Map.of()
                : resourceEntityMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.ID.in(allResourceIds))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

            for (ResourceDependency existing : existingDeps) {
                ResourceEntity sourceResource = resourceMap.get(existing.getResourceEntityId());
                ResourceEntity targetResource = resourceMap.get(existing.getDependsOnResourceEntityId());
                if (sourceResource == null || targetResource == null) continue;

                boolean stillPresent = items.stream().anyMatch(item ->
                    Objects.equals(item.sourceResourceCode(), sourceResource.getCode())
                        && Objects.equals(item.targetResourceCode(), targetResource.getCode()));
                if (!stillPresent) {
                    existing.setDeleteFlag(existing.getId());
                    existing.setDeletedAt(now);
                    dependencyMapper.update(existing);
                }
            }
        }

        for (DependencyBatchSyncReq.DependencySyncItem item : items) {
            Long sourceResourceId = typeResolutionService.resolveResourceId(
                tenantId, item.sourceResourceTypeCode(), item.sourceResourceCode(), item.sourceCodeType(), null);
            Long targetResourceId = typeResolutionService.resolveResourceId(
                tenantId, item.targetResourceTypeCode(), item.targetResourceCode(), item.targetCodeType(), null);
            if (sourceResourceId == null || targetResourceId == null) continue;

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
                if (sourceOperationBits != null) existing.setSourceOperationBits(sourceOperationBits);
                if (requiredOperationBits != null) existing.setRequiredOperationBits(requiredOperationBits);
                if (item.autoGrant() != null) existing.setAutoGrant(item.autoGrant());
                if (item.description() != null) existing.setDescription(item.description());
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
        if (operationCodes == null || operationCodes.isEmpty()) return null;

        Set<String> codeSet = new HashSet<>(operationCodes);
        Map<String, Long> codeToIdMap = typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, codeSet);
        Set<Long> opIds = codeToIdMap.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (opIds.isEmpty()) return 0L;

        Map<Long, OperationPermission> opMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, opIds);
        Long bits = 0L;
        for (OperationPermission op : opMap.values()) {
            if (op.getBinaryBit() != null) bits |= op.getBinaryBit();
        }
        return bits;
    }

    private ResourceDependencyResp toDependencyResp(ResourceDependency d, Map<Long, ResourceEntity> entityMap) {
        ResourceEntity src = entityMap.get(d.getResourceEntityId());
        ResourceEntity dep = entityMap.get(d.getDependsOnResourceEntityId());
        return new ResourceDependencyResp(
            d.getId(), d.getTenantId(), d.getResourceEntityId(),
            src != null ? src.getCode() : null,
            d.getDependsOnResourceEntityId(),
            dep != null ? dep.getCode() : null,
            d.getSourceOperationBits(), d.getRequiredOperationBits(),
            d.getAutoGrant(), d.getDescription(), d.getCreatedAt()
        );
    }

    /**
     * 批量加载 ResourceEntity 并构建 Map
     */
    private Map<Long, ResourceEntity> loadResourceEntityMap(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.ID.in(resourceIds))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));
    }

    /**
     * 从依赖列表中提取所有相关的 ResourceEntity ID
     */
    private Set<Long> extractResourceIds(List<ResourceDependency> dependencies) {
        return dependencies.stream()
            .flatMap(d -> Stream.of(d.getResourceEntityId(), d.getDependsOnResourceEntityId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}