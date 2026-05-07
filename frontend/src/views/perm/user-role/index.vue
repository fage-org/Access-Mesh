<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getUserRoles,
  batchAssignRoles,
  batchRevokeRoles,
  type UserRoleItem,
  SUBJECT_TYPE_CODES
} from "@/api/perm/userRole";
import {
  getRoleTree,
  transformRoleTreeResponse,
  type RoleTreeNode
} from "@/api/perm/role";
import UserSelector from "./components/UserSelector.vue";
import OrgContextSelector from "./components/OrgContextSelector.vue";
import RoleAssignTree from "./components/RoleAssignTree.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "PermUserRole"
});

// ========== 状态定义 ==========

const selectedUserId = ref<number | null>(null);
const selectedUsername = ref<string | null>(null);
const selectedOrgId = ref<number | null>(null);
const assignedRoles = ref<Array<UserRoleItem>>([]);
const roleTreeData = ref<Array<RoleTreeNode>>([]);
const loading = ref(false);
const assigning = ref(false);

// ========== 权限计算 ==========

const canAssign = hasPerms(PERM_CODES.SYS_USER_ASSIGN_ROLE);

// ========== 数据加载 ==========

const loadRoleTree = async () => {
  try {
    const res = await getRoleTree();
    if (res.success) {
      roleTreeData.value = transformRoleTreeResponse(res);
    }
  } catch {
    ElMessage.error("加载角色树失败");
  }
};

const loadAssignedRoles = async () => {
  if (!selectedUsername.value) return;

  loading.value = true;
  try {
    const res = await getUserRoles({
      subjectTypeCode: SUBJECT_TYPE_CODES.USER,
      subjectExternalId: selectedUsername.value
    });
    if (res.success) {
      assignedRoles.value = res.data.roles || [];
    }
  } catch {
    ElMessage.error("加载已分配角色失败");
  } finally {
    loading.value = false;
  }
};

// ========== 用户选择 ==========

const handleUserSelect = (userId: number, username: string) => {
  selectedUserId.value = userId;
  selectedUsername.value = username;
  loadAssignedRoles();
};

// ========== 组织上下文选择 ==========

const handleOrgSelect = (orgId: number) => {
  selectedOrgId.value = orgId;
};

// ========== 批量分配角色 ==========

const handleAssignRoles = async (
  roleTypeCode: string,
  roleExternalId: string
) => {
  if (assigning.value) return;
  if (!selectedUsername.value) {
    ElMessage.warning("请先选择用户");
    return;
  }
  if (!selectedOrgId.value) {
    ElMessage.warning("请先选择组织上下文");
    return;
  }
  if (!canAssign) {
    ElMessage.warning("无权限执行此操作");
    return;
  }

  assigning.value = true;
  try {
    const res = await batchAssignRoles({
      subjectExternalIds: [selectedUsername.value],
      subjectTypeCode: SUBJECT_TYPE_CODES.USER,
      domainCode: String(selectedOrgId.value),
      roleTypeCode,
      roleExternalId
    });
    if (res.success) {
      ElMessage.success("分配成功");
      await loadAssignedRoles();
    }
  } catch {
    ElMessage.error("分配失败");
  } finally {
    assigning.value = false;
  }
};

// ========== 移除角色 ==========

const handleRevokeRole = async (role: UserRoleItem) => {
  if (!selectedUsername.value) return;
  if (!canAssign) {
    ElMessage.warning("无权限执行此操作");
    return;
  }

  try {
    await ElMessageBox.confirm("确认回收该角色?", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });

    const res = await batchRevokeRoles({
      items: [
        {
          subjectTypeCode: SUBJECT_TYPE_CODES.USER,
          subjectExternalId: selectedUsername.value,
          domainCode: role.targetType,
          roleTypeCode: role.roleTypeCode,
          roleExternalId: role.roleExternalId,
          relationId: role.relationId
        }
      ]
    });
    if (res.success) {
      ElMessage.success("回收成功");
      await loadAssignedRoles();
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("回收失败");
    }
  }
};

// ========== 初始化 ==========

onMounted(() => {
  loadRoleTree();
});
</script>

<template>
  <div class="user-role-assignment">
    <div class="flex h-full">
      <!-- 左侧用户选择 -->
      <UserSelector
        class="w-[260px] border-r"
        :selected-user-id="selectedUserId"
        @select="handleUserSelect"
      />

      <!-- 右侧角色分配 -->
      <div class="flex-1 p-4">
        <div v-if="!selectedUserId" class="text-center py-10 text-gray-500">
          请先选择用户
        </div>

        <div v-else>
          <!-- 组织上下文选择 -->
          <OrgContextSelector
            :selected-org-id="selectedOrgId"
            class="mb-4"
            @select="handleOrgSelect"
          />

          <!-- 角色分配树 -->
          <RoleAssignTree
            :role-tree-data="roleTreeData"
            :assigned-roles="assignedRoles"
            :loading="loading"
            :assigning="assigning"
            :can-assign="canAssign"
            @assign="handleAssignRoles"
          />

          <!-- 已分配角色列表 -->
          <div class="mt-4">
            <h4 class="mb-2 font-medium">已分配角色</h4>
            <el-table v-loading="loading" :data="assignedRoles" border stripe>
              <el-table-column prop="roleName" label="角色名称" />
              <el-table-column prop="roleTypeCode" label="角色类型" />
              <el-table-column prop="roleExternalId" label="角色编码" />
              <el-table-column prop="targetType" label="组织上下文" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button
                    v-if="canAssign"
                    type="danger"
                    link
                    size="small"
                    @click="handleRevokeRole(row)"
                  >
                    移除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div
              v-if="assignedRoles.length === 0 && !loading"
              class="text-center py-10 text-gray-500"
            >
              暂未分配角色
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.user-role-assignment {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
