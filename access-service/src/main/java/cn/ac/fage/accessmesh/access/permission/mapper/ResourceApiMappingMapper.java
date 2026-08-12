package cn.ac.fage.accessmesh.access.permission.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 资源API映射数据访问接口
 * <p>
 * 提供资源API映射表的基础CRUD操作和自定义查询方法。
 * 资源API映射定义了资源与API接口的对应关系，用于Gateway权限校验。
 * 支持批量软删除操作。
 * </p>
 */
public interface ResourceApiMappingMapper extends BaseMapper<ResourceApiMapping> {

    /**
     * 批量软删除资源API映射
     *
     * @param tenantId  租户ID
     * @param ids       待删除的映射ID列表
     * @param deletedAt 删除时间戳
     * @return 更新的行数
     */
    int softDeleteBatch(@Param("tenantId") Long tenantId,
                        @Param("ids") List<Long> ids,
                        @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 根据ID和租户ID查询有效映射
     *
     * @param id       映射ID
     * @param tenantId 租户ID
     * @return 映射实体，不存在或已删除返回null
     */
    ResourceApiMapping selectValidById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 查询接口权限校验用的API映射（按服务编码、HTTP方法过滤）
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @param httpMethod  HTTP方法
     * @return 映射列表
     */
    List<ResourceApiMapping> selectForInterfaceCheck(@Param("tenantId") Long tenantId,
                                                      @Param("serviceCode") String serviceCode,
                                                      @Param("httpMethod") String httpMethod);

    /**
     * 查询接口快照用的API映射（按服务编码和资源ID集合过滤）
     *
     * @param tenantId       租户ID
     * @param serviceCode    服务编码
     * @param resourceIds    资源ID集合
     * @return 映射列表
     */
    List<ResourceApiMapping> selectForSnapshot(@Param("tenantId") Long tenantId,
                                                @Param("serviceCode") String serviceCode,
                                                @Param("resourceIds") Set<Long> resourceIds);

    /**
     * 根据租户ID、可选资源ID和可选服务编码查询有效映射列表
     *
     * @param tenantId   租户ID
     * @param resourceId 资源ID，可选
     * @param serviceCode 服务编码，可选
     * @return 映射列表
     */
    List<ResourceApiMapping> selectValidList(@Param("tenantId") Long tenantId,
                                             @Param("resourceId") Long resourceId,
                                             @Param("serviceCode") String serviceCode);

    /**
     * 根据ID集合和租户ID查询有效映射列表（用于批量删除前查询）
     *
     * @param tenantId 租户ID
     * @param ids      映射ID集合
     * @return 映射列表
     */
    List<ResourceApiMapping> selectValidByIds(@Param("tenantId") Long tenantId,
                                               @Param("ids") Set<Long> ids);

    /**
     * 批量查询指定资源实体的有效API映射
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return API映射列表
     */
    List<ResourceApiMapping> selectByResourceEntityIds(@Param("tenantId") Long tenantId,
                                                        @Param("resourceEntityIds") Set<Long> resourceEntityIds);

    /**
     * 根据租户ID和服务编码查询有效资源API映射列表
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 资源API映射列表
     */
    List<ResourceApiMapping> selectByTenantAndServiceCode(@Param("tenantId") Long tenantId,
                                                            @Param("serviceCode") String serviceCode);

    /**
     * 根据租户ID、资源实体ID、服务编码、HTTP方法和路径模式查询有效映射
     *
     * @param tenantId        租户ID
     * @param resourceEntityId 资源实体ID
     * @param serviceCode     服务编码
     * @param httpMethod      HTTP方法
     * @param pathPattern     路径模式
     * @return 映射实体，不存在返回null
     */
    ResourceApiMapping selectByUniqueKey(@Param("tenantId") Long tenantId,
                                         @Param("resourceEntityId") Long resourceEntityId,
                                         @Param("serviceCode") String serviceCode,
                                         @Param("httpMethod") String httpMethod,
                                         @Param("pathPattern") String pathPattern);
}