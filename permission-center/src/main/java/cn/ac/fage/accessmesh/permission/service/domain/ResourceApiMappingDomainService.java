package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;

public interface ResourceApiMappingDomainService {

    ResourceApiMapping selectOneById(Long id);

    List<ResourceApiMapping> selectListByQuery(QueryWrapper qw);

    ResourceApiMapping selectOneByQuery(QueryWrapper qw);

    long selectCountByQuery(QueryWrapper qw);

    void insert(ResourceApiMapping entity);

    int update(ResourceApiMapping entity);

    /**
     * Select a valid resource API mapping by ID with tenant and delete flag conditions.
     * Returns null if mapping not found, deleted, or doesn't belong to the tenant.
     */
    ResourceApiMapping selectValidById(Long tenantId, Long mappingId);
}
