/**
 * 「冲突规则」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `CONFLICT_RULE_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(CONFLICT_RULE_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 ConflictRuleAppServiceImpl）
 *
 * 资源类型 CONFLICT_RULE，写权限为 CREATE/UPDATE/DELETE **三档独立**（非 CREATE+MANAGE）：
 * - `CONFLICT_RULE:VIEW` - 查看冲突规则列表（前端路由门控；后端 list/detail 未见 VIEW 校验，🔧 种子缺失登记 T-PERM-030）
 * - `CONFLICT_RULE:CREATE` - 创建冲突规则（后端 createConflictRule 校验 CONFLICT_RULE:CREATE）
 * - `CONFLICT_RULE:UPDATE` - 编辑冲突规则（后端 updateConflictRule 校验 CONFLICT_RULE:UPDATE）
 * - `CONFLICT_RULE:DELETE` - 删除冲突规则（后端 deleteConflictRule/deleteConflictRulesByIds 校验 CONFLICT_RULE:DELETE）
 *
 * 即：与 CONDITION 一样是 CREATE/UPDATE/DELETE 三档。
 * sec（安全管理员）负责冲突规则定义，拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 只读 VIEW。
 *
 * 详见 `docs/design/frontend/conflict-rule.md` §权限接线。
 */
export const CONFLICT_RULE_PERMS = {
  /** 查看冲突规则列表（资源类型 CONFLICT_RULE） */
  CONFLICT_RULE_VIEW: "CONFLICT_RULE:VIEW",
  /** 创建冲突规则 */
  CONFLICT_RULE_ADD: "CONFLICT_RULE:CREATE",
  /** 编辑冲突规则 -- 对齐后端 CONFLICT_RULE:UPDATE（非 MANAGE） */
  CONFLICT_RULE_EDIT: "CONFLICT_RULE:UPDATE",
  /** 删除冲突规则 -- 对齐后端 CONFLICT_RULE:DELETE */
  CONFLICT_RULE_DELETE: "CONFLICT_RULE:DELETE"
} as const;

export type ConflictRulePermKey = keyof typeof CONFLICT_RULE_PERMS;
export type ConflictRulePermValue =
  (typeof CONFLICT_RULE_PERMS)[ConflictRulePermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 */
export const CONFLICT_RULE_PERM_LIST: ReadonlyArray<ConflictRulePermValue> =
  Array.from(new Set(Object.values(CONFLICT_RULE_PERMS)));

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const CONFLICT_RULE_VIEW_PERMS: ReadonlyArray<ConflictRulePermValue> = [
  CONFLICT_RULE_PERMS.CONFLICT_RULE_VIEW
];
