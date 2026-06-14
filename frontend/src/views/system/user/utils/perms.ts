/**
 * 「组织与用户」融合页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `Object.values(ORG_USER_PERMS)` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(ORG_USER_PERMS.XXX)`
 *
 * ## 甲层 ↔ 乙层映射
 * 与 `docs/design/org-user-permission-contract.md` §4 权限矩阵一一对应。
 * **甲层**（这里）：前端按钮可见性 perm 串，按 UI 模块切分以便菜单细粒度配权（如 HR 只管组织不管岗位）。
 * **乙层**：admin-service 的 `permissionValidator` 走资源类型 × 操作码门禁
 *           （`ADMIN_ORG/ADMIN_USER/ADMIN_ROLE` × `CREATE/UPDATE/DELETE/...`）。
 *
 * 前端 hasPerms 通过 ≠ 接口必能调通，后端是真权威；本目录仅做 UX 隐藏。
 *
 * ## 「岗位 = 特殊组织」契约（§D 备注 ⁴）
 * 岗位与普通组织共用 `/org/*` 端点（通过 `orgType=2` 区分），乙层同为 `ADMIN_ORG:CREATE/UPDATE/DELETE`。
 * 甲层把岗位拆成独立 `system:org:position:*` 串只是为了菜单切分，
 * **后端不存在独立 `ADMIN_POSITION` 资源类型**。
 * 因此 `POSITION_ASSIGN` 与 `ORG_MEMBER` 在乙层等价（均为 `ADMIN_ORG:UPDATE` 实例级门禁），
 * 仅 UI 命名拆分。
 */
export const ORG_USER_PERMS = {
  // ===== 组织树（A 区）—— 乙层 ADMIN_ORG（orgType=1） =====
  /** 页面/树可见性（菜单可见性派生，最小入口权） */
  ORG_VIEW: "system:org:view",
  /** 新增根/子组织 → ADMIN_ORG:CREATE */
  ORG_ADD: "system:org:add",
  /** 编辑/移动/启停组织 → ADMIN_ORG:UPDATE（admin-service 改类操作折叠到 UPDATE） */
  ORG_EDIT: "system:org:edit",
  /** 删除组织 → ADMIN_ORG:DELETE */
  ORG_DELETE: "system:org:delete",
  /**
   * 组织成员关系操作（添加/移除成员、设主组织）→ ADMIN_ORG:UPDATE（实例级，作用在目标组织）
   *
   * **默认树语义边界**：默认组织树上「添加/移除/设主」具有身份目录含义，
   * 最终由后端按 `docs/design/default-org-tree-user-lifecycle.md` 二次拒绝。
   */
  ORG_MEMBER: "system:org:member",

  // ===== 成员（B 区）—— 乙层 ADMIN_USER =====
  USER_VIEW: "system:user:view",
  /** 创建用户（只能归默认组织树）→ ADMIN_USER:CREATE */
  USER_ADD: "system:user:add",
  /** 编辑用户（改己豁免）→ ADMIN_USER:UPDATE */
  USER_EDIT: "system:user:edit",
  /** 删除用户 → ADMIN_USER:DELETE */
  USER_DELETE: "system:user:delete",
  /** 启用/禁用（toggle status）→ ADMIN_USER:ENABLE / DISABLE */
  USER_ENABLE: "system:user:enable",
  /** 重置密码（改己豁免）→ ADMIN_USER:RESET_PASSWORD */
  USER_RESET_PWD: "system:user:reset-pwd",

  // ===== 功能角色分配（C 区）—— 乙层 ROLE:MANAGE =====
  USER_ROLE_ASSIGN: "system:user:role:assign",

  // ===== 岗位（D 区，岗位 = 特殊组织 orgType=2）—— 乙层仍是 ADMIN_ORG =====
  POSITION_VIEW: "system:org:position:view",
  /** 新增岗位 → ADMIN_ORG:CREATE（岗位组织实例） */
  POSITION_ADD: "system:org:position:add",
  /** 编辑岗位 → ADMIN_ORG:UPDATE */
  POSITION_EDIT: "system:org:position:edit",
  /** 删除岗位 → ADMIN_ORG:DELETE */
  POSITION_DELETE: "system:org:position:delete",
  /**
   * 挂载/卸载岗位用户 → ADMIN_ORG:UPDATE（**乙层等价于 ORG_MEMBER**，仅 UI 命名拆分）
   */
  POSITION_ASSIGN: "system:org:position:assign"
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
