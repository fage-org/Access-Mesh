package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统菜单数据访问接口
 * <p>
 * 提供菜单表的基础CRUD操作和自定义查询方法。
 * 支持批量软删除、子孙菜单递归查询等操作。
 * </p>
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询菜单列表（用于构建树）
     *
     * @param tenantId 租户ID
     * @return 菜单列表，按排序字段和创建时间排序
     */
    List<SysMenu> selectMenusForTree(@Param("tenantId") Long tenantId);

    /**
     * 批量软删除菜单
     * <p>
     * 将指定菜单的delete_flag设置为id（行自身ID），deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的菜单ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL CTE递归查询获取所有子孙菜单ID（不含自身）
     * <p>
     * 从指定菜单开始，递归查询所有子孙菜单的ID。
     * 用于级联删除和权限计算场景。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("menuId") Long menuId);

    /**
     * 使用PostgreSQL CTE递归查询获取所有子孙菜单ID（含自身）
     * <p>
     * 从指定菜单开始，递归查询所有子孙菜单的ID，包含自身。
     * 用于批量操作场景，确保自身也被包含。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 子孙菜单ID列表（包含自身）
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("menuId") Long menuId);

    /**
     * 使用PostgreSQL CTE递归查询批量获取多个菜单的所有子孙菜单ID
     * <p>
     * 从多个菜单开始，递归查询所有子孙菜单的ID。
     * 返回DescendantResult对象列表，映射每个菜单ID到其子孙ID。
     * 用于批量级联操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return 子孙查询结果列表（menu_id, descendant_id对）
     */
    List<DescendantResult> selectBatchDescendantIds(@Param("tenantId") Long tenantId,
                                                    @Param("menuIds") Set<Long> menuIds);

    /**
     * 批量子孙查询结果类
     * <p>
     * 用于封装批量子孙查询的结果，包含菜单ID和子孙ID对。
     * </p>
     */
    @Getter
    @Setter
    class DescendantResult {
        private Long menuId;
        private Long descendantId;
    }

    /**
     * 根据主键ID查询有效菜单（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param menuId   菜单ID
     * @return 菜单实体，不存在则返回null
     */
    SysMenu selectValidById(@Param("tenantId") Long tenantId,
                            @Param("menuId") Long menuId);

    /**
     * 根据菜单ID集合批量查询有效菜单（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return 菜单实体列表
     */
    List<SysMenu> selectValidByIds(@Param("tenantId") Long tenantId,
                                   @Param("menuIds") Set<Long> menuIds);

    /**
     * 根据菜单ID集合查询菜单（租户隔离 + 未删除），用于祖先链加载
     *
     * @param tenantId 租户ID
     * @param menuIds  菜单ID集合
     * @return 菜单实体列表
     */
    List<SysMenu> selectByIdsForAncestors(@Param("tenantId") Long tenantId,
                                          @Param("menuIds") Set<Long> menuIds);

    /**
     * 统计指定菜单的子菜单数量（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param parentId 父菜单ID
     * @return 子菜单数量
     */
    long countChildren(@Param("tenantId") Long tenantId,
                       @Param("parentId") Long parentId);

    /**
     * 根据权限标识查询有效菜单（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @param permCode 权限标识
     * @return 菜单实体，不存在则返回null
     */
    SysMenu selectByPermCode(@Param("tenantId") Long tenantId,
                             @Param("permCode") String permCode);

    /**
     * 根据权限标识集合批量查询有效菜单（租户隔离 + 未删除）
     *
     * @param tenantId  租户ID
     * @param permCodes 权限标识集合
     * @return 菜单实体列表
     */
    List<SysMenu> selectByPermCodes(@Param("tenantId") Long tenantId,
                                    @Param("permCodes") Set<String> permCodes);

    /**
     * 根据权限标识集合查询已存在的菜单（租户隔离 + 未删除）
     *
     * @param tenantId  租户ID
     * @param permCodes 权限标识集合
     * @return 菜单实体列表
     */
    List<SysMenu> selectExistingByPermCodes(@Param("tenantId") Long tenantId,
                                            @Param("permCodes") Set<String> permCodes);

    /**
     * 查询租户下所有有效菜单（租户隔离 + 未删除）
     *
     * @param tenantId 租户ID
     * @return 菜单实体列表
     */
    List<SysMenu> selectAllValid(@Param("tenantId") Long tenantId);
}