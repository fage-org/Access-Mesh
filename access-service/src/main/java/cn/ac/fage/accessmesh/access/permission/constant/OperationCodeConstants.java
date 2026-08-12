package cn.ac.fage.accessmesh.access.permission.constant;

/**
 * 操作码常量类
 * <p>
 * 定义权限校验使用的操作码常量。
 * 替代原有的OperationType枚举，采用字符串形式的API简化权限判定。
 * 这些操作码存储在 operation_permission 表中。
 * </p>
 *
 * <p>使用示例：
 * <pre>
 * if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE)) {
 *     throw new SecurityException("Permission denied");
 * }
 * engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, userId, OperationCodeConstants.VIEW);
 * </pre>
 * </p>
 */
public final class OperationCodeConstants {

    /**
     * 创建操作码
     * <p>
     * 表示创建新资源实例的操作，如创建用户、创建角色等。
     * 属于类型级别的权限校验，校验是否有权创建该类型的资源。
     * </p>
     */
    public static final String CREATE = "CREATE";

    /**
     * 查看操作码
     * <p>
     * 表示查看资源实例详情的操作，如查看用户信息、查看角色配置等。
     * 属于实例级别的权限校验，校验是否有权查看特定资源实例。
     * </p>
     */
    public static final String VIEW = "VIEW";

    /**
     * 管理操作码
     * <p>
     * 表示管理资源实例的操作，如修改设置、配置属性等。
     * 属于实例级别的权限校验，校验是否有权管理特定资源实例。
     * 通常包含更新和配置类操作。
     * </p>
     */
    public static final String MANAGE = "MANAGE";

    /**
     * 更新操作码
     * <p>
     * 表示更新资源实例的操作。
     * 在某些场景下是MANAGE操作的别名，用于更细粒度的权限控制。
     * </p>
     */
    public static final String UPDATE = "UPDATE";

    /**
     * 删除操作码
     * <p>
     * 表示删除资源实例的操作，如删除用户、删除角色等。
     * 属于实例级别的权限校验，校验是否有权删除特定资源实例。
     * </p>
     */
    public static final String DELETE = "DELETE";

    /**
     * 分配操作码
     * <p>
     * 表示分配角色或权限给用户的操作，如为用户分配角色。
     * 用于角色管理和权限分配场景。
     * </p>
     */
    public static final String ASSIGN = "ASSIGN";

    /**
     * 撤销操作码
     * <p>
     * 表示撤销用户角色或权限的操作，如撤销用户的角色。
     * 用于角色管理和权限撤销场景。
     * </p>
     */
    public static final String REVOKE = "REVOKE";

    /**
     * 同步操作码
     * <p>
     * 表示数据同步操作，如同步接口、同步资源等。
     * 用于外部系统集成和数据导入场景。
     * </p>
     */
    public static final String SYNC = "SYNC";

    /**
     * 管理API映射操作码
     * <p>
     * 表示管理服务资源API映射的操作，如配置API与资源的对应关系。
     * 用于Gateway权限校验配置场景。
     * </p>
     */
    public static final String MANAGE_API_MAPPING = "MANAGE_API_MAPPING";

    /**
     * 同步接口操作码
     * <p>
     * 表示同步服务接口的操作，如从服务获取最新的接口列表。
     * 用于服务配置和接口管理场景。
     * </p>
     */
    public static final String SYNC_INTERFACE = "SYNC_INTERFACE";

    /**
     * 私有构造函数
     * <p>
     * 常量类不允许实例化。
     * </p>
     */
    private OperationCodeConstants() {}
}