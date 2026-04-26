package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceDependencyDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef.RESOURCE_DEPENDENCY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class ResourceDependencyDomainServiceImpl implements ResourceDependencyDomainService {

    private static final Logger log = LoggerFactory.getLogger(ResourceDependencyDomainServiceImpl.class);

    private final ResourceDependencyMapper dependencyMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;

    public ResourceDependencyDomainServiceImpl(ResourceDependencyMapper dependencyMapper,
                                                RoleResourcePermissionMapper rolePermMapper,
                                                PermissionVersionDomainService permissionVersionDomainService) {
        this.dependencyMapper = dependencyMapper;
        this.rolePermMapper = rolePermMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    @Override
    @Transactional
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
    @Transactional
    public void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId) {
        List<RoleResourcePermission> autoGrants = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.GRANT_SOURCE.eq("AUTO_DEP"))
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

    private boolean isTriggered(Long sourceBits, Long operationBits) {
        if (sourceBits == null) return true;
        return (sourceBits & operationBits) != 0;
    }

    private void autoGrantDependency(Long tenantId, Long roleId, ResourceDependency dep) {
        RoleResourcePermission rp = new RoleResourcePermission();
        rp.setTenantId(tenantId);
        rp.setAbstractRoleId(roleId);
        rp.setResourceEntityId(dep.getDependsOnResourceEntityId());
        rp.setOperationPermissionId(null);
        rp.setGrantSource("AUTO_DEP");
        rp.setGrantDepId(dep.getId());
        rp.setCanManage(false);
        rp.setCreatedAt(LocalDateTime.now());
        rp.setUpdatedAt(LocalDateTime.now());
        rp.setDeleteFlag(0L);
        rolePermMapper.insert(rp);
        permissionVersionDomainService.increment(tenantId, roleId);
        log.info("Auto-granted dependency: role={}, resource={}", roleId, dep.getDependsOnResourceEntityId());
    }
}
