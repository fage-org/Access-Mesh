/**
 * 「权限授予」页（4.1 v3）按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/perm.ts` 通过 `PERMISSION_GRANT_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(PERMISSION_GRANT_PERMS.XXX)`
 *
 * ## 双层门禁（设计文档 permission-grant.md §10，P1-3）
 * - 轨道 1（数据源可见性）：左栏主体数据源沿用入口页既有门禁
 *   （组织树 `ADMIN_ORG:VIEW` / 用户列表 `ADMIN_USER:VIEW` / 岗位 `ADMIN_ORG:VIEW_POSITION`，
 *   admin-service 数据，与 views/system/user/utils/perms.ts 同源；首期组织入口二期、个人入口移除，
 *   角色入口主体树为 permission-center 数据，随 ROLE:VIEW 门控）。
 * - 轨道 2（配权门禁）：矩阵查看/授权动作统一用对目标抽象角色的 `ROLE:VIEW` / `ROLE:MANAGE`
 *   （后端 role-resource-permission/* 均校验目标抽象角色）。
 *
 * perm 串字面量全部为既有资源类型:操作码复用（无新增权限串）：
 * ROLE:VIEW / ROLE:MANAGE（permission-center 乙层锚点）、CONDITION:VIEW（3.2 条件）、
 * RESOURCE:VIEW / OPERATION:VIEW（3.1 资源与操作）。
 */
export const PERMISSION_GRANT_PERMS = {
  // ===== 配权门禁（轨道 2，目标抽象角色） =====
  /** 矩阵查看（两入口通用） */
  ROLE_VIEW: "ROLE:VIEW",
  /** 授权/删除/详情层编辑 */
  ROLE_MANAGE: "ROLE:MANAGE",

  // ===== 只读依赖数据门控 =====
  /** 条件选择（弹窗 Step 4 / 详情层改条件；无权置灰并引导至 3.2） */
  CONDITION_VIEW: "CONDITION:VIEW",
  /** 资源树（矩阵资源行 / 弹窗 Step 3 资源选择） */
  RESOURCE_VIEW: "RESOURCE:VIEW",
  /** 操作列定义 */
  OPERATION_VIEW: "OPERATION:VIEW",

  // ===== 左栏数据源门禁（轨道 1，组织入口二期/个人入口预留） =====
  /** 组织树数据源（组织入口二期） */
  ORG_VIEW: "ADMIN_ORG:VIEW",
  /** 用户列表数据源（个人入口预留，首期移除） */
  USER_VIEW: "ADMIN_USER:VIEW",
  /** 岗位数据源（组织入口二期，岗位为组织子节点） */
  POSITION_VIEW: "ADMIN_ORG:VIEW_POSITION"
} as const;

export type PermissionGrantPermKey = keyof typeof PERMISSION_GRANT_PERMS;
export type PermissionGrantPermValue =
  (typeof PERMISSION_GRANT_PERMS)[PermissionGrantPermKey];

/**
 * 全部 perm 串清单（去重派生），用于路由 `meta.auths`。
 * 派生而非手抄，确保新增/重命名 perm 串时单点修改。
 */
export const PERMISSION_GRANT_PERM_LIST: ReadonlyArray<PermissionGrantPermValue> =
  Array.from(new Set(Object.values(PERMISSION_GRANT_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）：
 * 矩阵只读 + 只读依赖数据可见，无授权/数据源写门禁。
 */
export const PERMISSION_GRANT_VIEW_PERMS: ReadonlyArray<PermissionGrantPermValue> =
  [
    PERMISSION_GRANT_PERMS.ROLE_VIEW,
    PERMISSION_GRANT_PERMS.CONDITION_VIEW,
    PERMISSION_GRANT_PERMS.RESOURCE_VIEW,
    PERMISSION_GRANT_PERMS.OPERATION_VIEW
  ];
