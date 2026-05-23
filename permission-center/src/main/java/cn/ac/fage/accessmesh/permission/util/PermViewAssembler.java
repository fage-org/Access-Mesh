package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.dto.query.PermViewFilter;
import cn.ac.fage.accessmesh.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.permission.dto.query.PermViewResult.RoleInfo;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限视图装配器
 * <p>
 * 纯转换器：接收 {@link PermResult} + {@link PermViewFilter}，
 * 应用过滤和分页逻辑，返回 {@link PermViewResult}。
 * </p>
 *
 * <h3>Phase 1 已实现</h3>
 * <ul>
 *   <li>resourceKeyword — 按资源名称关键词过滤</li>
 *   <li>分页 — pageNum / pageSize / hasNext</li>
 *   <li>sourceRoleMap — 从 roleMap 提取来源角色信息</li>
 * </ul>
 *
 * <h3>TODO: Phase 4</h3>
 * <ul>
 *   <li>resourceTypes — 需注入 TypeResolutionService 做 String code → Integer value 转换后再比对</li>
 *   <li>operationCodes — 需注入 OperationPermissionMapper 按操作码过滤</li>
 *   <li>domainCode — 需注入 DomainClassifyService 按域过滤</li>
 *   <li>excludeApiResources — 需注入 TypeResolutionService 获取 API 资源类型值后排除</li>
 *   <li>includeScopePermissions — 需按 scopeAll 标识过滤</li>
 *   <li>includeSourceRoles / sourceRoleLimit — 需条件化构建和截断 sourceRoleMap</li>
 * </ul>
 */
public final class PermViewAssembler {

    private PermViewAssembler() {
    }

    /**
     * 将 {@link PermResult} 转换为 {@link PermViewResult}，
     * 应用指定的过滤条件和分页参数。
     *
     * @param result 权限查询结果（来自引擎）
     * @param filter 视图过滤条件（可为 null，使用默认值）
     * @return 权限视图结果
     */
    public static PermViewResult assemble(PermResult result, PermViewFilter filter) {
        List<RolePermEntry> entries = result.allEntries();
        Map<Long, ResourceEntity> resourceMap = result.resourceMap() != null ? result.resourceMap() : Map.of();
        Map<Long, AbstractRole> roleMap = result.roleMap() != null ? result.roleMap() : Map.of();

        // ── TODO: Phase 4 — resourceTypes 过滤 ──
        // 当前不执行：filter.getResourceTypes() 返回 String code，需 TypeResolutionService 转为 Integer 后比对 e.resourceType()

        // ── TODO: Phase 4 — operationCodes 过滤 ──

        // ── 已实现：resourceKeyword 过滤 ──
        entries = filterByKeyword(entries, filter, resourceMap);

        // ── TODO: Phase 4 — domainCode 过滤 ──
        // ── TODO: Phase 4 — excludeApiResources ──
        // ── TODO: Phase 4 — includeScopePermissions ──

        // ── 已实现：构建来源角色映射 ──
        Map<Long, RoleInfo> sourceRoleMap = buildSourceRoleMap(entries, roleMap);
        // ── TODO: Phase 4 — includeSourceRoles 条件化 / sourceRoleLimit 截断 ──

        // ── TODO: Phase 4 — 构建域编码映射 ──
        Map<Long, String> domainCodeMap = Map.of();

        // ── 已实现：分页 ──
        return paginate(entries, filter, result, resourceMap, roleMap, sourceRoleMap, domainCodeMap);
    }

    private static List<RolePermEntry> filterByKeyword(
        List<RolePermEntry> entries, PermViewFilter filter, Map<Long, ResourceEntity> resourceMap) {
        if (filter == null || filter.getResourceKeyword() == null || filter.getResourceKeyword().isBlank()) {
            return entries;
        }
        String keyword = filter.getResourceKeyword().toLowerCase();
        return entries.stream()
            .filter(e -> {
                if (e.resourceEntityId() == null) return false;
                ResourceEntity res = resourceMap.get(e.resourceEntityId());
                return res != null && res.getName() != null
                    && res.getName().toLowerCase().contains(keyword);
            })
            .collect(Collectors.toList());
    }

    private static Map<Long, RoleInfo> buildSourceRoleMap(
        List<RolePermEntry> entries, Map<Long, AbstractRole> roleMap) {
        Map<Long, RoleInfo> map = new LinkedHashMap<>();
        for (RolePermEntry entry : entries) {
            if (entry.roleId() != null && !map.containsKey(entry.roleId())) {
                AbstractRole role = roleMap.get(entry.roleId());
                if (role != null) {
                    // TODO: Phase 4 — typeCode 需注入 TypeResolutionService 解析
                    map.put(entry.roleId(),
                        new RoleInfo(null, role.getExternalId(), role.getName()));
                }
            }
        }
        return map;
    }

    private static PermViewResult paginate(
        List<RolePermEntry> entries, PermViewFilter filter, PermResult result,
        Map<Long, ResourceEntity> resourceMap, Map<Long, AbstractRole> roleMap,
        Map<Long, RoleInfo> sourceRoleMap, Map<Long, String> domainCodeMap) {
        long totalCount = entries.size();
        int pageNum = filter != null && filter.getPageNum() > 0 ? filter.getPageNum() : 1;
        int pageSize = filter != null && filter.getPageSize() > 0 ? filter.getPageSize() : 20;
        int offset = Math.max((pageNum - 1) * pageSize, 0);
        List<RolePermEntry> paged = entries.stream()
            .skip(offset)
            .limit(pageSize)
            .collect(Collectors.toList());
        boolean hasNext = offset + pageSize < totalCount;

        return PermViewResult.builder()
            .entries(paged)
            .resourceMap(resourceMap)
            .operationMap(result.operationMap())
            .roleMap(roleMap)
            .sourceRoleMap(sourceRoleMap)
            .domainCodeMap(domainCodeMap)
            .totalCount(totalCount)
            .pageNum(pageNum)
            .pageSize(pageSize)
            .hasNext(hasNext)
            .build();
    }
}
