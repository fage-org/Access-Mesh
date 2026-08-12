package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionCondition;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
     *
     * @param tenantId  租户ID
     * @param ids       待删除的权限条件ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID和条件编码查询有效权限条件
     *
     * @param tenantId 租户ID
     * @param code     条件编码
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectValidByCode(@Param("tenantId") Long tenantId,
                                           @Param("code") String code);

    /**
     * 根据租户ID和条件编码集合批量查询有效权限条件
     *
     * @param tenantId 租户ID
     * @param codes   条件编码集合
     * @return 权限条件列表
     */
    List<PermissionCondition> selectValidByCodes(@Param("tenantId") Long tenantId,
                                                  @Param("codes") Set<String> codes);

    /**
     * 根据ID集合查询有效权限条件（无租户过滤，用于批量加载）
     *
     * @param ids 权限条件ID集合
     * @return 权限条件列表
     */
    List<PermissionCondition> selectValidByIdsNoTenant(@Param("ids") Set<Long> ids);

    /**
     * 根据ID和租户ID查询有效权限条件
     *
     * @param conditionId 条件ID
     * @param tenantId   租户ID
     * @return 权限条件实体，不存在返回null
     */
    PermissionCondition selectValidById(@Param("conditionId") Long conditionId,
                                         @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID查询所有有效权限条件列表
     *
     * @param tenantId 租户ID
     * @return 权限条件列表
     */
    List<PermissionCondition> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和ID集合查询有效权限条件列表
     *
     * @param tenantId 租户ID
     * @param ids      条件ID集合
     * @return 权限条件列表
     */
    List<PermissionCondition> selectValidByIds(@Param("tenantId") Long tenantId,
                                                @Param("ids") Set<Long> ids);
}