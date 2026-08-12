package cn.ac.fage.accessmesh.access.permission.enums;

/**
 * 资源类型编码常量类
 * <p>
 * 定义权限校验使用的资源类型编码。
 * 这些编码存储在 type_definition 表中，type_key='resource_type'。
 * 用于 PermQueryEngine 进行权限判定时标识资源类型。
 * </p>
 */
public final class ResourceTypeCode {

    // ===== 核心资源类型 =====

    /**
     * 用户资源编码
     * <p>
     * 用于用户管理的权限校验，如查看、编辑用户信息。
     * </p>
     */
    public static final String USER = "USER";

    /**
     * 角色资源编码
     * <p>
     * 用于角色管理的权限校验，如创建、配置角色权限。
     * </p>
     */
    public static final String ROLE = "ROLE";

    /**
     * 资源实体编码
     * <p>
     * 用于资源管理的权限校验，如创建、配置资源。
     * </p>
     */
    public static final String RESOURCE = "RESOURCE";

    /**
     * 服务资源编码
     * <p>
     * 用于服务配置的权限校验，如同步服务接口。
     * </p>
     */
    public static final String SERVICE = "SERVICE";

    /**
     * 业务域资源编码
     * <p>
     * 用于业务域管理的权限校验，如创建、配置业务域。
     * </p>
     */
    public static final String DOMAIN = "DOMAIN";

    /**
     * API接口资源编码
     * <p>
     * 用于API映射管理的权限校验。
     * </p>
     */
    public static final String API = "API";

    // ===== 配置资源类型 =====

    /**
     * 类型定义资源编码
     * <p>
     * 用于类型定义管理的权限校验。
     * </p>
     */
    public static final String TYPE_DEFINITION = "TYPE_DEFINITION";

    /**
     * 系统配置资源编码
     * <p>
     * 用于系统配置管理的权限校验。
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