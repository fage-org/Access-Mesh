package cn.ac.fage.accessmesh.admin.security;

/**
 * Admin模块资源类型常量类
 * <p>
 * 定义Admin模块权限校验使用的标准资源类型码。
 * 作为权限校验请求的 resourceTypeCode 参数使用。
 * </p>
 */
public final class AdminResourceType {

    // ===== 核心管理资源 =====

    /**
     * 用户资源
     */
    public static final String USER = "ADMIN_USER";
    /**
     * 组织资源
     */
    public static final String ORG = "ADMIN_ORG";
    /**
     * 菜单资源
     */
    public static final String MENU = "ADMIN_MENU";
    /**
     * 角色资源
     */
    public static final String ROLE = "ADMIN_ROLE";

    // ===== 系统配置资源 =====

    /**
     * 字典类型资源
     */
    public static final String DICT = "ADMIN_DICT";
    /**
     * 字典数据资源
     */
    public static final String DICT_DATA = "ADMIN_DICT_DATA";
    /**
     * 系统配置资源
     */
    public static final String CONFIG = "ADMIN_CONFIG";

    // ===== OAuth2资源 =====

    /**
     * OAuth2客户端资源
     */
    public static final String OAUTH2_CLIENT = "ADMIN_OAUTH2_CLIENT";

    // ===== 其他资源 =====

    /**
     * 通知公告资源
     */
    public static final String NOTICE = "ADMIN_NOTICE";
    /**
     * 文件资源
     */
    public static final String FILE = "ADMIN_FILE";
    /**
     * 定时任务资源
     */
    public static final String JOB = "ADMIN_JOB";
    /**
     * 组织树配置资源
     */
    public static final String ORG_TREE_CONFIG = "ADMIN_ORG_TREE_CONFIG";
    /**
     * 同步重试资源
     */
    public static final String SYNC_RETRY = "ADMIN_SYNC_RETRY";

    /**
     * 私有构造方法（常量类）
     */
    private AdminResourceType() {}
}