package cn.ac.fage.accessmesh.admin.service.domain;

import cn.ac.fage.accessmesh.admin.entity.SysMenu;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单领域服务
 * 封装菜单树遍历、批量查询等核心领域逻辑
 */
public interface MenuDomainService {

    /**
     * 获取指定菜单的所有子孙菜单ID（不包括自身）
     * 使用 PostgreSQL CTE 递归查询
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表
     */
    List<Long> getDescendantIds(Long tenantId, Long menuId);

    /**
     * 获取指定菜单的所有子孙菜单ID（包括自身）
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表（包含自身）
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long menuId);

    /**
     * 批量获取多个菜单的子孙ID
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return menuId -> 子孙ID列表的映射
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> menuIds);

    /**
     * 获取指定菜单的祖先菜单ID
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 祖先菜单ID列表
     */
    List<Long> getAncestorIds(Long tenantId, Long menuId);

    /**
     * 查询有效的菜单（未删除、属于指定租户）
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 菜单实体，不存在返回null
     */
    SysMenu selectValidById(Long tenantId, Long menuId);

    /**
     * 批量查询有效的菜单
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return 菜单实体列表
     */
    List<SysMenu> selectValidByIds(Long tenantId, Set<Long> menuIds);

    /**
     * 批量软删除菜单
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> menuIds);

    /**
     * 删除菜单及其所有子孙菜单
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     */
    void deleteWithChildren(Long tenantId, Long menuId);

    /**
     * 检查菜单是否有子菜单
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 是否有子菜单
     */
    boolean hasChildren(Long tenantId, Long menuId);

    /**
     * 根据权限标识查询菜单
     *
     * @param tenantId 租户ID
     * @param permCode 权限标识
     * @return 菜单实体
     */
    SysMenu findByPermCode(Long tenantId, String permCode);

    /**
     * 批量根据权限标识查询菜单
     *
     * @param tenantId  租户ID
     * @param permCodes 权限标识集合
     * @return 菜单实体列表
     */
    List<SysMenu> findByPermCodes(Long tenantId, Set<String> permCodes);

    /**
     * 计算菜单深度（从根到指定菜单的层级数）
     *
     * @param tenantId 租户ID
     * @param parentId 父菜单ID
     * @return 深度值
     */
    int calculateDepth(Long tenantId, Long parentId);

    /**
     * 批量查询已存在的权限标识
     *
     * @param tenantId  租户ID
     * @param permCodes 权限标识集合
     * @return 已存在的权限标识集合
     */
    Set<String> findExistingPermCodes(Long tenantId, Set<String> permCodes);

    /**
     * 批量计算菜单深度（返回 parentId -> depth 的映射）
     *
     * @param tenantId  租户ID
     * @param parentIds 父菜单ID集合
     * @return parentId -> depth 的映射
     */
    Map<Long, Integer> batchCalculateDepth(Long tenantId, Set<Long> parentIds);

    /**
     * 批量插入菜单
     *
     * @param menus 菜单列表
     */
    void insertBatch(List<SysMenu> menus);
}