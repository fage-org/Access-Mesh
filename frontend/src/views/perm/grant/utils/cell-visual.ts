/**
 * 矩阵单元格图标正交模型（纯函数，设计文档 permission-grant.md §3.3/§6.2，🔧 T-FE-039）。
 *
 * 图标正交模型（2026-08-05 二轮评审映射定稿）：
 * - 条纹 = 有条件（全部有效来源带条件才条纹，任一来源无条件按实色展示）；
 * - 粗黑边框 = 可转授 canGrant（任一来源 canGrant=true 即显示，能力属性）；
 * - 红 = 撤销直接权限；淡红 = 撤销继承权限；红白/淡红白条纹 = 撤销来源有条件；
 * - 箭头：向上（资源继承）/ 向右（操作继承）/ 组合（资源+操作两段继承）。
 *
 * 多来源聚合归并（2026-08-06 三轮评审定稿）：
 * - 正常态最多一个聚合有效图标 + 撤销时最多再一个撤销图标（"有效+撤销最多并列两个"）；
 * - 颜色：任一来源为直接授权 -> 实色（直接优先）；全部来源为继承 -> 淡色；
 * - 箭头：存在"资源段 + 操作段"两段继承（同记录或不同记录并存）-> 组合箭头；
 *   单一继承类型 -> 对应单一箭头；有直接来源并存 -> 无箭头（直接优先）；
 * - 粗黑边框：任一来源 canGrant=true -> 显示；
 * - 条纹：任一有效来源无条件 -> 实色；全部来源有条件 -> 条纹。
 * - 聚合范围包含全部有效来源（含 AUTO_DEP：不占独立来源图标，仍参与全部聚合计算）。
 *
 * 撤销图标归并（对称于有效图标，2026-08-XX 评审确认）：多条被撤销来源时，
 * 任一来源为直接 -> 红；全部为继承 -> 淡红；全部带条件 -> 条纹，任一无条件 -> 实色。
 *
 * 子权限分叉精确投影（§3.3）：
 * - 仅当当前格存在 childCount>0 的直接主权限记录时显示分叉（直接 = 无资源/操作继承段；
 *   继承投影来源不复制来源记录的分叉）；
 * - 该直接主权限正在撤销（级联删子）时，分叉附着在红色撤销图标上；
 * - 存续有效来源与带分叉的撤销图标可并列（仍受"最多并列两个"约束）。
 */
import type { CellSource } from "./source-chain";

/** 有效图标聚合视觉（§3.3 多来源聚合归并结果） */
export type ValidIconVisual = {
  /** 实色（任一直接授权，含 AUTO_DEP 直接记录）vs 淡色（全部为继承来源） */
  solid: boolean;
  /** 箭头：none（直接优先）/ up（资源继承）/ right（操作继承）/ combined（两段继承） */
  arrow: "none" | "up" | "right" | "combined";
  /** 条纹 = 有条件：全部有效来源带条件才条纹，任一来源无条件按实色展示 */
  striped: boolean;
  /** 粗黑边框 = 可转授：任一来源 canGrant=true */
  boldBorder: boolean;
};

/** 撤销图标聚合视觉（§6.2 撤销标记；对称归并） */
export type RemoveIconVisual = {
  /** 红（任一被撤销来源为直接）vs 淡红（全部被撤销来源为继承） */
  red: boolean;
  /** 红白/淡红白条纹：全部被撤销来源带条件才条纹，任一无条件按实色展示 */
  striped: boolean;
};

/** 子权限分叉附着（§3.3 精确投影；仅直接主权限记录携带） */
export type ForkAttachment = {
  /** 存续（未被撤销）直接主权限记录的 childCount 之和（>0 → 分叉附有效图标） */
  validCount: number;
  /** 被撤销（级联删子）直接主权限记录的 childCount 之和（>0 → 分叉附撤销图标） */
  removedCount: number;
};

/** 来源是否为直接授权（无资源段且无操作段；AUTO_DEP 直接记录同样视为直接，参与聚合） */
export function isDirectSource(s: CellSource): boolean {
  return s.nodeInheritFromCode == null && s.opInheritFromCode == null;
}

/** 来源是否为带条件的直接主权限记录（分叉判定：仅直接主记录携带子权限） */
export function isDirectMainWithChildren(s: CellSource): boolean {
  return isDirectSource(s) && (s.childCount ?? 0) > 0;
}

/**
 * 多来源聚合归并为单一有效图标（§3.3；空来源返回 null）。
 * 规则：颜色（任一直接 -> 实色）/ 箭头（两段 -> 组合；单一 -> 对应；直接并存 -> 无）/
 * 粗黑边框（任一 canGrant）/ 条纹（全部有条件）。
 */
export function aggregateValidIcon(
  sources: CellSource[]
): ValidIconVisual | null {
  if (sources.length === 0) return null;
  const hasDirect = sources.some(isDirectSource);
  const hasNodeInherit = sources.some(s => s.nodeInheritFromCode != null);
  const hasOpInherit = sources.some(s => s.opInheritFromCode != null);
  let arrow: ValidIconVisual["arrow"] = "none";
  if (!hasDirect) {
    // 无直接来源：资源段+操作段并存（同记录或不同记录均可）-> 组合箭头
    arrow =
      hasNodeInherit && hasOpInherit
        ? "combined"
        : hasNodeInherit
          ? "up"
          : hasOpInherit
            ? "right"
            : "none";
  }
  return {
    solid: hasDirect,
    arrow,
    striped: sources.every(s => s.conditionCode != null),
    boldBorder: sources.some(s => s.canGrant)
  };
}

/**
 * 多条被撤销来源归并为单一撤销图标（§6.2；对称归并，评审确认）。
 * 颜色：任一直接 -> 红；全部继承 -> 淡红；条纹：全部带条件 -> 红白/淡红白条纹。
 */
export function aggregateRemoveIcon(
  sources: CellSource[]
): RemoveIconVisual | null {
  if (sources.length === 0) return null;
  return {
    red: sources.some(isDirectSource),
    striped: sources.every(s => s.conditionCode != null)
  };
}

/**
 * 子权限分叉附着判定（§3.3 精确投影）。
 * 返回存续侧与被撤销侧各自的直接主权限 childCount 之和；继承投影来源不参与。
 */
export function forkAttachmentOf(
  validSources: CellSource[],
  removedSources: CellSource[]
): ForkAttachment {
  let validCount = 0;
  for (const s of validSources) {
    if (isDirectMainWithChildren(s)) validCount += s.childCount ?? 0;
  }
  let removedCount = 0;
  for (const s of removedSources) {
    if (isDirectMainWithChildren(s)) removedCount += s.childCount ?? 0;
  }
  return { validCount, removedCount };
}

/** 草稿标记结构（与 grant-plan.ts markInfo 同构；避免循环依赖，此处内联声明） */
export type SourceDraftMark = {
  mark: "add" | "update" | "remove";
  changeId: string;
};

/**
 * 单元格 diff 背景标记（§6.2；🔧 T-FE-039 评审 P2-1 修正）。
 *
 * 与 cellDraftMark（remove 优先，用于撤销图标视觉）不同，本函数基于**有效侧**来源
 * 独立计算新增/修改背景——撤销旧分支与新增新分支混合时，新增背景不再被 remove 吞掉：
 * - 全部来源均为新增（无 remove 混合）→ "add"（绿底）
 * - 有效侧存在新增分支（已有格新增；含与 remove 混合）→ "partial-add"（淡绿底）
 * - 有效侧存在 update → "update"（黄底+黄描边）
 * - 其余（含纯 remove，由撤销图标表达）→ null
 */
export function cellBackgroundMark(
  sources: CellSource[],
  markInfo: ReadonlyMap<number, SourceDraftMark>
): "add" | "partial-add" | "update" | null {
  const total = sources.length;
  if (total === 0) return null;
  let addCount = 0;
  let updateCount = 0;
  let validCount = 0;
  for (const s of sources) {
    const mark = markInfo.get(s.recordId)?.mark;
    if (mark === "remove") continue; // 被撤销来源不计入有效侧
    validCount++;
    if (mark === "add") addCount++;
    else if (mark === "update") updateCount++;
  }
  if (validCount === 0) return null; // 纯 remove：由撤销图标表达
  if (addCount === total) return "add"; // 整格新增（validCount === total 且全部为新增）
  if (addCount > 0) return "partial-add"; // 已有格新增分支（含 remove 混合）
  if (updateCount > 0) return "update";
  return null;
}

/**
 * 按草稿标记拆分来源：被撤销（remove 标记）来源 -> removed；其余（add/update/未标记）-> valid。
 * 撤销后仍有其他有效授权 -> 有效图标 + 撤销图标并列；撤销后无有效授权 -> 只显示撤销图标。
 */
export function splitSourcesByDraft(
  sources: CellSource[],
  markInfo: ReadonlyMap<number, SourceDraftMark>
): { valid: CellSource[]; removed: CellSource[] } {
  const valid: CellSource[] = [];
  const removed: CellSource[] = [];
  for (const s of sources) {
    if (markInfo.get(s.recordId)?.mark === "remove") {
      removed.push(s);
    } else {
      valid.push(s);
    }
  }
  return { valid, removed };
}
