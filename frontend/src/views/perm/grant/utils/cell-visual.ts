/**
 * 矩阵单元格图标正交模型（纯函数，设计文档 permission-grant.md §3.3/§6.2，🔧 T-FE-039）。
 *
 * 图标正交模型（2026-08-05 二轮评审映射定稿）：
 * - 条纹 = 最终权限受条件约束（全部有效来源均带条件）；
 * - 粗黑边框 = 可转授 canGrant（任一来源 canGrant=true 即显示，能力属性）；
 * - 形态只表达权限来源/属性；颜色由渲染层表达状态：绿=有效/新增、黄=待更新、红=待撤销；
 * - 箭头：向上（资源继承）/ 向右（操作继承）/ 组合（资源+操作两段继承）。
 *
 * 多来源聚合归并（2026-08-06 三轮评审定稿）：
 * - 每种状态最多一个聚合图标；有效/新增、待更新、待撤销可并列；
 * - 颜色：任一来源为直接授权 -> 实色（直接优先）；全部来源为继承 -> 淡色；
 * - 箭头：存在"资源段 + 操作段"两段继承（同记录或不同记录并存）-> 组合箭头；
 *   单一继承类型 -> 对应单一箭头；有直接来源并存 -> 无箭头（直接优先）；
 * - 粗黑边框：任一来源 canGrant=true -> 显示；
 * - 条纹：全部有效来源带条件 -> 条纹；任一来源无条件 -> 普通填充。
 * - 聚合范围包含全部有效来源（含 AUTO_DEP：不占独立来源图标，仍参与全部聚合计算）。
 *
 * 待更新/待撤销来源复用同一聚合形态，只在渲染层切换黄/红色板。
 *
 * 子权限数量精确投影（§3.3）：
 * - 仅统计当前格 childCount>0 的直接主权限记录（直接 = 无资源/操作继承段）；
 * - 继承投影来源不复制来源记录的子权限数量；
 * - 子权限数量由独立标识汇总展示，不叠加到来源/状态图标上。
 */
import type { CellSource } from "./source-chain";

/** 权限图标聚合视觉（颜色无关；§3.3 多来源聚合归并结果） */
export type PermissionIconVisual = {
  /** 实色（任一直接授权，含 AUTO_DEP 直接记录）vs 淡色（全部为继承来源） */
  solid: boolean;
  /** 箭头：none（直接优先）/ up（资源继承）/ right（操作继承）/ combined（两段继承） */
  arrow: "none" | "up" | "right" | "combined";
  /** 条纹 = 最终权限受条件约束：全部有效来源均带条件 */
  striped: boolean;
  /** 粗黑边框 = 可转授：任一来源 canGrant=true */
  boldBorder: boolean;
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
 * 多来源聚合归并为单一颜色无关图标形态（§3.3；空来源返回 null）。
 * 规则：颜色（任一直接 -> 实色）/ 箭头（两段 -> 组合；单一 -> 对应；直接并存 -> 无）/
 * 粗黑边框（任一 canGrant）/ 条纹（全部来源带条件）。
 */
export function aggregatePermissionIcon(
  sources: CellSource[]
): PermissionIconVisual | null {
  if (sources.length === 0) return null;
  const hasDirect = sources.some(isDirectSource);
  const hasNodeInherit = sources.some(s => s.nodeInheritFromCode != null);
  const hasOpInherit = sources.some(s => s.opInheritFromCode != null);
  let arrow: PermissionIconVisual["arrow"] = "none";
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

/** 单一状态分组的子权限数量；继承投影来源不参与。 */
export function childPermissionCountOf(sources: CellSource[]): number {
  return sources.reduce(
    (count, source) =>
      count + (isDirectMainWithChildren(source) ? (source.childCount ?? 0) : 0),
    0
  );
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
 * 独立计算新增背景——撤销旧来源与新增来源混合时，新增背景不再被 remove 吞掉：
 * - 全部来源均为新增（无 remove 混合）→ "add"（绿底）
 * - 有效侧存在新增来源（已有格新增；含与 remove 混合）→ "partial-add"（淡绿底）
 * - update/remove 由同形异色图标表达，不叠加整格背景
 * - 其余 → null
 */
export function cellBackgroundMark(
  sources: CellSource[],
  markInfo: ReadonlyMap<number, SourceDraftMark>
): "add" | "partial-add" | null {
  const total = sources.length;
  if (total === 0) return null;
  let addCount = 0;
  let validCount = 0;
  for (const s of sources) {
    const mark = markInfo.get(s.recordId)?.mark;
    if (mark === "remove") continue; // 被撤销来源不计入有效侧
    validCount++;
    if (mark === "add") addCount++;
  }
  if (validCount === 0) return null; // 纯 remove：由撤销图标表达
  if (addCount === total) return "add"; // 整格新增（validCount === total 且全部为新增）
  if (addCount > 0) return "partial-add"; // 已有格新增来源（含 remove 混合）
  return null;
}

/**
 * 按草稿标记拆分来源：current=未标记/add，updated=update，removed=remove；
 * valid 保留“除 remove 外全部来源”的兼容语义。
 */
export function splitSourcesByDraft(
  sources: CellSource[],
  markInfo: ReadonlyMap<number, SourceDraftMark>
): {
  current: CellSource[];
  updated: CellSource[];
  valid: CellSource[];
  removed: CellSource[];
} {
  const current: CellSource[] = [];
  const updated: CellSource[] = [];
  const valid: CellSource[] = [];
  const removed: CellSource[] = [];
  for (const s of sources) {
    const mark = markInfo.get(s.recordId)?.mark;
    if (mark === "remove") {
      removed.push(s);
    } else if (mark === "update") {
      updated.push(s);
      valid.push(s);
    } else {
      current.push(s);
      valid.push(s);
    }
  }
  return { current, updated, valid, removed };
}
