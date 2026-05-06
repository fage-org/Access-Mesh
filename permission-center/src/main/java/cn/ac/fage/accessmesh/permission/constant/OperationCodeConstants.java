package cn.ac.fage.accessmesh.permission.constant;

/**
 * Operation code constants for permission checks.
 * Replaces OperationType enum for simpler string-based API.
 *
 * <p>Usage:
 * <pre>
 * engine.validate(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
 * engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW);
 * </pre>
 */
public final class OperationCodeConstants {

    /** Create new resource instance - type-level permission check. */
    public static final String CREATE = "CREATE";

    /** View resource instance details. */
    public static final String VIEW = "VIEW";

    /** Manage resource instance (update settings, properties). */
    public static final String MANAGE = "MANAGE";

    /** Update resource instance - alias for MANAGE in some contexts. */
    public static final String UPDATE = "UPDATE";

    /** Delete resource instance. */
    public static final String DELETE = "DELETE";

    /** Assign roles/permissions to users. */
    public static final String ASSIGN = "ASSIGN";

    /** Revoke roles/permissions from users. */
    public static final String REVOKE = "REVOKE";

    /** Sync data (interfaces, resources, etc). */
    public static final String SYNC = "SYNC";

    /** Manage API mappings for a service resource. */
    public static final String MANAGE_API_MAPPING = "MANAGE_API_MAPPING";

    /** Sync interfaces for a service. */
    public static final String SYNC_INTERFACE = "SYNC_INTERFACE";

    /** Grant permissions on resource to others. */
    public static final String GRANT = "GRANT";

    private OperationCodeConstants() {}
}