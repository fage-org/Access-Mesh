package cn.ac.fage.accessmesh.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.permission.entity.ResourceDependency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

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
}