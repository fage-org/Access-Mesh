package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Collection;
import java.util.Set;

public interface PermissionVersionDomainService {

    long getCurrentVersion(Long tenantId, Long roleId);

    /**
     * Calculate the maximum version across multiple roles.
     * Used when a user has multiple roles and we need the latest version.
     *
     * @param tenantId tenant ID
     * @param roleIds set of role IDs (if empty, returns 0)
     * @return maximum version number across all roles
     */
    long calculateMaxVersion(Long tenantId, Set<Long> roleIds);

    /**
     * Build a permission version key string for caching.
     * Format: userId:maxVersion
     *
     * @param userId user ID
     * @param tenantId tenant ID
     * @param roleIds set of role IDs
     * @return version key string like "123:42"
     */
    String buildPermissionVersionKey(Long userId, Long tenantId, Set<Long> roleIds);

    long increment(Long tenantId, Long roleId);

    void batchIncrement(Long tenantId, Collection<Long> roleIds);
}
