package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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

    /**
     * 根据租户ID、资源类型集合和操作码集合查询操作权限列表
     * <p>
     * 用于授权检查场景，批量查询指定资源类型和操作码的操作权限。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeValues 资源类型值集合
     * @param operationCodes   操作码集合（大写）
     * @return 操作权限列表
     */
    List<OperationPermission> selectByTenantResourceTypesAndOpCodes(
        @Param("tenantId") Long tenantId,
        @Param("resourceTypeValues") Set<Integer> resourceTypeValues,
        @Param("operationCodes") Set<String> operationCodes);

    /**
     * 根据租户ID查询操作权限列表（可选资源类型过滤）
     * <p>
     * 用于操作权限管理列表查询场景。
     * </p>
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值，可为null（不过滤）
     * @return 操作权限列表
     */
    List<OperationPermission> selectByTenantAndResourceType(
        @Param("tenantId") Long tenantId,
        @Param("resourceType") Integer resourceType);

    /**
     * 根据ID和租户ID查询有效的操作权限
     *
     * @param operationId 操作权限ID
     * @param tenantId    租户ID
     * @return 操作权限实体，不存在返回null
     */
    OperationPermission selectValidById(@Param("operationId") Long operationId,
                                        @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和ID集合查询有效的操作权限列表
     *
     * @param tenantId 租户ID
     * @param ids      操作权限ID集合
     * @return 操作权限列表
     */
    List<OperationPermission> selectValidByIds(@Param("tenantId") Long tenantId,
                                                @Param("ids") Set<Long> ids);

    /**
     * 根据租户ID、资源类型和操作码查询操作权限
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param code         操作码
     * @return 操作权限实体，不存在返回null
     */
    OperationPermission selectByResourceTypeAndCode(@Param("tenantId") Long tenantId,
                                                     @Param("resourceType") Integer resourceType,
                                                     @Param("code") String code);

    /**
     * 根据租户ID和操作码查询操作权限（不限资源类型）
     *
     * @param tenantId 租户ID
     * @param code     操作码
     * @return 操作权限实体，不存在返回null
     */
    OperationPermission selectByCode(@Param("tenantId") Long tenantId,
                                      @Param("code") String code);

    /**
     * 根据租户ID、资源类型和操作码集合批量查询操作权限
     *
     * @param tenantId     租户ID
     * @param resourceType 资源类型值
     * @param codes        操作码集合
     * @return 操作权限列表
     */
    List<OperationPermission> selectByResourceTypeAndCodes(@Param("tenantId") Long tenantId,
                                                            @Param("resourceType") Integer resourceType,
                                                            @Param("codes") Set<String> codes);

    /**
     * 根据租户ID和操作码集合批量查询操作权限（不限资源类型）
     *
     * @param tenantId 租户ID
     * @param codes    操作码集合
     * @return 操作权限列表
     */
    List<OperationPermission> selectByCodes(@Param("tenantId") Long tenantId,
                                             @Param("codes") Set<String> codes);
}