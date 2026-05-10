package cn.ac.fage.accessmesh.permission.util;

/**
 * 权限模块通用常量类
 * <p>
 * 定义permission-center模块中使用的通用常量值。
 * 包括状态值、特殊ID等。
 * </p>
 */
public final class PermissionConstants {

    /**
     * 不存在的ID值
     * <p>
     * 用于强制返回空查询结果的ID值。
     * 当查询失败（如无效的domainCode）时使用此值确保无结果。
     * </p>
     */
    public static final long NONEXISTENT_ID = -1L;

    /**
     * 启用状态值
     * <p>
     * 表示实体处于启用/激活状态。
     * </p>
     */
    public static final int ENABLED_STATUS = 1;

    /**
     * 禁用状态值
     * <p>
     * 表示实体处于禁用/未激活状态。
     * </p>
     */
    public static final int DISABLED_STATUS = 0;

    /**
     * 私有构造函数
     * <p>
     * 常量类不允许实例化。
     * </p>
     */
    private PermissionConstants() {
        // 防止实例化
    }
}