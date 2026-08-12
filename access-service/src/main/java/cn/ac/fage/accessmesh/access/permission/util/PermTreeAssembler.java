package cn.ac.fage.accessmesh.access.permission.util;

import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;

import java.util.*;

/**
 * 权限树装配器
 * <p>
 * 将权限查询结果构建为树形结构，用于前端树形展示。
 * </p>
 */
public final class PermTreeAssembler {

    private PermTreeAssembler() {
    }

    /**
     * 将 {@link PermResult} 构建为用户资源权限树。
     * <p>
     * 仅包含用户有权限的资源节点，按父子关系组织。
     * </p>
     *
     * @param result 权限查询结果
     * @param filter 视图过滤条件
     * @return 资源权限树节点列表
     */
    public static List<PermissionTreeResp.TreeNode> buildUserResourceTree(PermResult result, PermViewFilter filter) {
        List<RolePermEntry> entries = result.allEntries();
        Map<Long, ResourceEntity> resourceMap = result.resourceMap() != null ? result.resourceMap() : Map.of();

        // 收集有权限的资源 ID
        Set<Long> permittedIds = new LinkedHashSet<>();
        for (RolePermEntry entry : entries) {
            if (entry.resourceEntityId() != null) {
                permittedIds.add(entry.resourceEntityId());
            }
        }

        // 构建父子关系
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        Set<Long> childIds = new HashSet<>();
        for (Long id : permittedIds) {
            ResourceEntity entity = resourceMap.get(id);
            if (entity != null && entity.getParentId() != null && permittedIds.contains(entity.getParentId())) {
                childrenMap.computeIfAbsent(entity.getParentId(), k -> new ArrayList<>()).add(id);
                childIds.add(id);
            }
        }

        // 返回顶层节点（没有父节点在权限集合中的节点）
        List<PermissionTreeResp.TreeNode> roots = new ArrayList<>();
        for (Long id : permittedIds) {
            if (!childIds.contains(id)) {
                roots.add(buildNode(id, resourceMap, childrenMap, new HashSet<>()));
            }
        }
        return roots;
    }

    private static PermissionTreeResp.TreeNode buildNode(
        Long id, Map<Long, ResourceEntity> resourceMap,
        Map<Long, List<Long>> childrenMap, Set<Long> visited) {

        if (visited.contains(id)) {
            return new PermissionTreeResp.TreeNode(id, null, null, null, 0, Set.of(), false, null);
        }
        visited.add(id);

        ResourceEntity entity = resourceMap.get(id);
        List<Long> childIds = childrenMap.getOrDefault(id, List.of());
        List<PermissionTreeResp.TreeNode> children = childIds.stream()
            .map(childId -> buildNode(childId, resourceMap, childrenMap, visited))
            .toList();

        return new PermissionTreeResp.TreeNode(
            id,
            null,  // typeCode — Phase 4 注入 TypeResolutionService 后完善
            entity != null ? entity.getCode() : null,
            entity != null ? entity.getName() : null,
            0,     // depth
            Set.of(),
            false, // canGrant
            children.isEmpty() ? null : children
        );
    }
}
