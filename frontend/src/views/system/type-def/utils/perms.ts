/**
 * 「类型定义」页按钮权限码（perm 串）目录 —— 单一事实源 (SSOT)。
 *
 * ## 引用关系
 * - 路由 `meta.auths`：`router/modules/system.ts` 通过 `TYPE_DEF_PERM_LIST` 派生
 * - 角色矩阵 mock：`mock/login.ts` 反向 import 本对象拼装 ROLE_PERM_MATRIX
 * - 各组件 v-if/computed：直接 `hasPerms(TYPE_DEF_PERMS.XXX)`
 *
 * ## 权限锚点
 * 资源类型 `TYPE_DEFINITION`（permission-center 乙层模型 ResourceTypeCode.TYPE_DEFINITION），
 * 操作码对齐 OperationCodeConstants（后端 TypeDefinitionAppServiceImpl）。
 * - `TYPE_DEFINITION:VIEW` —— 列表/详情查看（listTypes:133 / getType:109 校验 VIEW）。
 * - `TYPE_DEFINITION:CREATE` —— 创建（createType:71 校验 CREATE）。
 * - `TYPE_DEFINITION:MANAGE` —— 编辑/删除统一口径（updateType:161 / deleteTypesByIds:209 校验 MANAGE）。
 *
 * 后端 update/remove 均以 `TYPE_DEFINITION:MANAGE` 做门禁，无独立 UPDATE/DELETE 操作码，
 * 前端 EDIT/DELETE 统一映射到 `TYPE_DEFINITION:MANAGE`（与角色管理页 ROLE:MANAGE 同口径）。
 *
 * 详见 `docs/design/frontend/type-def.md` §权限接线。
 */
export const TYPE_DEF_PERMS = {
  /** 查看类型定义列表/详情 */
  TYPE_VIEW: "TYPE_DEFINITION:VIEW",
  /** 创建类型定义 */
  TYPE_ADD: "TYPE_DEFINITION:CREATE",
  /** 编辑类型定义 —— 对齐后端 TYPE_DEFINITION:MANAGE */
  TYPE_EDIT: "TYPE_DEFINITION:MANAGE",
  /** 删除类型定义 —— 对齐后端 TYPE_DEFINITION:MANAGE */
  TYPE_DELETE: "TYPE_DEFINITION:MANAGE"
} as const;

export type TypeDefPermKey = keyof typeof TYPE_DEF_PERMS;
export type TypeDefPermValue = (typeof TYPE_DEF_PERMS)[TypeDefPermKey];

/**
 * 全部 perm 串清单（派生 + 去重），用于路由 `meta.auths`。
 *
 * EDIT/DELETE 均为 `TYPE_DEFINITION:MANAGE`，Object.values 会有重复值，
 * 用 Set 去重确保 `meta.auths` 无冗余条目。
 */
export const TYPE_DEF_PERM_LIST: ReadonlyArray<TypeDefPermValue> = Array.from(
  new Set(Object.values(TYPE_DEF_PERMS))
);

/**
 * 仅查看类 perm 串（mock 角色矩阵的最小集合）。
 */
export const TYPE_DEF_VIEW_PERMS: ReadonlyArray<TypeDefPermValue> = [
  TYPE_DEF_PERMS.TYPE_VIEW
];
