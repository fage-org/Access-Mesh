package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import com.mybatisflex.core.query.QueryWrapper;

import java.time.LocalDateTime;
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

    /**
     * Batch soft delete resource API mappings.
     * Sets delete_flag = id and deleted_at for each mapping.
     *
     * @param tenantId   the tenant ID
     * @param ids        the list of mapping IDs to delete
     * @param deletedAt  the timestamp of deletion
     * @return number of rows updated
     */
    int softDeleteBatch(Long tenantId, List<Long> ids, LocalDateTime deletedAt);
}
