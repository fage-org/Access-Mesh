package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限条件数据访问接口
 * <p>
 * 提供权限条件表的基础CRUD操作和自定义查询方法。
 * 权限条件用于限定权限的生效范围，如时间范围、数据属性等。
 * 支持批量软删除操作。
 * </p>
 */
public interface PermissionConditionMapper extends BaseMapper<PermissionCondition> {

    /**
     * 批量软删除权限条件
     * <p>
     * 将指定权限条件的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的权限条件ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}