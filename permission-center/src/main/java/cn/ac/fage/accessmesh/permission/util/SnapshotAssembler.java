package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限快照装配器
 * <p>
 * 将权限查询结果转换为接口权限快照条目列表。
 * 通过引擎获取用户全量权限，按 API 资源类型过滤，查询 API 映射并组装条目。
 * scopeAll 条目不展开为 N 个 API 资源，而是直接作为一个条目传递。
 * </p>
 */
@Component
public class SnapshotAssembler {

    private final ResourceApiMappingMapper apiMappingMapper;

    public SnapshotAssembler(ResourceApiMappingMapper apiMappingMapper) {
        this.apiMappingMapper = apiMappingMapper;
    }

    /**
     * 从 {@link PermResult} 构建接口权限快照条目列表。
     * <p>
     * 提取所有 API 类型资源的权限条目，查询对应的 API 映射，组装为快照条目。
     * scopeAll 条目不展开，直接作为一个条目放入结果（httpMethod 和 pathPattern 为 null）。
     * 实例级条目照常通过 API 映射组装。
     * </p>
     *
     * @param tenantId    租户ID
     * @param result      权限查询结果
     * @param serviceCode 服务编码
     * @param apiType     API 资源类型值
     * @return API 权限快照条目列表
     */
    public List<InterfaceSnapshotResp.ApiPermissionEntry> buildSnapshot(
            Long tenantId, PermResult result, String serviceCode, Integer apiType) {
        List<RolePermEntry> entries = result.allEntries();
        if (apiType == null || entries.isEmpty()) {
            return List.of();
        }

        // 筛选 API 类型的权限条目
        List<RolePermEntry> apiEntries = entries.stream()
            .filter(e -> e.resourceType() != null && e.resourceType().equals(apiType))
            .toList();

        if (apiEntries.isEmpty()) {
            return List.of();
        }

        List<InterfaceSnapshotResp.ApiPermissionEntry> snapshotEntries = new ArrayList<>();

        // 1. scopeAll 条目：不展开，直接作为一个条目
        for (RolePermEntry e : apiEntries) {
            if (Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null) {
                snapshotEntries.add(new InterfaceSnapshotResp.ApiPermissionEntry(
                    serviceCode, null, null, e.hasCondition(), e.conditionId(), true));
            }
        }

        // 2. 实例级条目：照常处理
        Set<Long> instanceResourceIds = apiEntries.stream()
            .filter(e -> e.resourceEntityId() != null && !Boolean.TRUE.equals(e.scopeAll()))
            .map(RolePermEntry::resourceEntityId)
            .collect(Collectors.toSet());

        if (!instanceResourceIds.isEmpty()) {
            // 查询 API 映射
            List<ResourceApiMapping> apiMappings = apiMappingMapper.selectForSnapshot(
                tenantId, serviceCode, instanceResourceIds);

            // 构建 permsByResource 用于条件查询
            Map<Long, List<RolePermEntry>> permsByResource = apiEntries.stream()
                .filter(e -> e.resourceEntityId() != null && !Boolean.TRUE.equals(e.scopeAll()))
                .collect(Collectors.groupingBy(RolePermEntry::resourceEntityId));

            for (ResourceApiMapping mapping : apiMappings) {
                List<RolePermEntry> resourcePerms = permsByResource.getOrDefault(
                    mapping.getResourceEntityId(), List.of());
                boolean hasCondition = resourcePerms.stream().anyMatch(RolePermEntry::hasCondition);
                Long conditionId = resourcePerms.stream()
                    .filter(e -> e.conditionId() != null)
                    .map(RolePermEntry::conditionId)
                    .findFirst().orElse(null);

                snapshotEntries.add(new InterfaceSnapshotResp.ApiPermissionEntry(
                    mapping.getServiceCode(),
                    mapping.getHttpMethod(),
                    mapping.getPathPattern(),
                    hasCondition,
                    conditionId,
                    false
                ));
            }
        }

        return snapshotEntries;
    }
}
