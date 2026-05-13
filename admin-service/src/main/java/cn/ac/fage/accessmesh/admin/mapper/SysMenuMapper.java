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
}