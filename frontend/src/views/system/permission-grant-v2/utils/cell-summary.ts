/**
 * V2 单元格聚合摘要显示映射（T-FE-031）。
 *
 * 聚合摘要（aggregateCell 的 effective + draftChange）+ 正交标记（allCovered / ⊘）
 * -> 色符字三合一标记集合 + R3 排序 + +N popover 内容。
 *
 * 设计依据：docs/design/frontend/permission-grant-state-model.md §6.2
 * - 主符号非唯一含义：颜色 + 符号 + 文字三重冗余（WCAG）
 * - R3 排序权重：直接/条件/ALL覆盖(0) > 操作继承(1) > 派生(2) > 草稿变更(3) > 不可授予(4) > 未授权(5)
 * - DIRECT/CONDITIONAL 可与 ⊘ 共存（已有授权但操作者不可新增）
 * - aggregate 只产 DIRECT/CONDITIONAL/UNAUTHORIZED；ALL_COVERED/INHERITED/DERIVED 为正交/预留
 */
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  GrantVariantId,
  V2DraftPermission,
  SummaryEffective,
  CellSummary,
  PermCellKeyStr
} from "./v2-types";
import { permCellKeyStr, normalizeConditionCode } from "./grant-variant";

export type CellTagType =
  | ""
  | "success"
  | "warning"
  | "danger"
  | "primary"
  | "info";

export interface CellMark {
  key: string;
  symbol: string;
  tagType: CellTagType;
  label: string;
  /** R3 排序权重（小者优先） */
  weight: number;
}

export interface CellDisplay {
  cell: PermCellKey;
  cellKeyStr: PermCellKeyStr;
  summary: CellSummary;
  /** R3 排序后默认可见的标记（前 DEFAULT_VISIBLE_MARK_COUNT 个） */
  marks: CellMark[];
  /** 折叠标记数（超过默认可见数的余量） */
  overflowCount: number;
  /** popover 完整内容行 */
  popoverLines: string[];
  /** INSTANCE 单元被同操作 ALL 覆盖（只看 mainDraft） */
  allCovered: boolean;
  /** baseline 是否有该 cell 直接记录 */
  hasBaselineDirectRecord: boolean;
  /** 操作者授予能力（fail-closed 粗筛） */
  grantableByOperator: boolean;
  denyReason: string | null;
  /** baseline 变体（含被 remove 的，供分支列表展示待移除态） */
  baselineVariants: V2DraftPermission[];
  /** draft 变体（replay 投影后） */
  draftVariants: V2DraftPermission[];
  expanded: boolean;
}

export const DEFAULT_VISIBLE_MARK_COUNT = 3;

/** effective -> 符号/颜色/权重（state-model §6.2） */
const EFFECTIVE_META: Record<
  SummaryEffective,
  { symbol: string; tagType: CellTagType; weight: number }
> = {
  DIRECT: { symbol: "✓", tagType: "success", weight: 0 },
  CONDITIONAL: { symbol: "◑", tagType: "warning", weight: 0 },
  ALL_COVERED: { symbol: "★", tagType: "primary", weight: 0 },
  INHERITED: { symbol: "⊙", tagType: "info", weight: 1 },
  DERIVED: { symbol: "⊕", tagType: "info", weight: 2 },
  UNAUTHORIZED: { symbol: "·", tagType: "", weight: 5 }
};

/** draftChange -> 符号/颜色/权重（正交于 effective，权重 3） */
const DRAFT_META: Record<
  NonNullable<CellSummary["draftChange"]>,
  { symbol: string; tagType: CellTagType; weight: number; label: string }
> = {
  ADD: { symbol: "＋", tagType: "success", weight: 3, label: "待新增" },
  REMOVE: { symbol: "－", tagType: "danger", weight: 3, label: "待移除" },
  MODIFY: { symbol: "✎", tagType: "warning", weight: 3, label: "已修改" }
};

/**
 * 构造单元格显示数据。
 * 纯函数：接收 baseline/draft 索引与能力标记，输出三合一标记 + popover。
 */
export function buildCellDisplay(params: {
  cell: PermCellKey;
  summary: CellSummary;
  mainIndex: Map<PermCellKeyStr, GrantVariantId[]>;
  baselineIndex: Map<PermCellKeyStr, GrantVariantId[]>;
  baselineMap: Map<GrantVariantId, V2DraftPermission>;
  grantableByOperator: boolean;
  denyReason: string | null;
  expanded: boolean;
}): CellDisplay {
  const {
    cell,
    summary,
    mainIndex,
    baselineIndex,
    baselineMap,
    grantableByOperator,
    denyReason,
    expanded
  } = params;
  const cellKeyStr = permCellKeyStr(cell);

  // allCovered：INSTANCE 单元被同操作 ALL 覆盖（只看 mainDraft，不 OR baseline）
  let allCovered = false;
  if (cell.scopeMode === "INSTANCE") {
    const allKey = permCellKeyStr({
      domainCode: cell.domainCode,
      resourceTypeCode: cell.resourceTypeCode,
      scopeMode: "ALL",
      resourceCode: null,
      codeType: null,
      operationCode: cell.operationCode
    });
    allCovered = (mainIndex.get(allKey)?.length ?? 0) > 0;
  }

  const hasBaselineDirectRecord =
    (baselineIndex.get(cellKeyStr)?.length ?? 0) > 0;

  const marks: CellMark[] = [];

  // 1. allCovered 正交标记（权重 0，仅 INSTANCE 被覆盖）
  if (allCovered) {
    marks.push({
      key: "all-covered",
      symbol: "★",
      tagType: "primary",
      label: "ALL 覆盖",
      weight: 0
    });
  }

  // 2. effective 主标记
  const effMeta = EFFECTIVE_META[summary.effective];
  let effLabel = "";
  if (summary.effective === "CONDITIONAL") {
    const condNames = summary.variants
      .map(v => normalizeConditionCode(v.conditionCode))
      .filter((c): c is string => c !== null);
    effLabel = condNames.length ? `条件：${condNames.join(" / ")}` : "条件分支";
  } else if (summary.effective === "DIRECT") {
    effLabel = allCovered && hasBaselineDirectRecord ? "● 有直接记录" : "直接";
  } else if (summary.effective === "UNAUTHORIZED") {
    effLabel = "未授权";
  } else if (summary.effective === "INHERITED") {
    effLabel = "继承";
  } else if (summary.effective === "DERIVED") {
    effLabel = "派生";
  }
  marks.push({
    key: "effective",
    symbol: effMeta.symbol,
    tagType: effMeta.tagType,
    label: effLabel,
    weight: effMeta.weight
  });

  // 3. draftChange 正交标记（权重 3，与 effective 并存）
  if (summary.draftChange) {
    const dm = DRAFT_META[summary.draftChange];
    marks.push({
      key: "draft",
      symbol: dm.symbol,
      tagType: dm.tagType,
      label: dm.label,
      weight: dm.weight
    });
  }

  // 4. ⊘ 能力标记（权重 4，与 DIRECT/CONDITIONAL 共存）
  if (!grantableByOperator) {
    marks.push({
      key: "capability",
      symbol: "⊘",
      tagType: "info",
      label: denyReason ? `不可授予：${denyReason}` : "不可授予",
      weight: 4
    });
  }

  // R3 排序（weight 升序，稳定排序）
  marks.sort((a, b) => a.weight - b.weight);

  const visible = marks.slice(0, DEFAULT_VISIBLE_MARK_COUNT);
  const overflowCount = marks.length - visible.length;

  // popover 完整内容
  const popoverLines: string[] = [];
  popoverLines.push(`有效态：${summary.effective}`);
  if (summary.draftChange)
    popoverLines.push(`草稿变更：${DRAFT_META[summary.draftChange].label}`);
  if (allCovered) popoverLines.push("被 ALL 覆盖");
  if (hasBaselineDirectRecord) popoverLines.push("baseline 有直接记录");
  if (!grantableByOperator)
    popoverLines.push(denyReason ? `不可授予：${denyReason}` : "不可授予");
  const directCount = summary.variants.filter(
    v => normalizeConditionCode(v.conditionCode) === null
  ).length;
  const condVariants = summary.variants.filter(
    v => normalizeConditionCode(v.conditionCode) !== null
  );
  if (directCount > 0) popoverLines.push(`直接分支：${directCount}`);
  if (condVariants.length > 0)
    popoverLines.push(
      `条件分支：${condVariants.map(v => v.conditionCode).join(" / ")}`
    );
  if (summary.variants.length === 0) popoverLines.push("未授权");

  const baselineVariants = (baselineIndex.get(cellKeyStr) ?? [])
    .map(vid => baselineMap.get(vid))
    .filter((v): v is V2DraftPermission => !!v);

  return {
    cell,
    cellKeyStr,
    summary,
    marks: visible,
    overflowCount,
    popoverLines,
    allCovered,
    hasBaselineDirectRecord,
    grantableByOperator,
    denyReason,
    baselineVariants,
    draftVariants: summary.variants,
    expanded
  };
}
