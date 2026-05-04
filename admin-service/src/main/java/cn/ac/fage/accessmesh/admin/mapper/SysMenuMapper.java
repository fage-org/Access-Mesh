package cn.ac.fage.accessmesh.admin.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * Batch soft delete menus.
     * Sets delete_flag = 1 and deleted_at for each menu.
     *
     * @param tenantId  the tenant ID
     * @param ids       the list of menu IDs to delete
     * @param deletedAt the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant menu IDs (excluding self).
     *
     * @param tenantId tenant ID
     * @param menuId   menu ID
     * @return descendant menu ID list
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId,
                                   @Param("menuId") Long menuId);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant menu IDs (including self).
     *
     * @param tenantId tenant ID
     * @param menuId   menu ID
     * @return descendant menu ID list (including self)
     */
    List<Long> selectDescendantIdsIncludingSelf(@Param("tenantId") Long tenantId,
                                                @Param("menuId") Long menuId);

    /**
     * Use PostgreSQL CTE recursive query to get all descendant menu IDs for multiple starting points.
     * Returns a list of DescendantResult objects mapping each menuId to its descendants.
     *
     * @param tenantId tenant ID
     * @param menuIds  set of menu IDs to find descendants for
     * @return list of descendant results (menu_id, descendant_id pairs)
     */
    List<DescendantResult> selectBatchDescendantIds(@Param("tenantId") Long tenantId,
                                                    @Param("menuIds") Set<Long> menuIds);

    /**
     * Result class for batch descendant query.
     */
    class DescendantResult {
        private Long menuId;
        private Long descendantId;

        public Long getMenuId() { return menuId; }
        public void setMenuId(Long menuId) { this.menuId = menuId; }
        public Long getDescendantId() { return descendantId; }
        public void setDescendantId(Long descendantId) { this.descendantId = descendantId; }
    }
}