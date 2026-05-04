package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

public interface AbstractRoleMapper extends BaseMapper<AbstractRole> {

    /**
     * 使用 PostgreSQL 递归 CTE 一次性查询所有子孙角色
     * @param groupRoleIds 起始组角色ID集合
     * @param tenantId 租户ID
     * @return 所有子孙角色列表（包含起始角色）
     */
    List<AbstractRole> selectRoleTreeByGroupIds(@Param("groupRoleIds") Set<Long> groupRoleIds, @Param("tenantId") Long tenantId);

    /**
     * 使用 PostgreSQL 递归 CTE 一次性查询所有子孙角色ID
     * @param tenantId 租户ID
     * @param roleId 起始角色ID
     * @return 所有子孙角色ID列表（不包含起始角色）
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
