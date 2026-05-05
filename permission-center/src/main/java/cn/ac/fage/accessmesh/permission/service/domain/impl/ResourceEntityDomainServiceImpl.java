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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;

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
            .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
            .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0));
        if (parentId != null) {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.PARENT_ID.eq(parentId));
        } else {
            qw.and(ResourceEntityTableDef.RESOURCE_ENTITY.PARENT_ID.isNull());
        }
        return resourceEntityMapper.selectListByQuery(qw);
    }

    @Override
    public List<ResourceEntity> listByType(Long tenantId, Integer resourceType) {
        return resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long resourceId) {
        LocalDateTime now = LocalDateTime.now();
        // Validate entity exists and belongs to tenant
        ResourceEntity entity = selectValidById(tenantId, resourceId);
        if (entity == null) return;

        // Use CTE to get all descendant IDs including self in a single query
        List<Long> allIds = resourceEntityMapper.selectDescendantIdsIncludingSelf(tenantId, resourceId);

        // Get all role permissions for the entire subtree
        List<Long> permIds = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(allIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        ).stream().map(RoleResourcePermission::getId).toList();

        // Batch soft delete all descendants (including self)
        resourceEntityMapper.softDeleteBatch(tenantId, allIds, now);

        // Batch soft delete associated role permissions
        if (!permIds.isEmpty()) {
            rolePermMapper.softDeleteBatch(tenantId, permIds, now);
        }
    }

    @Override
    public List<Long> getAncestorIds(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        // 使用 CTE 递归查询一次性获取所有祖先ID
        List<Long> ancestorIds = resourceEntityMapper.selectAncestorIds(tenantId, resourceEntityId);
        return ancestorIds != null ? ancestorIds : List.of();
    }

    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 使用 CTE 递归查询一次性获取所有资源的祖先ID
        List<ResourceEntityMapper.AncestorResult> results = resourceEntityMapper.selectAncestorIdsBatch(tenantId, resourceIds);

        // 按 resourceId 分组
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long resourceId : resourceIds) {
            result.put(resourceId, new ArrayList<>());
        }

        for (ResourceEntityMapper.AncestorResult ar : results) {
            result.computeIfAbsent(ar.getResourceId(), k -> new ArrayList<>()).add(ar.getAncestorId());
        }

        return result;
    }

    @Override
    public List<Long> getDescendantIds(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        // Use CTE recursive query for efficient single-query retrieval
        List<Long> ids = resourceEntityMapper.selectDescendantIds(tenantId, resourceEntityId);
        return ids != null ? ids : List.of();
    }

    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long resourceEntityId) {
        if (resourceEntityId == null) {
            return List.of();
        }
        // Use CTE recursive query for efficient single-query retrieval
        List<Long> ids = resourceEntityMapper.selectDescendantIdsIncludingSelf(tenantId, resourceEntityId);
        return ids != null ? ids : List.of();
    }

    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> resourceEntityIds) {
        if (resourceEntityIds == null || resourceEntityIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<Long>> result = new HashMap<>();
        // Initialize result with empty lists for all input IDs
        for (Long id : resourceEntityIds) {
            result.put(id, new ArrayList<>());
        }

        // Use batch CTE recursive query for efficient single-query retrieval of all descendants
        List<ResourceEntityMapper.DescendantResult> descendants = resourceEntityMapper.selectDescendantIdsBatch(tenantId, resourceEntityIds);

        // Group by resourceId
        for (ResourceEntityMapper.DescendantResult dr : descendants) {
            result.computeIfAbsent(dr.getResourceId(), k -> new ArrayList<>()).add(dr.getDescendantId());
        }

        return result;
    }

    @Override
    public ResourceEntity selectValidById(Long tenantId, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        return resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.ID.eq(resourceId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public Long findByTypeAndCode(Long tenantId, Integer resourceType, String code) {
        if (tenantId == null || resourceType == null || code == null || code.isBlank()) {
            return null;
        }
        ResourceEntity entity = resourceEntityMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(resourceType))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE.eq(code))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        return entity != null ? entity.getId() : null;
    }
}
