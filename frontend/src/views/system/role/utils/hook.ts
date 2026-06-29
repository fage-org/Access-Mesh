import { ref, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import {
  getRoleTree,
  createRole,
  updateRole,
  moveRole,
  removeRoles,
  listExtraRoles,
  addExtraRole,
  removeExtraRole,
  type RoleTreeNode,
  type RoleSummaryResp,
  type RoleTypeCode
} from "@/api/role-manage";
import {
  createEmptyRoleForm,
  isTypeRootNode,
  isReadonlyRoleType,
  type RoleFormData
} from "./types";

export function useRoleManage() {
  const roleTree = ref<RoleTreeNode[]>([]);
  const loading = ref(false);
  const selectedRole = ref<RoleTreeNode | null>(null);
  /** 分组角色额外基本角色列表 */
  const extraRoles = ref<RoleSummaryResp[]>([]);
  const extraRolesLoading = ref(false);

  /** 树过滤关键词 */
  const filterText = ref("");

  /** 树 props：el-tree 配置 */
  const treeProps = {
    label: "name",
    children: "children"
  };

  // ========== 树加载 ==========

  async function loadTree() {
    loading.value = true;
    try {
      const roots = await getRoleTree({ domainCode: null });
      roleTree.value = roots;
    } catch (error: any) {
      message(error.message || "加载角色树失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  /** 树节点过滤方法 */
  function filterNode(value: string, data: RoleTreeNode) {
    if (!value) return true;
    return data.name.includes(value);
  }

  /** 选中节点 */
  function handleNodeClick(node: RoleTreeNode) {
    selectedRole.value = node;
    // 分组角色选中时加载额外角色
    if (node.roleTypeCode === "GROUP_ROLE" && !isTypeRootNode(node)) {
      loadExtraRoles(node);
    } else {
      extraRoles.value = [];
    }
  }

  // ========== 额外角色（分组角色专属） ==========

  async function loadExtraRoles(node: RoleTreeNode) {
    if (!node.externalId) return;
    extraRolesLoading.value = true;
    try {
      extraRoles.value = await listExtraRoles({
        domainCode: null,
        groupRoleTypeCode: node.roleTypeCode,
        groupRoleExternalId: node.externalId
      });
    } catch (error: any) {
      message(error.message || "加载额外角色失败", { type: "error" });
      extraRoles.value = [];
    } finally {
      extraRolesLoading.value = false;
    }
  }

  async function handleAddExtraRole(basicRole: RoleSummaryResp) {
    const node = selectedRole.value;
    if (!node?.externalId) return;
    try {
      await addExtraRole({
        groupDomainCode: null,
        groupRoleTypeCode: node.roleTypeCode,
        groupRoleExternalId: node.externalId,
        basicDomainCode: null,
        basicRoleTypeCode: basicRole.roleTypeCode,
        basicRoleExternalId: basicRole.externalId
      });
      message("添加额外角色成功", { type: "success" });
      loadExtraRoles(node);
    } catch (error: any) {
      message(error.message || "添加失败", { type: "error" });
    }
  }

  async function handleRemoveExtraRole(basicRole: RoleSummaryResp) {
    const node = selectedRole.value;
    if (!node?.externalId) return;
    try {
      await removeExtraRole({
        groupDomainCode: null,
        groupRoleTypeCode: node.roleTypeCode,
        groupRoleExternalId: node.externalId,
        basicDomainCode: null,
        basicRoleTypeCode: basicRole.roleTypeCode,
        basicRoleExternalId: basicRole.externalId
      });
      message("移除额外角色成功", { type: "success" });
      loadExtraRoles(node);
    } catch (error: any) {
      message(error.message || "移除失败", { type: "error" });
    }
  }

  // ========== CRUD ==========

  /** 提交新建/编辑 */
  async function handleSubmitForm(
    mode: "create" | "edit",
    form: RoleFormData,
    editingId?: number
  ): Promise<boolean> {
    try {
      if (mode === "create") {
        await createRole({
          parentId: form.parentId,
          roleTypeCode: form.roleTypeCode as RoleTypeCode,
          externalId: form.externalId || null,
          name: form.name,
          sortOrder: form.sortOrder,
          extra: form.extra || null
        });
        message("创建成功", { type: "success" });
      } else if (editingId) {
        await updateRole({
          roleId: editingId,
          name: form.name,
          status: form.status,
          sortOrder: form.sortOrder,
          extra: form.extra || null
        });
        message("更新成功", { type: "success" });
      }
      await loadTree();
      return true;
    } catch (error: any) {
      message(error.message || "操作失败", { type: "error" });
      return false;
    }
  }

  /** 切换角色启用/禁用状态 */
  async function handleToggleStatus(node: RoleTreeNode) {
    const next = node.status === 1 ? 0 : 1;
    try {
      await updateRole({
        roleId: node.id,
        status: next
      });
      node.status = next;
      message(`${next === 1 ? "启用" : "禁用"}成功`, { type: "success" });
    } catch (error: any) {
      message(error.message || "操作失败", { type: "error" });
    }
  }

  /** 删除角色 */
  async function handleDelete(node: RoleTreeNode) {
    try {
      await ElMessageBox.confirm(
        `确认删除角色「${node.name}」？删除后其下权限配置与用户关联将一并处理。`,
        "删除确认",
        {
          confirmButtonText: "确定删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      return; // 取消
    }
    try {
      await removeRoles([node.id]);
      message("删除成功", { type: "success" });
      if (selectedRole.value?.id === node.id) {
        selectedRole.value = null;
        extraRoles.value = [];
      }
      await loadTree();
    } catch (error: any) {
      message(error.message || "删除失败", { type: "error" });
    }
  }

  /** 移动节点（拖拽：修改 parentId） */
  async function handleNodeDrop(
    draggingNode: { data: RoleTreeNode },
    targetNode: { data: RoleTreeNode },
    dropType: "before" | "after" | "inner"
  ) {
    const dragging = draggingNode.data;
    // 只读类型不可移动
    if (isReadonlyRoleType(dragging.roleTypeCode)) {
      message("组织/岗位角色由同步生成，不可移动", { type: "warning" });
      await loadTree();
      return;
    }
    // 计算目标父 id
    let parentId: number | null;
    if (dropType === "inner") {
      parentId = targetNode.data.id;
    } else {
      parentId = targetNode.data.parentId;
    }
    // 不允许跨类型移动到异类虚拟根
    if (
      parentId !== null &&
      targetNode.data.roleTypeCode !== dragging.roleTypeCode &&
      !isTypeRootNode(targetNode.data)
    ) {
      // 同类型虚拟根的兄弟节点判定：目标父必须是同类型
    }
    try {
      await moveRole({ roleId: dragging.id, parentId });
      message("移动成功", { type: "success" });
    } catch (error: any) {
      message(error.message || "移动失败", { type: "error" });
      await loadTree();
    }
  }

  onMounted(() => {
    loadTree();
  });

  return {
    roleTree,
    loading,
    selectedRole,
    extraRoles,
    extraRolesLoading,
    filterText,
    treeProps,
    loadTree,
    filterNode,
    handleNodeClick,
    loadExtraRoles,
    handleAddExtraRole,
    handleRemoveExtraRole,
    handleSubmitForm,
    handleToggleStatus,
    handleDelete,
    handleNodeDrop,
    createEmptyForm: createEmptyRoleForm
  };
}
