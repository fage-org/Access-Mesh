package cn.ac.fage.accessmesh.permission.util;

/**
 * Common constants for permission-center module.
 */
public final class PermissionConstants {

    /**
     * ID value used to force empty query results.
     * Used when a lookup fails (e.g., invalid domainCode) and we want to return no results.
     */
    public static final long NONEXISTENT_ID = -1L;

    /**
     * Status value indicating an entity is enabled/active.
     */
    public static final int ENABLED_STATUS = 1;

    /**
     * Status value indicating an entity is disabled/inactive.
     */
    public static final int DISABLED_STATUS = 0;

    private PermissionConstants() {
        // Prevent instantiation
    }
}