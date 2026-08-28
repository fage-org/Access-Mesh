/**
 * 「操作日志」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `OPERATION_LOG_PERM_LIST` 派生
 * - 各组件 v-if/computed：直接 `hasPerms(OPERATION_LOG_PERMS.LOG_VIEW)`
 *
 * ## 权限锚点（独立，T-PERM-025 审计分离）
 * 后端 `LogQueryAppServiceImpl` 的操作日志查询（list/count/action-options）以独立
 * `OPERATION_LOG:VIEW` 门禁（资源类型 ResourceTypeCode.OPERATION_LOG + 操作码 VIEW，
 * type_value=30 权威 DDL 种子；bootstrap 固定图已授予管理角色）——审计与系统配置查看
 * 权限分离（2026-08-28 设计定案），不再复用 `SYSTEM_CONFIG:VIEW`。
 *
 * 本页为只读查询页（无 CRUD 写操作），故只有 VIEW 一项，无 SAVE/MANAGE。
 *
 * 详见 `docs/design/frontend/operation-log.md` §权限接线。
 */
export const OPERATION_LOG_PERMS = {
  /** 查看操作日志列表 —— 独立 OPERATION_LOG:VIEW（T-PERM-025 审计分离） */
  LOG_VIEW: "OPERATION_LOG:VIEW"
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
 * 仅查看类 perm 串（最小集合）。
 */
export const OPERATION_LOG_VIEW_PERMS: ReadonlyArray<OperationLogPermValue> = [
  OPERATION_LOG_PERMS.LOG_VIEW
];
