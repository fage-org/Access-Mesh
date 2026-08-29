package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncResourcesResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源同步处理器实现类
 * <p>
 * 处理资源实体的同步逻辑，将服务配置中的API资源持久化到数据库。
 * 支持 upsert 与孤立资源清理（FULL-only，权威契约 §6.3）；服务删除级联清理见 cleanupServiceOwnedResources。
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
                ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
                    context.tenantId(), context.apiType(), api.resourceCode(), PermConstants.CodeType.DEFAULT);

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
                        throw new BizException(PermissionErrorCode.RESOURCE_STATE_CONFLICT.getCode(),
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

        // 获取该类型的所有API资源
        List<ResourceEntity> apiResources = resourceEntityMapper.selectApiResourcesByType(tenantId, apiType);

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
            : resourceApiMappingMapper.selectByResourceEntityIds(tenantId, syncedResourceIds)
            .stream().collect(Collectors.groupingBy(ResourceApiMapping::getResourceEntityId));

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
     * 清理服务归属的同步维护资源（服务删除级联，T-PERM-027）
     * <p>
     * 删除 owner_service_code ∈ serviceCodes 且 maintainSource=SERVICE_SYNC 的
     * API 资源中已无剩余有效映射的孤立资源。调用前已删除被删服务自身的全部映射，
     * 剩余映射只可能来自其他服务的跨服务手工映射——此类资源保留。
     * </p>
     *
     * @param tenantId     租户ID
     * @param serviceCodes 被删除服务的编码集合
     * @param apiType      API资源类型
     * @return 删除数量
     */
    @Override
    public int cleanupServiceOwnedResources(Long tenantId, Set<String> serviceCodes, Integer apiType) {
        if (serviceCodes == null || serviceCodes.isEmpty()) {
            return 0;
        }

        List<ResourceEntity> apiResources = resourceEntityMapper.selectApiResourcesByType(tenantId, apiType);

        List<ResourceEntity> ownedResources = apiResources.stream()
            .filter(resource -> PermConstants.MaintainSource.SERVICE_SYNC.equals(resource.getMaintainSource())
                && resource.getOwnerServiceCode() != null
                && serviceCodes.contains(resource.getOwnerServiceCode()))
            .toList();

        if (ownedResources.isEmpty()) {
            return 0;
        }

        // 批量加载映射以避免N+1问题；有剩余映射（跨服务手工映射）的资源保留
        Set<Long> ownedResourceIds = ownedResources.stream()
            .map(ResourceEntity::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Set<Long> referencedResourceIds = resourceApiMappingMapper.selectByResourceEntityIds(tenantId, ownedResourceIds)
            .stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .collect(Collectors.toSet());

        List<Long> idsToDelete = ownedResources.stream()
            .map(ResourceEntity::getId)
            .filter(id -> id != null && !referencedResourceIds.contains(id))
            .collect(Collectors.toList());
        if (idsToDelete.isEmpty()) {
            return 0;
        }

        resourceEntityMapper.softDeleteBatch(tenantId, idsToDelete, LocalDateTime.now());
        return idsToDelete.size();
    }

    /**
     * 合并基础路径和路径形成完整路径
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