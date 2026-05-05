package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;

import java.util.List;
import java.util.Set;

public interface AbstractRoleDomainService {

    Long createRole(Long tenantId, Long bizDomainId, Long parentId, Integer roleType,
                    String externalId, String name, Integer sortOrder, String extra);

    void deleteRole(Long tenantId, Long roleId);

    List<AbstractRole> listChildren(Long tenantId, Long parentId);

    List<Long> resolveDescendantIds(Long tenantId, Long roleId);

    /**
     * Select a valid role by ID with tenant and delete flag conditions.
     * Returns null if role not found, deleted, or doesn't belong to the tenant.
     */
    AbstractRole selectValidById(Long tenantId, Long roleId);

    /**
     * Batch select valid roles by IDs with tenant and delete flag conditions.
     * Returns list of roles that exist, are not deleted, and belong to the tenant.
     */
    List<AbstractRole> selectValidByIds(Long tenantId, Set<Long> roleIds);

    /**
     * Batch soft delete roles by IDs.
     * Sets delete_flag = id and deleted_at for each role.
     */
    void softDeleteBatch(Long tenantId, Set<Long> roleIds);

    /**
     * Batch resolve all descendant role IDs for multiple roles.
     * Uses CTE recursive query to get all descendant IDs efficiently.
     * @param tenantId tenant ID
     * @param roleIds starting role IDs
     * @return all descendant role IDs (excluding the starting roles)
     */
    List<Long> resolveDescendantIdsBatch(Long tenantId, Set<Long> roleIds);
}
