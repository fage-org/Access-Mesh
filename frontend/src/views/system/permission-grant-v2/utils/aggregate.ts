/**
 * V2 单元格聚合摘要（T-FE-030）。
 *
 * 输入：同 cell 的 draft 变体列表 + baseline 变体列表
 * 输出：CellSummary（SummaryEffective + SummaryDraftChange + variants）
 *
 * 聚合规则（state-model §7.1）：
 * - 含无条件分支（conditionCode=null）-> DIRECT
 * - 仅有条件分支 -> CONDITIONAL
 * - 无分支 -> UNAUTHORIZED
 * - 全分支待移除（draft 空 + baseline 非空）-> UNAUTHORIZED + draftChange=REMOVE
 *   （PENDING_REMOVE 是底层变体状态，非 SummaryEffective，不得作为有效态输出）
 * - 草稿副状态优先级：ADD > REMOVE > MODIFY
 *
 * aggregate 只产 DIRECT/CONDITIONAL/UNAUTHORIZED；
 * ALL_COVERED（正交标记）/INHERITED/DERIVED（依赖 T-PERM-034）不在聚合范围。
 */
import type {
  GrantVariantId,
  V2DraftPermission,
  SummaryEffective,
  SummaryDraftChange,
  CellSummary
} from "./v2-types";
import { normalizeConditionCode } from "./grant-variant";

/**
 * 聚合单元格摘要。
 * @param draftVariants 该 cell 的 draft 变体（replay 投影后，remove 已删）
 * @param baselineVariants 该 cell 的 baseline 变体（用于判定 REMOVE/MODIFY）
 */
export function aggregateCell(
  draftVariants: V2DraftPermission[],
  baselineVariants: V2DraftPermission[]
): CellSummary {
  const baselineIds = new Set<GrantVariantId>(
    baselineVariants.map(v => v.variantId)
  );

  // effective
  let effective: SummaryEffective;
  if (draftVariants.length === 0) {
    effective = "UNAUTHORIZED";
  } else if (
    draftVariants.some(v => normalizeConditionCode(v.conditionCode) === null)
  ) {
    effective = "DIRECT";
  } else {
    effective = "CONDITIONAL";
  }

  // draftChange：独立计算 hasAdd/hasRemove/hasModify，优先级 ADD > REMOVE > MODIFY
  // §7.1：任一 baseline 变体缺失即 REMOVE（含全分支待移除与部分移除）
  const draftIds = new Set(draftVariants.map(v => v.variantId));
  const baselineMap = new Map(
    baselineVariants.map(v => [v.variantId, v] as const)
  );
  const hasAdd = draftVariants.some(v => !baselineIds.has(v.variantId));
  const hasRemove = baselineVariants.some(v => !draftIds.has(v.variantId));
  const hasModify = draftVariants.some(v => {
    const b = baselineMap.get(v.variantId);
    return (
      b &&
      (normalizeConditionCode(b.conditionCode) !==
        normalizeConditionCode(v.conditionCode) ||
        b.canGrant !== v.canGrant)
    );
  });
  let draftChange: SummaryDraftChange = null;
  if (hasAdd) draftChange = "ADD";
  else if (hasRemove) draftChange = "REMOVE";
  else if (hasModify) draftChange = "MODIFY";

  return { effective, draftChange, variants: draftVariants };
}
