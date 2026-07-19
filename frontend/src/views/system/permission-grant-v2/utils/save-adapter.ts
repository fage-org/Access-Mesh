/**
 * V2 保存适配层（T-FE-033）-- 纯函数：diff 计算 + 请求项构造 + 响应匹配。
 *
 * 条件清除 wire 约定（error-flow §2.9 E32b/P2-3，后端 update 语义）：
 * - update 且条件未变化 -> conditionCode=null（不更新）
 * - update 从有条件清为无条件 -> conditionCode=""（清空）
 * - update 新增或更换条件 -> 具体 conditionCode
 * - add -> draft.conditionCode ?? ""（有条件必须发具体值，无条件发空串）
 *
 * 两步保存匹配（state-model §4.2 选项1，无后端改动）：
 * - save 主权限 -> 响应按 PermCellKey + normalized conditionCode 匹配得服务端 id
 * - add-child 用 parentPermissionId（已保存分支用服务端 id，新分支用匹配结果）
 *
 * 子权限 diff 级联过滤：父变体在 mainRemove 时，子权限 remove 跳过（后端级联删，不重复 remove-child）。
 */
import type {
  RolePermissionAddItem,
  RolePermissionUpdateItem,
  RolePermissionItem
} from "@/api/permission-grant";
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  GrantVariantId,
  V2DraftPermission,
  V2DraftState
} from "./v2-types";
import { permCellKeyStr, normalizeConditionCode } from "./grant-variant";

/** diff 条目（主/子统一结构） */
export interface V2DiffEntry {
  type: "add" | "update" | "remove";
  variantId: GrantVariantId;
  perm: V2DraftPermission;
  before: V2DraftPermission | null;
  isChild: boolean;
  /** 子权限的父变体（主权限为 null） */
  parentVariantId: GrantVariantId | null;
}

/** DraftPermission -> RolePermissionAddItem（add 请求；条件 wire：有条件发具体值，无条件发 ""） */
export function toAddItem(d: V2DraftPermission): RolePermissionAddItem {
  const item: RolePermissionAddItem = {
    resourceTypeCode: d.resourceTypeCode,
    operationCode: d.operationCode,
    scopeMode: d.scopeMode
  };
  if (d.scopeMode === "INSTANCE") {
    item.resourceCode = d.resourceCode ?? undefined;
    item.codeType = d.codeType ?? undefined;
  }
  item.conditionCode = normalizeConditionCode(d.conditionCode) ?? "";
  if (d.canGrant) item.canGrant = true;
  return item;
}

/**
 * DraftPermission -> RolePermissionUpdateItem（update 请求；条件 wire 精确规则）。
 * - 条件未变化 -> null（不更新）
 * - 从有条件清为无条件 -> ""
 * - 新增或更换条件 -> 具体值
 * canGrant 总是发 draft 值（未变化发同值无副作用；diff 已过滤两者皆未变的项）。
 */
export function toUpdateItem(
  draft: V2DraftPermission,
  baseline: V2DraftPermission
): RolePermissionUpdateItem {
  const draftCond = normalizeConditionCode(draft.conditionCode);
  const baseCond = normalizeConditionCode(baseline.conditionCode);
  let conditionCode: string | null;
  if (draftCond === baseCond) {
    conditionCode = null;
  } else if (draftCond === null) {
    conditionCode = "";
  } else {
    conditionCode = draftCond;
  }
  return {
    id: draft.variantId as number,
    conditionCode,
    canGrant: draft.canGrant
  };
}

/** 主权限 diff（draft 相对 baseline） */
export function computeMainDiff(
  mainDraft: Map<GrantVariantId, V2DraftPermission>,
  mainBaseline: V2DraftState
): V2DiffEntry[] {
  const entries: V2DiffEntry[] = [];
  for (const [vid, draft] of mainDraft) {
    const base = mainBaseline.mainMap.get(vid);
    if (!base) {
      entries.push({
        type: "add",
        variantId: vid,
        perm: draft,
        before: null,
        isChild: false,
        parentVariantId: null
      });
    } else {
      const condChanged =
        normalizeConditionCode(draft.conditionCode) !==
        normalizeConditionCode(base.conditionCode);
      const grantChanged = draft.canGrant !== base.canGrant;
      if (condChanged || grantChanged) {
        entries.push({
          type: "update",
          variantId: vid,
          perm: draft,
          before: base,
          isChild: false,
          parentVariantId: null
        });
      }
    }
  }
  for (const [vid, base] of mainBaseline.mainMap) {
    if (!mainDraft.has(vid)) {
      entries.push({
        type: "remove",
        variantId: vid,
        perm: base,
        before: null,
        isChild: false,
        parentVariantId: null
      });
    }
  }
  return entries;
}

/**
 * 子权限 diff（draft 相对 baseline）。
 * 级联过滤：父变体在 mainRemoveIds 时，子权限 remove 跳过（后端级联删，不重复 remove-child）。
 */
export function computeChildDiff(
  childDraft: Map<GrantVariantId, V2DraftPermission>,
  childBaseline: V2DraftState,
  mainRemoveIds: Set<GrantVariantId>
): V2DiffEntry[] {
  const entries: V2DiffEntry[] = [];
  for (const [vid, draft] of childDraft) {
    const base = childBaseline.childMap.get(vid);
    if (!base) {
      entries.push({
        type: "add",
        variantId: vid,
        perm: draft,
        before: null,
        isChild: true,
        parentVariantId: draft.dependOn
      });
    } else {
      const condChanged =
        normalizeConditionCode(draft.conditionCode) !==
        normalizeConditionCode(base.conditionCode);
      const grantChanged = draft.canGrant !== base.canGrant;
      if (condChanged || grantChanged) {
        entries.push({
          type: "update",
          variantId: vid,
          perm: draft,
          before: base,
          isChild: true,
          parentVariantId: draft.dependOn
        });
      }
    }
  }
  for (const [vid, base] of childBaseline.childMap) {
    if (!childDraft.has(vid)) {
      // 父变体在 mainRemove -> 后端级联删，跳过
      if (base.dependOn && mainRemoveIds.has(base.dependOn)) continue;
      entries.push({
        type: "remove",
        variantId: vid,
        perm: base,
        before: null,
        isChild: true,
        parentVariantId: base.dependOn
      });
    }
  }
  return entries;
}

/**
 * 匹配 save 响应：按 PermCellKey + normalized conditionCode 匹配新主权限服务端 id。
 * 用于两步保存第二步 add-child 的 parentPermissionId 解析。
 */
export function matchSaveResponse(
  saveItems: RolePermissionItem[],
  mainAddEntries: V2DiffEntry[]
): Map<GrantVariantId, number> {
  const map = new Map<GrantVariantId, number>();
  for (const entry of mainAddEntries) {
    if (entry.type !== "add") continue;
    const draftKey = permCellKeyStr(entry.perm as PermCellKey);
    const draftCond = normalizeConditionCode(entry.perm.conditionCode);
    const matched = saveItems.find(item => {
      const itemKey = permCellKeyStr(item as PermCellKey);
      const itemCond = normalizeConditionCode(item.conditionCode);
      return itemKey === draftKey && itemCond === draftCond;
    });
    if (matched) map.set(entry.variantId, matched.id);
  }
  return map;
}
