package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户角色关联数据访问接口
 * <p>
 * 提供用户角色关联表的基础CRUD操作和自定义查询方法。
 * 用户角色关联定义用户与角色的绑定关系，是权限分配的核心关联表。
 * 支持批量软删除操作。
 * </p>
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 批量软删除用户角色关联
     * <p>
     * 将指定关联记录的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的用户角色关联ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}