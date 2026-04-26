package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;

import java.util.List;

public interface ResourceDependencyDomainService {

    void processDependencies(Long tenantId, Long roleId, Long resourceEntityId, Long operationBits);

    void cleanupDependencies(Long tenantId, Long roleId, Long resourceEntityId);

    /**
     * Auto-grant dependencies for a batch of pending insert entries.
     * Returns new RoleResourcePermission entries that should also be inserted.
     */
    List<RoleResourcePermission> autoGrantForInsert(Long tenantId, Long roleId, List<RoleResourcePermission> toInsert);
}
