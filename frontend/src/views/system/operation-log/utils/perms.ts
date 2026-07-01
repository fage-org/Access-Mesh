/**
 * 「操作日志」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `OPERATION_LOG_PERM_LIST` 派生
 * - 各组件 v-if/computed：直接 `hasPerms(OPERATION_LOG_PERMS.LOG_VIEW)`
 *
 * ## 权限锚点（复用，非独立）
 * 后端 `LogQueryAppServiceImpl.listOperationLogs`（及 listChangeLogs/recentChanges）均以
 * `SYSTEM_CONFIG:VIEW` 做门禁（资源类型 ResourceTypeCode.SYSTEM_CONFIG + 操作码 VIEW），
 * **无独立 OPERATION_LOG 资源类型/权限码**。本页 SSOT 独立文件，但 VIEW 值复用 `SYSTEM_CONFIG:VIEW`
 * ——与后端一致。
 *
 * 本页为只读查询页（无 CRUD 写操作），故只有 VIEW 一项，无 SAVE/MANAGE。
 *
 * 🔧 `SYSTEM_CONFIG:VIEW` 复用作为日志查询门禁的审计语义问题登记 T-PERM-025：
 *   当前复用致「有系统配置 VIEW 权限即可查全部操作日志」，审计场景可能需独立 OPERATION_LOG:VIEW。
 *   确认型，非必改——若后端独立，前端仅需改本常量值。
 *
 * 详见 `docs/design/frontend/operation-log.md` §权限接线。
 */
export const OPERATION_LOG_PERMS = {
  /** 查看操作日志列表 —— 复用后端 SYSTEM_CONFIG:VIEW（无独立权限码） */
  LOG_VIEW: "SYSTEM_CONFIG:VIEW"
} as const;

export type OperationLogPermKey = keyof typeof OPERATION_LOG_PERMS;
export type OperationLogPermValue =
  (typeof OPERATION_LOG_PERMS)[OperationLogPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 * 本页仅 VIEW 一项，去重后仍为单元素数组。
 */
export const OPERATION_LOG_PERM_LIST: ReadonlyArray<OperationLogPermValue> =
  Array.from(new Set(Object.values(OPERATION_LOG_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 * 本页复用 SYSTEM_CONFIG:VIEW，已在前页（system-config）矩阵中分配，故 mock/login.ts 不再新增。
 */
export const OPERATION_LOG_VIEW_PERMS: ReadonlyArray<OperationLogPermValue> = [
  OPERATION_LOG_PERMS.LOG_VIEW
];
