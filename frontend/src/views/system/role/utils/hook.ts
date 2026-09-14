import { ref, onMounted } from "vue";
import { message } from "@/utils/message";
import { ElMessageBox } from "element-plus";
import {
  getRoleTree,
  createRole,
  updateRole,
  moveRole,
  removeRoles,
  type RoleTreeNode,
  type RoleTypeCode
} from "@/api/role-manage";
import {
  createEmptyRoleForm,
  isReadonlyRoleType,
  isPageVisibleRoleType,
  type RoleFormData
} from "./types";

export function useRoleManage() {
  const roleTree = ref<RoleTreeNode[]>([]);
  const loading = ref(false);
  const selectedRole = ref<RoleTreeNode | null>(null);

  /** 树过滤关键词 */
  const filterText = ref("");

  /** 树 props：el-tree 配置 */
  const treeProps = {
    label: "name",
    children: "children"
  };

  // ========== 树加载 ==========

  /**
   * 过滤树：仅保留角色管理页可管理的类型（MANAGEABLE_ROLE_TYPES，T-PERM-043 后仅
   * BASIC_ROLE）。ORG/POSITION/PERSONAL 由外部同步生成，不在本页展示（归权限授予/
   * 用户详情）；GROUP_ROLE 写入口已删除、选项隐藏，存量节点整棵裁掉（额外角色
   * 面板与 extra-roles API 已随 2026-09-14 轻量清扫批次删除）。
   *
   * C2 后树为扁平森林（parentId=null 真实角色为根，无类型虚拟根）：
   * 递归裁剪——节点类型不在可见集合则整棵裁掉，同类型子树保留。
   */
  function filterVisibleTree(nodes: RoleTreeNode[]): RoleTreeNode[] {
    const result: RoleTreeNode[] = [];
    for (const node of nodes) {
      if (!isPageVisibleRoleType(node.roleTypeCode)) continue;
      const children = node.children
        ? filterSameTypeChildren(node.children, node.roleTypeCode)
        : [];
      result.push({ ...node, children });
    }
    return result;
  }

  /** 递归保留同类型子节点（树内同类型层级） */
  function filterSameTypeChildren(
    nodes: RoleTreeNode[],
    typeCode: string
  ): RoleTreeNode[] {
    const result: RoleTreeNode[] = [];
    for (const node of nodes) {
      if (node.roleTypeCode !== typeCode) continue;
      const children = node.children
        ? filterSameTypeChildren(node.children, typeCode)
        : [];
      result.push({ ...node, children });
    }
    return result;
  }

  async function loadTree() {
    loading.value = true;
    try {
      const roots = await getRoleTree({ domainCode: null });
      roleTree.value = filterVisibleTree(roots);
      // 同步 selectedRole：在新树中按 id 重定位替换，避免拖拽/CRUD 后右侧详情拿旧节点
      // （评审 P2-loadTree：el-tree/CRUD 后 selectedRole 仍是旧对象，parentId 等字段滞后）。
      const currentId = selectedRole.value?.id;
      if (currentId != null) {
        const refreshed = findNodeInTree(roleTree.value, currentId);
        if (refreshed) {
          selectedRole.value = refreshed;
        } else {
          // 节点已不在树中（被删/被过滤）→ 清空选中
          selectedRole.value = null;
        }
      }
    } catch (error: any) {
      message(error.message || "加载角色树失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  /** 在树中按 id 递归查找节点 */
  function findNodeInTree(
    nodes: RoleTreeNode[],
    id: number
  ): RoleTreeNode | null {
    for (const node of nodes) {
      if (node.id === id) return node;
      if (node.children) {
        const found = findNodeInTree(node.children, id);
        if (found) return found;
      }
    }
    return null;
  }

  /** 树节点过滤方法 */
  function filterNode(value: string, data: RoleTreeNode) {
    if (!value) return true;
    return data.name.includes(value);
  }

  /** 选中节点 */
  function handleNodeClick(node: RoleTreeNode) {
    selectedRole.value = node;
  }

  // ========== CRUD ==========

  /**
   * 提交新建/编辑
   *
   * @param originalExtra 编辑目标的原 extra（来自 detail 回填的 initialData）——
   *   extraClear 公式消费：原值非空且表单清空 → 显式清空（T-FE-016，对齐资源页
   *   resource-operation hook 同构公式；JSON null 无法区分「未传」与「清空」）
   */
  async function handleSubmitForm(
    mode: "create" | "edit",
    form: RoleFormData,
    editingId?: number,
    originalExtra?: string | null
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
          extra: form.extra || null,
          extraClear: originalExtra != null && !form.extra
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
    dropType: "prev" | "inner" | "next"
  ) {
    const dragging = draggingNode.data;
    // 只读类型不可移动
    if (isReadonlyRoleType(dragging.roleTypeCode)) {
      message("组织/岗位/个人角色由同步生成，不可移动", { type: "warning" });
      await loadTree();
      return;
    }
    // 跨类型移动非法：inner 时目标是父（须同类型），before/after 时是兄弟（须同类型）
    if (targetNode.data.roleTypeCode !== dragging.roleTypeCode) {
      message("不允许跨角色类型移动", { type: "warning" });
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
    try {
      await moveRole({ roleId: dragging.id, parentId });
      message("移动成功", { type: "success" });
      // 重新拉树同步 parentId（el-tree 仅移动 DOM，不更新 data.parentId，
      // 不重拉会导致后续编辑父角色展示/连续拖拽按旧 parentId 判断）
      await loadTree();
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
    filterText,
    treeProps,
    loadTree,
    filterNode,
    handleNodeClick,
    handleSubmitForm,
    handleToggleStatus,
    handleDelete,
    handleNodeDrop,
    createEmptyForm: createEmptyRoleForm
  };
}
