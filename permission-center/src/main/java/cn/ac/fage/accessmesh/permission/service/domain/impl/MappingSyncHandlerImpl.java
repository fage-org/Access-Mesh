package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncMappingsResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * API映射同步处理器实现类
 * <p>
 * 处理API映射的同步逻辑，将服务配置中的API映射关系持久化到数据库。
 * 支持增量同步和过期清理。
 * </p>
 */
@Service
public class MappingSyncHandlerImpl implements MappingSyncHandler {

    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final ResourceEntityMapper resourceEntityMapper;

    /**
     * 构造函数
     *
     * @param resourceApiMappingMapper API映射Mapper
     * @param resourceEntityMapper     资源实体Mapper
     */
    public MappingSyncHandlerImpl(ResourceApiMappingMapper resourceApiMappingMapper,
                                   ResourceEntityMapper resourceEntityMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.resourceEntityMapper = resourceEntityMapper;
    }

    /**
     * 同步API映射
     * <p>
     * 根据服务配置同步API映射关系。首先需要获取资源实体，
     * 然后创建或更新对应的API映射记录。
     * </p>
     *
     * @param context 同步上下文
     * @return 同步结果，包含创建数、更新数和活跃映射键集合
     */
    @Override
    public SyncMappingsResult syncMappings(SyncContext context) {
        int createdCount = 0;
        int updatedCount = 0;
        Set<String> incomingKeys = new HashSet<>();

        // 首先同步资源以获取活跃的资源ID
        // 需要再次获取资源，以便将resourceCode映射到resourceId
        for (ServiceConfigSyncReq.GroupItem group : context.req().groups()) {
            for (ServiceConfigSyncReq.ApiItem api : group.apis()) {
                String fullPath = joinPath(context.basePath(), api.path());
                String routeResourceKey = api.httpMethod().toUpperCase() + "|" + fullPath + "|" + api.resourceCode();
                incomingKeys.add(routeResourceKey);
                String syncKey = context.req().serviceCode() + "|" + api.resourceCode();

                // 获取资源实体
                ResourceEntity resource = resourceEntityMapper.selectByTypeCodeAndCodeType(
                    context.tenantId(), context.apiType(), api.resourceCode(), PermConstants.CodeType.DEFAULT);

                if (resource == null) {
                    // 应该已由ResourceSyncHandler创建
                    // 如果不存在，抛出异常
                    throw new SystemException(PermissionErrorCode.SYNC_RESOURCE_NOT_FOUND.getCode(), "资源未找到: " + api.resourceCode());
                }

                // 查找已有映射
                ResourceApiMapping mapping = resourceApiMappingMapper.selectByUniqueKey(
                    context.tenantId(), resource.getId(),
                    context.req().serviceCode(),
                    api.httpMethod().toUpperCase(),
                    fullPath);

                if (mapping == null) {
                    // 创建新映射
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
                    // 更新已有映射
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

    /**
     * 清理过期的API映射
     * <p>
     * 删除不在活跃键集合中的API映射记录。
     * 仅清理由服务同步维护且属于当前服务的映射。
     * </p>
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @param incomingKeys 活跃的映射键集合
     * @return 删除数量
     */
    @Override
    public int cleanupObsoleteMappings(Long tenantId, String serviceCode, Set<String> incomingKeys) {
        int deletedCount = 0;

        // 获取该服务的所有已有映射
        List<ResourceApiMapping> existingMappings = resourceApiMappingMapper.selectByTenantAndServiceCode(tenantId, serviceCode);

        if (existingMappings.isEmpty()) {
            return 0;
        }

        // 批量加载资源以避免N+1问题
        Set<Long> mappingResourceIds = existingMappings.stream()
            .map(ResourceApiMapping::getResourceEntityId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, ResourceEntity> resourceMap = mappingResourceIds.isEmpty() ? Map.of()
            : resourceEntityMapper.selectValidByIds(tenantId, mappingResourceIds)
                .stream().collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // 性能优化：收集ID并批量软删除
        LocalDateTime now = LocalDateTime.now();
        List<Long> idsToDelete = new ArrayList<>();
        for (ResourceApiMapping mapping : existingMappings) {
            ResourceEntity resource = resourceMap.get(mapping.getResourceEntityId());
            if (resource == null) {
                continue;
            }

            // 仅删除由SERVICE_SYNC维护且属于当前服务的资源映射
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
