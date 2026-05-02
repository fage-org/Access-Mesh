package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class RolePermissionDomainServiceImpl implements RolePermissionDomainService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionDomainServiceImpl.class);

    private final RoleResourcePermissionMapper rolePermMapper;
    private final PermissionVersionDomainService permissionVersionDomainService;

    public RolePermissionDomainServiceImpl(RoleResourcePermissionMapper rolePermMapper,
                                            PermissionVersionDomainService permissionVersionDomainService) {
        this.rolePermMapper = rolePermMapper;
        this.permissionVersionDomainService = permissionVersionDomainService;
    }

    @Override
    public RolePermSnapshot getRolePermissions(Long tenantId, Long roleId) {
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        long version = permissionVersionDomainService.getCurrentVersion(tenantId, roleId);

        List<RolePermSnapshot.RolePermEntry> entries = perms.stream()
            .map(p -> new RolePermSnapshot.RolePermEntry(
                p.getId(), p.getAbstractRoleId(),
                p.getResourceEntityId(), null, p.getResourceType(),
                p.getOperationPermissionId(), null, null,
                p.getGrantSource(),
                p.getCanManage(), p.getConditionId(), p.getConditionId() != null,
                p.getDependOn()
            ))
            .collect(Collectors.toList());

        return new RolePermSnapshot(tenantId, roleId, version, entries);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(Long tenantId, Long roleId, List<RolePermSnapshot.RolePermEntry> entries, String changeSource) {
        for (RolePermSnapshot.RolePermEntry entry : entries) {
            RoleResourcePermission rp = new RoleResourcePermission();
            rp.setTenantId(tenantId);
            rp.setAbstractRoleId(roleId);
            rp.setResourceEntityId(entry.resourceEntityId());
            rp.setOperationPermissionId(entry.operationPermissionId());
            rp.setResourceType(entry.resourceType());
            rp.setDependOn(entry.dependOn());
            rp.setScopeAll(false);
            rp.setCanManage(entry.canManage() != null && entry.canManage());
            rp.setConditionId(entry.conditionId());
            rp.setGrantSource(changeSource != null ? changeSource : "MANUAL");
            rp.setCreatedAt(LocalDateTime.now());
            rp.setUpdatedAt(LocalDateTime.now());
            rp.setDeleteFlag(0L);
            rolePermMapper.insert(rp);
        }
        permissionVersionDomainService.increment(tenantId, roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> affectedRoles = Set.of(roleId);

        for (Long permId : permissionIds) {
            RoleResourcePermission rp = rolePermMapper.selectOneById(permId);
            if (rp != null && rp.getDeleteFlag() == 0L && rp.getAbstractRoleId().equals(roleId)) {
                rp.setDeleteFlag(rp.getId());
                rp.setDeletedAt(now);
                rolePermMapper.update(rp);

                // Cascade delete sub-permissions
                List<RoleResourcePermission> children = rolePermMapper.selectListByQuery(
                    QueryWrapper.create()
                        .where(ROLE_RESOURCE_PERMISSION.DEPEND_ON.eq(permId))
                        .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
                );
                for (RoleResourcePermission child : children) {
                    child.setDeleteFlag(child.getId());
                    child.setDeletedAt(now);
                    rolePermMapper.update(child);
                }
            }
        }

        permissionVersionDomainService.batchIncrement(tenantId, affectedRoles);
    }
}
