/**
 * V2 replay 纯函数（T-FE-030）。
 *
 * 输入：baseline V2DraftState + 有序 V2GrantTaskSnapshot[]
 * 输出：draft 投影（V2DraftState 副本）+ taskEffects
 *
 * 纯函数特性：
 * - 不原地修改输入（cloneState 后操作副本）
 * - 不生成 UUID（proposedVariantId 由任务构建时生成）
 * - 同输入连续 replay 结果完全一致
 *
 * R11 分支语义（state-model §3.4）：
 * - grant：同 cell+condition 已存在且属性相同 -> noChange；
 *   同 cell+condition canGrant 不同 -> update（保持 variantId）；
 *   不存在 -> add（proposedVariantId）；
 *   INSTANCE + ALL 覆盖 + 同 cell 无直接记录 + !keepDirectWhenAllCovered -> redundantSkipped
 * - update：保持 targetVariantId，更新 conditionCode/canGrant；编辑 conditionCode 检查同 cell 重复分支阻断
 * - remove：按 targetVariantId 删除 + 级联该父分支子权限
 *
 * 编辑 conditionCode 保持 variantId（非 remove+add）；禁止用 remove+add 表达条件编辑。
 */
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  V2DraftPermission,
  V2DraftState,
  V2GrantTaskSnapshot,
  VariantCommand,
  CommandEffect,
  TaskReplayEffect,
  V2ReplayResult,
  PermCellKeyStr
} from "./v2-types";
import {
  createV2DraftState,
  upsertMainVariant,
  removeMainVariant,
  normalizeConditionCode,
  permCellKeyStr
} from "./grant-variant";

/** 深拷贝 state（replay 不原地修改输入） */
function cloneState(state: V2DraftState): V2DraftState {
  const next = createV2DraftState();
  for (const [vid, perm] of state.mainMap) next.mainMap.set(vid, { ...perm });
  for (const [k, ids] of state.mainIndex) next.mainIndex.set(k, [...ids]);
  for (const [vid, perm] of state.childMap) next.childMap.set(vid, { ...perm });
  for (const [k, ids] of state.childIndex) next.childIndex.set(k, [...ids]);
  return next;
}

/** 查找同 cell + 同规范化 conditionCode 的已存在变体 */
function findVariantByCellCondition(
  state: V2DraftState,
  cellKey: PermCellKeyStr,
  conditionCode: string | null
): V2DraftPermission | undefined {
  const ids = state.mainIndex.get(cellKey) ?? [];
  const norm = normalizeConditionCode(conditionCode);
  for (const id of ids) {
    const perm = state.mainMap.get(id);
    if (perm && normalizeConditionCode(perm.conditionCode) === norm) {
      return perm;
    }
  }
  return undefined;
}

/** INSTANCE cell 是否被同操作 ALL 变体覆盖 */
function isAllCovered(state: V2DraftState, cell: PermCellKey): boolean {
  if (cell.scopeMode !== "INSTANCE") return false;
  const allKey = permCellKeyStr({
    domainCode: cell.domainCode,
    resourceTypeCode: cell.resourceTypeCode,
    scopeMode: "ALL",
    resourceCode: null,
    codeType: null,
    operationCode: cell.operationCode
  });
  const ids = state.mainIndex.get(allKey) ?? [];
  return ids.length > 0;
}

/** replay 主入口 */
export function replayGrantTasks(
  baseline: V2DraftState,
  tasks: V2GrantTaskSnapshot[]
): V2ReplayResult {
  const state = cloneState(baseline);
  const taskEffects = new Map<string, TaskReplayEffect>();

  for (const task of tasks) {
    const commands: CommandEffect[] = [];
    task.commands.forEach((cmd, idx) => {
      commands.push(applyCommand(state, cmd, idx));
    });
    taskEffects.set(task.taskId, { taskId: task.taskId, commands });
  }

  return {
    mainDraft: state.mainMap,
    mainIndex: state.mainIndex,
    childDraft: state.childMap,
    childIndex: state.childIndex,
    taskEffects
  };
}

/** 执行单条变体命令 */
function applyCommand(
  state: V2DraftState,
  cmd: VariantCommand,
  commandIndex: number
): CommandEffect {
  if (cmd.kind === "grant") {
    return applyGrant(state, cmd, commandIndex);
  }
  if (cmd.kind === "update") {
    return applyUpdate(state, cmd, commandIndex);
  }
  return applyRemove(state, cmd, commandIndex);
}

function applyGrant(
  state: V2DraftState,
  cmd: Extract<VariantCommand, { kind: "grant" }>,
  commandIndex: number
): CommandEffect {
  const cellKey = permCellKeyStr(cmd.cell);
  const normCond = normalizeConditionCode(cmd.conditionCode);

  // 同 cell+condition 已存在
  const existing = findVariantByCellCondition(state, cellKey, normCond);
  if (existing) {
    if (existing.canGrant === cmd.canGrant) {
      return {
        commandIndex,
        variantId: existing.variantId,
        effect: "noChange"
      };
    }
    // update：保持 variantId，更新 canGrant
    existing.canGrant = cmd.canGrant;
    return { commandIndex, variantId: existing.variantId, effect: "update" };
  }

  // R11：INSTANCE + ALL 覆盖 + 同 cell 无任何直接记录 + !keepDirect -> redundantSkipped
  const cellIds = state.mainIndex.get(cellKey) ?? [];
  const hasDirect = cellIds.length > 0;
  if (
    cmd.cell.scopeMode === "INSTANCE" &&
    !hasDirect &&
    !cmd.keepDirectWhenAllCovered &&
    isAllCovered(state, cmd.cell)
  ) {
    return {
      commandIndex,
      variantId: cmd.proposedVariantId,
      effect: "redundantSkipped"
    };
  }

  // add 前：proposedVariantId 全局未占用（mainMap + childMap，身份全局唯一）
  if (
    state.mainMap.has(cmd.proposedVariantId) ||
    state.childMap.has(cmd.proposedVariantId)
  ) {
    throw new Error(`proposedVariantId ${cmd.proposedVariantId} 已被占用`);
  }

  // add：使用 proposedVariantId（replay 不生成 UUID）
  const newPerm: V2DraftPermission = {
    domainCode: cmd.cell.domainCode,
    resourceTypeCode: cmd.cell.resourceTypeCode,
    scopeMode: cmd.cell.scopeMode,
    resourceCode: cmd.cell.resourceCode,
    codeType: cmd.cell.codeType,
    operationCode: cmd.cell.operationCode,
    variantId: cmd.proposedVariantId,
    conditionCode: normCond,
    canGrant: cmd.canGrant,
    dependOn: null,
    grantSource: "MANUAL",
    resourceName: cmd.resourceName
  };
  upsertMainVariant(state, newPerm);
  return { commandIndex, variantId: cmd.proposedVariantId, effect: "add" };
}

function applyUpdate(
  state: V2DraftState,
  cmd: Extract<VariantCommand, { kind: "update" }>,
  commandIndex: number
): CommandEffect {
  const perm = state.mainMap.get(cmd.targetVariantId);
  if (!perm) {
    return {
      commandIndex,
      variantId: cmd.targetVariantId,
      effect: "noChange",
      reason: "目标变体不存在"
    };
  }
  const normCond = normalizeConditionCode(cmd.conditionCode);
  const oldCond = normalizeConditionCode(perm.conditionCode);

  // 编辑 conditionCode：检查同 cell 另一分支重复（阻断，保持原值）
  if (oldCond !== normCond) {
    const cellKey = permCellKeyStr(perm);
    const dup = findVariantByCellCondition(state, cellKey, normCond);
    if (dup && dup.variantId !== cmd.targetVariantId) {
      return {
        commandIndex,
        variantId: cmd.targetVariantId,
        effect: "noChange",
        reason: "同 cell+condition 分支已存在"
      };
    }
  }

  // 保持 variantId，更新属性（编辑 conditionCode 非 remove+add）
  const condChanged = oldCond !== normCond;
  const grantChanged = perm.canGrant !== cmd.canGrant;
  perm.conditionCode = normCond;
  perm.canGrant = cmd.canGrant;
  // conditionCode 不影响 cellKey（六维不含 condition），索引不变
  return {
    commandIndex,
    variantId: cmd.targetVariantId,
    effect: condChanged || grantChanged ? "update" : "noChange"
  };
}

function applyRemove(
  state: V2DraftState,
  cmd: Extract<VariantCommand, { kind: "remove" }>,
  commandIndex: number
): CommandEffect {
  const removed = removeMainVariant(state, cmd.targetVariantId);
  return {
    commandIndex,
    variantId: cmd.targetVariantId,
    effect: removed ? "remove" : "noChange"
  };
}
