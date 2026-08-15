package cn.ac.fage.accessmesh.access.admin.service.domain;

import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单领域服务接口
 * <p>
 * 封装菜单树的核心领域逻辑，提供层级遍历、批量查询、软删除等操作。
 * 使用 PostgreSQL CTE 递归查询高效处理菜单树的层级关系。
 * 菜单用于前端动态路由配置和按钮权限控制。
 * 所有方法均遵循租户隔离原则，确保多租户数据安全。
 * </p>
 */
public interface MenuDomainService {

    /**
     * 获取指定菜单的所有子孙菜单ID（不含自身）
     * <p>
     * 使用 PostgreSQL CTE 递归查询向下遍历菜单树。
     * 用于级联删除、权限继承计算等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID，作为遍历起点
     * @return 子孙菜单ID列表，不含起始菜单自身
     */
    List<Long> getDescendantIds(Long tenantId, Long menuId);

    /**
     * 获取指定菜单的所有子孙菜单ID（含自身）
     * <p>
     * 使用 PostgreSQL CTE 递归查询向下遍历菜单树。
     * 用于权限范围计算、菜单树展示等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID，作为遍历起点
     * @return 子孙菜单ID列表，包含起始菜单自身
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long menuId);

    /**
     * 批量获取多个菜单的子孙菜单ID
     * <p>
     * 对多个菜单同时执行子孙查询，返回 ID 到子孙列表的映射。
     * 用于批量操作场景，减少数据库往返次数。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuIds  菜单ID集合，多个遍历起点
     * @return menuId 到子孙ID列表的映射
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> menuIds);

    /**
     * 获取指定菜单的祖先菜单ID
     * <p>
     * 向上遍历父链直到根节点，获取完整的祖先路径。
     * 用于菜单层级计算、权限向上追溯等场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID，作为遍历起点
     * @return 祖先菜单ID列表，从父节点到根节点排序
     */
    List<Long> getAncestorIds(Long tenantId, Long menuId);

    /**
     * 批量获取多个菜单的祖先菜单ID
     * <p>
     * 对多个菜单同时执行祖先查询，返回 ID 到祖先列表的映射。
     * 用于批量操作场景，减少数据库往返次数。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuIds  菜单ID集合，多个遍历起点
     * @return menuId 到祖先ID列表的映射
     */
    Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> menuIds);

    /**
     * 查询有效的菜单实体
     * <p>
     * 查询未删除且属于指定租户的菜单记录。
     * 用于需要精确验证菜单存在性的场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID
     * @return 菜单实体，不存在或已删除返回 null
     */
    SysMenu selectValidById(Long tenantId, Long menuId);

    /**
     * 批量查询有效的菜单实体
     * <p>
     * 批量查询未删除且属于指定租户的菜单记录。
     * 用于批量加载菜单信息避免 N+1 查询问题。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuIds  菜单ID集合
     * @return 菜单实体列表，不存在的ID会被忽略
     */
    List<SysMenu> selectValidByIds(Long tenantId, Set<Long> menuIds);

    /**
     * 批量软删除菜单
     * <p>
     * 将菜单的 delete_flag 设置为菜单ID，deleted_at 设置为当前时间。
     * 用于批量删除场景，保留数据记录便于审计追溯。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuIds  待删除的菜单ID列表
     */
    void softDeleteBatch(Long tenantId, List<Long> menuIds);

    /**
     * 删除菜单及其所有子孙菜单
     * <p>
     * 先查询子孙菜单ID，然后批量软删除所有子孙和自身。
     * 用于菜单树的整体删除场景。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID，删除该菜单及其所有子孙
     */
    void deleteWithChildren(Long tenantId, Long menuId);

    /**
     * 检查菜单是否有子菜单
     * <p>
     * 查询是否存在以该菜单为父节点的子菜单。
     * 用于删除前的验证，防止误删有子节点的菜单。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param menuId   菜单ID
     * @return 有子菜单返回 true，无子菜单返回 false
     */
    boolean hasChildren(Long tenantId, Long menuId);

    /**
     * 根据权限标识查询菜单
     * <p>
     * 通过权限标识（permCode）查找菜单，用于权限编码定位和唯一性检查。
     * 权限标识用于前端按钮级权限控制。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param permCode 权限标识
     * @return 菜单实体，不存在返回 null
     */
    SysMenu findByPermCode(Long tenantId, String permCode);

    /**
     * 批量根据权限标识查询菜单
     * <p>
     * 批量通过权限标识查找菜单，用于批量权限校验。
     * </p>
     *
     * @param tenantId  租户ID，用于多租户隔离
     * @param permCodes 权限标识集合
     * @return 菜单实体列表
     */
    List<SysMenu> findByPermCodes(Long tenantId, Set<String> permCodes);

    /**
     * 计算菜单深度
     * <p>
     * 从根菜单到指定菜单的层级数，用于菜单层级验证。
     * 深度值从 0 开始，根菜单深度为 0。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @param parentId 父菜单ID，从该节点开始计算深度
     * @return 菜单深度值
     */
    int calculateDepth(Long tenantId, Long parentId);

    /**
     * 批量查询已存在的权限标识
     * <p>
     * 从给定的权限标识集合中筛选出已存在的标识。
     * 用于批量创建菜单时的唯一性批量校验。
     * </p>
     *
     * @param tenantId  租户ID，用于多租户隔离
     * @param permCodes 权限标识集合
     * @return 已存在的权限标识集合
     */
    Set<String> findExistingPermCodes(Long tenantId, Set<String> permCodes);

    /**
     * 批量计算菜单深度
     * <p>
     * 对多个父菜单同时计算深度，返回 ID 到深度的映射。
     * 用于批量操作场景，减少数据库往返次数。
     * </p>
     *
     * @param tenantId  租户ID，用于多租户隔离
     * @param parentIds 父菜单ID集合
     * @return parentId 到深度值的映射
     */
    Map<Long, Integer> batchCalculateDepth(Long tenantId, Set<Long> parentIds);

    /**
     * 插入单个菜单并回填主键。
     *
     * @param menu 菜单实体
     */
    void insert(SysMenu menu);

    /**
     * 更新菜单可变字段。
     *
     * @param menu 菜单实体
     */
    void update(SysMenu menu);

    /**
     * 批量插入菜单
     * <p>
     * 批量插入多条菜单记录，用于菜单批量导入场景。
     * </p>
     *
     * @param menus 菜单列表
     */
    void insertBatch(List<SysMenu> menus);

    /**
     * 查询租户下所有有效菜单
     * <p>
     * 获取指定租户的所有未删除菜单，用于前端动态路由初始化。
     * </p>
     *
     * @param tenantId 租户ID，用于多租户隔离
     * @return 所有有效菜单列表
     */
    List<SysMenu> selectAllValid(Long tenantId);
}