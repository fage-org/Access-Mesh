package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import org.apache.ibatis.annotations.Param;

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
     * 查询租户的全局操作定义（resource_type IS NULL，适用所有资源类型；api-contract §5.3）。
     *
     * @param tenantId 租户ID
     * @return 全局操作权限列表
     */
    List<OperationPermission> selectGlobalOperations(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和资源类型集合批量查询操作权限
     *
     * @param tenantId      租户ID
     * @param resourceTypes 资源类型值集合
     * @return 操作权限列表
     */
    List<OperationPermission> selectByTenantAndResourceTypes(
        @Param("tenantId") Long tenantId,
        @Param("resourceTypes") Set<Integer> resourceTypes);

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
     * 根据租户ID和操作码查询全局操作权限（resource_type IS NULL，适用所有资源类型）
     * <p>
     * 全局操作作为类型专属操作的 fallback：当某资源类型未定义该操作码时，回退到全局操作。
     * </p>
     *
     * @param tenantId 租户ID
     * @param code     操作码
     * @return 全局操作权限实体，不存在返回null
     */
    OperationPermission selectGlobalByCode(@Param("tenantId") Long tenantId,
                                            @Param("code") String code);

    /**
     * 根据租户ID和操作码集合批量查询全局操作权限（resource_type IS NULL）
     *
     * @param tenantId 租户ID
     * @param codes    操作码集合
     * @return 全局操作权限列表
     */
    List<OperationPermission> selectGlobalByCodes(@Param("tenantId") Long tenantId,
                                                   @Param("codes") Set<String> codes);
}