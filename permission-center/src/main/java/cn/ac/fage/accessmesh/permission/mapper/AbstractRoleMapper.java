package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 抽象角色数据访问接口
 * <p>
 * 提供抽象角色表的基础CRUD操作和自定义查询方法。
 * 抽象角色包括基础角色和分组角色，是权限分配的核心实体。
 * 支持角色树递归查询、批量软删除等操作。
 * </p>
 */
public interface AbstractRoleMapper extends BaseMapper<AbstractRole> {

    /**
     * 使用PostgreSQL递归CTE查询所有后代角色
     * <p>
     * 从指定的分组角色ID开始，递归查询所有子孙角色。
     * 用于角色树查询和权限继承计算。
     * </p>
     *
     * @param groupRoleIds 起始分组角色ID集合
     * @param tenantId     租户ID
     * @return 所有后代角色列表（包含起始角色）
     */
    List<AbstractRole> selectRoleTreeByGroupIds(@Param("groupRoleIds") Set<Long> groupRoleIds, @Param("tenantId") Long tenantId);

    /**
     * 使用PostgreSQL递归CTE查询所有后代角色ID
     * <p>
     * 从指定角色开始，递归查询所有子孙角色的ID。
     * 用于批量删除或权限计算场景。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   起始角色ID
     * @return 所有后代角色ID列表（不含起始角色）
     */
    List<Long> selectDescendantIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 批量软删除抽象角色
     * <p>
     * 将指定角色的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的抽象角色ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 使用PostgreSQL递归CTE批量查询多个角色的所有后代角色ID
     * <p>
     * 从多个角色ID开始，递归查询所有子孙角色的ID。
     * 用于批量操作场景，提高查询效率。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleIds  起始角色ID集合
     * @return 所有后代角色ID列表（不含起始角色）
     */
    List<Long> selectDescendantIdsBatch(@Param("tenantId") Long tenantId, @Param("roleIds") Set<Long> roleIds);
    List<Long> selectAncestorGroupRoleIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}