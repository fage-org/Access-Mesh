import { ref, computed } from "vue";
import { ElMessageBox } from "element-plus";
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
  V2FailedChildOp
} from "./v2-types";
import {
  childPermCellKeyStr,
  permCellKeyStr,
  normalizeConditionCode
} from "./grant-variant";
import {
  toAddItem,
  toUpdateItem,
  computeMainDiff,
  computeChildDiff,
  matchSaveResponse,
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

  // ---- 保存状态（D5）----
  const failedChildren = ref<V2FailedChildOp[]>([]);
  const saving = ref(false);
  const saveError = ref<string | null>(null);
  const baselineStale = ref(false);

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
    readonly: readonly.value,
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
   * 重载 baseline（保存成功/重试用）。
   * 成功清 grantTasks + failedChildren + stale；失败置 baselineStale。
   * 注：T-FE-034 的 fetchBaseline+reconcile（不清草稿）未实现，本方法会清草稿，
   * 不用于 STALE_WITH_CHILD_FAILURE 恢复。
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
      return true;
    } catch {
      baselineStale.value = true;
      return false;
    }
  }

  /**
   * 两步保存（state-model §4.2 / error-flow §2.5）：
   * 1. save 主权限（add/update/remove + 子 update 合并）-> 匹配响应得新主权限 id
   * 2. add-child（按 parentPermissionId 分组，新父用匹配 id，baseline 父直接用）
   * 3. remove-child（父在 mainRemove 跳过，级联）
   * 4. reload baseline
   * 主成功子失败 -> CHILD_PARTIAL_FAILED（failedChildren overlay 保留）。
   * T-FE-033 走 DIRTY->SAVING->CLEAN/SAVE_FAILED；SAVE_PREVIEW/fetchBaseline+reconcile 留 T-FE-034。
   */
  async function saveAll(): Promise<boolean> {
    if (!currentRole.value || readonly.value || saving.value) return false;
    if (baselineStale.value) {
      message("权限事实已过期，请重新选择角色刷新后再保存", {
        type: "warning"
      });
      return false;
    }
    const role = currentRole.value;
    // 先捕获 diff（清 failedChildren 会改变 childDraft computed，必须先捕获再清空）
    const mDiff = [...mainDiff.value];
    const cDiff = [...childDiff.value];
    const failedSnapshot = [...failedChildren.value];
    // P1 修复：新增父 UUID -> perm 映射，reload 后重绑定失败子项 parentVariantId
    const mainAddUuidToPerm = new Map<GrantVariantId, V2DraftPermission>();
    for (const d of mDiff) {
      if (d.type === "add") mainAddUuidToPerm.set(d.variantId, d.perm);
    }
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
      if (needMainSave) {
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
      }

      // add-child（按 parentPermissionId 分组）
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

      const reloadOk = await reloadBaseline();

      if (childFailureOccurred) {
        // P1 修复：reload 后新增父变体已由临时 UUID 变为服务端 ID，重绑定失败子项的
        // parentVariantId/child/childKey，避免 retry 时 tempKeyToServerId 为空导致永久失败
        const reboundFailedChildAdd = failedChildAdd.map(fop => {
          if (typeof fop.parentVariantId === "number") return fop;
          const fromTemp = tempKeyToServerId.get(fop.parentVariantId);
          if (fromTemp) {
            return {
              ...fop,
              parentVariantId: fromTemp,
              child: { ...fop.child, dependOn: fromTemp },
              childKey: childPermCellKeyStr(fromTemp, fop.child)
            };
          }
          // fallback：reload 后 baseline 按 PermCellKey+conditionCode 匹配父服务端 ID
          const parentPerm = mainAddUuidToPerm.get(fop.parentVariantId);
          if (parentPerm) {
            const key = permCellKeyStr(parentPerm);
            const cond = normalizeConditionCode(parentPerm.conditionCode);
            const ids = draft.baselineState.value.mainIndex.get(key) ?? [];
            for (const id of ids) {
              const p = draft.baselineState.value.mainMap.get(id);
              if (p && normalizeConditionCode(p.conditionCode) === cond) {
                return {
                  ...fop,
                  parentVariantId: id,
                  child: { ...fop.child, dependOn: id },
                  childKey: childPermCellKeyStr(id, fop.child)
                };
              }
            }
          }
          return fop;
        });
        failedChildren.value = [...reboundFailedChildAdd, ...failedChildRemove];
        message(saveError.value ?? "部分子权限操作失败", { type: "warning" });
      } else if (!reloadOk) {
        saveError.value = "保存已提交，但刷新权限事实失败，请重新选择角色刷新";
        message(saveError.value, { type: "warning" });
      } else {
        message("保存成功", { type: "success" });
      }
      return true;
    } catch (e) {
      // 主请求失败（未执行 add-child/remove-child），恢复 overlay
      failedChildren.value = failedSnapshot;
      saveError.value = e instanceof Error ? e.message : "保存失败";
      message(saveError.value, { type: "error" });
      return true;
    } finally {
      saving.value = false;
    }
  }

  /**
   * 重试失败的子权限（T-FE-033 基本版：非 stale 直接 saveAll）。
   * T-FE-034 补 fetchBaseline+reconcile（stale 分支原子恢复）。
   */
  async function retryFailedChildren(): Promise<boolean> {
    if (failedChildren.value.length === 0) return false;
    if (baselineStale.value) {
      message("权限事实已过期，请重新选择角色刷新后再重试", {
        type: "warning"
      });
      return false;
    }
    return saveAll();
  }

  /** 放弃全部更改（清 grantTasks + failedChildren + saveError；不清 baselineStale） */
  function discardAll(): void {
    draft.resetDraft();
    failedChildren.value = [];
    saveError.value = null;
  }

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
    // 保存（D5，T-FE-033；hasDraft 覆盖 draft.hasDraft 以纳入 failedChildren）
    hasDraft,
    saving,
    saveError,
    baselineStale,
    failedChildren,
    mainDiff,
    childDiff,
    allDiff,
    saveAll,
    reloadBaseline,
    retryFailedChildren,
    discardAll
  };
}
