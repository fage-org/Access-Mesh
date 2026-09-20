package cn.ac.fage.accessmesh.access.resource.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
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

    /** 替换编译器生成的本服务边。 */
    int removeCompiledScope(@Param("tenantId") Long tenantId, @Param("service") String service,
                            @Param("now") LocalDateTime now);

    /** 诊断声明的展示描述独立更新，不重建聚合图的身份。 */
    int refreshCompiledDescriptions(@Param("tenantId") Long tenantId, @Param("service") String service,
                                    @Param("now") LocalDateTime now);

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

}
