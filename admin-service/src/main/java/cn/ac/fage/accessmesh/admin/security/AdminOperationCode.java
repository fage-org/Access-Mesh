package cn.ac.fage.accessmesh.admin.security;

/**
 * Admin-service operation code constants.
 * Used as operationCode in permission check requests.
 */
public final class AdminOperationCode {

    // Standard CRUD operations
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String VIEW = "VIEW";

    // Status operations
    public static final String ENABLE = "ENABLE";
    public static final String DISABLE = "DISABLE";

    // User-specific operations
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    // Permission operations
    public static final String GRANT = "GRANT";
    public static final String REVOKE = "REVOKE";

    // Other operations
    public static final String PUBLISH = "PUBLISH";
    public static final String TRIGGER = "TRIGGER";
    public static final String TOGGLE = "TOGGLE";

    private AdminOperationCode() {}
}