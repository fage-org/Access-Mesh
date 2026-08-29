/**
 * 「权限变更日志」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `PERMISSION_CHANGE_LOG_PERM_LIST` 派生
 * - 各组件 v-if/computed：直接 `hasPerms(PERMISSION_CHANGE_LOG_PERMS.LOG_VIEW)`
 *
 * ## 权限锚点（独立，T-PERM-032 审计分离设计定案）
 * 后端 `LogQueryAppServiceImpl.listChangeLogs/countChangeLogs` 以独立
 * `PERMISSION_CHANGE_LOG:VIEW` 门禁（资源类型 PERMISSION_CHANGE_LOG=31 + VIEW，
 * 对齐操作日志 OPERATION_LOG:VIEW 先例，2026-08-29 五步清单全链路落地）。
 * 边界：排查视图 recent-changes（permission-view）已随 T-PERM-033 切被查目标实例 USER:VIEW/ROLE:VIEW。
 *
 * 本页为只读查询页（无 CRUD 写操作），故只有 VIEW 一项，无 SAVE/MANAGE。
 *
 * 详见 `docs/design/frontend/permission-change-log.md` §权限接线。
 */
export const PERMISSION_CHANGE_LOG_PERMS = {
  /** 查看权限变更日志列表 -- 独立 PERMISSION_CHANGE_LOG:VIEW（T-PERM-032 审计分离） */
  LOG_VIEW: "PERMISSION_CHANGE_LOG:VIEW"
} as const;

export type PermissionChangeLogPermKey =
  keyof typeof PERMISSION_CHANGE_LOG_PERMS;
export type PermissionChangeLogPermValue =
  (typeof PERMISSION_CHANGE_LOG_PERMS)[PermissionChangeLogPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 * 本页仅 VIEW 一项，去重后仍为单元素数组。
 */
export const PERMISSION_CHANGE_LOG_PERM_LIST: ReadonlyArray<PermissionChangeLogPermValue> =
  Array.from(new Set(Object.values(PERMISSION_CHANGE_LOG_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 * T-PERM-032 起独立权限码，mock/login.ts 四账号按旧复用口径全员可查分配（对齐操作日志先例）。
 */
export const PERMISSION_CHANGE_LOG_VIEW_PERMS: ReadonlyArray<PermissionChangeLogPermValue> =
  [PERMISSION_CHANGE_LOG_PERMS.LOG_VIEW];
