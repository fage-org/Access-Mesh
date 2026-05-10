package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色资源权限数据访问接口
 * <p>
 * 提供角色资源权限表的基础CRUD操作和自定义查询方法。
 * 角色资源权限定义角色对资源的操作权限，是权限系统的核心关联表。
 * 支持批量软删除和级联删除子权限操作。
 * </p>
 */
public interface RoleResourcePermissionMapper extends BaseMapper<RoleResourcePermission> {

    /**
     * 批量软删除角色资源权限
     * <p>
     * 将指定权限记录的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的权限ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 级联软删除子权限
     * <p>
     * 根据父权限ID批量删除所有子权限记录。
     * 用于删除父资源时级联删除子资源的权限。
     * </p>
     *
     * @param tenantId  租户ID
     * @param parentIds 父权限ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int cascadeSoftDeleteChildren(@Param("tenantId") Long tenantId,
                                   @Param("parentIds") List<Long> parentIds,
                                   @Param("deletedAt") LocalDateTime deletedAt);
}