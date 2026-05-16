package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 资源API映射领域服务接口
 * <p>
 * 提供资源与API接口映射关系的基础数据访问操作。
 * 资源API映射定义了资源实体与HTTP接口的对应关系，
 * 用于Gateway进行接口级权限校验。
 * </p>
 */
public interface ResourceApiMappingDomainService {

    /**
     * 根据ID查询资源API映射
     *
     * @param id 映射ID
     * @return 资源API映射实体，不存在返回null
     */
    ResourceApiMapping selectOneById(Long id);

    /**
     * 插入资源API映射
     *
     * @param entity 资源API映射实体
     */
    void insert(ResourceApiMapping entity);

    /**
     * 更新资源API映射
     *
     * @param entity 资源API映射实体
     * @return 更新影响的行数
     */
    int update(ResourceApiMapping entity);

    /**
     * 根据ID查询有效映射
     *
     * @param tenantId 租户ID
     * @param mappingId 映射ID
     * @return 资源API映射实体，不存在或已删除返回null
     */
    ResourceApiMapping selectValidById(Long tenantId, Long mappingId);

    /**
     * 批量软删除映射
     *
     * @param tenantId  租户ID
     * @param ids       待删除的映射ID列表
     * @param deletedAt 删除时间
     * @return 删除影响的行数
     */
    int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt);

    /**
     * 根据租户ID和ID集合查询有效映射列表
     *
     * @param tenantId 租户ID
     * @param ids      映射ID集合
     * @return 映射列表
     */
    List<ResourceApiMapping> selectValidByIds(Long tenantId, Set<Long> ids);

    /**
     * 根据租户ID和服务编码查询有效映射列表
     *
     * @param tenantId    租户ID
     * @param serviceCode 服务编码
     * @return 映射列表
     */
    List<ResourceApiMapping> selectByTenantAndServiceCode(Long tenantId, String serviceCode);

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
    ResourceApiMapping selectByUniqueKey(Long tenantId, Long resourceEntityId,
                                          String serviceCode, String httpMethod, String pathPattern);

    /**
     * 根据租户ID查询有效映射列表
     *
     * @param tenantId 租户ID
     * @return 映射列表
     */
    List<ResourceApiMapping> selectByTenantId(Long tenantId);

    /**
     * 根据租户ID和资源实体ID集合查询有效映射列表
     *
     * @param tenantId         租户ID
     * @param resourceEntityIds 资源实体ID集合
     * @return 映射列表
     */
    List<ResourceApiMapping> selectByTenantAndResourceEntityIds(Long tenantId, Set<Long> resourceEntityIds);
}