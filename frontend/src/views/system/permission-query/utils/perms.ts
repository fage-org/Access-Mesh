/**
 * 「权限排查」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `PERMISSION_QUERY_PERM_LIST` 派生
 * - 页面 `hasPerms`：`hook.ts` 的 canQuery = USER_VIEW 或 ROLE_VIEW 任一命中
 *
 * ## 权限锚点（T-PERM-033 设计定案，2026-08-29）
 * 不引入独立排查权限码（原预案 `PERMISSION_QUERY:VIEW` 否决——权限码结构为「资源:操作」，
 * PERMISSION_QUERY 本身是操作描述而非资源；排查能力随目标数据可见性走）：
 *
 * - API 门禁（explain / effective-permissions / recent-changes）= 被查目标实例
 *   `USER:VIEW` / `ROLE:VIEW`（查谁就要对谁有 VIEW，与用户/角色管理页同源语义）
 * - query-scopes 维持运行时接口语义，无排查门禁（契约 §6.7 登记）
 * - 页面级 UI 门 = 任一目标类型可查（USER:VIEW 或 ROLE:VIEW），API 层仍按目标实例逐一校验
 *
 * 四账号 mock 矩阵经 ORG_USER_VIEW_PERMS（含 USER:VIEW）与
 * ROLE_MANAGE_VIEW_PERMS（含 ROLE:VIEW）已持有，无需增配。
 *
 * ## 安全闭环（路由框架不消费 meta.auths 隐藏菜单）
 * 页面入口必须：
 * - 立即 hasPerms 检查
 * - 无权时整页无权状态（阻止所有请求）
 * - hook/API 层短路（hasPerms 失败不发请求）
 *
 * 详见 `docs/design/frontend/permission-query.md` §权限接线。
 */
export const PERMISSION_QUERY_PERMS = {
  /** 查询用户目标（Tab1/Tab3 USER、Tab2 主体）所需的类型级 VIEW */
  USER_VIEW: "USER:VIEW",
  /** 查询角色目标（Tab1/Tab3 ROLE）所需的类型级 VIEW */
  ROLE_VIEW: "ROLE:VIEW"
} as const;

export type PermissionQueryPermKey = keyof typeof PERMISSION_QUERY_PERMS;
export type PermissionQueryPermValue =
  (typeof PERMISSION_QUERY_PERMS)[PermissionQueryPermKey];

/** 全部 perm 串清单（派生 + 去重），用于路由 meta.auths（仅声明，不被路由框架消费） */
export const PERMISSION_QUERY_PERM_LIST: ReadonlyArray<PermissionQueryPermValue> =
  Array.from(new Set(Object.values(PERMISSION_QUERY_PERMS)));
