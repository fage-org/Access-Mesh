/**
 * 「角色管理」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `ROLE_MANAGE_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(ROLE_MANAGE_PERMS.XXX)`
 *
 * ## 权限锚点（B1：前端操作码口径对齐后端）
 * 资源类型 `ROLE`（permission-center 乙层模型），操作码对齐 OperationCodeConstants。
 * - `ROLE:VIEW` —— 查看（路由可达 + 树可见）。
 * - `ROLE:CREATE` —— 创建。
 * - `ROLE:MANAGE` —— **编辑 / 启停 / 删除 / 移动 / 配权** 统一口径。
 * - `ROLE:ASSIGN` —— 分组角色**添加**额外基本角色（对齐后端 addGroupRoleExtraRole:104）。
 * - `ROLE:REVOKE` —— 分组角色**移除**额外基本角色（对齐后端 removeGroupRoleExtraRole:166）。
 *
 * 后端 `RoleManageAppServiceImpl` 的 updateRole(:150)/moveRole(:176)/deleteRoles(:196)
 * 均以 `ROLE:MANAGE` 做门禁，无独立的 UPDATE/DELETE/MOVE 操作码。前端原用 `ROLE:UPDATE`/
 * `ROLE:DELETE` 与后端不一致（评审 P2），现统一为 `ROLE:MANAGE`（B1）。
 * 配权亦用 `ROLE:MANAGE`，与「组织与用户」页功能角色分配（USER_ROLE_ASSIGN）同锚点同操作码，
 * 形成门禁一致性（org-user-permission-contract.md §5 备注³）。
 *
 * **额外角色 add/remove 独立门禁（评审 P1-额外角色）**：后端 `GroupRoleAppServiceImpl` 的
 * add/removeExtraRole 分别校验 `ASSIGN`/`REVOKE`（授予/回收分离，比 MANAGE 更敏感，与组织页
 * USER_ROLE_ASSIGN/REVOKE 同口径）。前端原两按钮都用 `canEdit`(MANAGE) 会 403 或看不到按钮，
 * 现补 `ROLE:ASSIGN`/`ROLE:REVOKE` 分别门控。
 *
 * ## 范围限定
 * 本页仅消费功能角色（BASIC_ROLE / GROUP_ROLE）；
 * ORG / POSITION / PERSONAL 由外部同步自动生成，本页不展示、不可手工 CRUD。
 *
 * 详见 `docs/design/frontend/role-manage.md` §权限接线。
 */
export const ROLE_MANAGE_PERMS = {
  /** 查看角色树/列表 */
  ROLE_VIEW: "ROLE:VIEW",
  /** 创建功能角色（BASIC_ROLE/GROUP_ROLE） */
  ROLE_ADD: "ROLE:CREATE",
  /** 编辑角色（名称/状态/排序/扩展）—— 对齐后端 ROLE:MANAGE（B1） */
  ROLE_EDIT: "ROLE:MANAGE",
  /** 删除角色 —— 对齐后端 ROLE:MANAGE（B1） */
  ROLE_DELETE: "ROLE:MANAGE",
  /** 配权/授予（跳转 4.1 权限授予页）—— ROLE:MANAGE */
  ROLE_GRANT: "ROLE:MANAGE",
  /** 分组角色添加额外基本角色 —— 对齐后端 ROLE:ASSIGN */
  ROLE_ASSIGN: "ROLE:ASSIGN",
  /** 分组角色移除额外基本角色 —— 对齐后端 ROLE:REVOKE */
  ROLE_REVOKE: "ROLE:REVOKE"
} as const;

export type RoleManagePermKey = keyof typeof ROLE_MANAGE_PERMS;
export type RoleManagePermValue = (typeof ROLE_MANAGE_PERMS)[RoleManagePermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 *
 * B1 后 EDIT/DELETE/GRANT 均为 `ROLE:MANAGE`，Object.values 会有重复值，
 * 用 Set 去重确保 `meta.auths` 无冗余条目。
 */
export const ROLE_MANAGE_PERM_LIST: ReadonlyArray<RoleManagePermValue> =
  Array.from(new Set(Object.values(ROLE_MANAGE_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const ROLE_MANAGE_VIEW_PERMS: ReadonlyArray<RoleManagePermValue> = [
  ROLE_MANAGE_PERMS.ROLE_VIEW
];
