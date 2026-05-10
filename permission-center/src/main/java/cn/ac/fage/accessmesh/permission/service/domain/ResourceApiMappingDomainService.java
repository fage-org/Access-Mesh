package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import com.mybatisflex.core.query.QueryWrapper;

import java.time.LocalDateTime;
import java.util.List;

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
     * 根据查询条件查询映射列表
     *
     * @param qw QueryWrapper查询条件
     * @return 资源API映射列表
     */
    List<ResourceApiMapping> selectListByQuery(QueryWrapper qw);

    /**
     * 根据查询条件查询单个映射
     *
     * @param qw QueryWrapper查询条件
     * @return 资源API映射实体，不存在返回null
     */
    ResourceApiMapping selectOneByQuery(QueryWrapper qw);

    /**
     * 根据查询条件统计映射数量
     *
     * @param qw QueryWrapper查询条件
     * @return 匹配的映射数量
     */
    long selectCountByQuery(QueryWrapper qw);

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
     * <p>
     * 查询未删除的资源API映射实体，包含租户校验。
     * 如果映射不存在、已删除或不属于租户，返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param mappingId 映射ID
     * @return 资源API映射实体，不存在或已删除返回null
     */
    ResourceApiMapping selectValidById(Long tenantId, Long mappingId);

    /**
     * 批量软删除映射
     * <p>
     * 批量设置映射的deleteFlag为ID值，记录删除时间。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的映射ID列表
     * @param deletedAt 删除时间戳
     * @return 删除影响的行数
     */
    int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt);
}