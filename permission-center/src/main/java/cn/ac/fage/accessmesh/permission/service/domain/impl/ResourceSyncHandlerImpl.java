package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncResourcesResult;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef.RESOURCE_ENTITY;
import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;

/**
 * Implementation of ResourceSyncHandler.
 * Handles the synchronization of resource entities.
 */
@Service
public class ResourceSyncHandlerImpl implements ResourceSyncHandler {

    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper resourceApiMappingMapper;

    public ResourceSyncHandlerImpl(ResourceEntityMapper resourceEntityMapper,
                                    ResourceApiMappingMapper resourceApiMappingMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    @Override
    public SyncResourcesResult syncResources(SyncContext context) {
        int createdCount = 0;
        int updatedCount = 0;
        Set<Long> activeResourceIds = new HashSet<>();

        for (ServiceConfigSyncReq.GroupItem group : context.req().groups()) {
            for (ServiceConfigSyncReq.ApiItem api : group.apis()) {
                String fullPath = joinPath(context.basePath(), api.path());
                String syncKey = context.req().serviceCode() + "|" + api.resourceCode();

                // Find existing resource
                ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(RESOURCE_ENTITY.TENANT_ID.eq(context.tenantId()))
                        .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(context.apiType()))
                        .and(RESOURCE_ENTITY.CODE.eq(api.resourceCode()))
                        .and(RESOURCE_ENTITY.CODE_TYPE.eq(PermConstants.CodeType.DEFAULT))
                        .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );

                if (resource == null) {
                    // Create new resource
                    resource = new ResourceEntity();
                    resource.setTenantId(context.tenantId());
                    resource.setResourceType(context.apiType());
                    resource.setCode(api.resourceCode());
                    resource.setCodeType(PermConstants.CodeType.DEFAULT);
                    resource.setName(api.name());
                    resource.setPath(fullPath);
                    resource.setStatus(1);
                    resource.setSortOrder(0);
                    resource.setOwnerServiceCode(context.req().serviceCode());
                    resource.setMaintainSource(PermConstants.MaintainSource.SERVICE_SYNC);
                    resource.setSyncKey(syncKey);
                    resource.setExtra("{}");
                    resource.setCreatedBy(context.operatorId());
                    resource.setCreatedAt(LocalDateTime.now());
                    resource.setUpdatedAt(LocalDateTime.now());
                    resource.setDeleteFlag(0L);
                    resourceEntityMapper.insert(resource);
                    createdCount++;
                } else {
                    // Validate ownership
                    if (!PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                        || resource.getOwnerServiceCode() == null
                        || !context.req().serviceCode().equals(resource.getOwnerServiceCode())) {
                        throw new IllegalStateException(
                            "resourceCode already maintained by non-sync source: " + api.resourceCode());
                    }
                    // Update existing resource
                    resource.setName(api.name());
                    resource.setPath(fullPath);
                    resource.setStatus(1);
                    resource.setSyncKey(syncKey);
                    resource.setUpdatedAt(LocalDateTime.now());
                    resourceEntityMapper.update(resource);
                    updatedCount++;
                }

                activeResourceIds.add(resource.getId());
            }
        }

        return new SyncResourcesResult(createdCount, updatedCount, activeResourceIds);
    }

    @Override
    public int cleanupOrphanedResources(Long tenantId, String serviceCode, Integer apiType,
                                         Set<Long> activeResourceIds) {
        int deletedCount = 0;

        // Get all API resources for this service
        List<ResourceEntity> apiResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(RESOURCE_ENTITY.RESOURCE_TYPE.eq(apiType))
                .and(RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );

        // Filter synced resources for this service
        List<ResourceEntity> syncedResources = apiResources.stream()
            .filter(resource -> PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                && serviceCode.equals(resource.getOwnerServiceCode()))
            .toList();

        if (syncedResources.isEmpty()) {
            return 0;
        }

        // Batch load mappings to avoid N+1
        Set<Long> syncedResourceIds = syncedResources.stream()
            .map(ResourceEntity::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, List<ResourceApiMapping>> mappingsByResourceId = syncedResourceIds.isEmpty() ? Map.of()
            : resourceApiMappingMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                    .and(RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.in(syncedResourceIds))
                    .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.groupingBy(ResourceApiMapping::getResourceEntityId));

        // Delete orphaned resources (those without remaining mappings)
        LocalDateTime now = LocalDateTime.now();
        for (ResourceEntity resource : syncedResources) {
            List<ResourceApiMapping> remainMappings = mappingsByResourceId.getOrDefault(
                resource.getId(), List.of());
            if (remainMappings.isEmpty()) {
                resource.setDeleteFlag(resource.getId());
                resource.setDeletedAt(now);
                resourceEntityMapper.update(resource);
                deletedCount++;
            }
        }

        return deletedCount;
    }

    /**
     * Join base path and path to form full path.
     */
    private String joinPath(String basePath, String path) {
        String bp = basePath == null ? "" : basePath;
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return (bp + p).replaceAll("//+", "/");
    }
}