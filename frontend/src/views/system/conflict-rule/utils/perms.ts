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
 * 资源类型 CONFLICT_RULE，读 VIEW + 写 CREATE/UPDATE/DELETE **四档独立**（T-PERM-030 收口口径）：
 * - `CONFLICT_RULE:VIEW` - 查看冲突规则（后端读三端点 list/detail/detect 类型级校验，T-PERM-030 补齐）
 * - `CONFLICT_RULE:CREATE` - 创建冲突规则（后端 createConflictRule 校验 CONFLICT_RULE:CREATE）
 * - `CONFLICT_RULE:UPDATE` - 编辑冲突规则（后端 updateConflictRule 校验 CONFLICT_RULE:UPDATE）
 * - `CONFLICT_RULE:DELETE` - 删除冲突规则（后端 deleteConflictRulesByIds 校验 CONFLICT_RULE:DELETE，
 *   单删方法已随 T-PERM-030 删除）
 *
 * 即：读 VIEW + 写三档；类型级门禁（CONFLICT_RULE 无 resource_entity 实例投影，T-PERM-030 口径）。
 * sec（安全管理员）负责冲突规则定义，拥有 VIEW+CREATE+UPDATE+DELETE；admin 全权；hr/auditor 只读 VIEW。
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
