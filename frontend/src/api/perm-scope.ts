/**
 * 权限查询 API 类型定义（对齐 api-contract.md §6.6-6.7）。
 *
 * T-PERM-015：前端 L2 数据权限类型落地。
 * 本文件为 query-scopes / query-resources 集成场景提供类型基础（归并后无聚合层，
 * 页面直连 /api/perm/* 契约端点——T-ACCESS-012 决策）。
 *
 * 使用场景：
 * - 消费 /api/perm/auth/query-scopes 响应时，前端可按 ScopeMode 四态分支渲染
 * - 前端排查页（permission-view）消费 effective-permissions 时，可区分 INSTANCE/ALL
 *
 * @see docs/design/permission-center/api-contract.md §6.6-6.7
 * @see frontend/src/utils/scope-mode.ts ScopeMode 枚举定义
 */
import type { ScopeMode } from "@/utils/scope-mode";

// ========== 公共窄类型 ==========

/**
 * 权限事实范围模式（对齐 api-contract §6.6/§6.8/§6.9）。
 *
 * 授权请求侧、权限事实列表项（query-resources / permission-view / interface-snapshot）
 * 只允许 INSTANCE | ALL 两态；完整四态 DENIED/INSTANCE/ALL/EMPTY 仅用于
 * query-scopes.scopeGroups[]（见 ScopeGroup.scopeMode）。
 *
 * @see docs/design/permission-center/api-contract.md §6.6
 */
export type ScopeFactMode = typeof ScopeMode.INSTANCE | typeof ScopeMode.ALL;

// ========== query-resources 响应 ==========

/** query-resources 资源条目（对齐 api-contract §6.6） */
export type ResourceEntry = {
  /** 资源类型编码 */
  resourceTypeCode: string;
  /** 资源编码，scopeMode=ALL 时为 null */
  resourceCode: string | null;
  /** 编码类型，scopeMode=ALL 时为 null */
  codeType: string | null;
  /** 资源名称，scopeMode=ALL 时为 null */
  resourceName: string | null;
  /** 是否可授予他人 */
  canGrant: boolean;
  /** 范围模式：INSTANCE=具体实例，ALL=全量范围（§6.6 仅允许此两态） */
  scopeMode: ScopeFactMode;
  /** 操作权限列表 */
  operations: Array<string>;
  /** 匹配的角色ID列表 */
  matchedRoleIds: Array<number>;
  /** 匹配的权限ID列表 */
  matchedPermissionIds: Array<number>;
  /** 授权来源列表 */
  grantSources: Array<string>;
};

/** query-resources 响应体 */
export type QueryResourcesResp = {
  items: Array<ResourceEntry>;
  cacheTtlSeconds: number;
};

// ========== query-scopes 响应 ==========

/** query-scopes 范围条目 */
export type ScopeItem = {
  resourceCode: string;
  codeType: string;
  resourceName: string | null;
};

/** query-scopes 范围分组（对齐 api-contract §6.7） */
export type ScopeGroup = {
  /** 资源类型编码 */
  resourceTypeCode: string;
  /** 操作码 */
  operationCode: string;
  /** 范围模式：DENIED/INSTANCE/ALL/EMPTY 四态 */
  scopeMode: ScopeMode;
  /** 有效范围实例列表，scopeMode=INSTANCE 时非空 */
  items: Array<ScopeItem>;
  /** 匹配的角色ID列表 */
  matchedRoleIds: Array<number>;
  /** 匹配的权限ID列表 */
  matchedPermissionIds: Array<number>;
  /** 依赖的父权限ID列表 */
  dependOnPermissionIds: Array<number>;
};

/** query-scopes 响应体 */
export type QueryScopesResp = {
  /** 拒绝原因，null 表示正常 */
  reason: string | null;
  /** 匹配的父操作码 */
  matchedParentOperations: Array<string>;
  /** 父权限ID列表 */
  parentPermissionIds: Array<number>;
  /** 范围分组列表 */
  scopeGroups: Array<ScopeGroup>;
  cacheTtlSeconds: number;
};
