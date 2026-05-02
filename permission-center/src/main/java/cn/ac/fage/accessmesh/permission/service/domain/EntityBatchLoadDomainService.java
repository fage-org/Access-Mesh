package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.PermissionCondition;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.Map;
import java.util.Set;

/**
 * Domain service for batch loading entities.
 * Provides unified batch loading methods to avoid N+1 queries and code duplication.
 * All methods enforce tenant isolation and soft-delete filtering.
 */
public interface EntityBatchLoadDomainService {

    /**
     * Batch load operation permissions by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids operation permission IDs
     * @return map of ID to OperationPermission, empty map if ids is empty
     */
    Map<Long, OperationPermission> batchLoadOperations(Long tenantId, Set<Long> ids);

    /**
     * Batch load resource entities by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids resource entity IDs
     * @return map of ID to ResourceEntity, empty map if ids is empty
     */
    Map<Long, ResourceEntity> batchLoadResources(Long tenantId, Set<Long> ids);

    /**
     * Batch load abstract roles by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids role IDs
     * @return map of ID to AbstractRole, empty map if ids is empty
     */
    Map<Long, AbstractRole> batchLoadRoles(Long tenantId, Set<Long> ids);

    /**
     * Batch load permission conditions by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids condition IDs
     * @return map of ID to PermissionCondition, empty map if ids is empty
     */
    Map<Long, PermissionCondition> batchLoadConditions(Long tenantId, Set<Long> ids);

    /**
     * Batch load biz domain codes by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids biz domain IDs
     * @return map of ID to domain code, empty map if ids is empty
     */
    Map<Long, String> batchLoadDomainCodes(Long tenantId, Set<Long> ids);

    /**
     * Batch load biz domains by IDs with tenant isolation.
     *
     * @param tenantId tenant ID for filtering (must not be null)
     * @param ids biz domain IDs
     * @return map of ID to BizDomain, empty map if ids is empty
     */
    Map<Long, BizDomain> batchLoadDomains(Long tenantId, Set<Long> ids);
}