/**
 * 「资源与操作定义」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `RESOURCE_OPERATION_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(RESOURCE_OPERATION_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 ResourceManageAppService / OperationAppService）
 *
 * 本页涉及**两个资源类型**的门禁：
 *
 * - `RESOURCE:VIEW` / `RESOURCE:CREATE` / `RESOURCE:MANAGE`（资源类型 RESOURCE）
 *   - 资源树/列表查看 -> `RESOURCE:VIEW`（类型级，T-PERM-028 已实现；bootstrap 固定图已持——VIEW 原有，CREATE/MANAGE 补于 T-FE-017）
 *   - 创建资源 -> `RESOURCE:CREATE`（后端 createResource/batchCreateResources 校验 RESOURCE:CREATE）
 *   - 编辑/移动/删除 -> `RESOURCE:MANAGE`（后端 updateResource/moveResource/deleteResources 校验 RESOURCE:MANAGE）
 *
 * - `OPERATION:VIEW` / `OPERATION:CREATE` / `OPERATION:MANAGE`（资源类型 OPERATION）
 *   - 操作列表查看 -> `OPERATION:VIEW`（类型级，T-PERM-028 已实现；bootstrap 固定图已持——VIEW 原有，CREATE/MANAGE 补于 T-FE-017）
 *   - 创建操作 -> `OPERATION:CREATE`（后端 createOperation 校验 OPERATION:CREATE）
 *   - 编辑/删除 -> `OPERATION:MANAGE`（后端 updateOperation/deleteOperations 校验 OPERATION:MANAGE）
 *
 * 即：资源与操作各自独立资源类型，VIEW/CREATE/MANAGE 三档门禁。sec（安全管理员）负责权限定义，
 * 拥有 RESOURCE/OPERATION 的 CREATE+MANAGE；admin 全权；hr/auditor 只读 VIEW。
 *
 * 详见 `docs/design/frontend/resource-operation.md` §权限接线。
 */
export const RESOURCE_OPERATION_PERMS = {
  /** 查看资源树/列表（资源类型 RESOURCE） */
  RESOURCE_VIEW: "RESOURCE:VIEW",
  /** 创建资源 */
  RESOURCE_ADD: "RESOURCE:CREATE",
  /** 编辑/移动/删除资源 -- 对齐后端 RESOURCE:MANAGE */
  RESOURCE_EDIT: "RESOURCE:MANAGE",
  RESOURCE_DELETE: "RESOURCE:MANAGE",
  RESOURCE_MOVE: "RESOURCE:MANAGE",
  /** 查看操作权限列表（资源类型 OPERATION） */
  OPERATION_VIEW: "OPERATION:VIEW",
  /** 创建操作权限 */
  OPERATION_ADD: "OPERATION:CREATE",
  /** 编辑/删除操作权限 -- 对齐后端 OPERATION:MANAGE */
  OPERATION_EDIT: "OPERATION:MANAGE",
  OPERATION_DELETE: "OPERATION:MANAGE"
} as const;

export type ResourceOperationPermKey = keyof typeof RESOURCE_OPERATION_PERMS;
export type ResourceOperationPermValue =
  (typeof RESOURCE_OPERATION_PERMS)[ResourceOperationPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 *
 * RESOURCE_EDIT/DELETE/MOVE 均为 `RESOURCE:MANAGE`，OPERATION_EDIT/DELETE 均为 `OPERATION:MANAGE`，
 * Object.values 会有重复值，用 Set 去重确保 `meta.auths` 无冗余条目。
 */
export const RESOURCE_OPERATION_PERM_LIST: ReadonlyArray<ResourceOperationPermValue> =
  Array.from(new Set(Object.values(RESOURCE_OPERATION_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const RESOURCE_OPERATION_VIEW_PERMS: ReadonlyArray<ResourceOperationPermValue> =
  [
    RESOURCE_OPERATION_PERMS.RESOURCE_VIEW,
    RESOURCE_OPERATION_PERMS.OPERATION_VIEW
  ];
