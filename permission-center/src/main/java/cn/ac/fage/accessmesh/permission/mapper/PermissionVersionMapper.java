package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.PermissionVersion;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 权限版本数据访问接口
 * <p>
 * 提供权限版本表的基础CRUD操作和自定义查询方法。
 * 权限版本表用于缓存一致性检查，Gateway通过版本号判断缓存的权限快照是否过期。
 * 支持按角色查询最新版本号、批量查询版本记录等操作。
 * </p>
 */
public interface PermissionVersionMapper extends BaseMapper<PermissionVersion> {

    /**
     * 查询指定角色的最新版本记录
     * <p>
     * 按版本号降序排列，取第一条记录。
     * 用于获取角色的当前权限版本号。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 最新版本记录，不存在返回null
     */
    PermissionVersion selectLatestByRole(@Param("tenantId") Long tenantId,
                                         @Param("roleId") Long roleId);

    /**
     * 批量查询多个角色的所有版本记录（按版本号降序）
     * <p>
     * 用于批量递增版本号时获取每个角色的当前最大版本号。
     * 结果按版本号降序排列，便于取每个角色的最大值。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  角色ID集合
     * @return 版本记录列表（按版本号降序）
     */
    List<PermissionVersion> selectAllByRolesOrdered(@Param("tenantId") Long tenantId,
                                                    @Param("roleIds") Collection<Long> roleIds);
}