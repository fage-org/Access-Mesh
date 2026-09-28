package cn.ac.fage.accessmesh.access.resource.service.domain.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import cn.ac.fage.accessmesh.access.resource.enums.ApiMappingSource;
import cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ApiMappingWriteDomainService.Write;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.access.projection.PermConstants;

import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.access.sync.strategy.SyncContext;
import cn.ac.fage.accessmesh.access.sync.strategy.SyncMappingsResult;
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
 * 支持 upsert 与过期清理（FULL-only，权威契约 §6.3）。
 * </p>
 */
@Service
public class MappingSyncHandlerImpl implements MappingSyncHandler {

    private final ResourceApiMappingMapper resourceApiMappingMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ApiMappingWriteDomainService mappingWriter;
    private final ServiceConfigMapper serviceConfigMapper;

    /**
     * 构造函数
     *
     * @param resourceApiMappingMapper API映射Mapper
     * @param resourceEntityMapper     资源实体Mapper
     */
    public MappingSyncHandlerImpl(ResourceApiMappingMapper resourceApiMappingMapper,
                                   ResourceEntityMapper resourceEntityMapper, ApiMappingWriteDomainService mappingWriter,
                                   ServiceConfigMapper serviceConfigMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.mappingWriter = mappingWriter;
        this.serviceConfigMapper = serviceConfigMapper;
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
        Set<String> codes = context.req().groups().stream().flatMap(g -> g.apis().stream())
            .map(a -> a.resourceCode()).collect(Collectors.toSet());
        Map<String, ResourceEntity> resourceByCode = codes.isEmpty() ? Map.of()
            : resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(context.tenantId(), context.apiType(), codes,
                Set.of(PermConstants.CodeType.DEFAULT)).stream().collect(Collectors.toMap(ResourceEntity::getCode, r -> r));
        Map<String, ResourceApiMapping> byRoute = new java.util.HashMap<>();
        for (ResourceApiMapping row : resourceApiMappingMapper.selectByTenantAndServiceCode(context.tenantId(), context.req().serviceCode())) {
            byRoute.put(BusinessKeyUtil.apiRouteResourceKey(row.getHttpMethod(), row.getPathPattern(),
                String.valueOf(row.getResourceEntityId())), row);
        }
        List<Write> writes = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        Set<String> incomingKeys = new HashSet<>();
        for (var group : context.req().groups()) {
            for (var api : group.apis()) {
                String fullPath = joinPath(context.basePath(), api.path());
                String method = api.httpMethod().toUpperCase(java.util.Locale.ROOT);
                incomingKeys.add(BusinessKeyUtil.apiRouteResourceKey(method, fullPath, api.resourceCode()));
                ResourceEntity resource = resourceByCode.get(api.resourceCode());
                if (resource == null) {
                    throw new SystemException(AccessErrorCode.SYNC_RESOURCE_NOT_FOUND.getCode(), "资源未找到: " + api.resourceCode());
                }
                String key = BusinessKeyUtil.apiRouteResourceKey(method, fullPath, String.valueOf(resource.getId()));
                ResourceApiMapping mapping = byRoute.get(key);
                if (mapping == null) {
                    mapping = new ResourceApiMapping();
                    mapping.setTenantId(context.tenantId());
                    mapping.setResourceEntityId(resource.getId());
                    mapping.setServiceCode(context.req().serviceCode());
                    mapping.setHttpMethod(method);
                    mapping.setPathPattern(fullPath);
                    mapping.setMatchOrder(0);
                    mapping.setCreatedBy(context.operatorId());
                    createdCount++;
                    byRoute.put(key, mapping);
                } else {
                    updatedCount++;
                }
                mapping.setEnabled(true);
                mapping.setUpdatedBy(context.operatorId());
                writes.add(new Write(mapping, api.requiredPermission()));
            }
        }
        mappingWriter.saveAll(context.tenantId(), context.req().serviceCode(), ApiMappingSource.SERVICE_SYNC, writes);
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
            // 映射拥有独立来源，不能从绑定资源的来源推断清理权。
            if (!PermConstants.MaintainSource.SERVICE_SYNC.equals(mapping.getMaintainSource())
                || !serviceCode.equals(mapping.getServiceCode())) {
                continue;
            }

            ResourceEntity resource = resourceMap.get(mapping.getResourceEntityId());
            if (resource == null) {
                idsToDelete.add(mapping.getId());
                continue;
            }

            String resourceCode = resource.getCode();
            String routeResourceKey = BusinessKeyUtil.apiRouteResourceKey(
                mapping.getHttpMethod().toUpperCase(), mapping.getPathPattern(), resourceCode);

            if (!incomingKeys.contains(routeResourceKey)) {
                idsToDelete.add(mapping.getId());
            }
        }
        if (!idsToDelete.isEmpty()) {
            resourceApiMappingMapper.softDeleteBatch(tenantId, idsToDelete, now);
            deletedCount = idsToDelete.size();
            // T-ACCESS-059：FULL 清理改变路由集，同事务递增配置代次（upsert 半边由 saveAll 覆盖）
            serviceConfigMapper.incrementConfigGeneration(tenantId, serviceCode);
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
