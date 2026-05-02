package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.AbstractRole;

import java.util.List;

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
}
