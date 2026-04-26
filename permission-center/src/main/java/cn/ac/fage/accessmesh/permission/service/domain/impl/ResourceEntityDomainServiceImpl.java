package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION;

@Service
public class ResourceEntityDomainServiceImpl implements ResourceEntityDomainService {

    private final ResourceEntityMapper resourceEntityMapper;
    private final RoleResourcePermissionMapper rolePermMapper;

    public ResourceEntityDomainServiceImpl(ResourceEntityMapper resourceEntityMapper,
                                            RoleResourcePermissionMapper rolePermMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.rolePermMapper = rolePermMapper;
    }

    @Override
    public List<ResourceEntity> listByParentId(Long tenantId, Long parentId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (parentId != null) {
            qw.and(RESOURCE_ENTITY.PARENT_ID.eq(parentId));
        } else {
            qw.and(RESOURCE_ENTITY.PARENT_ID.isNull());
        }
        return resourceEntityMapper.selectListByQuery(qw);
    }

    @Override
    public List<ResourceEntity> listByType(Long tenantId, Integer resourceType) {
        return resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional
    public void deleteWithChildren(Long tenantId, Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        ResourceEntity entity = resourceEntityMapper.selectOneById(resourceId);
        if (entity == null || entity.getDeleteFlag() != 0L) return;

        // Soft delete all children
        List<ResourceEntity> children = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.PARENT_ID.eq(resourceId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        for (ResourceEntity child : children) {
            child.setDeleteFlag(child.getId());
            child.setDeletedAt(now);
            resourceEntityMapper.update(child);
        }

        // Soft delete resource
        entity.setDeleteFlag(entity.getId());
        entity.setDeletedAt(now);
        resourceEntityMapper.update(entity);

        // Soft delete associated role permissions
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.eq(resourceId))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );
        for (RoleResourcePermission perm : perms) {
            perm.setDeleteFlag(perm.getId());
            perm.setDeletedAt(now);
            rolePermMapper.update(perm);
        }
    }
}
