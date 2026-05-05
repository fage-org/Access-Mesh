package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.enums.GrantSource;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceDependencyDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

@Service
public class ResourceDependencyDomainServiceImpl implements ResourceDependencyDomainService {

    private static final Logger log = LoggerFactory.getLogger(ResourceDependencyDomainServiceImpl.class);

    private final ResourceDependencyMapper dependencyMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;

    public ResourceDependencyDomainServiceImpl(ResourceDependencyMapper dependencyMapper,
                                                RoleResourcePermissionMapper rolePermMapper,
                                                ResourceEntityMapper resourceEntityMapper,
                                                OperationPermissionMapper operationPermissionMapper,
                                                PermissionVersionDomainService permissionVersionDomainService) {
        this.dependencyMapper = dependencyMapper;
        this.rolePermMapper = rolePermMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDependencies(Long tenantId, Long roleId, Long resourceEntityId, Long operationBits) {
        List<ResourceDependency> deps = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );

        for (ResourceDependency dep : deps) {
            if (dep.getAutoGrant() != null && dep.getAutoGrant()
                && isTriggered(dep.getSourceOperationBits(), operationBits)) {
                autoGrantDependency(tenantId, roleId, dep);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId) {
        List<RoleResourcePermission> autoGrants = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq(GrantSource.AUTO_DEP.getValue()))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Set<Long> affectedRoles = new HashSet<>();
        // Batch soft delete (performance fix: use single SQL instead of loop)
        LocalDateTime now = LocalDateTime.now();
        if (!autoGrants.isEmpty()) {
            List<Long> idsToDelete = autoGrants.stream()
                .map(RoleResourcePermission::getId)
                .collect(java.util.stream.Collectors.toList());
            rolePermMapper.softDeleteBatch(tenantId, idsToDelete, now);
            affectedRoles.add(roleId);
        }

        if (!affectedRoles.isEmpty()) {
            // 版本递增（事务提交后执行）
            final Set<Long> affectedRolesForCache = affectedRoles;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        permissionVersionDomainService.batchIncrement(tenantId, affectedRolesForCache);
                    }
                });
            }
        }
    }

    @Override
    public List<RoleResourcePermission> autoGrantForInsert(Long tenantId, Long roleId, List<RoleResourcePermission> toInsert) {
        List<RoleResourcePermission> autoGranted = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Collect all resourceEntityIds being granted
        Set<Long> resourceIds = new HashSet<>();
        for (RoleResourcePermission rp : toInsert) {
            if (rp.getResourceEntityId() != null) {
                resourceIds.add(rp.getResourceEntityId());
            }
        }
        if (resourceIds.isEmpty()) {
            return autoGranted;
        }

        // Performance fix: Pre-load all operation permissions for O(1) lookup in nested loop
        Set<Long> opIds = new HashSet<>();
        for (RoleResourcePermission rp : toInsert) {
            if (rp.getOperationPermissionId() != null) {
                opIds.add(rp.getOperationPermissionId());
            }
        }
        Map<Long, OperationPermission> opPermCache = new HashMap<>();
        if (!opIds.isEmpty()) {
            for (OperationPermission op : operationPermissionMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION.ID.in(opIds))
                    .and(cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef.OPERATION_PERMISSION.DELETE_FLAG.eq(0))
            )) {
                opPermCache.put(op.getId(), op);
            }
        }

        // Find dependency rules where source resource is in the granted resources
        List<ResourceDependency> deps = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.in(resourceIds))
                .and(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.AUTO_GRANT.eq(true))
                .and(ResourceDependencyTableDef.RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );

        // Pre-load all existing auto-grant permissions for this role and dependency targets
        // to avoid N+1 query in nested loop
        Set<Long> targetResourceIds = deps.stream()
            .map(ResourceDependency::getDependsOnResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> depIds = deps.stream()
            .map(ResourceDependency::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, Map<Long, RoleResourcePermission>> existingAutoGrants = targetResourceIds.isEmpty() ? Map.of()
            : rolePermMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                    .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                    .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(targetResourceIds))
                    .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq(GrantSource.AUTO_DEP.getValue()))
                    .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.GRANT_DEP_ID.in(depIds))
                    .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.groupingBy(
                RoleResourcePermission::getResourceEntityId,
                Collectors.toMap(RoleResourcePermission::getGrantDepId, Function.identity(), (a, b) -> a)
            ));

        for (ResourceDependency dep : deps) {
            for (RoleResourcePermission rp : toInsert) {
                if (Objects.equals(rp.getResourceEntityId(), dep.getResourceEntityId())
                    && isTriggered(dep.getSourceOperationBits(), getEffectiveOpBitsFromCache(rp.getOperationPermissionId(), opPermCache))) {
                    // Check if already granted using pre-loaded cache (no query)
                    Map<Long, RoleResourcePermission> resourceGrants = existingAutoGrants.get(dep.getDependsOnResourceEntityId());
                    boolean alreadyGranted = resourceGrants != null && resourceGrants.containsKey(dep.getId());

                    if (!alreadyGranted) {
                        Long requiredOpId = resolveOperationPermissionId(
                            tenantId, dep.getDependsOnResourceEntityId(), dep.getRequiredOperationBits(), null);
                        if (requiredOpId == null) {
                            continue;
                        }
                        RoleResourcePermission autoRp = new RoleResourcePermission();
                        autoRp.setTenantId(tenantId);
                        autoRp.setAbstractRoleId(roleId);
                        autoRp.setResourceEntityId(dep.getDependsOnResourceEntityId());
                        autoRp.setOperationPermissionId(requiredOpId);
                        autoRp.setResourceType(null);
                        autoRp.setDependOn(null);
                        autoRp.setScopeAll(false);
                        autoRp.setCanGrant(false);
                        autoRp.setConditionId(null);
                        autoRp.setGrantSource(GrantSource.AUTO_DEP.getValue());
                        autoRp.setGrantDepId(dep.getId());
                        autoRp.setCreatedAt(now);
                        autoRp.setUpdatedAt(now);
                        autoRp.setDeleteFlag(0L);
                        autoGranted.add(autoRp);
                    }
                }
            }
        }

        return autoGranted;
    }

    private boolean isTriggered(Long sourceBits, Long operationBits) {
        if (sourceBits == null) return true;
        return (sourceBits & operationBits) != 0;
    }

    private Long getEffectiveOpBits(Long opId) {
        if (opId == null) return 0L;
        OperationPermission op = operationPermissionMapper.selectOneById(opId);
        if (op == null) return 0L;
        return op.getEffectiveBits();
    }

    /**
     * Get effective operation bits from pre-loaded cache (performance optimization).
     * Used in nested loops to avoid N+1 queries.
     */
    private Long getEffectiveOpBitsFromCache(Long opId, Map<Long, OperationPermission> cache) {
        if (opId == null) return 0L;
        OperationPermission op = cache.get(opId);
        if (op == null) return 0L;
        return op.getEffectiveBits();
    }

    private void autoGrantDependency(Long tenantId, Long roleId, ResourceDependency dep) {
        Long requiredOpId = resolveOperationPermissionId(tenantId, dep.getDependsOnResourceEntityId(), dep.getRequiredOperationBits(), null);
        if (requiredOpId == null) {
            return;
        }
        RoleResourcePermission rp = new RoleResourcePermission();
        rp.setTenantId(tenantId);
        rp.setAbstractRoleId(roleId);
        rp.setResourceEntityId(dep.getDependsOnResourceEntityId());
        rp.setOperationPermissionId(requiredOpId);
        rp.setScopeAll(false);
        rp.setGrantSource(GrantSource.AUTO_DEP.getValue());
        rp.setGrantDepId(dep.getId());
        rp.setCanGrant(false);
        LocalDateTime now = LocalDateTime.now();
        rp.setCreatedAt(now);
        rp.setUpdatedAt(now);
        rp.setDeleteFlag(0L);
        rolePermMapper.insert(rp);
        // 版本递增（事务提交后执行）
        final Long roleIdForCache = roleId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    permissionVersionDomainService.increment(tenantId, roleIdForCache);
                }
            });
        }
        log.info("Auto-granted dependency: role={}, resource={}", roleId, dep.getDependsOnResourceEntityId());
    }

    private Long resolveOperationPermissionId(Long tenantId, Long resourceEntityId, Long requiredBits, Long fallbackOperationId) {
        if (requiredBits == null || requiredBits == 0L) {
            return fallbackOperationId;
        }
        Integer resourceType = null;
        if (resourceEntityId != null) {
            ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(ResourceEntityTableDef.RESOURCE_ENTITY.ID.eq(resourceEntityId))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );
            if (resource != null) {
                resourceType = resource.getResourceType();
            }
        }
        // Use SQL bitwise filtering to avoid full table load
        List<OperationPermission> matchingOps = operationPermissionMapper.selectByEffectiveBitsMatch(
            tenantId, resourceType, requiredBits);
        if (!matchingOps.isEmpty()) {
            return matchingOps.get(0).getId();
        }
        return fallbackOperationId;
    }
}