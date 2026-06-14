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
     * 启用/禁用状态切换（toggle）。
     * <p>
     * 启用与禁用共用同一操作码：UI 上是同一个 toggle 控件，业务上无独立配权必要。
     * 调用方按目标状态设置实体字段，但权限校验只用 ENABLE 一个码。
     * 历史上的 DISABLE 已合并入此码（v1.4）。
     */
    public static final String ENABLE = "ENABLE";

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
     * 管理操作（分配/回收角色等复合管理权限）。
     * <p>
     * 与 GRANT/REVOKE 的区别：MANAGE 是面向终端用户的「角色分配给用户」语义，
     * GRANT/REVOKE 是面向角色配置的「给角色配权限」语义。
     * 前端 {@code ROLE:MANAGE} 对应本操作码。
     */
    public static final String MANAGE = "MANAGE";

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

    // ===== 组织专属操作 =====

    /**
     * 管理普通组织成员关系（添加/移除成员、设主组织）。
     * <p>
     * 与组织树节点的「编辑」（{@link #UPDATE}）解耦：UPDATE 仅控制组织节点本身的属性变更，
     * MANAGE_MEMBER 控制组织实例下的 user-org 关系。两者业务上是不同 UX 控制点，
     * 配权也常需独立（如 HR 可编辑组织树但不能调整成员，组织管理员相反）。
     * <p>
     * 与岗位的 {@link #ASSIGN_POSITION_USER} 同构（普通组织 ↔ 岗位 各自一码）。
     * 由 {@link OrgOperationCodeMapper#resolveForUserOrg} 按 orgType 分发。
     */
    public static final String MANAGE_MEMBER = "MANAGE_MEMBER";

    // ===== 岗位专属操作（资源类型仍为 ADMIN_ORG，按 sys_org.orgType=2 区分） =====
    //
    // 设计动机：岗位 = 特殊组织，与普通组织共用 /org/* 端点和 ADMIN_ORG 资源锚点，
    // 但权限粒度需独立——例如 HR 能管组织树但不应直接动岗位，岗位负责人只管岗位
    // 不动组织树。通过精化操作码（CREATE_POSITION 之于 CREATE，类同 RESET_PASSWORD
    // 之于 UPDATE）将这两类管理权限解耦，避免新增独立 ADMIN_POSITION 资源类型造成
    // 锚点分裂、user-org 关系双写等问题。
    //
    // orgType → 操作码声明式映射见 {@link OrgOperationCodeMapper}。
    // 详见 docs/design/org-user-permission-contract.md §4 D 区与备注 ⁴。

    /**
     * 创建岗位（orgType=2 的 sys_org 实例）
     * <p>
     * 映射关系：{@code OrgOperationCodeMapper.resolve(orgType, CREATE)} → orgType=2 时返回本常量。
     */
    public static final String CREATE_POSITION = "CREATE_POSITION";
    /**
     * 查看岗位 Tab（与组织树查看权限解耦——v1.4 VIEW 类细化到资源类型）
     * <p>
     * 与 ADMIN_ORG:VIEW（组织树查看）独立，便于"只能看组织不能看岗位"或反之的细粒度配权。
     * 资源类型仍为 {@link AdminResourceType#ORG}，按 orgType=2 实例过滤。
     */
    public static final String VIEW_POSITION = "VIEW_POSITION";
    /**
     * 更新岗位（含编辑、移动、改状态，与普通组织 UPDATE 解耦）
     * <p>
     * 映射关系：{@code OrgOperationCodeMapper.resolve(orgType, UPDATE)} → orgType=2 时返回本常量。
     */
    public static final String UPDATE_POSITION = "UPDATE_POSITION";
    /**
     * 删除岗位
     * <p>
     * 映射关系：{@code OrgOperationCodeMapper.resolve(orgType, DELETE)} → orgType=2 时返回本常量。
     */
    public static final String DELETE_POSITION = "DELETE_POSITION";
    /**
     * 挂载/卸载/设主 岗位用户
     * <p>
     * 与普通组织成员归属（ADMIN_ORG:UPDATE）解耦：作用在 orgType=2 的 sys_org 实例上的
     * user-org 关系动作走此操作码，便于"岗位用户运营"独立配权。
     * <p>
     * 映射关系：{@code OrgOperationCodeMapper.resolveForUserOrg(orgType, UPDATE)} → orgType=2 时返回本常量。
     */
    public static final String ASSIGN_POSITION_USER = "ASSIGN_POSITION_USER";

    /**
     * 私有构造方法（常量类）
     */
    private AdminOperationCode() {}
}