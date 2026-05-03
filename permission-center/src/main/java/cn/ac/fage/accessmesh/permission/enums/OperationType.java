package cn.ac.fage.accessmesh.permission.enums;

/**
 * Operation types for permission checks.
 * Used as parameter in permission validation methods for extensibility.
 */
public enum OperationType {
    /**
     * Create new resource instance - type-level permission check.
     */
    CREATE("CREATE"),

    /**
     * View resource instance details.
     */
    VIEW("VIEW"),

    /**
     * Manage resource instance (update settings, properties).
     */
    MANAGE("MANAGE"),

    /**
     * Update resource instance - alias for MANAGE in some contexts.
     */
    UPDATE("UPDATE"),

    /**
     * Delete resource instance.
     */
    DELETE("DELETE"),

    /**
     * Assign roles/permissions to users.
     */
    ASSIGN("ASSIGN"),

    /**
     * Revoke roles/permissions from users.
     */
    REVOKE("REVOKE"),

    /**
     * Sync data (interfaces, resources, etc).
     */
    SYNC("SYNC"),

    /**
     * Manage API mappings for a service resource.
     */
    MANAGE_API_MAPPING("MANAGE_API_MAPPING"),

    /**
     * Sync interfaces for a service.
     */
    SYNC_INTERFACE("SYNC_INTERFACE"),

    /**
     * Grant permissions on resource to others.
     */
    GRANT("GRANT");

    private final String code;

    OperationType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * Parse from code string, case-insensitive.
     */
    public static OperationType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (OperationType op : values()) {
            if (op.code.equals(normalized) || op.name().equals(normalized)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown OperationType: " + code);
    }

    /**
     * Check if this operation is a type-level operation (no specific instance).
     * CREATE is type-level because the instance doesn't exist yet.
     */
    public boolean isTypeLevelOperation() {
        return this == CREATE;
    }
}
