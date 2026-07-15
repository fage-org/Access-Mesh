/**
 * 授权任务重放（T-FE-026）。
 *
 * 纯函数：由 baseline + 有序 grantTasks 重放生成 draft 投影 + 任务效果。
 * 不处理 failedChildren overlay（由 hook 在 computed 中叠加，保持 replay 无状态）。
 *
 * R11 口径（与 §16.3.6 allCovered 只看计划态一致）：
 * replay 从 baseline 起步后只检查当前投影 main.has，不额外 OR baseline；
 * 前序任务移除 ALL 后后续不再误判覆盖，前序任务新增的直接记录也必须被后续任务识别。
 *
 * intent=remove：删除主权限键 + 按 parentKey + "|" 级联移除投影中的子权限；
 * 不单独生成重复的 remove-child 请求，沿用保存层已有级联过滤（saveAll 跳过 dependOn 在 mainRemove 的子 remove）。
 *
 * 子权限完整集合替换：group 存在时 children 为最终期望集合，replay 先删前缀再写入；
 * group 缺失不修改；group 存在且 children=[] 明确移除全部子权限。
 *
 * 设计依据：docs/design/frontend/permission-grant.md §16.4.6 / §16.8 R11
 */
import type { GrantScopeMode } from "@/api/permission-grant";
import type {
  DraftPermission,
  GrantTaskSnapshot
} from "@/utils/permission-grant-types";
import { permCellKey, childPermCellKey } from "@/utils/permission-grant-types";

// ========== 任务效果（供 T-FE-028 右栏分组展示，避免届时反推） ==========

export type GrantTaskEffectType =
  | "add"
  | "update"
  | "remove"
  | "noChange"
  | "redundantSkipped";

export interface KeyEffect {
  key: string;
  effect: GrantTaskEffectType;
}

export interface GrantTaskEffect {
  taskId: string;
  mainKeys: KeyEffect[];
  childKeys: KeyEffect[];
}

export interface GrantTaskReplayResult {
  mainDraft: Map<string, DraftPermission>;
  childDraft: Map<string, DraftPermission>;
  taskEffects: Map<string, GrantTaskEffect>;
}

// ========== 辅助 ==========

function mainKeyOf(
  domainCode: string,
  resourceTypeCode: string,
  scopeMode: GrantScopeMode,
  resourceCode: string | null,
  codeType: string | null,
  operationCode: string
): string {
  return permCellKey({
    domainCode,
    resourceTypeCode,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  });
}

/** 构建主权限 DraftPermission（任务投影，保留已有 id） */
function buildMainDraft(
  task: GrantTaskSnapshot,
  resourceCode: string | null,
  codeType: string | null,
  resourceName: string | null,
  operationCode: string,
  existingId: number | null
): DraftPermission {
  return {
    id: existingId,
    domainCode: task.domainCode,
    resourceTypeCode: task.resourceTypeCode,
    scopeMode: task.scopeMode,
    resourceCode,
    codeType,
    operationCode,
    conditionCode: task.conditionCode,
    canGrant: task.canGrant,
    dependOn: null,
    dependOnTempKey: null,
    grantSource: "MANUAL",
    resourceName
  };
}

// ========== 重放 ==========

/**
 * 由 baseline + 有序 grantTasks 重放生成 draft 投影。
 * 顺序：baseline -> grantTasks（按数组顺序）。failedChildren overlay 由 hook 叠加。
 */
export function replayGrantTasks(
  mainBaseline: Map<string, DraftPermission>,
  childBaseline: Map<string, DraftPermission>,
  tasks: GrantTaskSnapshot[]
): GrantTaskReplayResult {
  const main = new Map(mainBaseline);
  const child = new Map(childBaseline);
  const taskEffects = new Map<string, GrantTaskEffect>();

  for (const task of tasks) {
    const mainKeys: KeyEffect[] = [];
    const childKeys: KeyEffect[] = [];

    if (task.intent === "remove") {
      // 移除：删除主权限键 + 级联子权限（不重复 remove-child，保存层级联过滤）
      for (const res of task.resources) {
        for (const op of task.operationCodes) {
          const key = mainKeyOf(
            task.domainCode,
            task.resourceTypeCode,
            task.scopeMode,
            res.resourceCode,
            res.codeType,
            op
          );
          if (main.has(key)) {
            main.delete(key);
            mainKeys.push({ key, effect: "remove" });
          } else {
            mainKeys.push({ key, effect: "noChange" });
          }
          // 级联删除投影中该主权限的子权限
          const prefix = key + "|";
          for (const k of [...child.keys()]) {
            if (k.startsWith(prefix)) {
              child.delete(k);
              childKeys.push({ key: k, effect: "remove" });
            }
          }
        }
      }
      taskEffects.set(task.taskId, {
        taskId: task.taskId,
        mainKeys,
        childKeys
      });
      continue;
    }

    // grant / adjust：写入主权限
    for (const res of task.resources) {
      for (const op of task.operationCodes) {
        const key = mainKeyOf(
          task.domainCode,
          task.resourceTypeCode,
          task.scopeMode,
          res.resourceCode,
          res.codeType,
          op
        );
        // R11：INSTANCE + 被 ALL 覆盖（当前投影）+ 无直接记录（当前投影）+ 未显式保留 -> 跳过
        if (task.scopeMode === "INSTANCE") {
          const allKey = mainKeyOf(
            task.domainCode,
            task.resourceTypeCode,
            "ALL",
            null,
            null,
            op
          );
          const hadDirect = main.has(key);
          const allCovered = main.has(allKey);
          if (allCovered && !hadDirect && !task.keepDirectWhenAllCovered) {
            mainKeys.push({ key, effect: "redundantSkipped" });
            continue;
          }
        }
        const existing = main.get(key);
        const baseExisting = mainBaseline.get(key);
        const draft = buildMainDraft(
          task,
          res.resourceCode,
          res.codeType,
          res.resourceName,
          op,
          // P1-1：remove 后 re-add 时 main 投影无该键，回退 baseline 保留服务端 id
          existing?.id ?? baseExisting?.id ?? null
        );
        if (existing) {
          if (
            existing.conditionCode === draft.conditionCode &&
            existing.canGrant === draft.canGrant
          ) {
            mainKeys.push({ key, effect: "noChange" });
          } else {
            main.set(key, draft);
            mainKeys.push({ key, effect: "update" });
          }
        } else {
          main.set(key, draft);
          mainKeys.push({ key, effect: "add" });
        }
      }
    }

    // 子权限：完整集合替换（先删前缀不在期望集合的子项，再写入期望集合）
    // P1-2：R11 跳过的父权限（main 无该键）不应用子权限，避免孤儿草稿无法解析父 ID
    for (const group of task.children) {
      if (!main.has(group.parentKey)) continue;
      const prefix = group.parentKey + "|";
      const desired = new Map<string, DraftPermission>();
      for (const c of group.children) {
        const childK = childPermCellKey(group.parentKey, {
          domainCode: c.domainCode,
          resourceTypeCode: c.resourceTypeCode,
          scopeMode: c.scopeMode,
          resourceCode: c.resourceCode,
          codeType: c.codeType,
          operationCode: c.operationCode
        });
        desired.set(childK, c);
      }
      // 删除当前投影中前缀下不在期望集合的子项
      for (const ck of [...child.keys()]) {
        if (ck.startsWith(prefix) && !desired.has(ck)) {
          child.delete(ck);
          childKeys.push({ key: ck, effect: "remove" });
        }
      }
      // 写入期望集合（P2-4：effect 相对当前投影判定，id 回退投影/baseline）
      for (const [ck, c] of desired) {
        const projExisting = child.get(ck);
        const baseExisting = childBaseline.get(ck);
        const childDraft: DraftPermission = {
          ...c,
          id: c.id ?? projExisting?.id ?? baseExisting?.id ?? null
        };
        child.set(ck, childDraft);
        if (projExisting) {
          if (
            projExisting.conditionCode === childDraft.conditionCode &&
            projExisting.canGrant === childDraft.canGrant
          ) {
            childKeys.push({ key: ck, effect: "noChange" });
          } else {
            childKeys.push({ key: ck, effect: "update" });
          }
        } else {
          childKeys.push({ key: ck, effect: "add" });
        }
      }
    }

    taskEffects.set(task.taskId, { taskId: task.taskId, mainKeys, childKeys });
  }

  return { mainDraft: main, childDraft: child, taskEffects };
}
