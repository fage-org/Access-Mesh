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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef;

/**
 * 资源同步处理器实现类
 * <p>
 * 处理资源实体的同步逻辑，将服务配置中的API资源持久化到数据库。
 * 支持增量同步和孤立资源清理。
 * </p>
 */
@Service
public class ResourceSyncHandlerImpl implements ResourceSyncHandler {

    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper resourceApiMappingMapper;

    /**
     * 构造函数
     *
     * @param resourceEntityMapper     资源实体Mapper
     * @param resourceApiMappingMapper API映射Mapper
     */
    public ResourceSyncHandlerImpl(ResourceEntityMapper resourceEntityMapper,
                                    ResourceApiMappingMapper resourceApiMappingMapper) {
        this.resourceEntityMapper = resourceEntityMapper;
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    /**
     * 同步资源实体
     * <p>
     * 根据服务配置同步API资源实体。创建不存在的新资源，
     * 更新已有的资源，并验证所有权归属。
     * </p>
     *
     * @param context 同步上下文
     * @return 同步结果，包含创建数、更新数和活跃资源ID集合
     */
    @Override
    public SyncResourcesResult syncResources(SyncContext context) {
        int createdCount = 0;
        int updatedCount = 0;
        Set<Long> activeResourceIds = new HashSet<>();

        for (ServiceConfigSyncReq.GroupItem group : context.req().groups()) {
            for (ServiceConfigSyncReq.ApiItem api : group.apis()) {
                String fullPath = joinPath(context.basePath(), api.path());
                String syncKey = context.req().serviceCode() + "|" + api.resourceCode();

                // 查找已有资源
                ResourceEntity resource = resourceEntityMapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(context.tenantId()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(context.apiType()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE.eq(api.resourceCode()))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.CODE_TYPE.eq(PermConstants.CodeType.DEFAULT))
                        .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
                );

                if (resource == null) {
                    // 创建新资源
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
                    LocalDateTime now = LocalDateTime.now();
                    resource.setCreatedAt(now);
                    resource.setUpdatedAt(now);
                    resource.setDeleteFlag(0L);
                    resourceEntityMapper.insert(resource);
                    createdCount++;
                } else {
                    // 验证所有权
                    if (!PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                        || resource.getOwnerServiceCode() == null
                        || !context.req().serviceCode().equals(resource.getOwnerServiceCode())) {
                        throw new IllegalStateException(
                            "资源编码已由非同步源维护: " + api.resourceCode());
                    }
                    // 更新已有资源
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

    /**
     * 清理孤立资源
     * <p>
     * 删除没有剩余API映射的资源实体。
     * 仅清理由服务同步维护且属于当前服务的资源。
     * </p>
     *
     * @param tenantId        租户ID
     * @param serviceCode     服务编码
     * @param apiType         API资源类型
     * @param activeResourceIds 活跃的资源ID集合
     * @return 删除数量
     */
    @Override
    public int cleanupOrphanedResources(Long tenantId, String serviceCode, Integer apiType,
                                         Set<Long> activeResourceIds) {
        int deletedCount = 0;

        // 获取该服务的所有API资源
        List<ResourceEntity> apiResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.eq(apiType))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );

        // 过滤由当前服务同步的资源
        List<ResourceEntity> syncedResources = apiResources.stream()
            .filter(resource -> PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                && serviceCode.equals(resource.getOwnerServiceCode()))
            .toList();

        if (syncedResources.isEmpty()) {
            return 0;
        }

        // 批量加载映射以避免N+1问题
        Set<Long> syncedResourceIds = syncedResources.stream()
            .map(ResourceEntity::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, List<ResourceApiMapping>> mappingsByResourceId = syncedResourceIds.isEmpty() ? Map.of()
            : resourceApiMappingMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.in(syncedResourceIds))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.groupingBy(ResourceApiMapping::getResourceEntityId));

        // 删除孤立资源（没有剩余映射的资源）
        // 性能优化：收集ID并批量软删除
        LocalDateTime now = LocalDateTime.now();
        List<Long> idsToDelete = new ArrayList<>();
        for (ResourceEntity resource : syncedResources) {
            List<ResourceApiMapping> remainMappings = mappingsByResourceId.getOrDefault(
                resource.getId(), List.of());
            if (remainMappings.isEmpty()) {
                idsToDelete.add(resource.getId());
            }
        }
        if (!idsToDelete.isEmpty()) {
            resourceEntityMapper.softDeleteBatch(tenantId, idsToDelete, now);
            deletedCount = idsToDelete.size();
        }

        return deletedCount;
    }

    /**
     * 合并基础路径和路径形成完整路径
     * <p>
     * 将基础路径和API路径合并，处理斜杠拼接。
     * </p>
     *
     * @param basePath 基础路径
     * @param path     API路径
     * @return 完整路径
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