import { ref, computed } from "vue";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_GRANT_V2_PERMS } from "./perms";
import {
  getRoleTree,
  type RoleTreeNode,
  type RolePermissionItem,
  type DomainCapability,
  type OperatorCapability
} from "@/api/permission-grant";
import { useV2GrantTransport } from "../transport";
import { useV2MatrixData } from "./useV2MatrixData";
import { useV2DraftModel } from "./useV2DraftModel";

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

  const draft = useV2DraftModel(baselineItems, roleContext, capability);

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
      // 切角色：清空旧角色草稿（不 replay 到新角色）+ 重置矩阵 + 加载新角色资源类型
      draft.resetDraft();
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
    ...draft
  };
}
