/**
 * 中栏操作权限摘要规范化（T-FE-025）。
 *
 * 设计依据：docs/design/frontend/permission-grant.md §16.3.1 / §16.8 R3 / R6。
 *
 * 关键修正（vs 单一 CellState 一对一映射）：
 * - 摘要拆成「有效主状态 + 草稿副状态」正交两维，例如 ALL 覆盖下移除直接记录
 *   应展示「★ ALL 覆盖」+「－ 移除直接记录」，而非单一「移除」。
 * - allCovered 与 hasBaselineDirectRecord 正交（store 已修正，不被直接记录压平），
 *   支持「★ ALL 覆盖 + ● 有直接记录」共存。
 * - ALL 覆盖时有效权限的条件/canGrant/子权限来自 ALL 来源 ctx（allSourceCtx），
 *   非 INSTANCE 直接记录；INSTANCE 直接事实由 hasBaselineDirectRecord 副标记承载。
 *
 * 类型定义在 @/utils/permission-grant-types（共享，供 PermissionSummaryCell 引用），
 * 本文件保留 normalizer + 排序逻辑（页面侧，调 store.buildMainCellContext 后规范化）。
 *
 * 派生权限（DERIVED/⊕）渲染能力保留，但 normalizer 当前不产生该状态
 * （R1/R7：前端阶段不展示派生，待 T-PERM-034）。
 */
import type { GrantScopeMode, OperationItem } from "@/api/permission-grant";
import type {
  PermissionCellContext,
  SummaryDraftChange,
  SummaryEffective,
  SummaryItem
} from "@/utils/permission-grant-types";

// 类型 re-export（定义在共享模块，供页面与组件统一引用）
export type {
  SummaryEffective,
  SummaryDraftChange,
  SummaryItem
} from "@/utils/permission-grant-types";

// ========== normalizer ==========

export interface NormalizeParams {
  domainCode: string;
  resourceTypeCode: string;
  scopeMode: GrantScopeMode;
  resourceCode: string | null;
  codeType: string | null;
  /** INSTANCE 单元格上下文（直接事实来源） */
  ctx: PermissionCellContext;
  /**
   * ALL 来源上下文（仅 INSTANCE 且 allCovered 时传入）。
   * ALL 覆盖时有效权限的条件/canGrant/子权限来自 ALL 权限，非 INSTANCE 直接记录。
   * ALL scope 或非覆盖时传 null。
   */
  allSourceCtx?: PermissionCellContext | null;
  op: OperationItem;
}

/** PermissionCellContext -> SummaryItem（正交分解） */
export function normalizeSummary(p: NormalizeParams): SummaryItem {
  const { ctx, op } = p;
  // draft 是否持有直接记录（GRANTED/PENDING_ADD/MODIFIED）
  // PENDING_REMOVE 时 draft 已删除直接记录，仅 baseline 保留
  const hasDirectInDraft =
    ctx.state === "GRANTED" ||
    ctx.state === "PENDING_ADD" ||
    ctx.state === "MODIFIED";

  // 有效主状态（R6 优先级：ALL 覆盖 > 直接(含条件) > 派生 > 继承 > 未授权）
  let effective: SummaryEffective;
  if (ctx.allCovered) {
    effective = "ALL_COVERED";
  } else if (hasDirectInDraft && ctx.draft?.conditionCode) {
    effective = "CONDITIONAL";
  } else if (hasDirectInDraft) {
    effective = "DIRECT";
  } else if (!ctx.grantableByOperator) {
    effective = "NOT_GRANTABLE";
  } else {
    effective = "UNAUTHORIZED";
  }

  // 草稿副状态
  let draftChange: SummaryDraftChange = null;
  if (ctx.state === "PENDING_ADD") draftChange = "ADD";
  else if (ctx.state === "PENDING_REMOVE") draftChange = "REMOVE";
  else if (ctx.state === "MODIFIED") draftChange = "MODIFY";

  // 有效权限来源：ALL 覆盖时取 ALL 来源 ctx，否则 INSTANCE ctx
  const effectiveCtx = ctx.allCovered && p.allSourceCtx ? p.allSourceCtx : ctx;

  return {
    operationCode: op.operationCode,
    operationName: op.operationName,
    effective,
    draftChange,
    allCovered: ctx.allCovered,
    hasBaselineDirectRecord: ctx.hasBaselineDirectRecord,
    conditionSummary: effectiveCtx.conditionSummary,
    canGrant: effectiveCtx.draft?.canGrant ?? false,
    childCount: effectiveCtx.childCount,
    grantableByOperator: ctx.grantableByOperator,
    denyReason: ctx.denyReason,
    readonly: ctx.readonly,
    trigger: {
      domainCode: p.domainCode,
      resourceTypeCode: p.resourceTypeCode,
      scopeMode: p.scopeMode,
      resourceCode: p.resourceCode,
      codeType: p.codeType,
      operationCode: op.operationCode,
      // trigger.draft 仍是 INSTANCE 直接记录（open-adjust 预填）
      conditionCode: ctx.draft?.conditionCode ?? null,
      draft: ctx.draft ? { ...ctx.draft } : null
    }
  };
}

// ========== 排序与展示（R3 / R6） ==========

/** 是否有状态（参与展示）：有效非未授权 或 有草稿变更 */
export function hasStatus(item: SummaryItem): boolean {
  return item.effective !== "UNAUTHORIZED" || item.draftChange !== null;
}

/**
 * R3/R6 排序权重：
 * 直接/条件/ALL 覆盖 > 派生 > 草稿变更(有效未授权) > 异常(不可授) > 未授权
 */
function effectiveRank(e: SummaryEffective, dc: SummaryDraftChange): number {
  if (e === "DIRECT" || e === "CONDITIONAL" || e === "ALL_COVERED") return 0;
  if (e === "DERIVED") return 1; // 派生（normalizer 当前不产生，权重预留）
  if (dc !== null) return 2; // 草稿变更（如移除后变未授权）
  if (e === "NOT_GRANTABLE") return 3;
  return 4; // UNAUTHORIZED
}

/** 排序：有状态优先，按 R3/R6 权重，同权重按操作名 */
export function sortSummary(items: SummaryItem[]): SummaryItem[] {
  return [...items].sort((a, b) => {
    const ra = effectiveRank(a.effective, a.draftChange);
    const rb = effectiveRank(b.effective, b.draftChange);
    if (ra !== rb) return ra - rb;
    return a.operationName.localeCompare(b.operationName);
  });
}
