/**
 * 数据范围模式枚举（对齐 perm-common ScopeMode 四态）。
 *
 * T-PERM-009 定义 / T-PERM-015 前端落地。
 * 用途：L2 数据权限运行时查询（query-scopes / query-resources）的 scopeMode 字段。
 *
 * | scopeMode | 含义                                 | 业务方行为                 |
 * |-----------|--------------------------------------|----------------------------|
 * | DENIED    | 无操作权限（合并原 allowed=false）   | 拒绝/403，不发 SQL         |
 * | INSTANCE  | 有权限 + 具体实例授权                | items[] 非空，按 IN 过滤   |
 * | ALL       | 有权限 + 全量授权（原 scopeAll）     | items[] 为空，不加范围过滤 |
 * | EMPTY     | 有权限但无数据（条件/互斥过滤后为空）| 返回空结果，不发 SQL       |
 *
 * 授权请求侧和权限事实列表项只使用 INSTANCE / ALL 两态；
 * query-scopes.scopeGroups[] 使用完整四态。
 *
 * @see docs/design/permission-center/api-contract.md §6.6-6.7
 * @see perm-sdk/perm-common/.../enums/ScopeMode.java
 */
export enum ScopeMode {
  /** 无操作权限 — 业务方应拒绝/403，不发 SQL */
  DENIED = "DENIED",
  /** 实例授权 — items[] 非空，按 resourceCode 加 IN 过滤 */
  INSTANCE = "INSTANCE",
  /** 全量授权 — items[] 为空，不加范围过滤 */
  ALL = "ALL",
  /** 有权限无数据 — 条件/互斥过滤后实例为空，返回空结果 */
  EMPTY = "EMPTY"
}

/** 合法 scopeMode 值集合，用于运行时校验 */
const VALID_SCOPE_MODES: ReadonlySet<string> = new Set<string>(
  Object.values(ScopeMode)
);

/**
 * 规范化 scopeMode：非四态合法值一律降级为 DENIED（fail-close）。
 *
 * 防御场景：后端新增枚举值、序列化错误、网络篡改等导致未知值传入时，
 * `??` 只处理 null/undefined，无法拦截 "UNKNOWN" 等非法字符串——
 * 此时 canAccess 会误判为 true（四个状态判断全 false 但 m !== DENIED）。
 * 降级为 DENIED 后调用方走拒绝分支，安全优先。
 */
function normalizeScopeMode(mode: ScopeMode | null | undefined): ScopeMode {
  if (mode != null && VALID_SCOPE_MODES.has(mode)) return mode;
  return ScopeMode.DENIED;
}

/**
 * scopeMode 分支判断工具集（fail-close）。
 *
 * 典型用法：
 * ```ts
 * const { isDenied, isInstance, isAll, isEmpty, canAccess, hasScope } = useScopeMode(scopeMode);
 * if (isDenied) { /* 显示无权限提示 *\/ }
 * if (isAll)     { /* 不加范围过滤，展示全量数据 *\/ }
 * if (isInstance) { /* 按 items[] 做 IN 过滤 *\/ }
 * if (isEmpty)   { /* 显示空结果提示 *\/ }
 * ```
 *
 * 防御语义：null / undefined / 未知值一律降级为 DENIED，
 * 宁可误拒不可误放。
 */
export function useScopeMode(mode: ScopeMode | null | undefined) {
  const m = normalizeScopeMode(mode);
  return {
    /** 当前 scopeMode 值 */
    scopeMode: m,
    /** 无操作权限 → 应拒绝/403 */
    isDenied: m === ScopeMode.DENIED,
    /** 实例级授权 → 按 items[] 做 IN 过滤 */
    isInstance: m === ScopeMode.INSTANCE,
    /** 全量授权 → 不加范围过滤 */
    isAll: m === ScopeMode.ALL,
    /** 有权限但无数据 → 返回空结果 */
    isEmpty: m === ScopeMode.EMPTY,
    /** 可访问（有操作权限，不论数据范围）: INSTANCE | ALL | EMPTY */
    canAccess: m !== ScopeMode.DENIED,
    /** 有可见数据范围: INSTANCE | ALL */
    hasScope: m === ScopeMode.INSTANCE || m === ScopeMode.ALL
  };
}
