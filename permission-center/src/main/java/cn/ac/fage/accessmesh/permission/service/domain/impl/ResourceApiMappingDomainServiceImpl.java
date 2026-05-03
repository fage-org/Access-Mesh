package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceApiMappingDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef.RESOURCE_API_MAPPING;

@Service
public class ResourceApiMappingDomainServiceImpl implements ResourceApiMappingDomainService {

    private final ResourceApiMappingMapper resourceApiMappingMapper;

    public ResourceApiMappingDomainServiceImpl(ResourceApiMappingMapper resourceApiMappingMapper) {
        this.resourceApiMappingMapper = resourceApiMappingMapper;
    }

    @Override
    public ResourceApiMapping selectOneById(Long id) {
        return resourceApiMappingMapper.selectOneById(id);
    }

    @Override
    public List<ResourceApiMapping> selectListByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectListByQuery(qw);
    }

    @Override
    public ResourceApiMapping selectOneByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectOneByQuery(qw);
    }

    @Override
    public long selectCountByQuery(QueryWrapper qw) {
        return resourceApiMappingMapper.selectCountByQuery(qw);
    }

    @Override
    public void insert(ResourceApiMapping entity) {
        resourceApiMappingMapper.insert(entity);
    }

    @Override
    public int update(ResourceApiMapping entity) {
        return resourceApiMappingMapper.update(entity);
    }

    @Override
    public ResourceApiMapping selectValidById(Long tenantId, Long mappingId) {
        if (mappingId == null) {
            return null;
        }
        return resourceApiMappingMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(RESOURCE_API_MAPPING.ID.eq(mappingId))
                .and(RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return resourceApiMappingMapper.softDeleteBatch(tenantId, ids, deletedAt);
    }
}
