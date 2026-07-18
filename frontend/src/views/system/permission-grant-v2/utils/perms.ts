/**
 * 「权限授予 V2」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * 引用关系：
 * - 路由 meta.auths：router/modules/system.ts 通过 PERMISSION_GRANT_V2_PERM_LIST 派生
 * - 页面 hasPerms：hasPerms(PERMISSION_GRANT_V2_PERMS.ROLE_VIEW / ROLE_MANAGE)
 *
 * 权限口径（与旧 permission-grant 页一致，对齐后端）：
 * - ROLE:VIEW 控制页面查看（进入页面、加载角色权限事实）
 * - ROLE:MANAGE 控制编辑和保存（矩阵编辑 T-FE-031+ / 保存 T-FE-034）
 * - 后端目标角色实例校验仍是最终依据（hasPerms 仅粗粒度门控）
 * - T-PERM-034 补能力/拒绝语义（grantableByOperator/denyReason），不另换权限码
 *
 * 安全闭环（路由框架不消费 meta.auths 隐藏菜单，showLink=false）：
 * - 无 ROLE:VIEW：整页无权状态，不发请求
 * - 有 VIEW 无 MANAGE：三栏可浏览，中栏只读，右栏无保存动作
 *
 * 详见 docs/design/frontend/permission-grant-state-model.md §D1（页面门控）/ §D3（编辑能力）。
 */
export const PERMISSION_GRANT_V2_PERMS = {
  ROLE_VIEW: "ROLE:VIEW",
  ROLE_MANAGE: "ROLE:MANAGE"
} as const;

export type PermissionGrantV2PermKey = keyof typeof PERMISSION_GRANT_V2_PERMS;
export type PermissionGrantV2PermValue =
  (typeof PERMISSION_GRANT_V2_PERMS)[PermissionGrantV2PermKey];

/** 全部 perm 串清单（派生 + 去重），用于路由 meta.auths */
export const PERMISSION_GRANT_V2_PERM_LIST: ReadonlyArray<PermissionGrantV2PermValue> =
  Array.from(new Set(Object.values(PERMISSION_GRANT_V2_PERMS)));
