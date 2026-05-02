package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Map;
import java.util.Set;

public interface UserRoleDomainService {

    Set<Long> resolveEffectiveRoles(Long tenantId, Long userId, Long bizDomainId);

    /**
     * Batch resolve effective roles for multiple users.
     * Returns a map of userId -> set of effective role IDs.
     *
     * @param tenantId    tenant ID
     * @param userIds     set of user IDs
     * @param bizDomainId business domain ID (null for global scope)
     * @return map of userId -> set of role IDs
     */
    Map<Long, Set<Long>> batchResolveEffectiveRoles(Long tenantId, Set<Long> userIds, Long bizDomainId);

    void invalidateRoleCache(Long tenantId, Long userId);

    void invalidateRoleCacheByRole(Long tenantId, Long roleId);
}
