package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceApiMappingDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
        return resourceApiMappingMapper.selectValidById(tenantId, mappingId);
    }

    /**
     * 批量软删除映射
     * <p>
     * 批量设置映射的deleteFlag为本行id，实现软删除。
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

    /**
     * 根据租户ID和ID集合查询有效映射列表
     *
     * @param tenantId 租户ID
     * @param ids      映射ID集合
     * @return 映射列表
     */
    @Override
    public List<ResourceApiMapping> selectValidByIds(Long tenantId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return resourceApiMappingMapper.selectValidByIds(tenantId, ids);
    }
}