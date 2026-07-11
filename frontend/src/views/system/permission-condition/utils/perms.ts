/**
 * 「权限条件」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `CONDITION_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(CONDITION_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 ConditionAppServiceImpl）
 *
 * 资源类型 CONDITION，写权限为 CREATE/UPDATE/DELETE **三档独立**（非 CREATE+MANAGE）：
 * - `CONDITION:VIEW` - 查看条件列表（前端路由门控；后端 list/detail 未见 VIEW 校验，🔧 种子缺失登记 T-PERM-029）
 * - `CONDITION:CREATE` - 创建条件（后端 createCondition 校验 CONDITION:CREATE）
 * - `CONDITION:UPDATE` - 编辑条件（后端 updateCondition 校验 CONDITION:UPDATE）
 * - `CONDITION:DELETE` - 删除条件（后端 deleteCondition/deleteConditionsByIds 校验 CONDITION:DELETE）
 *
 * 即：与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同，CONDITION 是 CREATE/UPDATE/DELETE 三档。
 * sec（安全管理员）负责条件定义，拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 只读 VIEW。
 *
 * 详见 `docs/design/frontend/permission-condition.md` §权限接线。
 */
export const CONDITION_PERMS = {
  /** 查看条件列表（资源类型 CONDITION） */
  CONDITION_VIEW: "CONDITION:VIEW",
  /** 创建条件 */
  CONDITION_ADD: "CONDITION:CREATE",
  /** 编辑条件 -- 对齐后端 CONDITION:UPDATE（非 MANAGE） */
  CONDITION_EDIT: "CONDITION:UPDATE",
  /** 删除条件 -- 对齐后端 CONDITION:DELETE */
  CONDITION_DELETE: "CONDITION:DELETE"
} as const;

export type ConditionPermKey = keyof typeof CONDITION_PERMS;
export type ConditionPermValue = (typeof CONDITION_PERMS)[ConditionPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 */
export const CONDITION_PERM_LIST: ReadonlyArray<ConditionPermValue> =
  Array.from(new Set(Object.values(CONDITION_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const CONDITION_VIEW_PERMS: ReadonlyArray<ConditionPermValue> = [
  CONDITION_PERMS.CONDITION_VIEW
];
