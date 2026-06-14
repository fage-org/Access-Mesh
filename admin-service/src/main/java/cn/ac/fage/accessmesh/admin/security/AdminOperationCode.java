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

    // ===== 岗位专属操作（资源类型仍为 ADMIN_ORG，按 sys_org.orgType=2 区分） =====
    //
    // 设计动机：岗位 = 特殊组织，与普通组织共用 /org/* 端点和 ADMIN_ORG 资源锚点，
    // 但权限粒度需独立——例如 HR 能管组织树但不应直接动岗位，岗位负责人只管岗位
    // 不动组织树。通过精化操作码（CREATE_POSITION 之于 CREATE，类同 RESET_PASSWORD
    // 之于 UPDATE）将这两类管理权限解耦，避免新增独立 ADMIN_POSITION 资源类型造成
    // 锚点分裂、user-org 关系双写等问题。
    //
    // 详见 docs/design/org-user-permission-contract.md §4 D 区与备注 ⁴。

    /**
     * 创建岗位（orgType=2 的 sys_org 实例）
     */
    public static final String CREATE_POSITION = "CREATE_POSITION";
    /**
     * 更新岗位（含编辑、移动、改状态，与普通组织 UPDATE 解耦）
     */
    public static final String UPDATE_POSITION = "UPDATE_POSITION";
    /**
     * 删除岗位
     */
    public static final String DELETE_POSITION = "DELETE_POSITION";
    /**
     * 挂载/卸载/设主 岗位用户
     * <p>
     * 与普通组织成员归属（ADMIN_ORG:UPDATE）解耦：作用在 orgType=2 的 sys_org 实例上的
     * user-org 关系动作走此操作码，便于"岗位用户运营"独立配权。
     * </p>
     */
    public static final String ASSIGN_POSITION_USER = "ASSIGN_POSITION_USER";

    /**
     * 私有构造方法（常量类）
     */
    private AdminOperationCode() {}
}