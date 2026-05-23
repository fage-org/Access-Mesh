package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.*;

/**
 * 权限快照装配器
 * <p>
 * 将权限查询结果转换为接口权限快照条目列表。
 * </p>
 */
public final class SnapshotAssembler {

    private SnapshotAssembler() {
    }

    /**
     * 从 {@link PermResult} 构建接口权限快照条目列表。
     * <p>
     * 提取所有 API 类型资源的权限条目，组装为快照条目。
     * Phase 4 注入 ResourceApiMappingMapper 后完善 path 匹配逻辑。
     * </p>
     *
     * @param result      权限查询结果
     * @param serviceCode 服务编码
     * @return API 权限快照条目列表
     */
    public static List<InterfaceSnapshotResp.ApiPermissionEntry> buildSnapshot(PermResult result, String serviceCode) {
        List<RolePermEntry> entries = result.allEntries();
        Map<Long, ResourceEntity> resourceMap = result.resourceMap() != null ? result.resourceMap() : Map.of();

        List<InterfaceSnapshotResp.ApiPermissionEntry> snapshotEntries = new ArrayList<>();
        for (RolePermEntry entry : entries) {
            if (entry.resourceEntityId() == null) continue;
            ResourceEntity resource = resourceMap.get(entry.resourceEntityId());
            if (resource == null) continue;

            snapshotEntries.add(new InterfaceSnapshotResp.ApiPermissionEntry(
                serviceCode,
                null,  // httpMethod — Phase 4 注入 ResourceApiMappingMapper 后完善
                resource.getPath() != null ? resource.getPath() : resource.getCode(),
                false, // hasCondition — entry.conditionId() != null
                null   // conditionId
            ));
        }
        return snapshotEntries;
    }
}
