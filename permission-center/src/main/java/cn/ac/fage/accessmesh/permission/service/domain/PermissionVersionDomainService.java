package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Collection;

public interface PermissionVersionDomainService {

    long getCurrentVersion(Long tenantId, Long roleId);

    long increment(Long tenantId, Long roleId);

    void batchIncrement(Long tenantId, Collection<Long> roleIds);
}
