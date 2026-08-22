package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper.DescendantResult;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单领域服务实现类
 * <p>
 * 封装菜单树遍历、批量查询等核心领域逻辑。
 * 使用PostgreSQL CTE递归查询实现高效的树结构操作。
 * </p>
 */
@Service
public class MenuDomainServiceImpl implements MenuDomainService {

    private final SysMenuMapper menuMapper;

    /**
     * 构造函数注入依赖
     *
     * @param menuMapper 菜单数据访问层
     */
    public MenuDomainServiceImpl(SysMenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    /**
     * 计算菜单子树高度
     * <p>
     * 从指定菜单向下的最大层级数（单节点=1），单条递归 CTE 完成。
     * 换父移动时与 calculateDepth 组合校验整棵树层级上限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   子树根菜单ID
     * @return 子树高度（根=1）；菜单不存在返回 0
     */
    @Override
    public int subtreeHeight(Long tenantId, Long menuId) {
        if (menuId == null || menuId == 0L) {
            return 0;
        }
        return menuMapper.selectSubtreeHeight(tenantId, menuId);
    }

    /**
     * 获取指定菜单的所有子孙菜单ID（不包括自身）
     * <p>
     * 使用PostgreSQL CTE递归查询，性能高效
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表
     */
    @Override
    public List<Long> getDescendantIds(Long tenantId, Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        List<Long> ids = menuMapper.selectDescendantIds(tenantId, menuId);
        return ids != null ? ids : List.of();
    }

    /**
     * 获取指定菜单的所有子孙菜单ID（包括自身）
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表（包含自身）
     */
    @Override
    public List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long menuId) {
        if (menuId == null) {
            return List.of();
        }
        List<Long> ids = menuMapper.selectDescendantIdsIncludingSelf(tenantId, menuId);
        return ids != null ? ids : List.of();
    }

    /**
     * 批量获取多个菜单的子孙ID
     * <p>
     * 使用单次批量CTE查询替代N+1查询，性能优化
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return menuId到子孙ID列表的映射
     */
    @Override
    public Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 为每个输入ID初始化空列表
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long id : menuIds) {
            result.put(id, new ArrayList<>());
        }

        // 性能优化：使用单次批量CTE查询替代N+1查询
        List<DescendantResult> descendants = menuMapper.selectBatchDescendantIds(tenantId, menuIds);
        for (DescendantResult dr : descendants) {
            Long rootId = dr.getMenuId();
            Long descId = dr.getDescendantId();
            if (rootId != null && descId != null) {
                result.computeIfAbsent(rootId, k -> new ArrayList<>()).add(descId);
            }
        }

        return result;
    }

    /**
     * 获取指定菜单的祖先菜单ID
     * <p>
     * 使用批量加载模式避免N+1查询
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 祖先菜单ID列表
     */
    @Override
    public List<Long> getAncestorIds(Long tenantId, Long menuId) {
        // 性能优化：使用批量加载模式（与OrgDomainServiceImpl.batchGetAncestorIds相同）
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(menuId));
        return ancestorMap.getOrDefault(menuId, List.of());
    }

    /**
     * 批量获取多个菜单的祖先ID
     * <p>
     * 使用批量加载模式，一次性加载所有菜单及其祖先链。
     * 避免递归调用导致的N+1查询问题。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return menuId到祖先ID列表的映射
     */
    @Override
    public Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 批量加载所有菜单及其祖先
        Map<Long, SysMenu> entityMap = new HashMap<>();
        Set<Long> toLoad = new HashSet<>(menuIds);

        while (!toLoad.isEmpty()) {
            List<SysMenu> loaded = menuMapper.selectByIdsForAncestors(tenantId, toLoad);
            toLoad.clear();
            for (SysMenu menu : loaded) {
                entityMap.put(menu.getId(), menu);
                if (menu.getParentId() != null && menu.getParentId() != 0L
                    && !entityMap.containsKey(menu.getParentId())) {
                    toLoad.add(menu.getParentId());
                }
            }
        }

        // 为每个输入menuId构建祖先链
        Map<Long, List<Long>> result = new HashMap<>();
        for (Long menuId : menuIds) {
            List<Long> ancestors = new ArrayList<>();
            Long current = menuId;
            while (current != null) {
                SysMenu menu = entityMap.get(current);
                if (menu == null) {
                    break;
                }
                if (menu.getParentId() != null && menu.getParentId() != 0L) {
                    ancestors.add(menu.getParentId());
                    current = menu.getParentId();
                } else {
                    break;
                }
            }
            result.put(menuId, ancestors);
        }
        return result;
    }

    /**
     * 查询有效的菜单
     * <p>
     * 未删除、属于指定租户
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 菜单实体，不存在返回null
     */
    @Override
    public SysMenu selectValidById(Long tenantId, Long menuId) {
        if (menuId == null) {
            return null;
        }
        return menuMapper.selectValidById(tenantId, menuId);
    }

    /**
     * 批量查询有效的菜单
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return 菜单实体列表
     */
    @Override
    public List<SysMenu> selectValidByIds(Long tenantId, Set<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectValidByIds(tenantId, menuIds);
    }

    /**
     * 批量软删除菜单
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        menuMapper.softDeleteBatch(tenantId, menuIds, LocalDateTime.now());
    }

    /**
     * 删除菜单及其所有子孙菜单
     * <p>
     * 先查询所有子孙ID（包括自身），然后批量软删除
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithChildren(Long tenantId, Long menuId) {
        SysMenu menu = selectValidById(tenantId, menuId);
        if (menu == null) return;

        List<Long> allIds = menuMapper.selectDescendantIdsIncludingSelf(tenantId, menuId);
        menuMapper.softDeleteBatch(tenantId, allIds, LocalDateTime.now());
    }

    /**
     * 检查菜单是否有子菜单
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 是否有子菜单
     */
    @Override
    public boolean hasChildren(Long tenantId, Long menuId) {
        long count = menuMapper.countChildren(tenantId, menuId);
        return count > 0;
    }

    /**
     * 检查路由路径是否已被其他有效菜单占用
     * <p>
     * uk_sys_menu_tenant_path 唯一索引预查，并发窗口由数据库唯一索引兜底
     * </p>
     *
     * @param tenantId  租户ID
     * @param path      路由路径
     * @param excludeId 排除的菜单ID（更新场景传自身ID，创建场景传 null）
     * @return 已被占用返回 true
     */
    @Override
    public boolean pathExists(Long tenantId, String path, Long excludeId) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return menuMapper.existsByPath(tenantId, path,
            excludeId != null ? Set.of(excludeId) : Set.of());
    }

    /**
     * 检查资源关联是否已被其他有效菜单占用
     * <p>
     * uk_sys_menu_tenant_resource 唯一索引预查，并发窗口由数据库唯一索引兜底
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 关联业务资源类型
     * @param resourceCode 关联业务资源实例
     * @param excludeId    排除的菜单ID（更新场景传自身ID，创建场景传 null）
     * @return 已被占用返回 true
     */
    @Override
    public boolean resourceExists(Long tenantId, String resourceType, String resourceCode, Long excludeId) {
        if (resourceType == null || resourceType.isBlank()) {
            return false;
        }
        return menuMapper.existsByResource(tenantId, resourceType, resourceCode,
            excludeId != null ? Set.of(excludeId) : Set.of());
    }

    /**
     * 计算菜单深度
     * <p>
     * 从根到指定菜单的层级数。使用批量祖先加载优化性能。
     * </p>
     *
     * @param tenantId 租户ID
     * @param parentId 父菜单ID
     * @return 深度值
     */
    @Override
    public int calculateDepth(Long tenantId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return 1;
        }

        // 性能优化：预加载祖先并计算深度
        // 使用batchGetAncestorIds批量加载所有祖先
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, Set.of(parentId));
        List<Long> ancestors = ancestorMap.getOrDefault(parentId, List.of());

        // 深度 = 祖先数量 + 1（自身）
        return ancestors.size() + 1;
    }

    /**
     * 批量计算菜单深度
     * <p>
     * 返回parentId到depth的映射，使用批量祖先加载优化性能
     * </p>
     *
     * @param tenantId  租户ID
     * @param parentIds 父菜单ID集合
     * @return parentId到depth的映射
     */
    @Override
    public Map<Long, Integer> batchCalculateDepth(Long tenantId, Set<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        // 过滤null和0（根级）
        Set<Long> validParentIds = parentIds.stream()
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());

        if (validParentIds.isEmpty()) {
            return Map.of();
        }

        // 使用batchGetAncestorIds获取所有父ID的所有祖先
        Map<Long, List<Long>> ancestorMap = batchGetAncestorIds(tenantId, validParentIds);

        // 构建parentId到depth的映射
        Map<Long, Integer> result = new HashMap<>();
        for (Long parentId : validParentIds) {
            List<Long> ancestors = ancestorMap.getOrDefault(parentId, List.of());
            // 深度 = 祖先数量 + 1（自身）
            result.put(parentId, ancestors.size() + 1);
        }
        return result;
    }

    /**
     * 批量插入菜单
     *
     * @param menus 菜单列表
     */
    @Override
    public void insert(SysMenu menu) {
        if (menu == null) {
            return;
        }
        menuMapper.insert(menu);
    }

    @Override
    public void update(SysMenu menu) {
        if (menu == null) {
            return;
        }
        menuMapper.update(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertBatch(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        menuMapper.insertBatch(menus);
    }

    /**
     * 查询租户下所有有效菜单
     * <p>
     * 用于动态路由生成，按排序字段升序排列
     * </p>
     *
     * @param tenantId 租户ID
     * @return 所有有效菜单列表
     */
    @Override
    public List<SysMenu> selectAllValid(Long tenantId) {
        return menuMapper.selectAllValid(tenantId);
    }
}