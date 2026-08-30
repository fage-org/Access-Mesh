/**
 * 「权限条件」页按钮权限码（perm 串）目录 -- 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `CONDITION_PERM_LIST` 派生
 *   （🔧 T-FE-040 v3.1 S5：条件查看全租户开放，路由不再做读取门禁，auths 已移除）
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 *   （🔧 T-FE-040 v3.1 S5：VIEW 列移除，仅写权限登记）
 * - 各组件 v-if/computed：直接 `hasPerms(CONDITION_PERMS.XXX)`
 *
 * ## 权限锚点（对齐后端 ConditionAppServiceImpl）
 *
 * 资源类型 CONDITION，写权限为 CREATE/UPDATE/DELETE **三档独立**（非 CREATE+MANAGE）：
 * - `CONDITION:VIEW` - ~~查看条件列表~~（**2026-08-08 产品确认移除读取门禁**：
 *   条件规则非敏感、全租户开放，无读取门禁；列表始终可读。本串保留仅对齐后端枚举，
 *   不再参与路由门控与角色矩阵登记）
 * - `CONDITION:CREATE` - 创建条件（后端 createCondition 校验 CONDITION:CREATE）
 * - `CONDITION:UPDATE` - 编辑条件（后端 updateCondition 校验 CONDITION:UPDATE）
 * - `CONDITION:DELETE` - 删除条件（后端 deleteConditionsByCodes 校验 CONDITION:DELETE）
 *
 * 即：与 RESOURCE/OPERATION 的 CREATE+MANAGE 两档不同，CONDITION 是 CREATE/UPDATE/DELETE 三档。
 * sec（安全管理员）负责条件定义，拥有 CREATE+UPDATE+DELETE；admin 全权；hr/auditor 无写权限
 * （列表读取全租户开放，不再由 VIEW 列控制）。
 *
 * 详见 `docs/design/frontend/permission-condition.md` §权限接线。
 */
export const CONDITION_PERMS = {
  /** 查看条件列表（资源类型 CONDITION；🔧 2026-08-08 起全租户开放，保留仅对齐后端枚举） */
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
 * 写权限串清单（派生 + 去重）。
 * 🔧 T-FE-040 v3.1（S5）：不含 CONDITION:VIEW（查看全租户开放，不进入角色矩阵）。
 */
export const CONDITION_PERM_LIST: ReadonlyArray<ConditionPermValue> =
  Array.from(new Set(Object.values(CONDITION_PERMS))).filter(
    p => p !== CONDITION_PERMS.CONDITION_VIEW
  );
