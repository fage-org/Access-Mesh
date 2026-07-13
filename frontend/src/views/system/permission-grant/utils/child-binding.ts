/**
 * ChildPermissionInline 的 pgStore adapter。
 *
 * T-FE-014 场景：直接读写 pgStore 的 childDraft/childBaseline，行为等价于
 * 原 ChildPermissionDrawer 的 buildChildContext/onToggle（P1-5/P1-8 评审修复保留）。
 *
 * binding 契约（getCell + toggleCell）满足 ChildPermissionInline 组件要求；
 * 额外暴露 getCellKey 供 index.vue 在 onOpenSetting 时算 childKey 构造
 * AdditionalSettingContext（childKey 算法属草稿模型知识，不进组件 binding 契约）。
 *
 * T-FE-026 授权弹窗将提供自己的 adapter（基于弹窗任务快照），
 * 确认加入变更才合并到页面草稿，取消直接丢弃局部 adapter。
 */
import type { usePermissionGrant } from "./hook";
import {
  permCellKey,
  type PermissionCellContext,
  type CellState,
  type ChildPermissionContext
} from "./types";
import type {
  ChildCellInput,
  ChildPermissionBinding
} from "@/components/ChildPermissionInline";

export type PgChildBinding = ChildPermissionBinding & {
  /** 算子权限稳定键（供 index.vue 构造 AdditionalSettingContext.childKey） */
  getCellKey(input: ChildCellInput): string;
  /** 算父权限稳定键（供 index.vue 构造 AdditionalSettingContext.key） */
  getParentKey(): string;
};

/**
 * 创建 pgStore adapter。
 *
 * @param store pgStore 实例
 * @param getContext 获取当前子权限上下文（响应式读取，每次调用取最新）
 */
export function createChildBinding(
  store: ReturnType<typeof usePermissionGrant>,
  getContext: () => ChildPermissionContext | null
): PgChildBinding {
  /** 父权限稳定键（与原 ChildPermissionDrawer.parentKey 算法一致） */
  function parentKey(): string {
    const ctx = getContext();
    if (!ctx) return "";
    const p = ctx.parent;
    return permCellKey({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: p.resourceTypeCode,
      scopeMode: p.scopeMode,
      resourceCode: p.resourceCode,
      codeType: p.codeType,
      operationCode: p.operationCode
    });
  }

  function getCellKey(input: ChildCellInput): string {
    return (
      parentKey() +
      "|" +
      permCellKey({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: input.childResourceTypeCode,
        scopeMode: input.scopeMode,
        resourceCode: input.resourceCode,
        codeType: input.codeType,
        operationCode: input.operationCode
      })
    );
  }

  function getCell(input: ChildCellInput): PermissionCellContext {
    const ck = getCellKey(input);
    const inDraft = store.childDraft.value.has(ck);
    const inBase = store.childBaseline.value.has(ck);
    let state: CellState = "UNAUTHORIZED";
    let draft = store.childDraft.value.get(ck) ?? null;
    if (inDraft && inBase) {
      const d = store.childDraft.value.get(ck)!;
      const b = store.childBaseline.value.get(ck)!;
      state =
        d.conditionCode !== b.conditionCode || d.canGrant !== b.canGrant
          ? "MODIFIED"
          : "GRANTED";
      draft = d;
    } else if (inDraft) {
      state = "PENDING_ADD";
      draft = store.childDraft.value.get(ck)!;
    } else if (inBase) {
      state = "PENDING_REMOVE";
      draft = store.childBaseline.value.get(ck)!;
    }
    // ALL 覆盖检查：实例单元格未授权时，查同操作 ALL 授权
    let allCovered = false;
    if (input.scopeMode === "INSTANCE" && state === "UNAUTHORIZED") {
      const allKey = getCellKey({
        ...input,
        scopeMode: "ALL",
        resourceCode: null,
        codeType: null
      });
      if (store.childDraft.value.has(allKey)) {
        state = "ALL_COVERED";
        allCovered = true;
      }
    }
    const condCode = draft?.conditionCode;
    const condSummary = condCode
      ? (store.conditions.value.find(c => c.code === condCode)?.name ??
        condCode)
      : null;
    // P1-8：子权限新增也需经过 grantableByOperator（操作者能力）
    const { grantable, reason } = store.isGrantableByOperator(
      input.childResourceTypeCode,
      input.operationCode
    );
    const isReadOnly = getContext()?.readonly ?? true;
    return {
      state,
      draft,
      allCovered,
      grantableByOperator: grantable,
      denyReason: reason,
      readonly: isReadOnly,
      childCount: 0,
      conditionSummary: condSummary
    };
  }

  function toggleCell(input: ChildCellInput): void {
    const ctx = getContext();
    if (!ctx) return;
    store.toggleChildCell(
      parentKey(),
      ctx.parent,
      input.childResourceTypeCode,
      input.scopeMode,
      input.resourceCode,
      input.codeType,
      input.operationCode
    );
  }

  return { getCell, toggleCell, getCellKey, getParentKey: parentKey };
}
