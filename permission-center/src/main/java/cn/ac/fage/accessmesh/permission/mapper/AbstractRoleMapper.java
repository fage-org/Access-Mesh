package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface AbstractRoleMapper extends BaseMapper<AbstractRole> {

    /**
     * Use PostgreSQL recursive CTE to query all descendant roles
     * @param groupRoleIds starting group role IDs
     * @param tenantId tenant ID
     * @return all descendant roles (including starting roles)
     */
    List<AbstractRole> selectRoleTreeByGroupIds(@Param("groupRoleIds") Set<Long> groupRoleIds, @Param("tenantId") Long tenantId);

    /**
     * Use PostgreSQL recursive CTE to query all descendant role IDs
     * @param tenantId tenant ID
     * @param roleId starting role ID
     * @return all descendant role IDs (excluding starting role)
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * Batch soft delete abstract roles.
     * Sets delete_flag = id and deleted_at for each abstract role.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of abstract role IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Use PostgreSQL recursive CTE to query all descendant role IDs for multiple roles.
     * @param tenantId tenant ID
     * @param roleIds starting role IDs
     * @return all descendant role IDs (excluding the starting roles)
     */
    List<Long> selectDescendantIdsBatch(@Param("tenantId") Long tenantId, @Param("roleIds") Set<Long> roleIds);
}
