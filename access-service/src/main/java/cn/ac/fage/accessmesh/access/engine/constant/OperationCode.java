package cn.ac.fage.accessmesh.access.engine.constant;

/**
 * 操作码常量类（唯一常量源，T-ACCESS-034 合一）
 * <p>
 * 原 {@code AdminOperationCode} 与 {@code OperationCodeConstants} 是同一物理注册表
 * {@code operation_permission} 的两个局部视图（admin 门禁轨 / permission 引擎轨），
 * 已随能力包融合合一为本类并删除（capability-structure §5.1 / 裁决 7）。按资源类型分节；
 * 共享码（CREATE/VIEW/UPDATE/DELETE/MANAGE/SYNC/ENABLE——ENABLE 为 USER 启停与
 * ADMIN_JOB 任务启停共用值面）多类型共用，按值命名。
 * 替代早期 OperationType 枚举，采用字符串形式简化权限判定；操作码存储在
 * {@code operation_permission} 表（schema 见 docs/design/schema/access-service.sql）。
 * </p>
 *
 * <p>统一常量面 = 注册表镜像（T-ACCESS-034 口径）：DDL 种子在册的操作码即使后端零代码
 * 引用也收录常量（ROLE:ASSIGN/REVOKE——授权矩阵可见可授予，历史用户角色代理门禁遗物）。
 * 该口径取代 T-PERM-019 D3 的「常量类只镜像代码引用面」（ASSIGN/REVOKE 常量曾按 D3 删除，
 * 本任务随合一恢复收录，种子行始终未动）。SDK 侧 {@code DefaultOpCode}(VIEW/EDIT/DELETE)
 * 为接入方契约独立维护，不与本类联动；EDIT 在服务端操作码注册表无预置，为已知差异
 * （extension-guide §2.3 注记）。</p>
 *
 * <p>使用示例（T-ORG-001 统一后操作者 ID 即主体 ID，无转换层；
 * 业务对象门禁统一业务编码语义，{@code resource_entity(ROLE).code = roleId}）：
 * <pre>
 * if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.ROLE, String.valueOf(roleId), OperationCode.MANAGE)) {
 *     throw new SecurityException("Permission denied");
 * }
 * engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(userId), OperationCode.VIEW);
 * </pre>
 * </p>
 */
public final class OperationCode {

    // ===== 通用（跨资源类型共享：全部类型 CRUD 预置 + 多类型 MANAGE + DEPENDENCY:SYNC） =====

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
     * 管理操作码
     * <p>
     * 表示管理资源实例的操作，如修改设置、配置属性等。
     * 属于实例级别的权限校验，校验是否有权管理特定资源实例。
     * 通常包含更新和配置类操作。
     * 使用方：ROLE/RESOURCE/SERVICE/TYPE_DEFINITION/SYSTEM_CONFIG/OPERATION 等类型的
     * 聚合管理门禁（粗粒度惯例保留，capability-structure §5.1：粗细统一仅 USER 一处）。
     * </p>
     */
    public static final String MANAGE = "MANAGE";

    /**
     * 同步操作码
     * <p>
     * 表示数据同步操作（DEPENDENCY:SYNC 批量同步资源依赖）。
     * 用于外部系统集成和数据导入场景。
     * </p>
     */
    public static final String SYNC = "SYNC";

    /**
     * 启用/禁用状态切换（toggle，共享码：USER 用户启停 + ADMIN_JOB 任务启停）。
     * <p>
     * 启用与禁用共用同一操作码：UI 上是同一个 toggle 控件，业务上无独立配权必要。
     * 调用方按目标状态设置实体字段，但权限校验只用 ENABLE 一个码。
     * 历史上的 DISABLE 已合并入此码（v1.4）。
     * USER 轨为细粒度门禁位（perm 轨 updateUser enabled≠null、admin 轨 /user/enable——
     * T-ACCESS-034 字段分档，防 UPDATE 绕过启停分权）。
     * </p>
     */
    public static final String ENABLE = "ENABLE";

    // ===== USER（重置密码；update/remove/启停门禁用通用 UPDATE/DELETE/ENABLE，T-ACCESS-034 细粒度化） =====

    /**
     * 重置密码操作
     */
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    // ===== ROLE（分组角色分配/撤销；MANAGE 见通用段） =====
    // （ADMIN_ROLE:GRANT/REVOKE 已随 T-ACCESS-018 类型收敛删除：零生产消费者，职责由 ROLE:MANAGE 承担）

    /**
     * 分配角色
     * <p>
     * DDL 种子在册（ROLE:ASSIGN bit=32）、授权矩阵可见可授予；后端零代码门禁引用——
     * 历史用户角色代理门禁遗物，按「统一常量面=注册表镜像」收录（T-ACCESS-034，
     * 取代 T-PERM-019 D3 只镜像代码引用面的口径）。
     * </p>
     */
    public static final String ASSIGN = "ASSIGN";

    /**
     * 撤销角色
     * <p>
     * DDL 种子在册（ROLE:REVOKE bit=64）、授权矩阵可见可授予；后端零代码门禁引用——
     * 同 {@link #ASSIGN}，注册表镜像收录。
     * </p>
     */
    public static final String REVOKE = "REVOKE";

    // ===== API（网关接口鉴权专用） =====

    /**
     * 访问接口
     * <p>
     * API:ACCESS 为网关接口鉴权专用操作码（mask=0，不继承 VIEW）：Gateway 层实例级
     * {@code API:ACCESS@接口资源} + 类型级可转授条目（T-API-001 鸡生蛋解法）。
     * 消费方：PermissionCheckAppServiceImpl（forInterfaceCheck）、SnapshotAssembler
     * （快照装配）、BootstrapGraphDefinition（固定图类型级 + 实例派生）。
     * </p>
     */
    public static final String ACCESS = "ACCESS";

    // ===== SERVICE（服务配置与接口映射；MANAGE/SERVICE:VIEW 见通用段） =====

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

    // ===== ORG（组织与岗位，orgType 分发见 OrgOperationCodeMapper） =====

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

    // ===== 岗位专属（资源类型为 ORG，按 sys_org.orgType=2 区分；原 ADMIN_ORG 已随 T-ACCESS-018 收敛） =====
    //
    // 设计动机：岗位 = 特殊组织，与普通组织共用 /org/* 端点和 ORG 资源锚点，
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
     * 与 ORG:VIEW（组织树查看）独立，便于"只能看组织不能看岗位"或反之的细粒度配权。
     * 资源类型仍为 {@code ResourceTypeCode.ORG}，按 orgType=2 实例过滤。
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
     * 与普通组织成员归属（ORG:UPDATE）解耦：作用在 orgType=2 的 sys_org 实例上的
     * user-org 关系动作走此操作码，便于"岗位用户运营"独立配权。
     * <p>
     * 映射关系：{@code OrgOperationCodeMapper.resolveForUserOrg(orgType, UPDATE)} → orgType=2 时返回本常量。
     */
    public static final String ASSIGN_POSITION_USER = "ASSIGN_POSITION_USER";

    // ===== 平台（NOTICE 发布 / JOB 启停与触发 / ORG_TREE_CONFIG 切换） =====

    /**
     * 发布操作（ADMIN_NOTICE）
     */
    public static final String PUBLISH = "PUBLISH";
    /**
     * 触发操作（ADMIN_JOB）
     */
    public static final String TRIGGER = "TRIGGER";
    /**
     * 切换状态操作（ADMIN_ORG_TREE_CONFIG：默认树/单关联切换）
     */
    public static final String TOGGLE = "TOGGLE";

    /**
     * 私有构造方法（常量类）
     */
    private OperationCode() {}
}
