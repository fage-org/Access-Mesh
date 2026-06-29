/**
 * 「角色管理」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `Object.values(ROLE_MANAGE_PERMS)` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(ROLE_MANAGE_PERMS.XXX)`
 *
 * ## 权限锚点
 * 资源类型 `ROLE`（permission-center 乙层模型），操作码对齐 OperationCodeConstants。
 * - 角色实例级 CRUD：`ROLE:VIEW / CREATE / UPDATE / DELETE`
 * - 角色配权/授予（本页跳转 4.1 权限授予页，门禁 ROLE:MANAGE）：
 *   `ROLE:MANAGE` —— 与「组织与用户」页功能角色分配（USER_ROLE_ASSIGN）同锚点同操作码，
 *   形成门禁一致性（org-user-permission-contract.md §5 备注³）。
 *
 * ## 范围限定
 * 本页仅消费功能角色（BASIC_ROLE / GROUP_ROLE / PERSONAL）；
 * ORG / POSITION 由组织同步自动生成，本页只读展示、不可手工 CRUD。
 *
 * 详见 `docs/design/frontend/role-manage.md` §权限接线。
 */
export const ROLE_MANAGE_PERMS = {
  // ===== 角色实例 CRUD —— ROLE 资源类型 =====
  /** 查看角色树/列表 */
  ROLE_VIEW: "ROLE:VIEW",
  /** 创建功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL） */
  ROLE_ADD: "ROLE:CREATE",
  /** 编辑角色（名称/状态/排序/扩展） */
  ROLE_EDIT: "ROLE:UPDATE",
  /** 删除角色 */
  ROLE_DELETE: "ROLE:DELETE",
  /**
   * 配权/授予（跳转 4.1 权限授予页）
   *
   * ROLE:MANAGE 是角色实例级配权门禁，与组织页功能角色分配同源；
   * 角色本身的定义/CRUD 不复用此码。
   */
  ROLE_GRANT: "ROLE:MANAGE"
} as const;

export type RoleManagePermKey = keyof typeof ROLE_MANAGE_PERMS;
export type RoleManagePermValue = (typeof ROLE_MANAGE_PERMS)[RoleManagePermKey];

/**
 * 全部 perm 串清单（派生），用于路由 `meta.auths`。
 * 派生而非手抄，确保新增/重命名 perm 串时单点修改。
 */
export const ROLE_MANAGE_PERM_LIST: ReadonlyArray<RoleManagePermValue> =
  Object.values(ROLE_MANAGE_PERMS);

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const ROLE_MANAGE_VIEW_PERMS: ReadonlyArray<RoleManagePermValue> = [
  ROLE_MANAGE_PERMS.ROLE_VIEW
];
