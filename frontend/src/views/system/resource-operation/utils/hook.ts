import { ref, reactive, computed, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import { hasPerms } from "@/utils/auth";
import {
  getResourceTree,
  createResource,
  updateResource,
  moveResource,
  removeResources,
  getOperationList,
  createOperation,
  updateOperation,
  removeOperations,
  type ResourceTreeNode,
  type OperationPermissionResp
} from "@/api/resource-operation";
import { getTypeDefList, TYPE_KEY, type TypeDefResp } from "@/api/type-def";
import { RESOURCE_OPERATION_PERMS } from "./perms";
import type {
  ResourceFormData,
  OperationFormData,
  ResourceMoveFormData
} from "./types";

/**
 * 资源与操作定义页 hook。
 *
 * 布局：左侧资源树（按资源类型过滤）+ 右侧选中资源信息条 + 该资源类型的操作权限表。
 *
 * 联动语义：
 * - `selectedResourceTypeCode` 是全局筛选维度，切换时同时刷新资源树与操作权限表，并清空选中节点。
 * - 选中树节点只更新 `selectedNode`（右侧信息条），不重载操作权限表（操作权限按 resourceType 维度，不跟单实例）。
 *
 * 权限隔离（设计 §7）：
 * - `canViewResource`（RESOURCE:VIEW）门控资源树加载与可见性。
 * - `canViewOperation`（OPERATION:VIEW）门控操作权限表加载与可见性。
 * - `loadTree`/`loadOperations` 内置权限短路，无权限直接清空返回，避免越权加载。
 *
 * 范式对齐 role/utils/hook.ts（左树右详情）+ type-def/utils/hook.ts（表格 CRUD）。
 */
export function useResourceOperation() {
  // ========== 权限门控 ==========
  const canViewResource = computed(() =>
    hasPerms(RESOURCE_OPERATION_PERMS.RESOURCE_VIEW)
  );
  const canViewOperation = computed(() =>
    hasPerms(RESOURCE_OPERATION_PERMS.OPERATION_VIEW)
  );

  // ========== 资源类型下拉（数据源 type_definition typeKey=resource_type） ==========
  const resourceTypes = ref<TypeDefResp[]>([]);
  const selectedResourceTypeCode = ref<string | null>(null);

  async function loadResourceTypes() {
    try {
      const res = await getTypeDefList({ typeKey: TYPE_KEY.RESOURCE_TYPE });
      resourceTypes.value = res.items
        .filter(t => t.typeKey === TYPE_KEY.RESOURCE_TYPE)
        .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
    } catch (e: any) {
      message(e.message || "加载资源类型失败", { type: "error" });
    }
  }

  // ========== 资源树 ==========
  const treeData = ref<ResourceTreeNode[]>([]);
  const resourceLoading = ref(false);
  const resourceSearch = ref("");
  const selectedNode = ref<ResourceTreeNode | null>(null);

  /** 在森林中递归查找指定 id 的节点（loadTree 后重定位 selectedNode 用） */
  function findNodeById(
    nodes: ResourceTreeNode[],
    id: number
  ): ResourceTreeNode | null {
    for (const node of nodes) {
      if (node.id === id) return node;
      if (node.children) {
        const found = findNodeById(node.children, id);
        if (found) return found;
      }
    }
    return null;
  }

  async function loadTree() {
    if (!canViewResource.value || !selectedResourceTypeCode.value) {
      treeData.value = [];
      return;
    }
    resourceLoading.value = true;
    try {
      const res = await getResourceTree({
        resourceTypeCode: selectedResourceTypeCode.value
      });
      treeData.value = res.items
        .map(i => i.root)
        .filter((n): n is ResourceTreeNode => n != null);
      // 刷新后重新定位选中节点（名称/状态/路径可能已变；节点被删/移出本类型则置 null）
      if (selectedNode.value) {
        selectedNode.value = findNodeById(
          treeData.value,
          selectedNode.value.id
        );
      }
    } catch (e: any) {
      message(e.message || "加载资源树失败", { type: "error" });
    } finally {
      resourceLoading.value = false;
    }
  }

  /** 切换资源类型：回写 typeCode，清空选中节点，按权限刷新树与操作表 */
  async function onResourceTypeChange(typeCode: string) {
    selectedResourceTypeCode.value = typeCode;
    selectedNode.value = null;
    await Promise.all([loadTree(), loadOperations()]);
  }

  function selectNode(node: ResourceTreeNode | null) {
    selectedNode.value = node;
  }

  // ========== 操作权限表 ==========
  const operations = ref<OperationPermissionResp[]>([]);
  const operationLoading = ref(false);
  const operationSearch = reactive({ keyword: "" });

  async function loadOperations() {
    if (!canViewOperation.value || !selectedResourceTypeCode.value) {
      operations.value = [];
      return;
    }
    operationLoading.value = true;
    try {
      const res = await getOperationList({
        resourceTypeCode: selectedResourceTypeCode.value
      });
      operations.value = res.items
        .slice()
        .sort((a, b) => a.binaryBit - b.binaryBit);
    } catch (e: any) {
      message(e.message || "加载操作权限失败", { type: "error" });
    } finally {
      operationLoading.value = false;
    }
  }

  const filteredOperations = computed(() => {
    if (!operationSearch.keyword) return operations.value;
    const kw = operationSearch.keyword.toLowerCase();
    return operations.value.filter(
      op =>
        op.code.toLowerCase().includes(kw) || op.name.toLowerCase().includes(kw)
    );
  });

  function resetOperationFilters() {
    operationSearch.keyword = "";
  }

  // ========== 资源 CRUD ==========
  async function submitResource(
    form: ResourceFormData,
    mode: "create" | "edit",
    editingId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        await createResource({
          parentId: form.parentId,
          resourceTypeCode: form.resourceTypeCode,
          code: form.code,
          codeType: form.codeType || undefined,
          name: form.name,
          status: form.status,
          sortOrder: form.sortOrder,
          extra: form.extra || null
        });
        message("资源创建成功", { type: "success" });
      } else if (editingId) {
        await updateResource({
          id: editingId,
          name: form.name,
          status: form.status,
          sortOrder: form.sortOrder,
          extra: form.extra || null
        });
        message("资源更新成功", { type: "success" });
      }
      await loadTree();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  async function deleteResource(node: ResourceTreeNode): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除资源「${node.name}」吗？其所有子资源将一并删除，关联的权限配置将失效。`,
        "删除资源",
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
      await removeResources([node.id]);
      message("资源已删除", { type: "success" });
      if (selectedNode.value?.id === node.id) selectedNode.value = null;
      await loadTree();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  async function submitMove(form: ResourceMoveFormData): Promise<boolean> {
    try {
      await moveResource({
        resourceId: form.resourceId,
        parentId: form.parentId
      });
      message("资源已移动", { type: "success" });
      await loadTree();
      return true;
    } catch (e: any) {
      message(e.message || "移动失败", { type: "error" });
      return false;
    }
  }

  // ========== 操作权限 CRUD ==========
  async function submitOperation(
    form: OperationFormData,
    mode: "create" | "edit",
    editingId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        await createOperation({
          resourceTypeCode: form.resourceTypeCode,
          code: form.code,
          name: form.name,
          binaryBit: form.binaryBit,
          inheritMask: form.inheritMask
        });
        message("操作权限创建成功", { type: "success" });
      } else if (editingId) {
        await updateOperation({
          operationId: editingId,
          name: form.name,
          binaryBit: form.binaryBit,
          inheritMask: form.inheritMask
        });
        message("操作权限更新成功", { type: "success" });
      }
      await loadOperations();
      return true;
    } catch (e: any) {
      message(e.message || "操作失败", { type: "error" });
      return false;
    }
  }

  async function deleteOperation(
    row: OperationPermissionResp
  ): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        `确认删除操作「${row.name}（${row.code}）」吗？关联的授权关系将失效。`,
        "删除操作权限",
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
      await removeOperations([row.id]);
      message("操作权限已删除", { type: "success" });
      await loadOperations();
      return true;
    } catch (e: any) {
      message(e.message || "删除失败", { type: "error" });
      return false;
    }
  }

  // ========== 刷新入口 ==========
  /** 刷新全部（左侧目录 + 右侧操作表，按权限） */
  async function refreshAll() {
    await Promise.all([loadTree(), loadOperations()]);
  }

  onMounted(async () => {
    await loadResourceTypes();
    if (resourceTypes.value.length > 0) {
      selectedResourceTypeCode.value = resourceTypes.value[0].typeCode;
      await Promise.all([loadTree(), loadOperations()]);
    }
  });

  return {
    // 权限
    canViewResource,
    canViewOperation,
    // 资源类型
    resourceTypes,
    selectedResourceTypeCode,
    onResourceTypeChange,
    // 资源树
    treeData,
    resourceLoading,
    resourceSearch,
    selectedNode,
    selectNode,
    loadTree,
    // 资源 CRUD
    submitResource,
    deleteResource,
    submitMove,
    // 操作权限
    operations,
    operationLoading,
    operationSearch,
    filteredOperations,
    resetOperationFilters,
    loadOperations,
    submitOperation,
    deleteOperation,
    // 刷新
    refreshAll
  };
}
