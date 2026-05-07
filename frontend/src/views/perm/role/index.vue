<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getRoleTree,
  getRoleDetail,
  deleteRole,
  transformRoleTreeResponse,
  type RoleTreeNode,
  type RoleDetail
} from "@/api/perm/role";
import RoleTree from "./components/RoleTree.vue";
import RoleDetailPanel from "./components/RoleDetail.vue";
import RoleForm from "./components/RoleForm.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "PermRole"
});

const router = useRouter();
const treeData = ref<Array<RoleTreeNode>>([]);
const selectedRoleId = ref<number | null>(null);
const selectedRoleDetail = ref<RoleDetail | null>(null);
const loading = ref(false);
const roleFormRef = ref();

// ========== 权限计算 ==========

const canCreate = hasPerms(PERM_CODES.SYS_ROLE_CREATE);
const canUpdate = hasPerms(PERM_CODES.SYS_ROLE_UPDATE);
const canDelete = hasPerms(PERM_CODES.SYS_ROLE_DELETE);
const canAssignPerm = hasPerms(PERM_CODES.SYS_ROLE_ASSIGN_PERM);

// ========== 数据加载 ==========

const loadRoleTree = async () => {
  loading.value = true;
  try {
    const res = await getRoleTree();
    if (res.success) {
      treeData.value = transformRoleTreeResponse(res);
    }
  } catch {
    ElMessage.error("加载角色树失败");
  } finally {
    loading.value = false;
  }
};

const loadRoleDetail = async (roleId: number) => {
  try {
    const res = await getRoleDetail({ id: roleId });
    if (res.success) {
      selectedRoleDetail.value = res.data;
    }
  } catch {
    ElMessage.error("加载角色详情失败");
  }
};

// ========== 角色树交互 ==========

const handleRoleNodeClick = async (roleId: number) => {
  selectedRoleId.value = roleId;
  await loadRoleDetail(roleId);
};

const handleCreateRoot = () => {
  roleFormRef.value.openDialog(null);
};

const handleCreateChild = (parentId: number) => {
  roleFormRef.value.openDialog(parentId);
};

const handleEdit = () => {
  if (selectedRoleDetail.value) {
    roleFormRef.value.openDialog(
      selectedRoleDetail.value.parentId,
      selectedRoleDetail.value
    );
  }
};

const handleDelete = async (roleId: number) => {
  try {
    await ElMessageBox.confirm(
      "确认删除该角色?删除后子角色将一并删除",
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await deleteRole({ ids: [roleId] });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadRoleTree();
      if (selectedRoleId.value === roleId) {
        selectedRoleId.value = null;
        selectedRoleDetail.value = null;
      }
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("删除角色失败");
    }
  }
};

const handleConfigPermission = () => {
  if (selectedRoleId.value) {
    router.push({
      path: "/perm/role/permission",
      query: { roleId: selectedRoleId.value }
    });
  }
};

const handleFormSuccess = async () => {
  const currentRoleId = selectedRoleId.value;
  await loadRoleTree();
  if (currentRoleId) {
    const roleExists = findRoleInTree(treeData.value, currentRoleId);
    if (roleExists) {
      await loadRoleDetail(currentRoleId);
    } else {
      selectedRoleId.value = null;
      selectedRoleDetail.value = null;
    }
  }
};

// ========== 递归查找角色 ==========

const findRoleInTree = (
  nodes: Array<RoleTreeNode>,
  targetId: number
): RoleTreeNode | null => {
  for (const node of nodes) {
    if (node.id === targetId) return node;
    if (node.children) {
      const found = findRoleInTree(node.children, targetId);
      if (found) return found;
    }
  }
  return null;
};

// ========== 初始化 ==========

onMounted(() => {
  loadRoleTree();
});
</script>

<template>
  <div class="role-management">
    <div class="flex h-full">
      <!-- 左侧角色树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            :disabled="!canCreate"
            @click="handleCreateRoot"
          >
            新增根角色
          </el-button>
        </div>
        <RoleTree
          :data="treeData"
          :loading="loading"
          :selected-role-id="selectedRoleId"
          :can-create="canCreate"
          :can-delete="canDelete"
          @node-click="handleRoleNodeClick"
          @create-child="handleCreateChild"
          @delete="handleDelete"
        />
      </div>

      <!-- 右侧详情区域 -->
      <div class="flex-1 p-4">
        <RoleDetailPanel
          :role-detail="selectedRoleDetail"
          :role-id="selectedRoleId"
          :can-update="canUpdate"
          :can-assign-perm="canAssignPerm"
          @edit="handleEdit"
          @config-permission="handleConfigPermission"
        />
      </div>

      <!-- 角色表单弹窗 -->
      <RoleForm ref="roleFormRef" @success="handleFormSuccess" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.role-management {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
