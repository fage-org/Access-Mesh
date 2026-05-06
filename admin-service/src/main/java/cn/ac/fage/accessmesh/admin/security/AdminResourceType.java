package cn.ac.fage.accessmesh.admin.security;

/**
 * Admin-service resource type constants.
 * Used as resourceTypeCode in permission check requests.
 */
public final class AdminResourceType {

    // Core admin resources
    public static final String USER = "ADMIN_USER";
    public static final String ORG = "ADMIN_ORG";
    public static final String MENU = "ADMIN_MENU";
    public static final String ROLE = "ADMIN_ROLE";

    // System configuration resources
    public static final String DICT = "ADMIN_DICT";
    public static final String DICT_DATA = "ADMIN_DICT_DATA";
    public static final String CONFIG = "ADMIN_CONFIG";

    // OAuth2 resources
    public static final String OAUTH2_CLIENT = "ADMIN_OAUTH2_CLIENT";

    // Other resources
    public static final String NOTICE = "ADMIN_NOTICE";
    public static final String FILE = "ADMIN_FILE";
    public static final String JOB = "ADMIN_JOB";
    public static final String ORG_TREE_CONFIG = "ADMIN_ORG_TREE_CONFIG";
    public static final String SYNC_RETRY = "ADMIN_SYNC_RETRY";

    private AdminResourceType() {}
}