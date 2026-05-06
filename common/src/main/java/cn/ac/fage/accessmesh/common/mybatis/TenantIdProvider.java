package cn.ac.fage.accessmesh.common.mybatis;

import java.util.Set;

/**
 * Provides the set of active tenant IDs for tenant-aware scheduled tasks.
 *
 * <p>Each service module should register a bean implementing this interface,
 * typically by querying a well-known table (e.g., {@code SELECT DISTINCT tenant_id FROM sys_user}).
 */
@FunctionalInterface
public interface TenantIdProvider {

    /**
     * Get all active tenant IDs.
     *
     * @return set of tenant IDs, or empty set if no tenants exist
     */
    Set<Long> getTenantIds();
}
