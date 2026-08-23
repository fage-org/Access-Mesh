package cn.ac.fage.accessmesh.access.permission.enums;

/**
 * 资源类型编码常量类（唯一常量源，T-ACCESS-016 §13.4 / T-ACCESS-018 合一）。
 * <p>
 * 终态 23 个资源类型码与 {@code docs/design/schema/access-service.sql} 文件头
 * type_value 终值分配表一一对应；原管理门禁常量类 {@code AdminResourceType}
 * （ADMIN_USER/ADMIN_ORG/ADMIN_ROLE/ADMIN_MENU/ADMIN_CONFIG）已随类型收敛删除，
 * 五组管理类型并入 USER/ORG/ROLE/MENU/SYSTEM_CONFIG，权限引擎与管理门禁共用本类。
 * 退役码值 16/17/18/19/22/28 不复用，后续新类型从 30 起顺延。
 * </p>
 */
public final class ResourceTypeCode {

    // ===== 公共基础资源类型 =====

    /**
     * 菜单资源编码。
     * <p>
     * MENU:CREATE/UPDATE/DELETE/VIEW 仅保护菜单配置后台；普通用户菜单可见性
     * 按 sys_menu.resource_type/resource_code 关联业务权限派生（architecture §13.4）。
     * </p>
     */
    public static final String MENU = "MENU";

    /**
     * 按钮资源编码
     */
    public static final String BUTTON = "BUTTON";

    /**
     * API接口资源编码
     * <p>
     * 用于API映射管理与网关接口鉴权。
     * </p>
     */
    public static final String API = "API";

    /**
     * 数据资源编码（数据权限资源实体）
     */
    public static final String DATA = "DATA";

    /**
     * 角色资源编码。
     * <p>
     * 角色实例级授权与角色管理门禁共用（resource_entity(ROLE).code = roleId，
     * 原 ADMIN_ROLE 并入）。
     * </p>
     */
    public static final String ROLE = "ROLE";

    /**
     * 用户资源编码。
     * <p>
     * 用户实例级授权与用户管理门禁共用（resource_entity(USER).code = subjectId，
     * 原 ADMIN_USER 并入）。
     * </p>
     */
    public static final String USER = "USER";

    /**
     * 组织资源编码。
     * <p>
     * 组织/岗位管理门禁（resource_entity(ORG).code = sys_org.id，原 ADMIN_ORG 收敛，
     * type_value=29）。岗位精化操作码（CREATE_POSITION 等）仍挂本类型，
     * 按 sys_org.orgType 区分。
     * </p>
     */
    public static final String ORG = "ORG";

    // ===== 权限中心资源类型 =====

    /**
     * 资源实体编码
     * <p>
     * 用于资源实体自身管理链路的权限校验。
     * </p>
     */
    public static final String RESOURCE = "RESOURCE";

    /**
     * 服务资源编码
     * <p>
     * 用于服务配置、API 映射与接口同步的权限校验。
     * </p>
     */
    public static final String SERVICE = "SERVICE";

    /**
     * 业务域资源编码
     * <p>
     * 用于业务域管理的权限校验。
     * </p>
     */
    public static final String DOMAIN = "DOMAIN";

    /**
     * 类型定义资源编码
     * <p>
     * 用于类型定义管理的权限校验。
     * </p>
     */
    public static final String TYPE_DEFINITION = "TYPE_DEFINITION";

    /**
     * 系统配置资源编码。
     * <p>
     * 系统配置/业务域/域配置管理门禁（原 ADMIN_CONFIG 并入）。
     * </p>
     */
    public static final String SYSTEM_CONFIG = "SYSTEM_CONFIG";

    /**
     * 操作权限资源编码
     * <p>
     * 用于操作权限管理的权限校验。
     * </p>
     */
    public static final String OPERATION = "OPERATION";

    /**
     * 权限条件资源编码
     * <p>
     * 用于权限条件管理的权限校验。
     * </p>
     */
    public static final String CONDITION = "CONDITION";

    /**
     * 冲突规则资源编码
     * <p>
     * 用于冲突规则管理的权限校验。
     * </p>
     */
    public static final String CONFLICT_RULE = "CONFLICT_RULE";

    /**
     * 资源依赖资源编码
     * <p>
     * 用于资源依赖管理的权限校验。
     * </p>
     */
    public static final String DEPENDENCY = "DEPENDENCY";

    // ===== 管理域保留类型（无重复对象不改名，T-ACCESS-016 §13.1） =====

    /**
     * 字典类型资源编码
     */
    public static final String ADMIN_DICT = "ADMIN_DICT";

    /**
     * 字典数据资源编码
     */
    public static final String ADMIN_DICT_DATA = "ADMIN_DICT_DATA";

    /**
     * OAuth2客户端资源编码
     */
    public static final String ADMIN_OAUTH2_CLIENT = "ADMIN_OAUTH2_CLIENT";

    /**
     * 通知公告资源编码
     */
    public static final String ADMIN_NOTICE = "ADMIN_NOTICE";

    /**
     * 文件资源编码
     */
    public static final String ADMIN_FILE = "ADMIN_FILE";

    /**
     * 定时任务资源编码
     */
    public static final String ADMIN_JOB = "ADMIN_JOB";

    /**
     * 组织树配置资源编码
     */
    public static final String ADMIN_ORG_TREE_CONFIG = "ADMIN_ORG_TREE_CONFIG";

    /**
     * 私有构造函数
     * <p>
     * 常量类不允许实例化。
     * </p>
     */
    private ResourceTypeCode() {
        // 常量类，不允许创建实例
    }
}
