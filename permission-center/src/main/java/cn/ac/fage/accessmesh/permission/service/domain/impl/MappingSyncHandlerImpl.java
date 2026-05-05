package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncMappingsResult;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef;

/**
 * Implementation of MappingSyncHandler.
 * Handles the synchronization of API mappings.
 */
@Service
public class MappingSyncHandlerImpl implements MappingSyncHandler {

    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final ResourceEntityMapper resourceEntityMapper;

    public MappingSyncHandlerImpl(ResourceApiMappingMapper resourceApiMappingMapper,
                                   ResourceEntityMapper resourceEntityMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.resourceEntityMapper = resourceEntityMapper;
    }

    @Override
    public SyncMappingsResult syncMappings(SyncContext context) {
        int createdCount = 0;
        int updatedCount = 0;
        Set<String> incomingKeys = new HashSet<>();

        // First, sync resources to get the active resource IDs
        // We need to get resources again since we need to map resourceCode to resourceId
        for (ServiceConfigSyncReq.GroupItem group : context.req().groups()) {
            for (ServiceConfigSyncReq.ApiItem api : group.apis()) {
                String fullPath = joinPath(context.basePath(), api.path());
                String routeResourceKey = api.httpMethod().toUpperCase() + "|" + fullPath + "|" + api.resourceCode();
                incomingKeys.add(routeResourceKey);
                String syncKey = context.req().serviceCode() + "|" + api.resourceCode();

                // Get the resource entity
                ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(context.tenantId()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(context.apiType()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE.eq(api.resourceCode()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE_TYPE.eq(PermConstants.CodeType.DEFAULT))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );

                if (resource == null) {
                    // This should have been created by ResourceSyncHandler
                    // If not, throw exception
                    throw new IllegalStateException("Resource not found: " + api.resourceCode());
                }

                // Find existing mapping
                ResourceApiMapping mapping = resourceApiMappingMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(context.tenantId()))
                        .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.eq(resource.getId()))
                        .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.SERVICE_CODE.eq(context.req().serviceCode()))
                        .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.HTTP_METHOD.eq(api.httpMethod().toUpperCase()))
                        .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.PATH_PATTERN.eq(fullPath))
                        .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                );

                if (mapping == null) {
                    // Create new mapping
                    mapping = new ResourceApiMapping();
                    mapping.setTenantId(context.tenantId());
                    mapping.setResourceEntityId(resource.getId());
                    mapping.setServiceCode(context.req().serviceCode());
                    mapping.setHttpMethod(api.httpMethod().toUpperCase());
                    mapping.setPathPattern(fullPath);
                    mapping.setMatchOrder(0);
                    mapping.setEnabled(true);
                    mapping.setExtra("{\"syncKey\":\"" + syncKey + "\"}");
                    mapping.setCreatedBy(context.operatorId());
                    LocalDateTime now = LocalDateTime.now();
                    mapping.setCreatedAt(now);
                    mapping.setUpdatedAt(now);
                    mapping.setDeleteFlag(0L);
                    resourceApiMappingMapper.insert(mapping);
                    createdCount++;
                } else {
                    // Update existing mapping
                    mapping.setEnabled(true);
                    mapping.setExtra("{\"syncKey\":\"" + syncKey + "\"}");
                    mapping.setUpdatedAt(LocalDateTime.now());
                    resourceApiMappingMapper.update(mapping);
                    updatedCount++;
                }
            }
        }

        return new SyncMappingsResult(createdCount, updatedCount, incomingKeys);
    }

    @Override
    public int cleanupObsoleteMappings(Long tenantId, String serviceCode, Set<String> incomingKeys) {
        int deletedCount = 0;

        // Get all existing mappings for this service
        List<ResourceApiMapping> existingMappings = resourceApiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.SERVICE_CODE.eq(serviceCode))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );

        if (existingMappings.isEmpty()) {
            return 0;
        }

        // Batch load resources to avoid N+1
        Set<Long> mappingResourceIds = existingMappings.stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, ResourceEntity> resourceMap = mappingResourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ResourceEntityTableDef.RESOURCE_ENTITY.ID.in(mappingResourceIds))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // Performance fix: collect IDs and batch soft delete
        LocalDateTime now = LocalDateTime.now();
        List<Long> idsToDelete = new ArrayList<>();
        for (ResourceApiMapping mapping : existingMappings) {
            ResourceEntity resource = resourceMap.get(mapping.getResourceEntityId());
            if (resource == null) {
                continue;
            }

            // Only delete mappings for SERVICE_SYNC resources owned by this service
            if (!PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                || !serviceCode.equals(resource.getOwnerServiceCode())) {
                continue;
            }

            String routeKey = mapping.getHttpMethod().toUpperCase() + "|" + mapping.getPathPattern();
            String resourceCode = resource.getCode();
            String routeResourceKey = routeKey + "|" + resourceCode;

            if (!incomingKeys.contains(routeResourceKey)) {
                idsToDelete.add(mapping.getId());
            }
        }
        if (!idsToDelete.isEmpty()) {
            resourceApiMappingMapper.softDeleteBatch(tenantId, idsToDelete, now);
            deletedCount = idsToDelete.size();
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