/**
 * V2 GrantVariant 操作与索引维护（T-FE-030）。
 *
 * 职责：UUID 生成、V2DraftState 增删、PermCellKey 索引维护、baseline 转换。
 * 不含 replay（见 replay.ts）、聚合（见 aggregate.ts）。
 *
 * 设计依据：state-model §2.2 P1-2（多条件模型）、§7.1（不变量）
 * - GrantVariantId 类型从 v2-types import，不在此重复定义
 * - conditionCode 内部 null=无条件，"" 规范化为 null（"" 仅保存层表达清除）
 * - 子权限键含父变体，避免不同父分支下相同子单元碰撞
 * - baseline 转换两遍：先写 maps，再按 dependOn 构建索引（O(n)，不用 items.find O(n²)）；
 *   父变体不存在则报错，不生成孤儿子权限
 */
import type { RolePermissionItem } from "@/api/permission-grant";
import { permCellKey, type PermCellKey } from "@/utils/permission-grant-types";
import type {
  GrantVariantId,
  V2DraftPermission,
  V2DraftState,
  PermCellKeyStr,
  ChildPermCellKeyStr
} from "./v2-types";

/** 生成临时 variantId（UUID，任务构建时调用，不在 replay） */
export function generateVariantId(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  // fallback（非安全上下文）
  return (
    "v-" +
    Date.now().toString(36) +
    "-" +
    Math.random().toString(36).slice(2, 10)
  );
}

/** 规范化 conditionCode：内部 null=无条件，"" 视为 null（""仅保存层表达清除） */
export function normalizeConditionCode(code: string | null): string | null {
  return code === "" ? null : code;
}

/** PermCellKey -> 字符串（复用共享 permCellKey） */
export function permCellKeyStr(k: PermCellKey): PermCellKeyStr {
  return permCellKey(k);
}

/** 子权限键 = parentVariantId + "|" + childPermCellKeyStr（含父变体） */
export function childPermCellKeyStr(
  parentVariantId: GrantVariantId,
  child: PermCellKey
): ChildPermCellKeyStr {
  return `${parentVariantId}|${permCellKey(child)}`;
}

/** 创建空 V2DraftState */
export function createV2DraftState(): V2DraftState {
  return {
    mainMap: new Map(),
    mainIndex: new Map(),
    childMap: new Map(),
    childIndex: new Map()
  };
}

/** 向 state 写入主权限变体（身份坐标不可变 + dependOn=null + main/child 类别唯一） */
export function upsertMainVariant(
  state: V2DraftState,
  perm: V2DraftPermission
): void {
  if (perm.dependOn !== null) {
    throw new Error(
      `upsertMainVariant 要求 dependOn === null（variantId=${perm.variantId}）`
    );
  }
  // 全局唯一：ID 已存在于 child 类别 -> 类别冲突
  if (state.childMap.has(perm.variantId)) {
    throw new Error(
      `variantId ${perm.variantId} 身份冲突：已存在于 childMap（main/child 类别不同）`
    );
  }
  const newKey = permCellKeyStr(perm);
  const existing = state.mainMap.get(perm.variantId);
  if (existing) {
    const oldKey = permCellKeyStr(existing);
    if (oldKey !== newKey) {
      throw new Error(
        `variantId ${perm.variantId} 坐标不可变：${oldKey} -> ${newKey}`
      );
    }
    // 同类同坐标：幂等覆盖
    state.mainMap.set(perm.variantId, perm);
    return;
  }
  // 新增
  const ids = state.mainIndex.get(newKey) ?? [];
  ids.push(perm.variantId);
  state.mainIndex.set(newKey, ids);
  state.mainMap.set(perm.variantId, perm);
}

/** 删除主权限变体（维护索引 + 级联该父分支子权限） */
export function removeMainVariant(
  state: V2DraftState,
  variantId: GrantVariantId
): boolean {
  const perm = state.mainMap.get(variantId);
  if (!perm) return false;
  const key = permCellKeyStr(perm);
  state.mainMap.delete(variantId);
  removeIndexEntry(state.mainIndex, key, variantId);
  // 级联删除该父变体的全部子权限（仅该父分支，不影响同 cell 其他变体）
  cascadeRemoveChildren(state, variantId);
  return true;
}

/** 向 state 写入子权限变体（父变体存在 + 身份坐标不可变 + main/child 类别唯一） */
export function upsertChildVariant(
  state: V2DraftState,
  perm: V2DraftPermission
): void {
  if (perm.dependOn === null) {
    throw new Error("子权限 dependOn 不能为 null");
  }
  if (!state.mainMap.has(perm.dependOn)) {
    throw new Error(`孤儿子权限：父变体 ${perm.dependOn} 不存在于 mainMap`);
  }
  // 全局唯一：ID 已存在于 main 类别 -> 类别冲突
  if (state.mainMap.has(perm.variantId)) {
    throw new Error(
      `variantId ${perm.variantId} 身份冲突：已存在于 mainMap（main/child 类别不同）`
    );
  }
  const newKey = childPermCellKeyStr(perm.dependOn, perm);
  const existing = state.childMap.get(perm.variantId);
  if (existing) {
    const oldKey =
      existing.dependOn !== null
        ? childPermCellKeyStr(existing.dependOn, existing)
        : "";
    if (oldKey !== newKey) {
      throw new Error(
        `variantId ${perm.variantId} 坐标/父变体不可变：${oldKey} -> ${newKey}`
      );
    }
    state.childMap.set(perm.variantId, perm);
    return;
  }
  const ids = state.childIndex.get(newKey) ?? [];
  ids.push(perm.variantId);
  state.childIndex.set(newKey, ids);
  state.childMap.set(perm.variantId, perm);
}

/** 删除子权限变体（维护索引） */
export function removeChildVariant(
  state: V2DraftState,
  variantId: GrantVariantId
): boolean {
  const perm = state.childMap.get(variantId);
  if (!perm || perm.dependOn === null) return false;
  const key = childPermCellKeyStr(perm.dependOn, perm);
  state.childMap.delete(variantId);
  removeIndexEntry(state.childIndex, key, variantId);
  return true;
}

/** 级联删除指定父变体的全部子权限（仅该父分支） */
export function cascadeRemoveChildren(
  state: V2DraftState,
  parentVariantId: GrantVariantId
): number {
  const toRemove: GrantVariantId[] = [];
  for (const [vid, perm] of state.childMap) {
    if (perm.dependOn === parentVariantId) toRemove.push(vid);
  }
  for (const vid of toRemove) removeChildVariant(state, vid);
  return toRemove.length;
}

/** 索引条目移除辅助（空则删 key） */
function removeIndexEntry(
  index: Map<string, GrantVariantId[]>,
  key: string,
  variantId: GrantVariantId
): void {
  const ids = index.get(key);
  if (!ids) return;
  const next = ids.filter(id => id !== variantId);
  if (next.length === 0) index.delete(key);
  else index.set(key, next);
}

/**
 * baseline 转换：raw RolePermissionItem[] -> V2DraftState。
 * 两遍转换（O(n)，不用 items.find O(n²)）：
 * 1) 建 variantId -> V2DraftPermission，写 mainMap/childMap
 * 2) 按 dependOn 构建父子索引；父变体不存在则抛错（不生成孤儿子权限）
 */
export function buildBaseline(items: RolePermissionItem[]): V2DraftState {
  const state = createV2DraftState();
  const pendingChildren: Array<{
    perm: V2DraftPermission;
    parentVariantId: GrantVariantId;
  }> = [];

  // 第一遍：写 maps
  for (const item of items) {
    const perm: V2DraftPermission = {
      domainCode: item.domainCode,
      resourceTypeCode: item.resourceTypeCode,
      scopeMode: item.scopeMode,
      resourceCode: item.resourceCode,
      codeType: item.codeType,
      operationCode: item.operationCode,
      variantId: item.id,
      conditionCode: normalizeConditionCode(item.conditionCode),
      canGrant: item.canGrant,
      dependOn: item.dependOn,
      grantSource: item.grantSource,
      resourceName: item.resourceName
    };
    if (item.dependOn === null) {
      state.mainMap.set(perm.variantId, perm);
    } else {
      state.childMap.set(perm.variantId, perm);
      pendingChildren.push({
        perm,
        parentVariantId: item.dependOn
      });
    }
  }

  // 第二遍：主权限索引
  for (const perm of state.mainMap.values()) {
    const key = permCellKeyStr(perm);
    const ids = state.mainIndex.get(key) ?? [];
    ids.push(perm.variantId);
    state.mainIndex.set(key, ids);
  }

  // 第二遍：子权限索引 + 父变体校验
  for (const { perm, parentVariantId } of pendingChildren) {
    const parent = state.mainMap.get(parentVariantId);
    if (!parent) {
      throw new Error(
        `孤儿子权限：variantId=${perm.variantId} 的父变体 ${parentVariantId} 不存在于 baseline`
      );
    }
    perm.dependOn = parent.variantId;
    const key = childPermCellKeyStr(parent.variantId, perm);
    const ids = state.childIndex.get(key) ?? [];
    ids.push(perm.variantId);
    state.childIndex.set(key, ids);
  }

  return state;
}
