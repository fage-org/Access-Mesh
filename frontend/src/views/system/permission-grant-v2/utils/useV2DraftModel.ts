/**
 * V2 草稿模型 composable（T-FE-031）。
 *
 * 范围：baseline 投影 + grantTasks replay + 单项任务原子操作 + 单元格显示解析
 *   + 展开单焦点控制。
 *
 * 设计依据：
 * - docs/design/frontend/permission-grant-state-model.md §0（单一事实源：baseline + grantTasks -> replay）
 * - §2.2 P1-2 方案 A：以 GrantVariantId 为身份，多 OR 分支并存
 * - §3.4 R11 重叠语义（T-FE-031 实现基础 add/update/remove；redundantSkipped/keepDirect 留 T-FE-032）
 * - interaction §4.1 单元格点击分态语义
 *
 * 任务撤销语义（plan Q5 修正）：
 * - baseline 分支首次编辑 -> commit update；后续编辑同一任务 -> replace（保 taskId/createdAt/位置）
 * - 新增分支后继续配置 -> replace 原 grant 任务（保留 proposedVariantId）
 * - 撤销 PENDING_ADD -> removeGrantTask（产生该变体的 grant 任务）
 * - 恢复 PENDING_REMOVE -> removeGrantTask（对应 remove 任务）
 * - 恢复 MODIFIED -> removeGrantTask（对应 update 任务，撤销属性修改）
 * - 移除 baseline 分支 -> commit remove 任务
 * - baseline 分支改回原值 -> removeGrantTask（清理 noChange，避免 hasDraft 恒真）
 * - 任务身份从 grantTasks + commands 派生，不维护第二份可漂移映射
 *
 * 能力门控（plan Q6 修正 + P1 修复）：以 !readonly 为总门 + 操作者 canManage + 类型/操作集合粗筛（fail-closed）。
 *   addBranch 在 mutation 边界再次检查 grantableByOperator（不只依赖按钮显示）。
 *   canGrant 不约束回收：已有分支即使不可新增，也允许编辑/移除/恢复修改。
 */
import { ref, computed, type ComputedRef, type Ref } from "vue";
import type { RolePermissionItem } from "@/api/permission-grant";
import type { PermCellKey } from "@/utils/permission-grant-types";
import type {
  GrantVariantId,
  V2DraftState,
  V2DraftPermission,
  V2GrantTaskSnapshot,
  V2ReplayResult,
  VariantCommand,
  PermCellKeyStr,
  CellSummary
} from "./v2-types";
import {
  buildBaseline,
  generateVariantId,
  permCellKeyStr,
  normalizeConditionCode
} from "./grant-variant";
import { replayGrantTasks } from "./replay";
import { aggregateCell } from "./aggregate";
import { buildCellDisplay, type CellDisplay } from "./cell-summary";

interface DraftContext {
  domainCode: string;
  roleExternalId: string;
  roleTypeCode: string;
}

interface CapabilityInput {
  readonly: boolean;
  operatorCanManage: boolean;
  grantableResourceTypeCodes: string[];
  grantableOperationCodes: string[];
}

export interface AddBranchResult {
  ok: boolean;
  reason?: string;
}

export function useV2DraftModel(
  baselineItems: Ref<RolePermissionItem[]>,
  roleContext: ComputedRef<DraftContext>,
  capability: ComputedRef<CapabilityInput>
) {
  const grantTasks = ref<V2GrantTaskSnapshot[]>([]);
  /** 展开单焦点：PermCellKeyStr（已含 resourceTypeCode），同时同资源类型最多一个 */
  const expandedCell = ref<PermCellKeyStr | null>(null);

  const baselineState = computed<V2DraftState>(() =>
    buildBaseline(baselineItems.value)
  );

  const replayResult = computed<V2ReplayResult>(() =>
    replayGrantTasks(baselineState.value, grantTasks.value)
  );

  const mainDraft = computed(() => replayResult.value.mainDraft);
  const mainIndex = computed(() => replayResult.value.mainIndex);
  const childDraft = computed(() => replayResult.value.childDraft);
  const childIndex = computed(() => replayResult.value.childIndex);

  const hasDraft = computed(() => grantTasks.value.length > 0);

  // ========== 任务原子操作 ==========

  function commitGrantTask(task: V2GrantTaskSnapshot) {
    grantTasks.value = [...grantTasks.value, task];
  }

  /** 原位替换（保 taskId / createdAt / 数组位置） */
  function replaceGrantTask(taskId: string, newTask: V2GrantTaskSnapshot) {
    grantTasks.value = grantTasks.value.map(t =>
      t.taskId === taskId
        ? { ...newTask, taskId: t.taskId, createdAt: t.createdAt }
        : t
    );
  }

  function removeGrantTask(taskId: string) {
    grantTasks.value = grantTasks.value.filter(t => t.taskId !== taskId);
  }

  function resetDraft() {
    grantTasks.value = [];
    expandedCell.value = null;
  }

  // ========== 任务身份派生（从 grantTasks + commands，不维护第二份映射） ==========

  /** 判断变体是否为新增（proposedVariantId，不在 baseline） */
  function isPendingAdd(variantId: GrantVariantId): boolean {
    return (
      !baselineState.value.mainMap.has(variantId) &&
      !baselineState.value.childMap.has(variantId)
    );
  }

  /** 找产生/编辑该变体的任务（grant.proposedVariantId 或 update.targetVariantId） */
  function findEditTaskForVariant(
    variantId: GrantVariantId
  ): V2GrantTaskSnapshot | null {
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (cmd.kind === "grant" && cmd.proposedVariantId === variantId)
          return t;
        if (cmd.kind === "update" && cmd.targetVariantId === variantId)
          return t;
      }
    }
    return null;
  }

  /** 找移除该变体的 remove 任务 */
  function findRemoveTaskForVariant(
    variantId: GrantVariantId
  ): V2GrantTaskSnapshot | null {
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (cmd.kind === "remove" && cmd.targetVariantId === variantId)
          return t;
      }
    }
    return null;
  }

  /** 判断该变体是否有 update 任务（用于 modified 状态判定） */
  function hasUpdateTask(variantId: GrantVariantId): boolean {
    const t = findEditTaskForVariant(variantId);
    return (
      !!t &&
      t.commands.some(
        c => c.kind === "update" && c.targetVariantId === variantId
      )
    );
  }

  // ========== 任务构建 ==========

  function buildTask(
    cell: PermCellKey,
    commands: VariantCommand[],
    intent: "grant" | "adjust" | "remove"
  ): V2GrantTaskSnapshot {
    const ctx = roleContext.value;
    return {
      taskId: `task-${generateVariantId()}`,
      domainCode: ctx.domainCode,
      roleExternalId: ctx.roleExternalId,
      roleTypeCode: ctx.roleTypeCode,
      resourceTypeCode: cell.resourceTypeCode,
      scopeMode: cell.scopeMode,
      intent,
      operationCodes: [cell.operationCode],
      resources: [
        {
          resourceCode: cell.resourceCode,
          codeType: cell.codeType,
          resourceName: null
        }
      ],
      commands,
      createdAt: Date.now()
    };
  }

  // ========== 单项操作 ==========

  /** 新增无条件分支（UNAUTHORIZED + grantable 点击主区域） */
  function grantUnconditional(
    cell: PermCellKey,
    resourceName: string | null
  ): void {
    const grant = grantableByOperator(cell);
    if (!grant.ok) return;
    const proposedVariantId = generateVariantId();
    const cmd: VariantCommand = {
      kind: "grant",
      cell,
      proposedVariantId,
      conditionCode: null,
      canGrant: false,
      resourceName,
      keepDirectWhenAllCovered: false
    };
    commitGrantTask(buildTask(cell, [cmd], "grant"));
  }

  /**
   * 添加条件分支（分支列表"添加分支"按钮）。
   * P1 修复：mutation 边界再次检查 grantableByOperator（不只依赖按钮显示）。
   * 重复条件基础阻断（同 PermCellKey + 规范化 conditionCode 已存在 -> 拒绝）。
   */
  function addBranch(
    cell: PermCellKey,
    conditionCode: string | null,
    canGrant: boolean,
    resourceName: string | null
  ): AddBranchResult {
    const grant = grantableByOperator(cell);
    if (!grant.ok) {
      return { ok: false, reason: grant.reason ?? "不可新增" };
    }
    const normalized = normalizeConditionCode(conditionCode);
    const key = permCellKeyStr(cell);
    const existing = mainIndex.value.get(key) ?? [];
    for (const vid of existing) {
      const v = mainDraft.value.get(vid);
      if (v && normalizeConditionCode(v.conditionCode) === normalized) {
        return {
          ok: false,
          reason: normalized
            ? `条件分支「${normalized}」已存在`
            : "无条件分支已存在"
        };
      }
    }
    const proposedVariantId = generateVariantId();
    const cmd: VariantCommand = {
      kind: "grant",
      cell,
      proposedVariantId,
      conditionCode: normalized,
      canGrant,
      resourceName,
      keepDirectWhenAllCovered: false
    };
    commitGrantTask(buildTask(cell, [cmd], "grant"));
    return { ok: true };
  }

  /**
   * 编辑分支 conditionCode/canGrant（update 保持 variantId）。
   * - baseline 分支改回原值 -> removeGrantTask（清理 noChange，避免 hasDraft 恒真）
   * - 新增分支（proposedVariantId）-> replace 原 grant 任务
   * - baseline 分支首次编辑 -> commit update
   * - 同一 update 任务再次编辑 -> replace
   */
  function updateVariant(
    variantId: GrantVariantId,
    conditionCode: string | null,
    canGrant: boolean,
    cell: PermCellKey
  ): void {
    if (capability.value.readonly) return;
    const normalized = normalizeConditionCode(conditionCode);

    // baseline 分支改回原值 -> 撤销 update 任务（清理 noChange）
    const baseline = baselineState.value.mainMap.get(variantId);
    if (baseline && !isPendingAdd(variantId)) {
      const baselineCond = normalizeConditionCode(baseline.conditionCode);
      if (baselineCond === normalized && baseline.canGrant === canGrant) {
        if (hasUpdateTask(variantId)) {
          const t = findEditTaskForVariant(variantId);
          if (t) removeGrantTask(t.taskId);
          return;
        }
      }
    }

    const existing = findEditTaskForVariant(variantId);
    if (existing) {
      const newCommands = existing.commands.map(cmd => {
        if (cmd.kind === "grant" && cmd.proposedVariantId === variantId) {
          return { ...cmd, conditionCode: normalized, canGrant };
        }
        if (cmd.kind === "update" && cmd.targetVariantId === variantId) {
          return { ...cmd, conditionCode: normalized, canGrant };
        }
        return cmd;
      });
      replaceGrantTask(existing.taskId, { ...existing, commands: newCommands });
    } else {
      const cmd: VariantCommand = {
        kind: "update",
        targetVariantId: variantId,
        conditionCode: normalized,
        canGrant
      };
      commitGrantTask(buildTask(cell, [cmd], "adjust"));
    }
  }

  /**
   * 移除分支（分支列表"撤销此分支"）。
   * - 新增分支（PENDING_ADD）-> removeGrantTask（撤销产生它的 grant 任务）
   * - baseline 分支 -> commit remove 任务（已有则幂等跳过）
   */
  function removeVariant(variantId: GrantVariantId, cell: PermCellKey): void {
    if (capability.value.readonly) return;
    if (isPendingAdd(variantId)) {
      const t = findEditTaskForVariant(variantId);
      if (t) removeGrantTask(t.taskId);
      return;
    }
    if (findRemoveTaskForVariant(variantId)) return; // 幂等
    const cmd: VariantCommand = { kind: "remove", targetVariantId: variantId };
    commitGrantTask(buildTask(cell, [cmd], "remove"));
  }

  /** 恢复 PENDING_REMOVE（撤销对应 remove 任务） */
  function restoreVariant(variantId: GrantVariantId): void {
    if (capability.value.readonly) return;
    const t = findRemoveTaskForVariant(variantId);
    if (t) removeGrantTask(t.taskId);
  }

  /** 恢复 MODIFIED（撤销对应 update 任务，恢复 baseline 属性） */
  function restoreModify(variantId: GrantVariantId): void {
    if (capability.value.readonly) return;
    const t = findEditTaskForVariant(variantId);
    if (!t) return;
    if (
      t.commands.some(
        c => c.kind === "update" && c.targetVariantId === variantId
      )
    ) {
      removeGrantTask(t.taskId);
    }
  }

  // ========== 能力门控 + 显示解析 ==========

  /** 操作者授予能力（fail-closed 粗筛；canGrant 不约束回收） */
  function grantableByOperator(cell: PermCellKey): {
    ok: boolean;
    reason: string | null;
  } {
    const cap = capability.value;
    if (cap.readonly) return { ok: false, reason: "只读" };
    if (!cap.operatorCanManage)
      return { ok: false, reason: "操作者无管理能力" };
    if (!cap.grantableResourceTypeCodes.includes(cell.resourceTypeCode))
      return { ok: false, reason: "该资源类型不可授予" };
    if (!cap.grantableOperationCodes.includes(cell.operationCode))
      return { ok: false, reason: "该操作不可授予" };
    return { ok: true, reason: null };
  }

  /** 聚合单元格摘要（供测试与外部消费） */
  function cellSummary(cell: PermCellKey): CellSummary {
    const key = permCellKeyStr(cell);
    const draftVariants = (mainIndex.value.get(key) ?? [])
      .map(vid => mainDraft.value.get(vid))
      .filter((v): v is V2DraftPermission => !!v);
    const baselineVariants = (baselineState.value.mainIndex.get(key) ?? [])
      .map(vid => baselineState.value.mainMap.get(vid))
      .filter((v): v is V2DraftPermission => !!v);
    return aggregateCell(draftVariants, baselineVariants);
  }

  /** 解析单元格完整显示数据 */
  function resolveCell(cell: PermCellKey): CellDisplay {
    const summary = cellSummary(cell);
    const grant = grantableByOperator(cell);
    return buildCellDisplay({
      cell,
      summary,
      mainIndex: mainIndex.value,
      baselineIndex: baselineState.value.mainIndex,
      baselineMap: baselineState.value.mainMap,
      grantableByOperator: grant.ok,
      denyReason: grant.reason,
      expanded: expandedCell.value === permCellKeyStr(cell)
    });
  }

  // ========== 点击主区域分态路由（interaction §4.1） ==========

  function handleMainClick(
    cell: PermCellKey,
    resourceName: string | null
  ): void {
    if (capability.value.readonly) return;
    const display = resolveCell(cell);
    const { summary } = display;

    if (summary.effective === "UNAUTHORIZED") {
      // 全分支待移除 -> 恢复全部 baseline 分支
      if (summary.draftChange === "REMOVE") {
        for (const v of display.baselineVariants) restoreVariant(v.variantId);
        return;
      }
      // 无分支 + grantable -> 新增无条件分支
      if (display.grantableByOperator) {
        grantUnconditional(cell, resourceName);
      }
      return;
    }

    // DIRECT/CONDITIONAL
    // PENDING_ADD 单分支（新增后无其他分支）-> 撤销该新增
    if (
      summary.draftChange === "ADD" &&
      summary.variants.length === 1 &&
      isPendingAdd(summary.variants[0].variantId)
    ) {
      removeVariant(summary.variants[0].variantId, cell);
      return;
    }
    // 其余（有授权 / MODIFIED / 部分移除）-> 展开分支列表
    toggleExpand(cell);
  }

  // ========== 展开控制（单焦点） ==========

  function toggleExpand(cell: PermCellKey): boolean {
    const key = permCellKeyStr(cell);
    if (expandedCell.value === key) {
      expandedCell.value = null;
      return false;
    }
    expandedCell.value = key;
    return true;
  }

  function collapseExpanded(): void {
    expandedCell.value = null;
  }

  return {
    grantTasks,
    expandedCell,
    baselineState,
    replayResult,
    mainDraft,
    mainIndex,
    childDraft,
    childIndex,
    hasDraft,
    commitGrantTask,
    replaceGrantTask,
    removeGrantTask,
    resetDraft,
    isPendingAdd,
    grantUnconditional,
    addBranch,
    updateVariant,
    removeVariant,
    restoreVariant,
    restoreModify,
    grantableByOperator,
    cellSummary,
    resolveCell,
    handleMainClick,
    toggleExpand,
    collapseExpanded
  };
}
