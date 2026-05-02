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
                p.getCanGrant(), p.getConditionId(), p.getConditionId() != null,
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
            rp.setCanGrant(entry.canGrant() != null && entry.canGrant());
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
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // Validate permissions belong to the role
        long validCount = rolePermMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.ID.in(permissionIds))
                .and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId))
                .and(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        if (validCount == 0) {
            return;
        }

        // Batch soft delete the permissions (single SQL, avoid N+1)
        rolePermMapper.softDeleteBatch(tenantId, permissionIds, now);

        // Batch cascade delete all children (single SQL, avoid N+1)
        rolePermMapper.cascadeSoftDeleteChildren(tenantId, permissionIds, now);

        permissionVersionDomainService.increment(tenantId, roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokePermissionWithCascade(Long tenantId, Long roleId, Long permissionId, LocalDateTime deletedAt) {
        RoleResourcePermission rp = rolePermMapper.selectOneById(permissionId);
        if (rp != null && rp.getDeleteFlag() == 0L && rp.getAbstractRoleId().equals(roleId)
            && rp.getTenantId().equals(tenantId)) {
            // Soft delete the permission
            rp.setDeleteFlag(rp.getId());
            rp.setDeletedAt(deletedAt);
            rolePermMapper.update(rp);

            // Cascade soft delete sub-permissions using batch SQL (avoid N+1)
            rolePermMapper.cascadeSoftDeleteChildren(tenantId, List.of(permissionId), deletedAt);
        }
    }

    @Override
    public RoleResourcePermission selectValidById(Long tenantId, Long roleId, Long permissionId) {
        if (permissionId == null) {
            return null;
        }
        QueryWrapper qw = QueryWrapper.create()
            .where(ROLE_RESOURCE_PERMISSION.ID.eq(permissionId))
            .and(ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
            .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0));
        if (roleId != null) {
            qw.and(ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.eq(roleId));
        }
        return rolePermMapper.selectOneByQuery(qw);
    }
}
