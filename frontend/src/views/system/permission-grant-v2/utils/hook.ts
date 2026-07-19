import { ref, computed, onMounted, onUnmounted } from "vue";
import { ElMessageBox } from "element-plus";
import { onBeforeRouteLeave } from "vue-router";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_GRANT_V2_PERMS } from "./perms";
import {
  getRoleTree,
  type RoleTreeNode,
  type RolePermissionItem,
  type RolePermissionAddItem,
  type RolePermissionUpdateItem,
  type DomainCapability,
  type OperatorCapability
} from "@/api/permission-grant";
import { useV2GrantTransport } from "../transport";
import { useV2MatrixData } from "./useV2MatrixData";
import { useV2DraftModel } from "./useV2DraftModel";
import type {
  GrantVariantId,
  V2DraftPermission,
  V2FailedChildOp,
  V2SavePhase
} from "./v2-types";
import { childPermCellKeyStr, buildBaseline } from "./grant-variant";
import {
  toAddItem,
  toUpdateItem,
  computeMainDiff,
  computeChildDiff,
  matchSaveResponse,
  classifySaveError,
  reconcileIdentities,
  deriveSavePhase,
  type V2DiffEntry
} from "./save-adapter";

/**
 * 权限授予 V2 页 hook（facade）。
 *
 * 组合三个内部 composable，对组件暴露统一接口：
 * - D1–D3 门控 + 角色上下文（本文件）
 * - useV2MatrixData：资源类型/资源树/操作/条件候选加载
 * - useV2DraftModel：baseline + grantTasks replay + 单项任务原子操作 + 单元格解析
 *
 * 设计要点：
 * 1. D1 门控：无 ROLE:VIEW 整页无权（canView fail-closed）
 * 2. D3 readonly fail-closed：虚拟根/directGrantable=false/enabled=false/canManage=false/
 *    operatorCapability.canManage=false 任一命中即只读
 * 3. selectRole 请求序号：快速连续选角只接受最后一次响应
 * 4. selectRole 草稿隔离：有草稿时确认放弃；成功切换后重建 baseline + 清空旧任务
 *    （不能把旧角色任务 replay 到新角色；完整离开保护留 T-FE-034）
 * 5. 可变授权走 useV2GrantTransport()；只读端点复用共享 API
 */

const DEFAULT_DOMAIN_CAPABILITY: DomainCapability = {
  supportsChildren: false,
  childResourceTypeCodes: []
};
const DEFAULT_OPERATOR_CAPABILITY: OperatorCapability = {
  canManage: false,
  grantableResourceTypeCodes: [],
  grantableOperationCodes: []
};

export function usePermissionGrantV2() {
  // ---- 权限门控（D1）----
  const canView = computed(() => hasPerms(PERMISSION_GRANT_V2_PERMS.ROLE_VIEW));
  const canManage = computed(() =>
    hasPerms(PERMISSION_GRANT_V2_PERMS.ROLE_MANAGE)
  );

  // ---- 当前上下文（D2）----
  const currentRole = ref<RoleTreeNode | null>(null);
  const currentDomainCode = ref<string>("");

  // ---- 数据 ----
  const roleTree = ref<RoleTreeNode[]>([]);
  /** 原始 baseline（buildBaseline 转 V2DraftState + replay 投影） */
  const baselineItems = ref<RolePermissionItem[]>([]);

  // ---- 能力 ----
  const domainCapability = ref<DomainCapability>({
    ...DEFAULT_DOMAIN_CAPABILITY
  });
  const operatorCapability = ref<OperatorCapability>({
    ...DEFAULT_OPERATOR_CAPABILITY
  });

  // ---- 保存状态（D5，T-FE-034 扩展）----
  const failedChildren = ref<V2FailedChildOp[]>([]);
  const saving = ref(false);
  const saveError = ref<string | null>(null);
  const baselineStale = ref(false);
  /** SAVE_PREVIEW bottom-sheet 中间态（DIRTY->SAVING 间，Q4 强制预览） */
  const savePreview = ref(false);
  /** SAVE_OUTCOME_UNKNOWN：主请求超时/断网/5xx，服务端可能已提交，禁止盲目重试 */
  const saveOutcomeUnknown = ref(false);
  /** 主请求业务拒绝标志（仅 business 错误设 true；区别于 saveError 消息字段，不靠消息推断状态） */
  const mainFailed = ref(false);
  /** 结果核对进行中（区分 SAVE_OUTCOME_UNKNOWN 核对中 vs 核对失败，供 sheet 显示恢复入口） */
  const reconciling = ref(false);

  // ---- 加载状态 ----
  const loadingRoleTree = ref(false);
  const loadingContext = ref(false);

  // ---- 请求序号（race 防护：快速连续选角只接受最后一次响应）----
  let selectRoleSeq = 0;

  // ========== 计算（D3）==========

  /** 是否只读（fail-closed：任一能力缺失即只读） */
  const readonly = computed(() => {
    if (!currentRole.value) return true;
    const r = currentRole.value;
    if (r.roleExternalId.startsWith("__virtual_root_")) return true;
    if (!r.directGrantable) return true;
    if (!r.enabled) return true;
    if (!canManage.value) return true;
    if (!r.canManage) return true;
    if (!operatorCapability.value.canManage) return true;
    return false;
  });

  /** 只读原因（供 UI 解释禁用） */
  const readonlyReason = computed<string | null>(() => {
    if (!currentRole.value) return null;
    const r = currentRole.value;
    if (r.roleExternalId.startsWith("__virtual_root_"))
      return "虚拟根角色不可配置";
    if (!r.directGrantable) return "角色不可直接授权";
    if (!r.enabled) return "角色已禁用";
    if (!canManage.value) return "无 ROLE:MANAGE 权限";
    if (!r.canManage) return "当前角色不可管理";
    if (!operatorCapability.value.canManage) return "操作者无管理能力";
    return null;
  });

  /**
   * 交互锁定（T-FE-034：saving/preview/outcome-unknown/stale 期间三栏只读）。
   * 不同于 D3 readonly（失格），交互锁定是临时保存态，不改变 page-header "只读" 标记。
   * 传入 useV2DraftModel capability.readonly，使编辑门控（addBranch/updateVariant 等）自动禁用。
   */
  const interactionLocked = computed(
    () =>
      readonly.value ||
      saving.value ||
      savePreview.value ||
      saveOutcomeUnknown.value ||
      baselineStale.value
  );

  // ========== 子 composable ==========

  const matrix = useV2MatrixData(currentDomainCode);

  const roleContext = computed(() => {
    const r = currentRole.value;
    return {
      domainCode: currentDomainCode.value,
      roleExternalId: r?.roleExternalId ?? "",
      roleTypeCode: r?.roleTypeCode ?? ""
    };
  });

  const capability = computed(() => ({
    readonly: interactionLocked.value,
    operatorCanManage: operatorCapability.value.canManage,
    grantableResourceTypeCodes:
      operatorCapability.value.grantableResourceTypeCodes,
    grantableOperationCodes: operatorCapability.value.grantableOperationCodes
  }));

  const draft = useV2DraftModel(
    baselineItems,
    roleContext,
    capability,
    failedChildren
  );

  // ========== diff（保存层 + 右栏变更流） ==========
  const mainDiff = computed(() =>
    computeMainDiff(draft.mainDraft.value, draft.baselineState.value)
  );
  const childDiff = computed(() => {
    const mainRemoveIds = new Set<GrantVariantId>(
      mainDiff.value.filter(d => d.type === "remove").map(d => d.variantId)
    );
    return computeChildDiff(
      draft.childDraft.value,
      draft.baselineState.value,
      mainRemoveIds
    );
  });
  const allDiff = computed<V2DiffEntry[]>(() => [
    ...mainDiff.value,
    ...childDiff.value
  ]);
  const hasDraft = computed(
    () => draft.grantTasks.value.length > 0 || failedChildren.value.length > 0
  );

  /**
   * D5 保存阶段（T-FE-034，state-model §2，派生逻辑见 save-adapter.deriveSavePhase）。
   * mainFailed 为显式标志，不靠 saveError 消息推断（saveError 仅展示，承载多种提示）。
   */
  const savePhase = computed<V2SavePhase>(() =>
    deriveSavePhase({
      saving: saving.value,
      savePreview: savePreview.value,
      saveOutcomeUnknown: saveOutcomeUnknown.value,
      baselineStale: baselineStale.value,
      failedChildrenCount: failedChildren.value.length,
      mainFailed: mainFailed.value,
      hasDraft: hasDraft.value
    })
  );

  // ========== 加载 ==========

  /** 加载角色树（复用共享 abstract-role/tree） */
  async function loadRoleTree(keyword?: string) {
    if (!canView.value) return;
    loadingRoleTree.value = true;
    try {
      const resp = await getRoleTree({ keyword });
      roleTree.value = resp.items;
    } catch (e) {
      message(e instanceof Error ? e.message : "加载角色树失败", {
        type: "error"
      });
    } finally {
      loadingRoleTree.value = false;
    }
  }

  /**
   * 加载角色权限快照（不直接修改 refs，返回完整响应供调用方原子提交）。
   * 可变授权走 V2 transport；失败抛错由调用方处理。
   */
  async function loadRolePermissionSnapshot(
    role: RoleTreeNode,
    domainCode: string
  ) {
    const transport = useV2GrantTransport();
    return transport.getRolePermissionList({
      domainCode,
      roleTypeCode: role.roleTypeCode,
      roleExternalId: role.roleExternalId
    });
  }

  /**
   * 选择角色 -> 草稿隔离确认 -> 加载快照 + 成功后原子提交 + 重建矩阵/baseline。
   * - 虚拟根及 canView=false 节点不可触发
   * - 有草稿时确认放弃（不能把旧角色任务 replay 到新角色）
   * - 请求序号校验：快速连续选角只接受最后一次响应
   * - 失败时旧上下文完全不变
   */
  async function selectRole(role: RoleTreeNode) {
    if (role.roleExternalId.startsWith("__virtual_root_")) return;
    if (!role.canView) return;
    // 保存/结果核对期间禁止切换角色（避免旧保存流程的 reload/reconcile 写入新角色上下文）
    if (
      saving.value ||
      savePreview.value ||
      saveOutcomeUnknown.value ||
      reconciling.value
    ) {
      message("保存或结果核对进行中，请等待完成后再切换角色", {
        type: "warning"
      });
      return;
    }

    // 草稿隔离：有未保存草稿时确认放弃
    if (draft.hasDraft.value) {
      try {
        await ElMessageBox.confirm(
          `当前角色有 ${draft.grantTasks.value.length} 个未保存草稿任务，切换角色将放弃全部草稿。是否继续？`,
          "切换角色",
          {
            type: "warning",
            confirmButtonText: "放弃并切换",
            cancelButtonText: "取消"
          }
        );
      } catch {
        return; // 用户取消
      }
    }

    const seq = ++selectRoleSeq;
    loadingContext.value = true;
    try {
      const domainCode = role.domainCode ?? "";
      const resp = await loadRolePermissionSnapshot(role, domainCode);
      // 请求序号校验：快速连续选角只接受最后一次响应
      if (seq !== selectRoleSeq) return;
      // 原子提交：先设上下文与 baseline，再重置草稿与矩阵
      currentRole.value = role;
      currentDomainCode.value = domainCode;
      baselineItems.value = resp.items;
      domainCapability.value = resp.domainCapability;
      operatorCapability.value = resp.operatorCapability;
      // 切角色：清空旧角色草稿 + 保存状态（不 replay 到新角色）
      draft.resetDraft();
      failedChildren.value = [];
      saveError.value = null;
      baselineStale.value = false;
      savePreview.value = false;
      saveOutcomeUnknown.value = false;
      mainFailed.value = false;
      reconciling.value = false;
      matrix.resetMatrix();
      await matrix.loadResourceTypes();
      matrix.loadConditionOptions();
    } catch (e) {
      if (seq !== selectRoleSeq) return;
      message(e instanceof Error ? e.message : "加载权限事实失败", {
        type: "error"
      });
      // 旧上下文不变
    } finally {
      if (seq === selectRoleSeq) loadingContext.value = false;
    }
  }

  // ========== 保存（两步 + 部分失败，T-FE-033） ==========

  /**
   * 重载 baseline（全成功路径用：清草稿回 CLEAN）。
   * 成功清 grantTasks + failedChildren + stale + preview/outcome-unknown；失败置 baselineStale。
   * 不用于 STALE_WITH_CHILD_FAILURE / SAVE_OUTCOME_UNKNOWN 恢复（那些用 fetchBaselineAndReconcile 不清草稿）。
   */
  async function reloadBaseline(): Promise<boolean> {
    if (!currentRole.value) return false;
    try {
      const resp = await loadRolePermissionSnapshot(
        currentRole.value,
        currentDomainCode.value
      );
      baselineItems.value = resp.items;
      domainCapability.value = resp.domainCapability;
      operatorCapability.value = resp.operatorCapability;
      draft.resetDraft();
      failedChildren.value = [];
      baselineStale.value = false;
      savePreview.value = false;
      saveOutcomeUnknown.value = false;
      mainFailed.value = false;
      reconciling.value = false;
      saveError.value = null;
      return true;
    } catch {
      baselineStale.value = true;
      return false;
    }
  }

  /**
   * fetchBaseline + reconcile（不清草稿，T-FE-034 Q2/Q3）。
   * 用于 SAVE_OUTCOME_UNKNOWN 与子操作失败后恢复：拉取新 baseline，reconcile 身份合并
   * （临时 UUID -> 服务端 id）+ 剔除已落库 failedChildren + 清理 settled commands，
   * 一次性发布 baseline/grantTasks/failedChildren。
   * 成功返回 true（已清 stale/outcome-unknown）；失败返回 false（调用方决定状态）。
   */
  async function fetchBaselineAndReconcile(): Promise<boolean> {
    if (!currentRole.value) return false;
    let resp: Awaited<ReturnType<typeof loadRolePermissionSnapshot>>;
    try {
      resp = await loadRolePermissionSnapshot(
        currentRole.value,
        currentDomainCode.value
      );
    } catch {
      return false;
    }
    try {
      const nextBaseline = buildBaseline(resp.items);
      const result = reconcileIdentities(
        nextBaseline,
        draft.grantTasks.value,
        failedChildren.value
      );
      if (!result.ok) {
        // 多匹配/非一一映射：reconcile 失败，不发布
        return false;
      }
      // 一次性发布（state-model §2.5 P1-1：先拉取事实再原子更新，避免中间态丢数据）
      baselineItems.value = resp.items;
      domainCapability.value = resp.domainCapability;
      operatorCapability.value = resp.operatorCapability;
      draft.grantTasks.value = result.tasks;
      failedChildren.value = result.failedChildren;
      baselineStale.value = false;
      saveOutcomeUnknown.value = false;
      return true;
    } catch {
      // buildBaseline（孤儿数据）/ reconcileIdentities 内部 replay（身份冲突）抛错
      // 保持旧 baseline + 当前 failedChildren，返回 false 由调用方决定状态（不逃逸到 confirmSave 外层 catch）
      return false;
    }
  }

  /**
   * 保存入口：门禁通过后进入 SAVE_PREVIEW（Q4 强制预览）。
   * 区分"未开始"（return false）与"开始后失败"（confirmSave 内 true）。
   */
  async function requestSave(): Promise<boolean> {
    if (!currentRole.value || readonly.value || saving.value) return false;
    if (baselineStale.value) {
      message("权限事实已过期，请重新选择角色刷新后再保存", {
        type: "warning"
      });
      return false;
    }
    if (saveOutcomeUnknown.value) {
      message("保存结果未确认，请等待事实核对完成", { type: "warning" });
      return false;
    }
    if (!hasDraft.value) {
      message("无变更", { type: "info" });
      return false;
    }
    savePreview.value = true;
    return true;
  }

  /** 取消 SAVE_PREVIEW（回 DIRTY） */
  function cancelSavePreview(): void {
    savePreview.value = false;
  }

  /** SAVE_PREVIEW 确认入口 -> doSave（实际保存逻辑） */
  async function confirmSave(): Promise<boolean> {
    if (!savePreview.value) return false;
    return doSave();
  }

  /**
   * 实际保存执行（SAVE_PREVIEW -> SAVING -> 分支，state-model §4.2 / error-flow §2.5）：
   * 1. save 主权限（add/update/remove + 子 update 合并）-> 匹配响应得新主权限 id
   * 2. add-child（按 parentPermissionId 分组，新父用匹配 id，baseline 父直接用）
   * 3. remove-child（父在 mainRemove 跳过，级联）
   * 4. 全成功 -> reloadBaseline 回 CLEAN；子失败 -> fetchBaseline+reconcile（Q3）
   * 主请求失败：Q1 分类 - business -> MAIN_FAILED（草稿保留可重试）；
   *             unknown（超时/断网/5xx）-> SAVE_OUTCOME_UNKNOWN（fetchBaseline+reconcile）。
   * confirmSave（SAVE_PREVIEW 确认）与 reconcileAndRetry（一键刷新并重试）调用此核心；
   * 后者绕过预览直接执行（验收第7条原子动作）。
   */
  async function doSave(): Promise<boolean> {
    if (!currentRole.value || readonly.value || saving.value) return false;
    if (baselineStale.value) {
      message("权限事实已过期，请重新选择角色刷新后再保存", {
        type: "warning"
      });
      return false;
    }
    // SAVE_PREVIEW -> SAVING
    savePreview.value = false;
    saveOutcomeUnknown.value = false;
    mainFailed.value = false;
    const role = currentRole.value;
    // 先捕获 diff（清 failedChildren 会改变 childDraft computed，必须先捕获再清空）
    const mDiff = [...mainDiff.value];
    const cDiff = [...childDiff.value];
    const failedSnapshot = [...failedChildren.value];
    saving.value = true;
    saveError.value = null;
    failedChildren.value = [];

    try {
      const mainAdd: RolePermissionAddItem[] = [];
      const updateItems: RolePermissionUpdateItem[] = [];
      const mainRemove: number[] = [];
      const childRemoveIds: number[] = [];
      const childRemoveItems: V2FailedChildOp[] = [];
      const childAddEntries: V2DiffEntry[] = [];

      for (const d of mDiff) {
        if (d.type === "add") mainAdd.push(toAddItem(d.perm));
        else if (d.type === "update" && d.before)
          updateItems.push(toUpdateItem(d.perm, d.before));
        else if (d.type === "remove")
          mainRemove.push(d.perm.variantId as number);
      }
      for (const d of cDiff) {
        if (d.type === "add") {
          childAddEntries.push(d);
        } else if (d.type === "update" && d.before) {
          updateItems.push(toUpdateItem(d.perm, d.before));
        } else if (d.type === "remove") {
          childRemoveIds.push(d.perm.variantId as number);
          childRemoveItems.push({
            op: "remove",
            child: d.perm,
            childKey: childPermCellKeyStr(d.perm.dependOn!, d.perm),
            parentVariantId: d.perm.dependOn!
          });
        }
      }

      if (
        mainAdd.length === 0 &&
        updateItems.length === 0 &&
        mainRemove.length === 0 &&
        childAddEntries.length === 0 &&
        childRemoveIds.length === 0
      ) {
        message("无变更", { type: "info" });
        return true;
      }

      const transport = useV2GrantTransport();
      const tempKeyToServerId = new Map<GrantVariantId, number>();
      const needMainSave =
        mainAdd.length > 0 || updateItems.length > 0 || mainRemove.length > 0;

      // ---- 主请求 ----
      if (needMainSave) {
        try {
          const saveResp = await transport.saveRolePermission({
            domainCode: currentDomainCode.value,
            roleTypeCode: role.roleTypeCode,
            roleExternalId: role.roleExternalId,
            add: mainAdd,
            update: updateItems,
            remove: mainRemove
          });
          const mainAddEntriesForMatch = mDiff.filter(d => d.type === "add");
          const matched = matchSaveResponse(
            saveResp.items,
            mainAddEntriesForMatch
          );
          for (const [k, v] of matched) tempKeyToServerId.set(k, v);
        } catch (e) {
          // 主请求失败：Q1 分类分流
          if (classifySaveError(e) === "business") {
            // MAIN_FAILED：服务端未提交，恢复 overlay，草稿完整保留可重试
            failedChildren.value = failedSnapshot;
            saveError.value = e instanceof Error ? e.message : "保存失败";
            mainFailed.value = true;
            message(saveError.value, { type: "error" });
          } else {
            // SAVE_OUTCOME_UNKNOWN：先恢复历史 failedChildren 快照再 reconcile
            //（doSave 开头已清空；unknown 分支须保留历史子失败供重绑/settled 判断，
            // 否则子失败状态下再次保存遇超时/5xx 会丢失待重试项）
            failedChildren.value = failedSnapshot;
            await handleSaveOutcomeUnknown();
          }
          return true;
        }
      }

      // ---- 子操作（add-child / remove-child）----
      let childFailureOccurred = false;
      const failedChildAdd: V2FailedChildOp[] = [];
      const addChildByParent = new Map<
        number,
        {
          item: RolePermissionAddItem;
          child: V2DraftPermission;
          childKey: string;
        }[]
      >();
      for (const entry of childAddEntries) {
        const parentVariantId = entry.perm.dependOn!;
        // baseline 父（number）直接用；新增父（string）用匹配结果
        const parentId =
          typeof parentVariantId === "number"
            ? parentVariantId
            : (tempKeyToServerId.get(parentVariantId) ?? null);
        if (!parentId) {
          failedChildAdd.push({
            op: "add",
            child: entry.perm,
            childKey: childPermCellKeyStr(parentVariantId, entry.perm),
            parentVariantId
          });
          childFailureOccurred = true;
          continue;
        }
        if (!addChildByParent.has(parentId)) addChildByParent.set(parentId, []);
        addChildByParent.get(parentId)!.push({
          item: toAddItem(entry.perm),
          child: entry.perm,
          childKey: childPermCellKeyStr(parentVariantId, entry.perm)
        });
      }

      for (const [parentId, entries] of addChildByParent) {
        try {
          await transport.addChildPermission({
            parentPermissionId: parentId,
            children: entries.map(e => e.item)
          });
        } catch (e) {
          childFailureOccurred = true;
          const errMsg = e instanceof Error ? e.message : "子权限保存失败";
          for (const entry of entries) {
            failedChildAdd.push({
              op: "add",
              child: entry.child,
              childKey: entry.childKey,
              parentVariantId: entry.child.dependOn!
            });
          }
          saveError.value = `主权限已保存，部分子权限保存失败：${errMsg}`;
        }
      }

      // remove-child
      const failedChildRemove: V2FailedChildOp[] = [];
      for (let i = 0; i < childRemoveIds.length; i++) {
        try {
          await transport.removeChildPermission({
            permissionId: childRemoveIds[i]
          });
        } catch {
          childFailureOccurred = true;
          failedChildRemove.push(childRemoveItems[i]);
        }
      }

      // ---- 子操作后刷新 ----
      if (childFailureOccurred) {
        // Q3：fetchBaseline + reconcile（不清草稿），保留未完成 failedChildren
        await reconcileAfterChildFailure(failedChildAdd, failedChildRemove);
      } else {
        // 全成功：reloadBaseline 清草稿回 CLEAN
        const reloadOk = await reloadBaseline();
        if (!reloadOk) {
          saveError.value =
            "保存已提交，但刷新权限事实失败，请重新选择角色刷新";
          message(saveError.value, { type: "warning" });
        } else {
          message("保存成功", { type: "success" });
        }
      }
      return true;
    } catch (e) {
      // 兜底（主请求错误已在 inner catch 处理；此处为未预期异常）
      failedChildren.value = failedSnapshot;
      saveError.value = e instanceof Error ? e.message : "保存失败";
      message(saveError.value, { type: "error" });
      return true;
    } finally {
      saving.value = false;
    }
  }

  /**
   * SAVE_OUTCOME_UNKNOWN 恢复（Q1/Q2）。
   * 主请求超时/断网/5xx，服务端可能已提交：fetchBaseline+reconcile
   * -> 已落库项 noChange / 未落库项作 diff 续传。fetchBaseline/reconcile 失败保持
   * SAVE_OUTCOME_UNKNOWN（Q1：不降级 MAIN_FAILED），由 sheet 提供重新核对/放弃入口。
   * reconciling 区分"核对中"与"核对失败"，供 sheet 显示恢复动作。
   */
  async function handleSaveOutcomeUnknown(): Promise<void> {
    saveOutcomeUnknown.value = true;
    reconciling.value = true;
    saveError.value = "保存请求未确认结果，正在重新拉取事实核对…";
    message(saveError.value, { type: "warning" });
    const ok = await fetchBaselineAndReconcile();
    reconciling.value = false;
    if (!ok) {
      saveError.value = "保存结果未确认且事实刷新失败，可重新核对或放弃后刷新";
      return;
    }
    // reconcile 成功：saveOutcomeUnknown 已清，reconciling 已清
    const remaining = allDiff.value.length;
    if (remaining > 0) {
      // 仍有未落库项 -> DIRTY 续传（不设 mainFailed，saveError 仅展示提示）
      saveError.value = `已落库部分权限，仍有 ${remaining} 项未确认，请确认后重新保存`;
      message(saveError.value, { type: "warning" });
    } else {
      saveError.value = null;
      message("保存结果已确认：全部权限已落库", { type: "success" });
    }
  }

  /** 重新核对（SAVE_OUTCOME_UNKNOWN 核对失败后的恢复入口） */
  async function retryReconcile(): Promise<boolean> {
    if (!saveOutcomeUnknown.value || reconciling.value) return false;
    await handleSaveOutcomeUnknown();
    return true;
  }

  /**
   * 放弃结果核对（SAVE_OUTCOME_UNKNOWN 核对失败后的安全退出）。
   * 清 saveOutcomeUnknown + 设 baselineStale 强制刷新事实后才能继续
   *（避免基于未确认状态盲目重试导致重复落库）。
   */
  function discardOutcomeUnknown(): void {
    saveOutcomeUnknown.value = false;
    reconciling.value = false;
    saveError.value = null;
    baselineStale.value = true;
  }

  /** 关闭 MAIN_FAILED sheet（回 DIRTY 修改草稿） */
  function dismissMainError(): void {
    mainFailed.value = false;
    saveError.value = null;
  }

  /**
   * 子操作失败后恢复（Q3：fetchBaseline + reconcile，仅保留未完成 failedChildren）。
   * reconcile 剔除已落库 add / 已删除 remove；未完成项保留 overlay。
   * fetchBaseline/reconcile 失败 -> STALE_WITH_CHILD_FAILURE（failedChildren 非空 + stale）。
   */
  async function reconcileAfterChildFailure(
    failedChildAdd: V2FailedChildOp[],
    failedChildRemove: V2FailedChildOp[]
  ): Promise<void> {
    failedChildren.value = [...failedChildAdd, ...failedChildRemove];
    const ok = await fetchBaselineAndReconcile();
    if (!ok) {
      baselineStale.value = true;
      saveError.value = "保存已提交但刷新失败，且部分子权限待重试";
      message(saveError.value, { type: "warning" });
      return;
    }
    // reconcile 成功：failedChildren 已剔除已落库/已删项
    if (failedChildren.value.length > 0) {
      saveError.value = `主权限已保存，${failedChildren.value.length} 项子权限保存失败`;
      message(saveError.value, { type: "warning" });
    } else {
      // 全部落库 -> CLEAN
      saveError.value = null;
      message("保存成功", { type: "success" });
    }
  }

  /**
   * 内部共享：fetchBaseline + reconcile +（若有未完成项）requestSave。
   * 用于 retryFailedChildren stale 分支与 refreshAndRetry（STALE_WITH_CHILD_FAILURE 一键）。
   * 恢复顺序固定：先 fetchBaseline+reconcile 清 stale -> 再 retry 清 childFailure（Q3）。
   */
  async function reconcileAndRetry(): Promise<boolean> {
    message("正在刷新事实并重试…", { type: "info" });
    const ok = await fetchBaselineAndReconcile();
    if (!ok) {
      baselineStale.value = true;
      message("事实刷新失败，请重新选择角色", { type: "warning" });
      return false;
    }
    // 清 stale（fetchBaselineAndReconcile 成功已清）
    if (failedChildren.value.length === 0) {
      message("刷新后发现子权限已全部落库", { type: "success" });
      return true;
    }
    // 直接执行重试（绕过 SAVE_PREVIEW 二次确认），符合验收第7条原子一键动作
    return doSave();
  }

  /**
   * 重试失败的子权限。
   * - 非 stale：直接 requestSave（SAVE_PREVIEW -> confirmSave，overlay 子项在 diff）
   * - stale：fetchBaseline+reconcile 原子恢复后 retry（Q3）
   */
  async function retryFailedChildren(): Promise<boolean> {
    if (failedChildren.value.length === 0) return false;
    if (baselineStale.value) return reconcileAndRetry();
    return requestSave();
  }

  /**
   * STALE_WITH_CHILD_FAILURE 一键"刷新并重试"（Q3 原子动作）。
   * fetchBaseline -> reconcile -> 清 stale -> 若仍有 failedChildren 再 retry。
   */
  async function refreshAndRetry(): Promise<boolean> {
    if (!baselineStale.value || failedChildren.value.length === 0) return false;
    return reconcileAndRetry();
  }

  /** 放弃全部更改（清 grantTasks + failedChildren + saveError + preview/outcome-unknown/mainFailed；不清 baselineStale） */
  function discardAll(): void {
    draft.resetDraft();
    failedChildren.value = [];
    saveError.value = null;
    savePreview.value = false;
    saveOutcomeUnknown.value = false;
    mainFailed.value = false;
    reconciling.value = false;
    // 不清 baselineStale（事实仍陈旧）
  }

  // ========== 离开保护（T-FE-034，error-flow §2.7） ==========

  function beforeUnloadHandler(e: BeforeUnloadEvent) {
    if (hasDraft.value || saving.value) {
      e.preventDefault();
      e.returnValue = "";
    }
  }

  /**
   * 路由离开保护：saving 强制阻止（草稿正在落库）；有草稿确认丢弃。
   * 不同于 selectRole 的切换确认，此为跨页路由。
   */
  onBeforeRouteLeave(async (_to, _from, next) => {
    if (saving.value) {
      message("保存进行中，请等待完成", { type: "warning" });
      next(false);
      return;
    }
    if (hasDraft.value) {
      try {
        await ElMessageBox.confirm(
          "当前有未保存变更，离开将丢弃。是否继续？",
          "离开确认",
          {
            type: "warning",
            confirmButtonText: "离开",
            cancelButtonText: "取消"
          }
        );
        next();
      } catch {
        next(false);
      }
      return;
    }
    next();
  });

  onMounted(() => {
    window.addEventListener("beforeunload", beforeUnloadHandler);
  });

  onUnmounted(() => {
    window.removeEventListener("beforeunload", beforeUnloadHandler);
  });

  return {
    // D1–D3
    canView,
    canManage,
    currentRole,
    currentDomainCode,
    roleTree,
    baselineItems,
    domainCapability,
    operatorCapability,
    loadingRoleTree,
    loadingContext,
    readonly,
    readonlyReason,
    loadRoleTree,
    selectRole,
    // 矩阵数据
    ...matrix,
    // 草稿模型
    ...draft,
    // 保存（D5，T-FE-034；hasDraft 覆盖 draft.hasDraft 以纳入 failedChildren）
    hasDraft,
    saving,
    saveError,
    baselineStale,
    failedChildren,
    savePreview,
    saveOutcomeUnknown,
    mainFailed,
    reconciling,
    savePhase,
    interactionLocked,
    mainDiff,
    childDiff,
    allDiff,
    requestSave,
    confirmSave,
    cancelSavePreview,
    dismissMainError,
    retryReconcile,
    discardOutcomeUnknown,
    reloadBaseline,
    retryFailedChildren,
    refreshAndRetry,
    discardAll
  };
}
