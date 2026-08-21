package cn.ac.fage.accessmesh.access.admin.security;

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
     * 用户管理资源。
     * <p>
     * 该类型表示”被管理的用户实例”，在 access-service 中对应
     * resource_entity(ADMIN_USER, code=sys_user.id)，通过业务键定位。
     * 不要与 abstract_user 主体事实混用。
     * </p>
     */
    public static final String USER = "ADMIN_USER";
    /**
     * 组织管理资源。
     * <p>
     * 该类型表示”被管理的组织/岗位实例”，在 access-service 中对应
     * resource_entity(ADMIN_ORG, code=sys_org.id)，通过业务键定位。
     * 组织/岗位作为角色容器时另行同步为 abstract_role(ORG/POSITION)。
     * </p>
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
     * 历史同步任务资源码。sys_sync_task 已随 T-ACCESS-005 退役，常量仅保留给存量授权查询。
     */
    public static final String SYNC_TASK = "ADMIN_SYNC_TASK";

    /**
     * 私有构造方法（常量类）
     */
    private AdminResourceType() {}
}
