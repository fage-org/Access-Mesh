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
import { RequestError } from "@/api/_envelope";
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  GrantVariantId,
  V2DraftPermission,
  V2DraftState,
  V2GrantTaskSnapshot,
  V2FailedChildOp,
  VariantCommand,
  V2SavePhase
} from "./v2-types";
import {
  permCellKeyStr,
  normalizeConditionCode,
  childPermCellKeyStr,
  findChildVariantByParentCellCondition
} from "./grant-variant";
import { replayGrantTasks } from "./replay";

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

// ===========================================================================
// T-FE-034：保存错误分类 + reconcile 身份合并
// ===========================================================================

/**
 * 保存错误分类（Q1：明确拒绝白名单，5xx 归结果未知，禁止靠 message 判断）。
 *
 * business（MAIN_FAILED，服务端未提交，可安全重试）：
 * - RequestError kind=business（后端业务码 / unwrap 抛出）
 * - axios 400/401/403/409/422 等明确拒绝响应
 *
 * unknown（SAVE_OUTCOME_UNKNOWN，服务端可能已提交，禁止盲目重试）：
 * - ECONNABORTED / ETIMEDOUT / ERR_NETWORK / ERR_CANCELED
 * - 408 / 所有 5xx（500/502/503/504）
 * - 无 response 的网络层失败 / 无法识别来源
 */
export function classifySaveError(e: unknown): "business" | "unknown" {
  if (e instanceof RequestError) {
    return e.kind === "business" ? "business" : "unknown";
  }
  if (typeof e === "object" && e !== null) {
    const ax = e as { code?: string; response?: { status?: number } };
    if (
      ax.code === "ERR_CANCELED" ||
      ax.code === "ECONNABORTED" ||
      ax.code === "ETIMEDOUT" ||
      ax.code === "ERR_NETWORK"
    ) {
      return "unknown";
    }
    const status = ax.response?.status;
    if (status !== undefined) {
      if (status === 408 || status >= 500) return "unknown";
      // 明确拒绝白名单：仅可信的"请求被拒绝"响应归 business（服务端未提交）
      if (
        status === 400 ||
        status === 401 ||
        status === 403 ||
        status === 409 ||
        status === 422
      )
        return "business";
      return "unknown"; // 其他状态默认 unknown（不安全直接重试）
    }
    if (ax.code !== undefined) return "unknown"; // 有 code 无 response（网络层失败）
  }
  return "unknown"; // 无法识别 -> 保守归结果未知
}

/**
 * D5 保存阶段派生（T-FE-034，纯函数，供 hook savePhase computed 与测试复用）。
 * 优先级：SAVING > SAVE_PREVIEW > SAVE_FAILED_MAIN > SAVE_OUTCOME_UNKNOWN
 * > STALE_WITH_CHILD_FAILURE > STALE > SAVE_FAILED_CHILD > DIRTY > CLEAN。
 * mainFailed 优先于 failedChildren/stale：主请求业务拒绝时本次保存未提交，
 * 即使历史 failedChildren 非空也不应显示"主权限已保存"（避免被子失败遮蔽）。
 * mainFailed 为显式标志（仅主请求 business 拒绝），不靠 saveError 消息推断。
 */
export function deriveSavePhase(input: {
  saving: boolean;
  savePreview: boolean;
  saveOutcomeUnknown: boolean;
  baselineStale: boolean;
  failedChildrenCount: number;
  mainFailed: boolean;
  hasDraft: boolean;
}): V2SavePhase {
  if (input.saving) return "SAVING";
  if (input.savePreview) return "SAVE_PREVIEW";
  if (input.mainFailed) return "SAVE_FAILED_MAIN";
  if (input.saveOutcomeUnknown) return "SAVE_OUTCOME_UNKNOWN";
  if (input.baselineStale && input.failedChildrenCount > 0)
    return "STALE_WITH_CHILD_FAILURE";
  if (input.baselineStale) return "STALE";
  if (input.failedChildrenCount > 0) return "SAVE_FAILED_CHILD";
  if (input.hasDraft) return "DIRTY";
  return "CLEAN";
}

/** 主权限匹配键：PermCellKeyStr + normalized conditionCode */
function mainMatchKey(cellKey: string, cond: string | null): string {
  return `${cellKey}|${cond ?? "null"}`;
}

/** 子权限匹配键：服务端父 ID + 子 PermCellKeyStr + normalized conditionCode */
function childMatchKey(
  parentId: GrantVariantId,
  cellKey: string,
  cond: string | null
): string {
  return `${parentId}|${cellKey}|${cond ?? "null"}`;
}

/** 按重绑映射替换身份（仅 string UUID 替换为服务端 number id，number 不变） */
function rebindId(
  id: GrantVariantId,
  rebind: Map<GrantVariantId, number>
): GrantVariantId {
  if (typeof id === "string" && rebind.has(id)) return rebind.get(id)!;
  return id;
}

/** 重绑命令的主权限身份引用（grant.proposedVariantId / update|remove.targetVariantId / child-grant.parentVariantId） */
function rebindCommandMain(
  cmd: VariantCommand,
  mainRebind: Map<GrantVariantId, number>
): VariantCommand {
  switch (cmd.kind) {
    case "grant":
      return {
        ...cmd,
        proposedVariantId: rebindId(cmd.proposedVariantId, mainRebind)
      };
    case "update":
    case "remove":
      return {
        ...cmd,
        targetVariantId: rebindId(cmd.targetVariantId, mainRebind)
      };
    case "child-grant":
      return {
        ...cmd,
        parentVariantId: rebindId(cmd.parentVariantId, mainRebind)
      };
    default:
      return cmd;
  }
}

/** 重绑命令的子权限身份引用（child-grant.proposedVariantId / child-update|child-remove.targetVariantId） */
function rebindCommandChild(
  cmd: VariantCommand,
  childRebind: Map<GrantVariantId, number>
): VariantCommand {
  switch (cmd.kind) {
    case "child-grant":
      return {
        ...cmd,
        proposedVariantId: rebindId(cmd.proposedVariantId, childRebind)
      };
    case "child-update":
    case "child-remove":
      return {
        ...cmd,
        targetVariantId: rebindId(cmd.targetVariantId, childRebind)
      };
    default:
      return cmd;
  }
}

/** 重绑 failedChildren 身份引用（parentVariantId / child.variantId / child.dependOn / childKey） */
function rebindFailedChild(
  fop: V2FailedChildOp,
  mainRebind: Map<GrantVariantId, number>,
  childRebind: Map<GrantVariantId, number>
): V2FailedChildOp {
  const newParent = rebindId(fop.parentVariantId, mainRebind);
  const newChild: V2DraftPermission = {
    ...fop.child,
    variantId: rebindId(fop.child.variantId, childRebind),
    dependOn:
      fop.child.dependOn !== null
        ? rebindId(fop.child.dependOn, mainRebind)
        : null
  };
  return {
    ...fop,
    parentVariantId: newParent,
    child: newChild,
    childKey: childPermCellKeyStr(newParent, newChild)
  };
}

/**
 * 判断 failedChild 是否已结算（已落库或已删除），结算则从 overlay 剔除（Q3）。
 * - add 失败：nextBaseline 已有该子（父+cell+condition 匹配）-> 已落库，剔除
 * - remove 失败：nextBaseline 已无该子（variantId 不在 childMap）-> 已删除，剔除
 */
function isFailedChildSettled(
  fop: V2FailedChildOp,
  nextBaseline: V2DraftState
): boolean {
  if (fop.op === "add") {
    if (fop.child.dependOn === null) return false;
    const found = findChildVariantByParentCellCondition(
      nextBaseline,
      fop.child.dependOn,
      fop.child,
      normalizeConditionCode(fop.child.conditionCode)
    );
    return !!found;
  }
  return !nextBaseline.childMap.has(fop.child.variantId);
}

/**
 * 清理已结算命令（replay effect=noChange/redundantSkipped），只保留尚未完成的命令（Q2 第6点）。
 * 命令移除后任务可能为空，空任务删除（避免 grantTasks.length 虚假 DIRTY）。
 */
function cleanSettledCommands(
  tasks: V2GrantTaskSnapshot[],
  replay: ReturnType<typeof replayGrantTasks>
): V2GrantTaskSnapshot[] {
  const result: V2GrantTaskSnapshot[] = [];
  for (const t of tasks) {
    const effects = replay.taskEffects.get(t.taskId);
    if (!effects) {
      result.push(t);
      continue;
    }
    const remaining = t.commands.filter((_, idx) => {
      const eff = effects.commands[idx];
      return (
        eff && eff.effect !== "noChange" && eff.effect !== "redundantSkipped"
      );
    });
    if (remaining.length > 0) result.push({ ...t, commands: remaining });
  }
  return result;
}

export interface ReconcileResult {
  ok: boolean;
  tasks: V2GrantTaskSnapshot[];
  failedChildren: V2FailedChildOp[];
  mainRebind: Map<GrantVariantId, number>;
  childRebind: Map<GrantVariantId, number>;
}

/**
 * reconcile 身份合并（Q2 增强方案 A，不引入长期 UUID 映射表，保持 replay 纯函数）。
 *
 * 场景：SAVE_OUTCOME_UNKNOWN（主请求超时/断网/5xx，服务端可能已提交）或子操作失败后
 * fetchBaseline，grantTasks 里 grant/child-grant 命令的临时 UUID 可能已落库为服务端 id。
 * 不合并身份会导致 replay 产生错误 diff（UUID 变体判 add 重复 + number 变体判 remove 误删）。
 *
 * 流程：
 * 1. 主权限 UUID 匹配：PermCellKey + normalized conditionCode，限定 grantSource=MANUAL，要求唯一匹配
 * 2. 重绑所有主权限身份引用（grant/update/remove/child-grant.parentVariantId/failedChildren）
 * 3. 子权限 UUID 匹配：服务端父 ID + 子 PermCellKey + conditionCode，要求唯一匹配
 * 4. 重绑子权限身份引用（child-grant.proposedVariantId/child-update|child-remove/failedChildren.child）
 * 5. failedChildren settled 判断（add 已落库 / remove 已删 -> 剔除）
 * 6. replay 验证 + 清理 settled commands（noChange/redundantSkipped）
 * 7. 多匹配或非一一映射 -> ok=false（禁止任选一个）
 *
 * 纯函数：不修改输入，返回新 tasks + failedChildren + 重绑映射。
 */
export function reconcileIdentities(
  nextBaseline: V2DraftState,
  tasks: V2GrantTaskSnapshot[],
  failedChildren: V2FailedChildOp[]
): ReconcileResult {
  const fail = (): ReconcileResult => ({
    ok: false,
    tasks,
    failedChildren,
    mainRebind: new Map(),
    childRebind: new Map()
  });

  // 1. 主权限索引：PermCellKey + normalized conditionCode -> serverId[]（限定 MANUAL）
  const mainIndex = new Map<string, number[]>();
  for (const [vid, perm] of nextBaseline.mainMap) {
    if (perm.grantSource !== "MANUAL") continue;
    const key = mainMatchKey(
      permCellKeyStr(perm),
      normalizeConditionCode(perm.conditionCode)
    );
    if (!mainIndex.has(key)) mainIndex.set(key, []);
    mainIndex.get(key)!.push(vid as number);
  }

  // 2. 主权限 UUID 匹配（唯一 + 一一映射）
  const mainRebind = new Map<GrantVariantId, number>();
  const usedMainIds = new Set<number>();
  for (const t of tasks) {
    for (const cmd of t.commands) {
      if (cmd.kind !== "grant") continue;
      const uuid = cmd.proposedVariantId;
      if (typeof uuid !== "string") continue; // 已是 number，无需重绑
      const key = mainMatchKey(
        permCellKeyStr(cmd.cell),
        normalizeConditionCode(cmd.conditionCode)
      );
      const ids = mainIndex.get(key) ?? [];
      if (ids.length === 0) continue; // 未落库，保留 UUID
      if (ids.length > 1) return fail(); // 多匹配
      const serverId = ids[0];
      if (usedMainIds.has(serverId)) return fail(); // 非一一映射
      usedMainIds.add(serverId);
      mainRebind.set(uuid, serverId);
    }
  }

  // 3. 重绑主权限身份引用（深拷贝 commands）
  const tasksAfterMain = tasks.map(t => ({
    ...t,
    commands: t.commands.map(c => rebindCommandMain(c, mainRebind))
  }));

  // 4. 子权限索引：服务端父 ID + 子 PermCellKey + conditionCode -> serverId[]
  const childIndex = new Map<string, number[]>();
  for (const [vid, perm] of nextBaseline.childMap) {
    if (perm.dependOn === null) continue;
    const key = childMatchKey(
      perm.dependOn,
      permCellKeyStr(perm),
      normalizeConditionCode(perm.conditionCode)
    );
    if (!childIndex.has(key)) childIndex.set(key, []);
    childIndex.get(key)!.push(vid as number);
  }

  // 5. 子权限 UUID 匹配（parentVariantId 已重绑为 number）
  const childRebind = new Map<GrantVariantId, number>();
  const usedChildIds = new Set<number>();
  for (const t of tasksAfterMain) {
    for (const cmd of t.commands) {
      if (cmd.kind !== "child-grant") continue;
      const uuid = cmd.proposedVariantId;
      if (typeof uuid !== "string") continue;
      const key = childMatchKey(
        cmd.parentVariantId,
        permCellKeyStr(cmd.cell),
        normalizeConditionCode(cmd.conditionCode)
      );
      const ids = childIndex.get(key) ?? [];
      if (ids.length === 0) continue;
      if (ids.length > 1) return fail();
      const serverId = ids[0];
      if (usedChildIds.has(serverId)) return fail();
      usedChildIds.add(serverId);
      childRebind.set(uuid, serverId);
    }
  }

  // 6. 重绑子权限身份引用
  const tasksAfterChild = tasksAfterMain.map(t => ({
    ...t,
    commands: t.commands.map(c => rebindCommandChild(c, childRebind))
  }));

  // 7. failedChildren 重绑 + settled 判断（剔除已落库/已删）
  const newFailedChildren: V2FailedChildOp[] = [];
  for (const fop of failedChildren) {
    const rebindFop = rebindFailedChild(fop, mainRebind, childRebind);
    if (isFailedChildSettled(rebindFop, nextBaseline)) continue;
    newFailedChildren.push(rebindFop);
  }

  // 8. replay 验证 + 清理 settled commands
  const replay = replayGrantTasks(nextBaseline, tasksAfterChild);
  const cleanedTasks = cleanSettledCommands(tasksAfterChild, replay);

  return {
    ok: true,
    tasks: cleanedTasks,
    failedChildren: newFailedChildren,
    mainRebind,
    childRebind
  };
}
