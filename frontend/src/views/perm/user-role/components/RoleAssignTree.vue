<script setup lang="ts">
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  type RoleTreeNode,
  getRoleTypeTag,
  flattenRoleTree
} from "@/api/perm/role";
import { type UserRoleItem } from "@/api/perm/userRole";

defineOptions({
  name: "RoleAssignTree"
});

const props = defineProps<{
  roleTreeData: Array<RoleTreeNode>;
  assignedRoles: Array<UserRoleItem>;
  loading: boolean;
  assigning: boolean;
  canAssign: boolean;
}>();

const emit = defineEmits<{
  assign: [roleTypeCode: string, roleExternalId: string];
}>();

// ========== 状态定义 ==========

const selectedRoleExternalId = ref<string | null>(null);
const selectedRoleTypeCode = ref<string | null>(null);

// ========== 计算属性 ==========

const flatRoles = computed(() => {
  return flattenRoleTree(props.roleTreeData);
});

// 已分配角色 externalId 列表
const assignedRoleExternalIds = computed(() => {
  return props.assignedRoles.map(r => r.roleExternalId);
});

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "name",
  value: "externalId"
};

// ========== 节点点击 ==========

const handleNodeClick = (data: RoleTreeNode) => {
  selectedRoleExternalId.value = data.externalId;
  selectedRoleTypeCode.value = data.roleTypeCode;
};

// ========== 分配角色 ==========

const handleAssign = () => {
  if (!selectedRoleExternalId.value || !selectedRoleTypeCode.value) {
    ElMessage.warning("请先选择角色");
    return;
  }

  if (assignedRoleExternalIds.value.includes(selectedRoleExternalId.value)) {
    ElMessage.warning("该角色已分配");
    return;
  }

  emit("assign", selectedRoleTypeCode.value, selectedRoleExternalId.value);
};

// ========== 状态标签 ==========

const isAssigned = (externalId: string) => {
  return assignedRoleExternalIds.value.includes(externalId);
};
</script>

<template>
  <div class="role-assign-tree">
    <h4 class="mb-2 font-medium">角色选择</h4>

    <!-- 角色树 -->
    <el-scrollbar class="flex-1" style="max-height: 400px">
      <el-tree
        v-loading="props.loading"
        :data="props.roleTreeData"
        :props="defaultProps"
        node-key="externalId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="flex items-center justify-between w-full pr-2">
            <div class="flex items-center gap-2">
              <span class="truncate">{{ data.name }}</span>
              <el-tag
                :type="getRoleTypeTag(data.roleTypeCode).type"
                size="small"
              >
                {{ getRoleTypeTag(data.roleTypeCode).text }}
              </el-tag>
            </div>
            <el-tag v-if="isAssigned(data.externalId)" type="info" size="small">
              已分配
            </el-tag>
          </div>
        </template>
      </el-tree>
    </el-scrollbar>

    <!-- 分配按钮 -->
    <el-button
      type="primary"
      size="small"
      class="mt-4"
      :loading="props.assigning"
      :disabled="!props.canAssign || props.assigning"
      @click="handleAssign"
    >
      分配选中角色
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.role-assign-tree {
  :deep(.el-tree-node__content) {
    height: 36px;
  }
}
</style>
