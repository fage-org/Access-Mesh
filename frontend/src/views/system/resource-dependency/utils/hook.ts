import { ref, reactive, computed, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import { hasPerms } from "@/utils/auth";
import {
  getDependencyList,
  createDependency,
  updateDependency,
  removeDependencies,
  checkDependencyCycle,
  type ResourceDependencyResp,
  type ResourceDependencyCreateReq,
  type ResourceDependencyUpdateReq,
  type DependencyCycleCheckResp
} from "@/api/resource-dependency";
import {
  getResourceTree,
  getOperationList,
  type ResourceTreeNode,
  type OperationPermissionResp
} from "@/api/resource-operation";
import { RESOURCE_DEPENDENCY_PERMS } from "./perms";
import type { DependencyFormData } from "./types";
import { hasBit } from "@/utils/bit-ops";

/**
 * 资源依赖页组合式逻辑。
 *
 * 引用数据映射（Resp 已随 T-PERM-031 补静态字段，映射保留为冗余快路径）：
 * - resourceMap：getResourceTree 扁平化 -> id->{name, resourceTypeCode, code, codeType}
 * - operationList：getOperationList 全量操作；bitsToOpCodes(bits, typeCode) 按资源类型
 *   + 全局操作过滤，hasBit（BigInt）位与拆解（P1 修复：typeCode 隔离跨类型同 bit 误匹配，
 *   BigInt 避免 32 位截断）
 *
 * 资源用业务键标识（create/update/check 请求），前端表单以 resourceEntityId 选择，
 * 提交时由 resourceMap 反查业务键构造请求。
 */
export function useResourceDependency() {
  // ========== 权限门控 ==========
  const canView = hasPerms(RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_VIEW);
  const canCreate = hasPerms(RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_ADD);
  const canEdit = hasPerms(RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_EDIT);
  const canDelete = hasPerms(
    RESOURCE_DEPENDENCY_PERMS.RESOURCE_DEPENDENCY_DELETE
  );

  // ========== 列表状态 ==========
  const loading = ref(false);
  const list = ref<ResourceDependencyResp[]>([]);
  const search = reactive({ keyword: "" });

  // ========== 引用数据 ==========
  const resourceMap = ref<Map<number, ResourceTreeNode>>(new Map());
  const resourceList = ref<ResourceTreeNode[]>([]);
  const operationList = ref<OperationPermissionResp[]>([]);
  const resourceTypeOptions = ref<Array<{ value: string; label: string }>>([]);

  // ========== 映射辅助 ==========

  /** 资源 ID -> 名称（缺失回退 #id） */
  function resolveResourceName(id: number | null | undefined): string {
    if (id == null) return "—";
    return resourceMap.value.get(id)?.name ?? `#${id}`;
  }

  /** 资源 ID -> 资源类型编码 */
  function resolveResourceTypeCode(
    id: number | null | undefined
  ): string | null {
    if (id == null) return null;
    return resourceMap.value.get(id)?.resourceTypeCode ?? null;
  }

  /** 资源 ID -> "名称（code）" */
  function resolveResourceLabel(id: number | null | undefined): string {
    if (id == null) return "—";
    const node = resourceMap.value.get(id);
    if (!node) return `#${id}`;
    return `${node.name}（${node.code}）`;
  }

  /** 操作位 -> 操作码列表（按资源类型拆解，操作定义按类型隔离；BigInt 位与兼容 63 位）。
   *  P1 修复：typeCode 隔离避免跨类型同 bit 误匹配；hasBit 避免 32 位截断。 */
  function bitsToOpCodes(
    bits: number | string | null,
    typeCode: string | null
  ): string[] {
    if (bits == null || bits === 0) return [];
    const codes: string[] = [];
    for (const op of operationList.value) {
      if (op.binaryBit == null) continue;
      // 只匹配该资源类型，避免跨类型同 bit 误匹配
      if (op.resourceTypeCode !== typeCode) continue;
      if (hasBit(bits, op.binaryBit)) codes.push(op.code);
    }
    return codes;
  }

  /** 操作位 -> 操作名称展示（null=任意，空=-）。
   *  P2 修复：直接从经类型过滤的操作对象取 name，避免 find(o => o.code === c)
   *  跨类型同名 code 误匹配（同一 code 在不同资源类型下名称可能不同）。 */
  function bitsToOpNames(
    bits: number | string | null,
    typeCode: string | null
  ): string {
    if (bits == null) return "任意";
    const names: string[] = [];
    for (const op of operationList.value) {
      if (op.binaryBit == null) continue;
      if (op.resourceTypeCode !== typeCode) continue;
      if (hasBit(bits, op.binaryBit)) names.push(op.name);
    }
    return names.length === 0 ? "-" : names.join("、");
  }

  /** 按资源类型过滤的资源选项（表单下拉用） */
  function resourcesForType(
    typeCode: string | null
  ): Array<{ id: number; name: string; code: string }> {
    if (!typeCode) return [];
    return resourceList.value
      .filter(r => r.resourceTypeCode === typeCode)
      .map(r => ({ id: r.id, name: r.name, code: r.code }))
      .sort((a, b) => a.id - b.id);
  }

  /** 按资源类型过滤的操作选项（操作定义按类型隔离） */
  function operationsForType(
    typeCode: string | null
  ): OperationPermissionResp[] {
    if (!typeCode) return [];
    return operationList.value.filter(op => op.resourceTypeCode === typeCode);
  }

  // ========== 请求构造 ==========

  /** 表单 -> 创建请求（反查业务键） */
  function buildCreatePayload(
    form: DependencyFormData
  ): ResourceDependencyCreateReq | null {
    const source = resourceMap.value.get(form.sourceResourceEntityId!);
    const target = resourceMap.value.get(form.targetResourceEntityId!);
    if (!source || !target) return null;
    return {
      sourceResourceTypeCode: source.resourceTypeCode,
      sourceResourceCode: source.code,
      sourceCodeType: source.codeType,
      sourceOperationCodes:
        form.sourceOperationCodes.length > 0 ? form.sourceOperationCodes : null,
      targetResourceTypeCode: target.resourceTypeCode,
      targetResourceCode: target.code,
      targetCodeType: target.codeType,
      requiredOperationCodes: form.requiredOperationCodes,
      autoGrant: form.autoGrant,
      description: form.description || null
    };
  }

  /** 表单 -> 更新请求（全量替换，含 id） */
  function buildUpdatePayload(
    form: DependencyFormData,
    id: number
  ): ResourceDependencyUpdateReq | null {
    const source = resourceMap.value.get(form.sourceResourceEntityId!);
    const target = resourceMap.value.get(form.targetResourceEntityId!);
    if (!source || !target) return null;
    return {
      id,
      sourceResourceTypeCode: source.resourceTypeCode,
      sourceResourceCode: source.code,
      sourceCodeType: source.codeType,
      sourceOperationCodes:
        form.sourceOperationCodes.length > 0 ? form.sourceOperationCodes : null,
      targetResourceTypeCode: target.resourceTypeCode,
      targetResourceCode: target.code,
      targetCodeType: target.codeType,
      requiredOperationCodes: form.requiredOperationCodes,
      autoGrant: form.autoGrant,
      description: form.description || null
    };
  }

  // ========== 列表加载 ==========

  async function loadList() {
    if (!canView) return;
    loading.value = true;
    try {
      const res = await getDependencyList({});
      list.value = res.items;
    } catch (e: any) {
      message(e.message || "加载依赖列表失败", { type: "error" });
      list.value = [];
    } finally {
      loading.value = false;
    }
  }

  // ========== 引用数据加载 ==========

  /** 递归扁平化资源树 */
  function flattenTree(
    node: ResourceTreeNode,
    map: Map<number, ResourceTreeNode>
  ) {
    map.set(node.id, node);
    if (node.children) {
      for (const child of node.children) flattenTree(child, map);
    }
  }

  async function loadRefData() {
    try {
      const [treeRes, opRes] = await Promise.all([
        getResourceTree({}),
        getOperationList({})
      ]);
      // 资源映射
      const map = new Map<number, ResourceTreeNode>();
      for (const item of treeRes.items) {
        if (item.root) flattenTree(item.root, map);
      }
      resourceMap.value = map;
      resourceList.value = Array.from(map.values()).sort((a, b) => a.id - b.id);
      // 操作映射
      operationList.value = opRes.items;
      const typeNameMap = new Map<string, string>();
      for (const op of opRes.items) {
        if (op.resourceTypeCode != null && op.resourceTypeName) {
          typeNameMap.set(op.resourceTypeCode, op.resourceTypeName);
        }
      }
      // 资源类型选项：从 resourceMap distinct，中文名从 operationList 补
      const typeSet = new Set<string>();
      for (const node of map.values()) {
        typeSet.add(node.resourceTypeCode);
      }
      resourceTypeOptions.value = Array.from(typeSet)
        .sort()
        .map(code => ({ value: code, label: typeNameMap.get(code) ?? code }));
    } catch (e: any) {
      message(e.message || "加载引用数据失败", { type: "error" });
    }
  }

  // ========== 过滤 ==========

  const filteredList = computed(() => {
    const kw = search.keyword.trim().toLowerCase();
    if (!kw) return list.value;
    return list.value.filter(d => {
      const sourceName = resolveResourceName(d.resourceEntityId).toLowerCase();
      const targetName = resolveResourceName(
        d.dependsOnResourceEntityId
      ).toLowerCase();
      const desc = (d.description ?? "").toLowerCase();
      return (
        sourceName.includes(kw) ||
        targetName.includes(kw) ||
        d.sourceResourceCode.toLowerCase().includes(kw) ||
        d.depResourceCode.toLowerCase().includes(kw) ||
        desc.includes(kw)
      );
    });
  });

  function resetFilters() {
    search.keyword = "";
  }

  // ========== CRUD ==========

  async function submitDependency(
    form: DependencyFormData,
    mode: "create" | "edit",
    editingId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        const payload = buildCreatePayload(form);
        if (!payload) {
          message("资源选择无效，请重新选择", { type: "warning" });
          return false;
        }
        await createDependency(payload);
        message("资源依赖创建成功", { type: "success" });
      } else if (editingId) {
        const payload = buildUpdatePayload(form, editingId);
        if (!payload) {
          message("资源选择无效，请重新选择", { type: "warning" });
          return false;
        }
        await updateDependency(payload);
        message("资源依赖更新成功", { type: "success" });
      } else {
        return false;
      }
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  async function deleteDependency(
    row: ResourceDependencyResp
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除依赖「${resolveResourceLabel(row.resourceEntityId)} -> ${resolveResourceLabel(
          row.dependsOnResourceEntityId
        )}」吗？`,
        "删除资源依赖",
        {
          type: "warning",
          confirmButtonText: "删除",
          cancelButtonText: "取消"
        }
      );
    } catch {
      return false;
    }
    try {
      await removeDependencies([row.id]);
      message("资源依赖已删除", { type: "success" });
      await loadList();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  // ========== 循环检测 ==========

  async function checkCycle(
    sourceResourceEntityId: number | null,
    targetResourceEntityId: number | null
  ): Promise<DependencyCycleCheckResp | null> {
    if (sourceResourceEntityId == null || targetResourceEntityId == null) {
      message("请选择源资源与目标资源", { type: "warning" });
      return null;
    }
    const source = resourceMap.value.get(sourceResourceEntityId);
    const target = resourceMap.value.get(targetResourceEntityId);
    if (!source || !target) {
      message("资源选择无效", { type: "warning" });
      return null;
    }
    try {
      return await checkDependencyCycle({
        sourceResourceTypeCode: source.resourceTypeCode,
        sourceResourceCode: source.code,
        sourceCodeType: source.codeType,
        targetResourceTypeCode: target.resourceTypeCode,
        targetResourceCode: target.code,
        targetCodeType: target.codeType
      });
    } catch (e: any) {
      message(e.message || "循环检测失败", { type: "error" });
      return null;
    }
  }

  onMounted(() => {
    loadList();
    loadRefData();
  });

  return {
    canView,
    canCreate,
    canEdit,
    canDelete,
    list,
    loading,
    search,
    filteredList,
    resetFilters,
    loadList,
    submitDependency,
    deleteDependency,
    checkCycle,
    // 引用数据
    resourceMap,
    resourceList,
    operationList,
    resourceTypeOptions,
    // 映射辅助
    resolveResourceName,
    resolveResourceTypeCode,
    resolveResourceLabel,
    bitsToOpCodes,
    bitsToOpNames,
    resourcesForType,
    operationsForType
  };
}
