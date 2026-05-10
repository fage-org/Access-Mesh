package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作权限数据访问接口
 * <p>
 * 提供操作权限表的基础CRUD操作和自定义查询方法。
 * 操作权限定义了资源可执行的操作类型，使用位运算进行权限匹配。
 * 支持按位掩码查询和批量软删除操作。
 * </p>
 */
public interface OperationPermissionMapper extends BaseMapper<OperationPermission> {

    /**
     * 按位掩码查询操作权限
     * <p>
     * 查询满足条件(binary_bit | inherit_mask) & requiredBits = requiredBits的操作权限。
     * 使用SQL位运算在数据库层面过滤，避免全表加载。
     * 用于权限匹配场景，提高查询效率。
     * </p>
     *
     * @param tenantId     租户ID，用于数据隔离
     * @param resourceType 资源类型过滤条件，可为null
     * @param requiredBits 需匹配的位掩码
     * @return 匹配的操作权限列表
     */
    @Select("""
        SELECT id, tenant_id, resource_type, code, name, binary_bit, inherit_mask,
               created_by, updated_by, deleted_by, created_at, updated_at, deleted_at, delete_flag
        FROM operation_permission
        WHERE tenant_id = #{tenantId}
          AND delete_flag = 0
          AND (#{resourceType} IS NULL OR resource_type = #{resourceType})
          AND ((binary_bit | inherit_mask) & #{requiredBits}) = #{requiredBits}
        """)
    List<OperationPermission> selectByEffectiveBitsMatch(
        @Param("tenantId") Long tenantId,
        @Param("resourceType") Integer resourceType,
        @Param("requiredBits") Long requiredBits);

    /**
     * 批量软删除操作权限
     * <p>
     * 将指定操作权限的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的操作权限ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);
}