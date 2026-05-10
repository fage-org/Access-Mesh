package cn.ac.fage.accessmesh.admin.security;

/**
 * Admin模块操作码常量类
 * <p>
 * 定义Admin模块权限校验使用的标准操作码。
 * 作为权限校验请求的 operationCode 参数使用。
 * </p>
 */
public final class AdminOperationCode {

    // ===== 标准CRUD操作 =====

    /**
     * 创建操作
     */
    public static final String CREATE = "CREATE";
    /**
     * 更新操作
     */
    public static final String UPDATE = "UPDATE";
    /**
     * 删除操作
     */
    public static final String DELETE = "DELETE";
    /**
     * 查看操作
     */
    public static final String VIEW = "VIEW";

    // ===== 状态操作 =====

    /**
     * 启用操作
     */
    public static final String ENABLE = "ENABLE";
    /**
     * 禁用操作
     */
    public static final String DISABLE = "DISABLE";

    // ===== 用户专属操作 =====

    /**
     * 重置密码操作
     */
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    // ===== 权限操作 =====

    /**
     * 授权操作
     */
    public static final String GRANT = "GRANT";
    /**
     * 撤销权限操作
     */
    public static final String REVOKE = "REVOKE";

    // ===== 其他操作 =====

    /**
     * 发布操作
     */
    public static final String PUBLISH = "PUBLISH";
    /**
     * 触发操作
     */
    public static final String TRIGGER = "TRIGGER";
    /**
     * 切换状态操作
     */
    public static final String TOGGLE = "TOGGLE";

    /**
     * 私有构造方法（常量类）
     */
    private AdminOperationCode() {}
}