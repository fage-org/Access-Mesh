import { ref, computed } from "vue";
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

/**
 * 权限授予 V2 页 hook（T-FE-029 骨架范围）。
 *
 * 范围：页面门控（D1）+ 角色上下文（D2）+ 编辑能力（D3 fail-closed）+ 选角色加载快照。
 * 不含：方案 A 草稿模型（T-FE-030）、矩阵编辑（T-FE-031）、变更流（T-FE-032）、
 *       保存/失败恢复（T-FE-033/034）、离开保护（T-FE-034，无草稿时无需）。
 *
 * 设计要点（docs/design/frontend/permission-grant-state-model.md §0/§D1/§D3）：
 * 1. D1 门控：无 ROLE:VIEW 整页无权不发请求（canView fail-closed）
 * 2. D3 readonly fail-closed：虚拟根 / directGrantable=false / enabled=false /
 *    canManage=false / operatorCapability.canManage=false 任一命中即只读
 * 3. selectRole 请求序号：快速连续选角时只接受最后一次响应（race 防护）
 * 4. selectRole 原子提交：先 loadSnapshot 成功后一次性提交 refs，失败旧上下文不变
 * 5. 虚拟根及 canView=false 节点不可触发快照请求
 * 6. 可变授权走 useV2GrantTransport()；只读端点复用共享 API（getRoleTree）
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
  /** 原始 baseline（T-FE-030 转 GrantVariantId maps + replay 投影） */
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
   * 选择角色 -> 加载快照 + 成功后原子提交。
   * - 虚拟根及 canView=false 节点不可触发快照请求
   * - 请求序号校验：快速连续选角时只接受最后一次响应
   * - 失败时旧上下文完全不变
   */
  async function selectRole(role: RoleTreeNode) {
    if (role.roleExternalId.startsWith("__virtual_root_")) return;
    if (!role.canView) return;
    const seq = ++selectRoleSeq;
    loadingContext.value = true;
    try {
      const domainCode = role.domainCode ?? "";
      const resp = await loadRolePermissionSnapshot(role, domainCode);
      // 请求序号校验：快速连续选角时只接受最后一次响应
      if (seq !== selectRoleSeq) return;
      currentRole.value = role;
      currentDomainCode.value = domainCode;
      baselineItems.value = resp.items;
      domainCapability.value = resp.domainCapability;
      operatorCapability.value = resp.operatorCapability;
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
    selectRole
  };
}
