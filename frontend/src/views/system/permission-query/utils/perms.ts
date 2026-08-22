/**
 * 「权限排查」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `PERMISSION_QUERY_PERM_LIST` 派生
 * - 页面 `hasPerms`：`hasPerms(PERMISSION_QUERY_PERMS.QUERY_VIEW)`
 *
 * ## 权限锚点（临时口径，🔧 T-PERM-033 定稿）
 * Phase 1 mock 阶段复用 `SYSTEM_CONFIG:VIEW`（与操作日志/变更日志同源临时口径）。
 *
 * 后端门禁现状（核实 access-service）：
 * - explain：SYSTEM_CONFIG:VIEW（PermissionViewAppServiceImpl:665）
 * - effective-permissions：目标实例 USER:VIEW / ROLE:VIEW（PermissionViewAppServiceImpl:134/153）
 * - query-resources/query-scopes：运行时接口，无排查门禁
 *
 * 前端单独配 SYSTEM_CONFIG:VIEW 不能形成安全闭环（effective-permissions 还需目标实例 VIEW）。
 * T-PERM-033 完成后切换为独立 `PERMISSION_QUERY:VIEW` 全链路：
 * - 新增资源类型 ResourceTypeCode.PERMISSION_QUERY
 * - 类型/操作种子 + 默认角色授权
 * - 权限码下发白名单（UserMenuQueryServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES，
 *   当前不含 PERMISSION_QUERY）
 * - 统一门禁（聚合层已取消——T-ACCESS-012 决策；方案 A: PERMISSION_QUERY:VIEW 全租户排查；
 *   方案 B: PERMISSION_QUERY:VIEW + 被查目标 USER:VIEW/ROLE:VIEW，T-PERM-033 定稿）
 *
 * 切换时仅需改本常量值，路由/组件 hasPerms 调用不变。
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
  /** 权限排查查询 -- 临时复用 SYSTEM_CONFIG:VIEW，T-PERM-033 后切换 PERMISSION_QUERY:VIEW */
  QUERY_VIEW: "SYSTEM_CONFIG:VIEW"
} as const;

export type PermissionQueryPermKey = keyof typeof PERMISSION_QUERY_PERMS;
export type PermissionQueryPermValue =
  (typeof PERMISSION_QUERY_PERMS)[PermissionQueryPermKey];

/** 全部 perm 串清单（派生 + 去重），用于路由 meta.auths */
export const PERMISSION_QUERY_PERM_LIST: ReadonlyArray<PermissionQueryPermValue> =
  Array.from(new Set(Object.values(PERMISSION_QUERY_PERMS)));

/** 仅查看类 perm 串（mock 角色矩阵最小集合）。
 * 本页复用 SYSTEM_CONFIG:VIEW，已在前页（system-config）矩阵中分配，mock/login.ts 不再新增。 */
export const PERMISSION_QUERY_VIEW_PERMS: ReadonlyArray<PermissionQueryPermValue> =
  [PERMISSION_QUERY_PERMS.QUERY_VIEW];
