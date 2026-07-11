/**
 * 「资源依赖」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `RESOURCE_DEPENDENCY_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(RESOURCE_DEPENDENCY_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 DependencyAppServiceImpl）
 *
 * 资源类型 DEPENDENCY，操作码 CREATE/UPDATE/DELETE/SYNC + VIEW：
 * - `DEPENDENCY:VIEW` - 查看依赖列表（前端路由门控；后端 list/graph/check 未校验 VIEW，🔧 种子缺失登记 T-PERM-031）
 * - `DEPENDENCY:CREATE` - 创建依赖（后端 createDependency 校验 DEPENDENCY:CREATE）
 * - `DEPENDENCY:UPDATE` - 编辑依赖（后端 updateDependency 校验 DEPENDENCY:UPDATE）
 * - `DEPENDENCY:DELETE` - 删除依赖（后端 deleteDependencies 校验 DEPENDENCY:DELETE，validateBatch）
 * - `DEPENDENCY:SYNC` - 批量同步（后端 batchSyncDependencies 校验 DEPENDENCY:SYNC；P0 标 TODO，本页不暴露按钮）
 *
 * 即：CREATE/UPDATE/DELETE 三档独立（非 MANAGE），另含 SYNC（batch-sync 专用）。
 * sec（安全管理员）负责依赖规则定义，拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 只读 VIEW。
 *
 * 详见 `docs/design/frontend/resource-dependency.md` §权限接线。
 */
export const RESOURCE_DEPENDENCY_PERMS = {
  /** 查看资源依赖列表（资源类型 DEPENDENCY） */
  RESOURCE_DEPENDENCY_VIEW: "DEPENDENCY:VIEW",
  /** 创建资源依赖 */
  RESOURCE_DEPENDENCY_ADD: "DEPENDENCY:CREATE",
  /** 编辑资源依赖 -- 对齐后端 DEPENDENCY:UPDATE（非 MANAGE） */
  RESOURCE_DEPENDENCY_EDIT: "DEPENDENCY:UPDATE",
  /** 删除资源依赖 -- 对齐后端 DEPENDENCY:DELETE */
  RESOURCE_DEPENDENCY_DELETE: "DEPENDENCY:DELETE",
  /** 批量同步资源依赖 -- 对齐后端 DEPENDENCY:SYNC（P0 标 TODO，本页不暴露按钮） */
  RESOURCE_DEPENDENCY_SYNC: "DEPENDENCY:SYNC"
} as const;

export type ResourceDependencyPermKey = keyof typeof RESOURCE_DEPENDENCY_PERMS;
export type ResourceDependencyPermValue =
  (typeof RESOURCE_DEPENDENCY_PERMS)[ResourceDependencyPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 */
export const RESOURCE_DEPENDENCY_PERM_LIST: ReadonlyArray<ResourceDependencyPermValue> =
  Array.from(new Set(Object.values(RESOURCE_DEPENDENCY_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const RESOURCE_DEPENDENCY_VIEW_PERMS: ReadonlyArray<ResourceDependencyPermValue> =
  [RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_VIEW];
