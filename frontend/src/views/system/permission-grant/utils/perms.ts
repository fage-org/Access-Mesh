/**
 * 「权限授予」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * 引用关系：
 * - 路由 meta.auths：router/modules/system.ts 通过 PERMISSION_GRANT_PERM_LIST 派生
 * - 页面 hasPerms：hasPerms(PERMISSION_GRANT_PERMS.ROLE_VIEW / ROLE_MANAGE)
 *
 * 权限口径（核实后端，非临时口径，决策点 4 调整）：
 * - ROLE:VIEW 控制页面查看（进入页面、加载角色权限事实）
 * - ROLE:MANAGE 控制编辑和保存（单元格操作、附加设置、三段式 save）
 * - 后端目标角色实例校验仍是最终依据（hasPerms 仅粗粒度门控）
 * - T-PERM-034 补能力/拒绝语义（grantableByOperator/denyReason），不另换权限码
 *
 * 安全闭环（路由框架不消费 meta.auths 隐藏菜单）：
 * - 无 ROLE:VIEW：整页无权状态，不发请求
 * - 有 VIEW 无 MANAGE：三栏可浏览，中栏只读，右栏无保存动作
 * - CONDITION:VIEW 查看条件候选；CONDITION:CREATE 内联新建条件（附加设置弹窗）
 *
 * 详见 docs/design/frontend/permission-grant.md §10。
 */
export const PERMISSION_GRANT_PERMS = {
  ROLE_VIEW: "ROLE:VIEW",
  ROLE_MANAGE: "ROLE:MANAGE",
  CONDITION_VIEW: "CONDITION:VIEW",
  CONDITION_CREATE: "CONDITION:CREATE"
} as const;

export type PermissionGrantPermKey = keyof typeof PERMISSION_GRANT_PERMS;
export type PermissionGrantPermValue =
  (typeof PERMISSION_GRANT_PERMS)[PermissionGrantPermKey];

/** 全部 perm 串清单（派生 + 去重），用于路由 meta.auths */
export const PERMISSION_GRANT_PERM_LIST: ReadonlyArray<PermissionGrantPermValue> =
  Array.from(new Set(Object.values(PERMISSION_GRANT_PERMS)));

/** 仅查看类 perm 串（mock 角色矩阵最小集合）。
 * 本页复用 ROLE:VIEW / ROLE:MANAGE / CONDITION:VIEW / CONDITION:CREATE，
 * 已在前页（role-manage / permission-condition）矩阵中分配，mock/login.ts 不再新增。 */
export const PERMISSION_GRANT_VIEW_PERMS: ReadonlyArray<PermissionGrantPermValue> =
  [PERMISSION_GRANT_PERMS.ROLE_VIEW];
