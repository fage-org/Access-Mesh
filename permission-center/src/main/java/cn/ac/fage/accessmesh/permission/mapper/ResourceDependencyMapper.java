package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 资源依赖关系数据访问接口
 * <p>
 * 提供资源依赖关系表的基础CRUD操作和自定义查询方法。
 * 资源依赖关系定义资源之间的依赖层级，影响权限的继承和传播。
 * 支持批量软删除和按服务来源批量删除操作。
 * </p>
 */
public interface ResourceDependencyMapper extends BaseMapper<ResourceDependency> {

    /**
     * 查询指定资源的有效依赖规则
     *
     * @param tenantId        租户ID
     * @param resourceEntityId 源资源实体ID
     * @return 依赖规则列表
     */
    List<ResourceDependency> selectByResourceEntityId(@Param("tenantId") Long tenantId,
                                                      @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 批量查询多个资源的自动授权依赖规则
     *
     * @param tenantId     租户ID
     * @param resourceIds  源资源实体ID集合
     * @return 依赖规则列表
     */
    List<ResourceDependency> selectAutoGrantByResourceIds(@Param("tenantId") Long tenantId,
                                                          @Param("resourceIds") Set<Long> resourceIds);

    /**
     * 批量软删除资源依赖关系
     * <p>
     * 将指定依赖关系的delete_flag设置为id，deleted_at设置为当前时间。
     * 用于批量删除场景，避免物理删除。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的资源依赖关系ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 按服务来源批量软删除资源依赖关系
     * <p>
     * 根据服务编码和维护来源批量删除依赖关系，用于全量同步场景。
     * 将匹配记录的delete_flag设置为id，deleted_at设置为当前时间。
     * </p>
     *
     * @param tenantId         租户ID
     * @param ownerServiceCode 所属服务编码
     * @param maintainSource   维护来源
     * @param deletedAt        删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatchByOwnerService(@Param("tenantId") Long tenantId,
                                       @Param("ownerServiceCode") String ownerServiceCode,
                                       @Param("maintainSource") String maintainSource,
                                       @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据租户ID查询所有有效资源依赖列表
     *
     * @param tenantId 租户ID
     * @return 资源依赖列表
     */
    List<ResourceDependency> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和可选资源实体ID查询有效资源依赖列表
     *
     * @param tenantId         租户ID
     * @param resourceEntityId 资源实体ID，可为null
     * @return 资源依赖列表
     */
    List<ResourceDependency> selectByTenantAndResourceEntityId(@Param("tenantId") Long tenantId,
                                                                 @Param("resourceEntityId") Long resourceEntityId);

    /**
     * 根据租户ID、服务编码和维持来源查询有效资源依赖列表
     *
     * @param tenantId         租户ID
     * @param ownerServiceCode 服务编码
     * @param maintainSource   维持来源
     * @return 资源依赖列表
     */
    List<ResourceDependency> selectByOwnerService(@Param("tenantId") Long tenantId,
                                                    @Param("ownerServiceCode") String ownerServiceCode,
                                                    @Param("maintainSource") String maintainSource);

    /**
     * 根据租户ID和ID集合查询有效资源依赖列表
     *
     * @param tenantId 租户ID
     * @param ids      资源依赖ID集合
     * @return 资源依赖列表
     */
    List<ResourceDependency> selectValidByIds(@Param("tenantId") Long tenantId,
                                               @Param("ids") Set<Long> ids);

    /**
     * 根据租户ID、源资源ID集合和目标资源ID集合查询有效资源依赖列表
     *
     * @param tenantId          租户ID
     * @param sourceResourceIds 源资源ID集合
     * @param targetResourceIds 目标资源ID集合
     * @return 资源依赖列表
     */
    List<ResourceDependency> selectBySourceAndTargetIds(@Param("tenantId") Long tenantId,
                                                          @Param("sourceResourceIds") Set<Long> sourceResourceIds,
                                                          @Param("targetResourceIds") Set<Long> targetResourceIds);
}