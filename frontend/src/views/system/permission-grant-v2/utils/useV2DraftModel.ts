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
  CellSummary,
  TaskResource,
  V2FailedChildOp
} from "./v2-types";
import {
  buildBaseline,
  generateVariantId,
  permCellKeyStr,
  normalizeConditionCode,
  childPermCellKeyStr,
  findChildVariantByParentCellCondition
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
  capability: ComputedRef<CapabilityInput>,
  failedChildren: Ref<V2FailedChildOp[]> = ref([])
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
  /**
   * 子权限草稿（computed = replay + failedChildren overlay）。
   * 投影顺序：baseline -> grantTasks replay -> failedChildren overlay（add->set / remove->delete）。
   * childIndex 保持 replay（不含 overlay）；子权限矩阵展开用 childVariantsOf 遍历 childDraft。
   */
  const childDraft = computed<Map<GrantVariantId, V2DraftPermission>>(() => {
    const m = new Map(replayResult.value.childDraft);
    for (const fop of failedChildren.value) {
      if (fop.op === "add") m.set(fop.child.variantId, fop.child);
      else m.delete(fop.child.variantId);
    }
    return m;
  });
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

  /**
   * 从任务中删除满足 predicate 的 commands（P1 修复：批量任务逐 command 撤销）。
   * 任务为空时才删整个任务，避免连带撤销同批其他分支。
   */
  function removeCommandsFromTask(
    taskId: string,
    predicate: (cmd: VariantCommand) => boolean
  ): void {
    const t = grantTasks.value.find(x => x.taskId === taskId);
    if (!t) return;
    const remaining = t.commands.filter(cmd => !predicate(cmd));
    if (remaining.length === 0) {
      removeGrantTask(taskId);
      return;
    }
    replaceGrantTask(taskId, { ...t, commands: remaining });
    // P1 修复：删 command 后若任务剩余全无有效变更（noChange/redundantSkipped），
    // 清理整个任务，避免矩阵无差异但 hasDraft 虚假为 true
    const effects = replayResult.value.taskEffects.get(taskId);
    if (
      effects &&
      effects.commands.every(
        c => c.effect === "noChange" || c.effect === "redundantSkipped"
      )
    ) {
      removeGrantTask(taskId);
    }
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

  /**
   * 构建批量任务快照（1 任务多 commands，state-model §3.5 / interaction §4.3）。
   * operationCodes/resources 为展示用聚合（去重）；执行以 commands 为准。
   * cells 须同 resourceTypeCode（同资源类型下批量）；scopeMode 取首个（展示用）。
   */
  function buildBatchTask(
    commands: VariantCommand[],
    intent: "grant" | "adjust" | "remove",
    cells: PermCellKey[]
  ): V2GrantTaskSnapshot {
    const ctx = roleContext.value;
    const operationCodes = Array.from(new Set(cells.map(c => c.operationCode)));
    const resourceKeySet = new Set<string>();
    const resources: TaskResource[] = [];
    for (const c of cells) {
      const rk = `${c.scopeMode}|${c.resourceCode ?? ""}|${c.codeType ?? ""}`;
      if (resourceKeySet.has(rk)) continue;
      resourceKeySet.add(rk);
      resources.push({
        resourceCode: c.resourceCode,
        codeType: c.codeType,
        resourceName: null
      });
    }
    const first = cells[0];
    return {
      taskId: `task-${generateVariantId()}`,
      domainCode: ctx.domainCode,
      roleExternalId: ctx.roleExternalId,
      roleTypeCode: ctx.roleTypeCode,
      resourceTypeCode: first?.resourceTypeCode ?? "",
      scopeMode: first?.scopeMode ?? "INSTANCE",
      intent,
      operationCodes,
      resources,
      commands,
      createdAt: Date.now()
    };
  }

  // ========== 单项操作 ==========

  /**
   * 新增无条件分支（UNAUTHORIZED + grantable 点击主区域）。
   * keepDirectWhenAllCovered：R11 被 ALL 覆盖+无直接记录时是否显式创建直接记录
   * （false=默认跳过 redundantSkipped；true=仍创建 add）。
   */
  function grantUnconditional(
    cell: PermCellKey,
    resourceName: string | null,
    keepDirectWhenAllCovered = false
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
      keepDirectWhenAllCovered
    };
    commitGrantTask(buildTask(cell, [cmd], "grant"));
  }

  /**
   * 添加条件分支（分支列表"添加分支"按钮）。
   * P1 修复：mutation 边界再次检查 grantableByOperator（不只依赖按钮显示）。
   * 重复条件基础阻断（同 PermCellKey + 规范化 conditionCode 已存在 -> 拒绝）。
   * keepDirectWhenAllCovered：R11 被 ALL 覆盖+无直接记录时是否显式创建（false=redundantSkipped）。
   */
  function addBranch(
    cell: PermCellKey,
    conditionCode: string | null,
    canGrant: boolean,
    resourceName: string | null,
    keepDirectWhenAllCovered = false
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
    // P1 修复：检查 baseline 中待移除的同条件分支，避免 remove+add 替换
    // （否则保存后旧分支子权限级联删除且产生新 ID，违反 R11 同条件应 noChange/update）
    const baselineIds = baselineState.value.mainIndex.get(key) ?? [];
    for (const vid of baselineIds) {
      const b = baselineState.value.mainMap.get(vid);
      if (
        b &&
        normalizeConditionCode(b.conditionCode) === normalized &&
        findRemoveTaskForVariant(vid)
      ) {
        return {
          ok: false,
          reason: normalized
            ? `条件分支「${normalized}」已待移除，请先恢复后编辑`
            : "无条件分支已待移除，请先恢复后编辑"
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
      keepDirectWhenAllCovered
    };
    commitGrantTask(buildTask(cell, [cmd], "grant"));
    return { ok: true };
  }

  /**
   * 编辑分支 conditionCode/canGrant（update 保持 variantId）。
   * P1-2：mutation 边界检查同 cell 其他变体（排除自身）重复 conditionCode，阻断并返回原因。
   * P1-1：改回原值/撤销 update 只删该 variant 的 command（批量任务不连累其他分支）。
   * - baseline 分支改回原值 -> 删 update command（清理 noChange）
   * - 新增分支（proposedVariantId）-> replace 原 grant 任务内该 command
   * - baseline 分支首次编辑 -> commit update
   * - 同一 update 任务再次编辑 -> replace
   */
  function updateVariant(
    variantId: GrantVariantId,
    conditionCode: string | null,
    canGrant: boolean,
    cell: PermCellKey
  ): { ok: boolean; reason?: string } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const normalized = normalizeConditionCode(conditionCode);

    // P1-2：重复条件阻断（同 cell 其他变体已存在该 conditionCode）
    const currentPerm = mainDraft.value.get(variantId);
    if (currentPerm) {
      const key = permCellKeyStr(currentPerm);
      const otherIds = (mainIndex.value.get(key) ?? []).filter(
        id => id !== variantId
      );
      for (const oid of otherIds) {
        const v = mainDraft.value.get(oid);
        if (v && normalizeConditionCode(v.conditionCode) === normalized) {
          return {
            ok: false,
            reason: normalized
              ? `条件分支「${normalized}」已存在`
              : "无条件分支已存在"
          };
        }
      }
    }

    // baseline 分支改回原值 -> 撤销该 variant 的 update command（清理 noChange）
    const baseline = baselineState.value.mainMap.get(variantId);
    if (baseline && !isPendingAdd(variantId)) {
      const baselineCond = normalizeConditionCode(baseline.conditionCode);
      if (baselineCond === normalized && baseline.canGrant === canGrant) {
        if (hasUpdateTask(variantId)) {
          const t = findEditTaskForVariant(variantId);
          if (t)
            removeCommandsFromTask(
              t.taskId,
              cmd => cmd.kind === "update" && cmd.targetVariantId === variantId
            );
        }
        return { ok: true };
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
    return { ok: true };
  }

  /**
   * 清理引用指定父变体的孤儿 child command（P2 修复：PENDING_ADD 父撤销时级联）。
   * 删除 child-grant（parentVariantId 匹配）+ 关联子变体的 child-update/child-remove。
   * replay 已阻止孤儿进 childDraft，但 command 残留会使 grantTasks 非空（hasDraft 虚假）。
   */
  function cleanOrphanChildCommands(parentVariantId: GrantVariantId): void {
    const childVariantIds = new Set<GrantVariantId>();
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (
          cmd.kind === "child-grant" &&
          cmd.parentVariantId === parentVariantId
        ) {
          childVariantIds.add(cmd.proposedVariantId);
        }
      }
    }
    if (childVariantIds.size === 0) return;
    const orphanTaskIds = new Set<string>();
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (
          (cmd.kind === "child-grant" &&
            cmd.parentVariantId === parentVariantId) ||
          ((cmd.kind === "child-update" || cmd.kind === "child-remove") &&
            childVariantIds.has(cmd.targetVariantId))
        ) {
          orphanTaskIds.add(t.taskId);
          break;
        }
      }
    }
    for (const taskId of orphanTaskIds) {
      removeCommandsFromTask(
        taskId,
        cmd =>
          (cmd.kind === "child-grant" &&
            cmd.parentVariantId === parentVariantId) ||
          ((cmd.kind === "child-update" || cmd.kind === "child-remove") &&
            childVariantIds.has(cmd.targetVariantId))
      );
    }
  }

  /**
   * 移除分支（分支列表"撤销此分支"）。
   * P1-1：PENDING_ADD 只删该 variant 的 grant command（批量任务不连累其他分支）。
   * P2-3：PENDING_ADD 父撤销时同步清理孤儿 child command（级联语义，避免 hasDraft 虚假）。
   * - baseline 分支 -> commit remove 任务（已有则幂等跳过）
   */
  function removeVariant(variantId: GrantVariantId, cell: PermCellKey): void {
    if (capability.value.readonly) return;
    if (isPendingAdd(variantId)) {
      const t = findEditTaskForVariant(variantId);
      if (t)
        removeCommandsFromTask(
          t.taskId,
          cmd => cmd.kind === "grant" && cmd.proposedVariantId === variantId
        );
      cleanOrphanChildCommands(variantId);
      return;
    }
    if (findRemoveTaskForVariant(variantId)) return; // 幂等
    const cmd: VariantCommand = { kind: "remove", targetVariantId: variantId };
    commitGrantTask(buildTask(cell, [cmd], "remove"));
  }

  /**
   * P1-2：恢复 baseline variant 后与同 cell 其他 draft 分支 conditionCode 冲突检测。
   * 恢复后 baseline conditionCode 若与另一 draft 分支重复，replay 会合并/noChange，
   * 导致新增分支消失但任务残留；故在 mutation 边界阻断并返回原因。
   */
  function checkRestoreConflict(variantId: GrantVariantId): string | null {
    const baseline = baselineState.value.mainMap.get(variantId);
    if (!baseline) return null;
    const restoredCond = normalizeConditionCode(baseline.conditionCode);
    const key = permCellKeyStr(baseline);
    const otherIds = (mainIndex.value.get(key) ?? []).filter(
      id => id !== variantId
    );
    for (const oid of otherIds) {
      const v = mainDraft.value.get(oid);
      if (v && normalizeConditionCode(v.conditionCode) === restoredCond) {
        return restoredCond
          ? `恢复后与「${restoredCond}」分支重复`
          : "恢复后与无条件分支重复";
      }
    }
    return null;
  }

  /**
   * 恢复 PENDING_REMOVE（撤销对应 remove command）。
   * P1-1：只删该 variant 的 remove command（批量任务不连累其他分支）。
   * P1-2：检查恢复后与同 cell 其他 draft 分支 conditionCode 冲突，冲突则返回失败。
   */
  function restoreVariant(variantId: GrantVariantId): {
    ok: boolean;
    reason?: string;
  } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const t = findRemoveTaskForVariant(variantId);
    if (!t) return { ok: true };
    const conflict = checkRestoreConflict(variantId);
    if (conflict) return { ok: false, reason: conflict };
    removeCommandsFromTask(
      t.taskId,
      cmd => cmd.kind === "remove" && cmd.targetVariantId === variantId
    );
    return { ok: true };
  }

  /**
   * 恢复 MODIFIED（撤销该 variant 的 update command，恢复 baseline 属性）。
   * P1-1：只删该 variant 的 update command（批量任务不连累其他分支）。
   * P1-2：检查恢复后与同 cell 其他 draft 分支 conditionCode 冲突，冲突则返回失败。
   */
  function restoreModify(variantId: GrantVariantId): {
    ok: boolean;
    reason?: string;
  } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const t = findEditTaskForVariant(variantId);
    if (!t) return { ok: true };
    if (
      !t.commands.some(
        c => c.kind === "update" && c.targetVariantId === variantId
      )
    ) {
      return { ok: true };
    }
    const conflict = checkRestoreConflict(variantId);
    if (conflict) return { ok: false, reason: conflict };
    removeCommandsFromTask(
      t.taskId,
      cmd => cmd.kind === "update" && cmd.targetVariantId === variantId
    );
    return { ok: true };
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
      // Q4：被 ALL 覆盖 + 无直接记录 + 可授予 -> 展开分支列表
      // （列表内 redundant 警告 + 跳过/仍创建），不直接 grantUnconditional，
      // 避免 replay redundantSkipped 后单元格无变化造成"点了没反应"困惑
      if (display.allCovered && display.grantableByOperator) {
        toggleExpand(cell);
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

  // ========== R11 redundant 候选判定 + 批量操作（T-FE-032） ==========

  /**
   * R11 redundant 候选：INSTANCE + 被 ALL 覆盖 + 当前 draft 无直接分支。
   * 与 replay applyGrant 的 !hasDirect + isAllCovered 判定一致。
   * 此类单元格新增分支默认 redundantSkipped；keepDirectWhenAllCovered=true 才创建。
   */
  function redundantCandidate(cell: PermCellKey): boolean {
    if (cell.scopeMode !== "INSTANCE") return false;
    const display = resolveCell(cell);
    return display.allCovered && display.summary.variants.length === 0;
  }

  /**
   * 批量授予全部（工具栏"授予全部"）。
   * 选择集内 UNAUTHORIZED + grantable + 非全待移除 -> 新增无条件分支（1 任务多 commands）。
   * redundant 候选走默认 redundantSkipped（keepDirect=false），计入 redundantCandidates 提示。
   * 已有授权的不变（仅对 UNAUTHORIZED 操作）。
   */
  function batchGrantAll(cells: PermCellKey[]): {
    granted: number;
    redundantCandidates: number;
  } {
    if (capability.value.readonly)
      return { granted: 0, redundantCandidates: 0 };
    const commands: VariantCommand[] = [];
    let redundantCandidates = 0;
    for (const cell of cells) {
      const summary = cellSummary(cell);
      if (summary.effective !== "UNAUTHORIZED") continue;
      if (summary.draftChange === "REMOVE") continue; // 全待移除走恢复
      if (!grantableByOperator(cell).ok) continue;
      if (redundantCandidate(cell)) redundantCandidates++;
      commands.push({
        kind: "grant",
        cell,
        proposedVariantId: generateVariantId(),
        conditionCode: null,
        canGrant: false,
        resourceName: null,
        keepDirectWhenAllCovered: false
      });
    }
    const granted = commands.length - redundantCandidates;
    // 全 redundantSkipped 时不提交（避免污染 grantTasks）
    if (commands.length === 0 || granted === 0)
      return { granted: 0, redundantCandidates };
    commitGrantTask(buildBatchTask(commands, "grant", cells));
    return { granted, redundantCandidates };
  }

  /**
   * 批量新增条件分支（工具栏"设置条件/转授权"侧拉确认）。
   * 为选中每个单元格新增一条统一 conditionCode + canGrant 的 OR 分支（add）。
   * 逐单元格重复条件阻断（同 cell+规范化 conditionCode 已存在 -> 收入 failures）。
   * keepDirectWhenAllCovered：redundant 候选是否显式创建。
   * 修改既有分支须逐分支（非批量），本方法只新增。
   */
  function batchAddBranch(
    cells: PermCellKey[],
    conditionCode: string | null,
    canGrant: boolean,
    keepDirectWhenAllCovered: boolean
  ): {
    added: number;
    failures: { cell: PermCellKey; reason: string }[];
    redundantSkipped: number;
  } {
    if (capability.value.readonly)
      return { added: 0, failures: [], redundantSkipped: 0 };
    const normalized = normalizeConditionCode(conditionCode);
    const commands: VariantCommand[] = [];
    const failures: { cell: PermCellKey; reason: string }[] = [];
    let redundantSkipped = 0;
    for (const cell of cells) {
      if (!grantableByOperator(cell).ok) {
        failures.push({ cell, reason: "不可新增" });
        continue;
      }
      const key = permCellKeyStr(cell);
      const existing = mainIndex.value.get(key) ?? [];
      const dup = existing.some(vid => {
        const v = mainDraft.value.get(vid);
        return v && normalizeConditionCode(v.conditionCode) === normalized;
      });
      if (dup) {
        failures.push({
          cell,
          reason: normalized
            ? `条件「${normalized}」已存在`
            : "无条件分支已存在"
        });
        continue;
      }
      // P1 修复：baseline 待移除的同条件分支会导致 remove+add 替换，阻断
      const baselineIds = baselineState.value.mainIndex.get(key) ?? [];
      const removedSameCond = baselineIds.some(vid => {
        const b = baselineState.value.mainMap.get(vid);
        return (
          b &&
          normalizeConditionCode(b.conditionCode) === normalized &&
          !!findRemoveTaskForVariant(vid)
        );
      });
      if (removedSameCond) {
        failures.push({
          cell,
          reason: normalized
            ? `条件「${normalized}」已待移除，请先恢复`
            : "无条件分支已待移除，请先恢复"
        });
        continue;
      }
      // P2 修复：redundant 候选 + !keepDirect -> 将被 replay redundantSkipped
      if (!keepDirectWhenAllCovered && redundantCandidate(cell)) {
        redundantSkipped++;
      }
      commands.push({
        kind: "grant",
        cell,
        proposedVariantId: generateVariantId(),
        conditionCode: normalized,
        canGrant,
        resourceName: null,
        keepDirectWhenAllCovered
      });
    }
    // added = 实际进 mainDraft 的（排除 redundantSkipped）
    const added =
      commands.length - (!keepDirectWhenAllCovered ? redundantSkipped : 0);
    // 无实际 add 时不提交（全 redundantSkipped 或空，避免污染 grantTasks）
    if (commands.length === 0 || added === 0)
      return { added: 0, failures, redundantSkipped };
    commitGrantTask(buildBatchTask(commands, "grant", cells));
    return { added, failures, redundantSkipped };
  }

  /**
   * 批量撤销全部（工具栏"撤销全部"）。
   * 选择集内有直接记录的单元格 -> 移除其全部 baseline 变体（1 任务多 remove commands）。
   * 幂等：已有 remove 任务的变体跳过。PENDING_ADD 草稿不在范围（非直接记录，单项撤销）。
   * canGrant 不约束回收：不检查 grantableByOperator。
   */
  function batchRemove(cells: PermCellKey[]): { removed: number } {
    if (capability.value.readonly) return { removed: 0 };
    const commands: VariantCommand[] = [];
    for (const cell of cells) {
      const key = permCellKeyStr(cell);
      const baselineIds = baselineState.value.mainIndex.get(key) ?? [];
      for (const vid of baselineIds) {
        if (findRemoveTaskForVariant(vid)) continue; // 幂等
        commands.push({ kind: "remove", targetVariantId: vid });
      }
    }
    if (commands.length === 0) return { removed: 0 };
    commitGrantTask(buildBatchTask(commands, "remove", cells));
    return { removed: commands.length };
  }

  // ========== 子权限操作（T-FE-033） ==========

  /** 该 cell 任一主权限变体是否有子权限（⌗ 聚合标记） */
  function hasChildren(cell: PermCellKey): boolean {
    const key = permCellKeyStr(cell);
    const variantIds = mainIndex.value.get(key) ?? [];
    if (variantIds.length === 0) return false;
    const variantSet = new Set<GrantVariantId>(variantIds);
    for (const p of childDraft.value.values()) {
      if (p.dependOn !== null && variantSet.has(p.dependOn)) return true;
    }
    return false;
  }

  /** 该父变体的全部子权限变体（含 overlay，按 dependOn 过滤） */
  function childVariantsOf(
    parentVariantId: GrantVariantId
  ): V2DraftPermission[] {
    return [...childDraft.value.values()].filter(
      p => p.dependOn === parentVariantId
    );
  }

  /** draft 中查找同 parent+cell+condition 的子变体（重复检测，含 overlay） */
  function findChildInDraft(
    parentVariantId: GrantVariantId,
    childCell: PermCellKey,
    conditionCode: string | null
  ): V2DraftPermission | undefined {
    const norm = normalizeConditionCode(conditionCode);
    const cellKey = permCellKeyStr(childCell);
    for (const p of childDraft.value.values()) {
      if (
        p.dependOn === parentVariantId &&
        permCellKeyStr(p) === cellKey &&
        normalizeConditionCode(p.conditionCode) === norm
      ) {
        return p;
      }
    }
    return undefined;
  }

  function findEditTaskForChildVariant(
    variantId: GrantVariantId
  ): V2GrantTaskSnapshot | null {
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (cmd.kind === "child-grant" && cmd.proposedVariantId === variantId)
          return t;
        if (cmd.kind === "child-update" && cmd.targetVariantId === variantId)
          return t;
      }
    }
    return null;
  }

  function findRemoveTaskForChildVariant(
    variantId: GrantVariantId
  ): V2GrantTaskSnapshot | null {
    for (const t of grantTasks.value) {
      for (const cmd of t.commands) {
        if (cmd.kind === "child-remove" && cmd.targetVariantId === variantId)
          return t;
      }
    }
    return null;
  }

  function hasChildUpdateTask(variantId: GrantVariantId): boolean {
    const t = findEditTaskForChildVariant(variantId);
    return (
      !!t &&
      t.commands.some(
        c => c.kind === "child-update" && c.targetVariantId === variantId
      )
    );
  }

  /**
   * 新增子权限分支（子矩阵"添加分支"）。
   * - 父变体必须在 mainDraft 投影（否则"父权限未生效"）
   * - 重复检测：同 parent+cell+condition 已存在 -> 阻断
   * - baseline 待移除同条件子变体 -> 阻断（避免 remove+add 替换）
   * - 子权限不应用 R11 redundantSkipped
   */
  function addChildBranch(
    parentVariantId: GrantVariantId,
    childCell: PermCellKey,
    conditionCode: string | null,
    canGrant: boolean,
    resourceName: string | null
  ): AddBranchResult {
    const grant = grantableByOperator(childCell);
    if (!grant.ok) return { ok: false, reason: grant.reason ?? "不可新增" };
    if (!mainDraft.value.has(parentVariantId)) {
      return { ok: false, reason: "父权限未生效，子权限不可配" };
    }
    const normalized = normalizeConditionCode(conditionCode);
    const dup = findChildInDraft(parentVariantId, childCell, normalized);
    if (dup) {
      return {
        ok: false,
        reason: normalized
          ? `子条件分支「${normalized}」已存在`
          : "无条件子分支已存在"
      };
    }
    const baselineDup = findChildVariantByParentCellCondition(
      baselineState.value,
      parentVariantId,
      childCell,
      normalized
    );
    if (baselineDup && findRemoveTaskForChildVariant(baselineDup.variantId)) {
      return {
        ok: false,
        reason: normalized
          ? `子条件分支「${normalized}」已待移除，请先恢复后编辑`
          : "无条件子分支已待移除，请先恢复后编辑"
      };
    }
    const proposedVariantId = generateVariantId();
    const cmd: VariantCommand = {
      kind: "child-grant",
      parentVariantId,
      cell: childCell,
      proposedVariantId,
      conditionCode: normalized,
      canGrant,
      resourceName
    };
    commitGrantTask(buildTask(childCell, [cmd], "grant"));
    return { ok: true };
  }

  /**
   * 编辑子权限分支 conditionCode/canGrant（child-update 保持 variantId）。
   * - 重复检测：同 parent+cell 其他子变体已存在该 conditionCode -> 阻断
   * - baseline 改回原值 -> 撤销 child-update command（清理 noChange）
   */
  function updateChildVariant(
    childVariantId: GrantVariantId,
    conditionCode: string | null,
    canGrant: boolean
  ): { ok: boolean; reason?: string } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const currentPerm = childDraft.value.get(childVariantId);
    if (!currentPerm || currentPerm.dependOn === null) {
      return { ok: false, reason: "子变体不存在" };
    }
    const normalized = normalizeConditionCode(conditionCode);
    const cellKey = permCellKeyStr(currentPerm);
    for (const p of childDraft.value.values()) {
      if (
        p.dependOn === currentPerm.dependOn &&
        p.variantId !== childVariantId &&
        permCellKeyStr(p) === cellKey &&
        normalizeConditionCode(p.conditionCode) === normalized
      ) {
        return {
          ok: false,
          reason: normalized
            ? `子条件分支「${normalized}」已存在`
            : "无条件子分支已存在"
        };
      }
    }
    const baseline = baselineState.value.childMap.get(childVariantId);
    if (baseline && !isPendingAdd(childVariantId)) {
      const baselineCond = normalizeConditionCode(baseline.conditionCode);
      if (baselineCond === normalized && baseline.canGrant === canGrant) {
        if (hasChildUpdateTask(childVariantId)) {
          const t = findEditTaskForChildVariant(childVariantId);
          if (t)
            removeCommandsFromTask(
              t.taskId,
              cmd =>
                cmd.kind === "child-update" &&
                cmd.targetVariantId === childVariantId
            );
        }
        return { ok: true };
      }
    }
    const existing = findEditTaskForChildVariant(childVariantId);
    if (existing) {
      const newCommands = existing.commands.map(cmd => {
        if (
          cmd.kind === "child-grant" &&
          cmd.proposedVariantId === childVariantId
        ) {
          return { ...cmd, conditionCode: normalized, canGrant };
        }
        if (
          cmd.kind === "child-update" &&
          cmd.targetVariantId === childVariantId
        ) {
          return { ...cmd, conditionCode: normalized, canGrant };
        }
        return cmd;
      });
      replaceGrantTask(existing.taskId, { ...existing, commands: newCommands });
    } else {
      const cmd: VariantCommand = {
        kind: "child-update",
        targetVariantId: childVariantId,
        conditionCode: normalized,
        canGrant
      };
      commitGrantTask(buildTask(currentPerm, [cmd], "adjust"));
    }
    return { ok: true };
  }

  /**
   * 移除子权限分支。
   * - PENDING_ADD 只删该 variant 的 child-grant command（批量任务不连累）
   * - baseline 子变体 -> commit child-remove（已有则幂等跳过）
   */
  function removeChildVariantOp(childVariantId: GrantVariantId): void {
    if (capability.value.readonly) return;
    if (isPendingAdd(childVariantId)) {
      const t = findEditTaskForChildVariant(childVariantId);
      if (t)
        removeCommandsFromTask(
          t.taskId,
          cmd =>
            cmd.kind === "child-grant" &&
            cmd.proposedVariantId === childVariantId
        );
      return;
    }
    if (findRemoveTaskForChildVariant(childVariantId)) return; // 幂等
    const perm = childDraft.value.get(childVariantId);
    if (!perm) return;
    const cmd: VariantCommand = {
      kind: "child-remove",
      targetVariantId: childVariantId
    };
    commitGrantTask(buildTask(perm, [cmd], "remove"));
  }

  /** 恢复 baseline 子变体后与同 parent+cell 其他 draft 子分支冲突检测 */
  function checkRestoreChildConflict(
    childVariantId: GrantVariantId
  ): string | null {
    const baseline = baselineState.value.childMap.get(childVariantId);
    if (!baseline || baseline.dependOn === null) return null;
    const restoredCond = normalizeConditionCode(baseline.conditionCode);
    const cellKey = permCellKeyStr(baseline);
    for (const p of childDraft.value.values()) {
      if (
        p.dependOn === baseline.dependOn &&
        p.variantId !== childVariantId &&
        permCellKeyStr(p) === cellKey &&
        normalizeConditionCode(p.conditionCode) === restoredCond
      ) {
        return restoredCond
          ? `恢复后与「${restoredCond}」子分支重复`
          : "恢复后与无条件子分支重复";
      }
    }
    return null;
  }

  /** 恢复 PENDING_REMOVE 子变体（撤销 child-remove command） */
  function restoreChildVariant(childVariantId: GrantVariantId): {
    ok: boolean;
    reason?: string;
  } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const t = findRemoveTaskForChildVariant(childVariantId);
    if (!t) return { ok: true };
    const conflict = checkRestoreChildConflict(childVariantId);
    if (conflict) return { ok: false, reason: conflict };
    removeCommandsFromTask(
      t.taskId,
      cmd =>
        cmd.kind === "child-remove" && cmd.targetVariantId === childVariantId
    );
    return { ok: true };
  }

  /** 恢复 MODIFIED 子变体（撤销 child-update command） */
  function restoreChildModify(childVariantId: GrantVariantId): {
    ok: boolean;
    reason?: string;
  } {
    if (capability.value.readonly) return { ok: false, reason: "只读" };
    const t = findEditTaskForChildVariant(childVariantId);
    if (!t) return { ok: true };
    if (
      !t.commands.some(
        c => c.kind === "child-update" && c.targetVariantId === childVariantId
      )
    ) {
      return { ok: true };
    }
    const conflict = checkRestoreChildConflict(childVariantId);
    if (conflict) return { ok: false, reason: conflict };
    removeCommandsFromTask(
      t.taskId,
      cmd =>
        cmd.kind === "child-update" && cmd.targetVariantId === childVariantId
    );
    return { ok: true };
  }

  /** 子权限单元格完整显示（子矩阵展开用；allCovered 简化为 false） */
  function resolveChildCell(
    parentVariantId: GrantVariantId,
    childCell: PermCellKey
  ): CellDisplay {
    const childKeyStr = permCellKeyStr(childCell);
    const draftVariants = childVariantsOf(parentVariantId).filter(
      p => permCellKeyStr(p) === childKeyStr
    );
    const baselineKey = childPermCellKeyStr(parentVariantId, childCell);
    const blIds = baselineState.value.childIndex.get(baselineKey) ?? [];
    const baselineVariants = blIds
      .map(vid => baselineState.value.childMap.get(vid))
      .filter((v): v is V2DraftPermission => !!v);
    const summary = aggregateCell(draftVariants, baselineVariants);
    const grant = grantableByOperator(childCell);
    // 构造临时 baselineIndex（key=permCellKeyStr，只含该 cell）适配 buildCellDisplay
    const tempBaselineIndex = new Map<PermCellKeyStr, GrantVariantId[]>();
    if (blIds.length > 0) tempBaselineIndex.set(childKeyStr, blIds);
    return buildCellDisplay({
      cell: childCell,
      summary,
      mainIndex: childIndex.value,
      baselineIndex: tempBaselineIndex,
      baselineMap: baselineState.value.childMap,
      grantableByOperator: grant.ok,
      denyReason: grant.reason,
      expanded: false
    });
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
    collapseExpanded,
    redundantCandidate,
    batchGrantAll,
    batchAddBranch,
    batchRemove,
    // 子权限（T-FE-033）
    hasChildren,
    childVariantsOf,
    addChildBranch,
    updateChildVariant,
    removeChildVariantOp,
    restoreChildVariant,
    restoreChildModify,
    resolveChildCell
  };
}
