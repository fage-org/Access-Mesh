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
                .where(ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(allIds))
                .and(ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
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
        List<Long> ids = new ArrayList<>();
        Long current = resourceEntityId;
        while (current != null) {
            ResourceEntity e = resourceEntityMapper.selectOneById(current);
            if (e == null || e.getDeleteFlag() != 0L || !e.getTenantId().equals(tenantId)) {
                break;
            }
            if (e.getParentId() != null) {
                ids.add(e.getParentId());
                current = e.getParentId();
            } else {
                break;
            }
        }
        return ids;
    }

    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Batch load all entities that might be needed (initial + ancestors)
        // First, load the initial set
        Map<Long, ResourceEntity> entityMap = new HashMap<>();
        Set<Long> toLoad = new HashSet<>(resourceIds);

        while (!toLoad.isEmpty()) {
            List<ResourceEntity> loaded = resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_ENTITY.ID.in(toLoad))
                    .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );
            toLoad.clear();
            for (ResourceEntity e : loaded) {
                entityMap.put(e.getId(), e);
                if (e.getParentId() != null && !entityMap.containsKey(e.getParentId())) {
                    toLoad.add(e.getParentId());
                }
            }
        }

        // Now compute ancestors for each input resourceId
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long resourceId : resourceIds) {
            List<Long> ancestors = new ArrayList<>();
            Long current = resourceId;
            while (current != null) {
                ResourceEntity e = entityMap.get(current);
                if (e == null) {
                    break;
                }
                if (e.getParentId() != null) {
                    ancestors.add(e.getParentId());
                    current = e.getParentId();
                } else {
                    break;
                }
            }
            result.put(resourceId, ancestors);
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

        // Process each resource ID individually using CTE
        // This is efficient because each CTE query is a single database round-trip
        for (Long resourceEntityId : resourceEntityIds) {
            List<Long> descendants = resourceEntityMapper.selectDescendantIds(tenantId, resourceEntityId);
            if (descendants != null && !descendants.isEmpty()) {
                result.get(resourceEntityId).addAll(descendants);
            }
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
                .where(RESOURCE_ENTITY.ID.eq(resourceId))
                .and(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
    }
}
