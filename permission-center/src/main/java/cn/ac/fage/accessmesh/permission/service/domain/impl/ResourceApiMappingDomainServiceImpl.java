package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceApiMappingDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef;

/**
 * 资源API映射领域服务实现类
 * <p>
 * 提供资源与API接口映射关系的基础数据访问操作。
 * 资源API映射定义了资源实体与HTTP接口的对应关系，
 * 用于Gateway进行接口级权限校验。
 * 每条映射记录包含HTTP方法、路径模式、匹配顺序等属性。
 * 该服务封装Mapper调用，提供统一的领域层访问接口。
 * 支持批量软删除操作，提高删除效率。
 * </p>
 */
@Service
public class ResourceApiMappingDomainServiceImpl implements ResourceApiMappingDomainService {

    private final ResourceApiMappingMapper resourceApiMappingMapper;

    /**
     * 构造函数注入依赖
     *
     * @param resourceApiMappingMapper 资源API映射数据访问层
     */
    public ResourceApiMappingDomainServiceImpl(ResourceApiMappingMapper resourceApiMappingMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    /**
     * 根据ID查询资源API映射
     * <p>
     * 直接调用Mapper的selectOneById方法。
     * 不检查租户和删除标志，用于内部查询。
     * </p>
     *
     * @param id 映射ID
     * @return 资源API映射实体，不存在返回null
     */
    @Override
    public ResourceApiMapping selectOneById(Long id) {
        return resourceApiMappingMapper.selectOneById(id);
    }

    /**
     * 根据查询条件查询映射列表
     * <p>
     * 调用Mapper的selectListByQuery方法，支持复杂查询条件。
     * </p>
     *
     * @param qw QueryWrapper查询条件
     * @return 资源API映射列表
     */
    @Override
    public List<ResourceApiMapping> selectListByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectListByQuery(qw);
    }

    /**
     * 根据查询条件查询单个映射
     * <p>
     * 调用Mapper的selectOneByQuery方法，返回第一条匹配记录。
     * </p>
     *
     * @param qw QueryWrapper查询条件
     * @return 资源API映射实体，不存在返回null
     */
    @Override
    public ResourceApiMapping selectOneByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectOneByQuery(qw);
    }

    /**
     * 根据查询条件统计映射数量
     * <p>
     * 调用Mapper的selectCountByQuery方法。
     * </p>
     *
     * @param qw QueryWrapper查询条件
     * @return 匹配的映射数量
     */
    @Override
    public long selectCountByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectCountByQuery(qw);
    }

    /**
     * 插入资源API映射
     * <p>
     * 调用Mapper的insert方法，插入新映射记录。
     * </p>
     *
     * @param entity 资源API映射实体
     */
    @Override
    public void insert(ResourceApiMapping entity) {
        resourceApiMappingMapper.insert(entity);
    }

    /**
     * 更新资源API映射
     * <p>
     * 调用Mapper的update方法，更新已有映射记录。
     * </p>
     *
     * @param entity 资源API映射实体
     * @return 更新影响的行数
     */
    @Override
    public int update(ResourceApiMapping entity) {
        return resourceApiMappingMapper.update(entity);
    }

    /**
     * 根据ID查询有效映射
     * <p>
     * 查询未删除的资源API映射实体，包含租户校验。
     * 如果映射ID为null，直接返回null。
     * </p>
     *
     * @param tenantId 租户ID
     * @param mappingId 映射ID
     * @return 资源API映射实体，不存在或已删除返回null
     */
    @Override
    public ResourceApiMapping selectValidById(Long tenantId, Long mappingId) {
        if (mappingId == null) {
            return null;
        }
        return resourceApiMappingMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.ID.eq(mappingId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );
    }

    /**
     * 批量软删除映射
     * <p>
     * 批量设置映射的deleteFlag为非0值，实现软删除。
     * 使用单条SQL批量更新，提高删除效率。
     * </p>
     *
     * @param tenantId  租户ID
     * @param ids       待删除的映射ID列表
     * @param deletedAt 删除时间
     * @return 删除影响的行数
     */
    @Override
    public int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return resourceApiMappingMapper.softDeleteBatch(tenantId, ids, deletedAt);
    }
}