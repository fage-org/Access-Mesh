package cn.ac.fage.accessmesh.permission;

/**
 * Permission-center error codes (20001-29999 range).
 */
public final class PermErrorCode {

    private PermErrorCode() {}

    // Validation errors (20001-20099)
    public static final int INVALID_PARAM = 20001;
    public static final int MISSING_PARAM = 20002;

    // Not found errors (20100-20199)
    public static final int USER_NOT_FOUND = 20100;
    public static final int ROLE_NOT_FOUND = 20101;
    public static final int RESOURCE_NOT_FOUND = 20102;
    public static final int OPERATION_NOT_FOUND = 20103;
    public static final int CONDITION_NOT_FOUND = 20104;
    public static final int DEPENDENCY_NOT_FOUND = 20105;
    public static final int CONFIG_NOT_FOUND = 20106;
    public static final int DOMAIN_NOT_FOUND = 20107;
    public static final int SERVICE_CONFIG_NOT_FOUND = 20108;
    public static final int API_MAPPING_NOT_FOUND = 20109;
    public static final int CONFLICT_RULE_NOT_FOUND = 20110;
    public static final int TYPE_NOT_FOUND = 20111;

    // Conflict / duplication errors (20200-20299)
    public static final int USER_EXISTS = 20200;
    public static final int ROLE_EXISTS = 20201;
    public static final int ROLE_ALREADY_ASSIGNED = 20202;

    // Type resolution errors (20300-20399)
    public static final int UNKNOWN_TYPE_CODE = 20300;
    public static final int UNKNOWN_DOMAIN_CODE = 20301;
    public static final int TYPE_MISMATCH = 20302;

    // State errors (20400-20499)
    public static final int INVALID_OPERATION = 20400;
    public static final int SYNC_MODE_NOT_SUPPORTED = 20401;
}
