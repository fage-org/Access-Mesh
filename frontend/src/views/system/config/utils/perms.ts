/**
 * 「系统配置」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `SYSTEM_CONFIG_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(SYSTEM_CONFIG_PERMS.XXX)`
 *
 * ## 权限锚点
 * 资源类型 `SYSTEM_CONFIG`（access-service ResourceTypeCode.SYSTEM_CONFIG），
 * 操作码对齐 OperationCodeConstants（后端 SystemConfigAppServiceImpl）。
 * - `SYSTEM_CONFIG:VIEW` —— 列表/详情查看（listSystemConfigs / getSystemConfig 校验 VIEW）。
 * - `SYSTEM_CONFIG:MANAGE` —— 保存配置（upsertSystemConfig 校验 MANAGE）。
 *
 * 后端仅 list/detail/save 三个端点，save 为 upsert 幂等语义，无独立 CREATE/UPDATE/DELETE 操作码。
 * 前端「新增配置」与「编辑配置」统一映射到 `SYSTEM_CONFIG:MANAGE`（与类型定义页 EDIT/DELETE→MANAGE 同口径，
 * 但更简——系统配置只有一个写操作 save）。
 *
 * 详见 `docs/design/frontend/system-config.md` §权限接线。
 */
export const SYSTEM_CONFIG_PERMS = {
  /** 查看系统配置列表/详情 */
  CONFIG_VIEW: "SYSTEM_CONFIG:VIEW",
  /** 保存系统配置（新建/编辑统一 upsert）—— 对齐后端 SYSTEM_CONFIG:MANAGE */
  CONFIG_SAVE: "SYSTEM_CONFIG:MANAGE"
} as const;

export type SystemConfigPermKey = keyof typeof SYSTEM_CONFIG_PERMS;
export type SystemConfigPermValue =
  (typeof SYSTEM_CONFIG_PERMS)[SystemConfigPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 *
 * 当前 VIEW/SAVE 不同值无重复，仍用 Set 去重保持与同范式（type-def）一致，确保 `meta.auths` 无冗余。
 */
export const SYSTEM_CONFIG_PERM_LIST: ReadonlyArray<SystemConfigPermValue> =
  Array.from(new Set(Object.values(SYSTEM_CONFIG_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const SYSTEM_CONFIG_VIEW_PERMS: ReadonlyArray<SystemConfigPermValue> = [
  SYSTEM_CONFIG_PERMS.CONFIG_VIEW
];
