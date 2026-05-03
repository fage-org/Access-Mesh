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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

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
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
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
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq(GrantSource.AUTO_DEP.getValue()))
                .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceEntityId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        Set<Long> affectedRoles = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (RoleResourcePermission rp : autoGrants) {
            rp.setDeleteFlag(rp.getId());
            rp.setDeletedAt(now);
            rolePermMapper.update(rp);
            affectedRoles.add(roleId);
        }

        if (!affectedRoles.isEmpty()) {
            permissionVersionDomainService.batchIncrement(tenantId, affectedRoles);
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

        // Find dependency rules where source resource is in the granted resources
        List<ResourceDependency> deps = dependencyMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_DEPENDENCY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_DEPENDENCY.RESOURCE_ENTITY_ID.in(resourceIds))
                .and(RESOURCE_DEPENDENCY.AUTO_GRANT.eq(true))
                .and(RESOURCE_DEPENDENCY.DELETE_FLAG.eq(0))
        );

        for (ResourceDependency dep : deps) {
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
                        .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                        .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                        .and(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(targetResourceIds))
                        .and(ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq(GrantSource.AUTO_DEP.getValue()))
                        .and(ROLE_RESOURCE_PERMISSION.GRANT_DEP_ID.in(depIds))
                        .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                ).stream().collect(Collectors.groupingBy(
                    RoleResourcePermission::getResourceEntityId,
                    Collectors.toMap(RoleResourcePermission::getGrantDepId, p -> p, (a, b) -> a)
                ));

            for (RoleResourcePermission rp : toInsert) {
                if (Objects.equals(rp.getResourceEntityId(), dep.getResourceEntityId())
                    && isTriggered(dep.getSourceOperationBits(), getEffectiveOpBits(rp.getOperationPermissionId()))) {
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
        rp.setCreatedAt(LocalDateTime.now());
        rp.setUpdatedAt(LocalDateTime.now());
        rp.setDeleteFlag(0L);
        rolePermMapper.insert(rp);
        permissionVersionDomainService.increment(tenantId, roleId);
        log.info("Auto-granted dependency: role={}, resource={}", roleId, dep.getDependsOnResourceEntityId());
    }

    private Long resolveOperationPermissionId(Long tenantId, Long resourceEntityId, Long requiredBits, Long fallbackOperationId) {
        if (requiredBits == null || requiredBits == 0L) {
            return fallbackOperationId;
        }
        Integer resourceType = null;
        if (resourceEntityId != null) {
            ResourceEntity resource = resourceEntityMapper.selectOneById(resourceEntityId);
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
