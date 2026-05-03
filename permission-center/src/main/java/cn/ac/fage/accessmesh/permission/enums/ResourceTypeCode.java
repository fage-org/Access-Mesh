package cn.ac.fage.accessmesh.permission.enums;

/**
 * Resource type codes for permission checks.
 * These codes are stored in type_definition table with type_key='resource_type'.
 */
public final class ResourceTypeCode {

    // ===== Core resource types =====
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";
    public static final String RESOURCE = "RESOURCE";
    public static final String SERVICE = "SERVICE";
    public static final String DOMAIN = "DOMAIN";
    public static final String API = "API";

    // ===== Config resource types =====
    public static final String TYPE_DEFINITION = "TYPE_DEFINITION";
    public static final String SYSTEM_CONFIG = "SYSTEM_CONFIG";
    public static final String OPERATION = "OPERATION";
    public static final String CONDITION = "CONDITION";
    public static final String CONFLICT_RULE = "CONFLICT_RULE";
    public static final String DEPENDENCY = "DEPENDENCY";

    private ResourceTypeCode() {
        // Constants class, no instances
    }
}