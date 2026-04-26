package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Set;

public interface UserRoleDomainService {

    Set<Long> resolveEffectiveRoles(Long tenantId, Long userId, Long bizDomainId);

    void invalidateRoleCache(Long tenantId, Long userId);

    void invalidateRoleCacheByRole(Long tenantId, Long roleId);
}
