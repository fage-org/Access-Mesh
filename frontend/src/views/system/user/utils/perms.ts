/**
 * 「组织与用户」融合页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `Object.values(ORG_USER_PERMS)` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(ORG_USER_PERMS.XXX)`
 *
 * ## v1.4「双轨并行」命名空间统一
 * 前后端统一使用 `资源类型:操作码` 词法（permission-center 的乙层模型），
 * 不再经 `sys_menu.perm_code` 中转，也无 `system:模块:动作` 翻译层：
 * - 前端 `hasPerms("ADMIN_ORG:CREATE_POSITION")` 与后端 `engine.hasPermission(... ADMIN_ORG, CREATE_POSITION)` 同源
 * - 管理员只在权限中心一处配权，前端按钮即时跟随
 *
 * 双轨：
 * - 轨道 1（菜单可见性）：`ADMIN_MENU:VIEW` —— 决定路由可达，不在本表登记
 * - 轨道 2（按钮权限）：本表所有条目 —— 决定页面内按钮是否显示
 *
 * 详见 `docs/design/org-user-permission-contract.md` v1.4 + §4 权限矩阵。
 *
 * ## 「岗位 = 特殊组织」
 * 岗位与普通组织共用 `/org/*` 端点（通过 `orgType=2` 区分），资源类型同为 `ADMIN_ORG`，
 * 但乙层使用**独立的精化操作码**实现"组织管理员 ≠ 岗位管理员"细粒度配权：
 *   - 岗位查看      → `ADMIN_ORG:VIEW_POSITION`
 *   - 岗位 CRUD     → `ADMIN_ORG:CREATE_POSITION / UPDATE_POSITION / DELETE_POSITION`
 *   - 岗位用户挂载  → `ADMIN_ORG:ASSIGN_POSITION_USER`
 * **不新增 `ADMIN_POSITION` 资源类型**，以保持锚点单一性、避免 user-org 关系双写。
 */
export const ORG_USER_PERMS = {
  // ===== 组织树（A 区）—— ADMIN_ORG（orgType=1） =====
  /** 查看组织树 */
  ORG_VIEW: "ADMIN_ORG:VIEW",
  /** 新增根/子组织 */
  ORG_ADD: "ADMIN_ORG:CREATE",
  /** 编辑/移动/启停组织节点（与「成员管理」解耦） */
  ORG_EDIT: "ADMIN_ORG:UPDATE",
  /** 删除组织 */
  ORG_DELETE: "ADMIN_ORG:DELETE",
  /**
   * 普通组织成员关系（添加/移除成员、设主组织）
   *
   * v1.4 从 UPDATE 拆出独立操作码 MANAGE_MEMBER，与岗位的 ASSIGN_POSITION_USER 同构。
   * **默认树语义边界**：默认组织树上「添加/移除/设主」具有身份目录含义，
   * 由后端按 `docs/design/default-org-tree-user-lifecycle.md` 二次拒绝。
   */
  ORG_MEMBER: "ADMIN_ORG:MANAGE_MEMBER",

  // ===== 成员（B 区）—— ADMIN_USER =====
  /** 查看用户列表 */
  USER_VIEW: "ADMIN_USER:VIEW",
  /** 创建用户（只能归默认组织树） */
  USER_ADD: "ADMIN_USER:CREATE",
  /** 编辑用户（改己豁免） */
  USER_EDIT: "ADMIN_USER:UPDATE",
  /** 删除用户 */
  USER_DELETE: "ADMIN_USER:DELETE",
  /** 启用/禁用切换（v1.4 toggle 语义，DISABLE 已合并入 ENABLE） */
  USER_ENABLE: "ADMIN_USER:ENABLE",
  /** 重置密码（改己豁免） */
  USER_RESET_PWD: "ADMIN_USER:RESET_PASSWORD",

  // ===== 功能角色分配（C 区）—— permission-center 的 ROLE 资源类型 =====
  //
  // 契约依据：org-user-permission-contract.md §5 备注³
  // 功能角色分配 = ROLE:MANAGE（目标角色实例），不得复用 ADMIN_ROLE:GRANT/REVOKE（配权语义，属红线）。
  // permission-center 内部 UserManageAppServiceImpl.assignRole/revokeRolesBatch
  // 同样使用 ROLE:MANAGE 做二次校验，形成门禁一致性。
  /** 分配/回收功能角色（ROLE:MANAGE，权限锚点为目标 abstract_role） */
  USER_ROLE_ASSIGN: "ROLE:MANAGE",
  USER_ROLE_REVOKE: "ROLE:MANAGE",

  // ===== 岗位（D 区，岗位 = 特殊组织 orgType=2）—— ADMIN_ORG + 精化操作码 =====
  /** 查看岗位 Tab（v1.4 VIEW 类细化到资源类型，与 ADMIN_ORG:VIEW 解耦） */
  POSITION_VIEW: "ADMIN_ORG:VIEW_POSITION",
  /** 新增岗位 */
  POSITION_ADD: "ADMIN_ORG:CREATE_POSITION",
  /** 编辑岗位 */
  POSITION_EDIT: "ADMIN_ORG:UPDATE_POSITION",
  /** 删除岗位 */
  POSITION_DELETE: "ADMIN_ORG:DELETE_POSITION",
  /**
   * 挂载/卸载/设主 岗位用户
   *
   * 与普通组织成员归属（ADMIN_ORG:MANAGE_MEMBER）解耦，便于
   * "岗位用户运营"独立配权。资源锚点仍是岗位 org 实例。
   */
  POSITION_ASSIGN: "ADMIN_ORG:ASSIGN_POSITION_USER"
} as const;

export type OrgUserPermKey = keyof typeof ORG_USER_PERMS;
export type OrgUserPermValue = (typeof ORG_USER_PERMS)[OrgUserPermKey];

/**
 * 全部 perm 串清单（派生），用于路由 `meta.auths`。
 * 派生而非手抄，确保新增/重命名 perm 串时单点修改。
 */
export const ORG_USER_PERM_LIST: ReadonlyArray<OrgUserPermValue> =
  Object.values(ORG_USER_PERMS);

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const ORG_USER_VIEW_PERMS: ReadonlyArray<OrgUserPermValue> = [
  ORG_USER_PERMS.ORG_VIEW,
  ORG_USER_PERMS.USER_VIEW,
  ORG_USER_PERMS.POSITION_VIEW
];
