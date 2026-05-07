<script setup lang="ts">
import { ref, watch, onUnmounted } from "vue";
import {
  type RoleTreeNode,
  getRoleTypeTag,
  getStatusTag
} from "@/api/perm/role";

defineOptions({
  name: "RoleTree"
});

const props = defineProps<{
  data: Array<RoleTreeNode>;
  loading: boolean;
  selectedRoleId: number | null;
  canCreate: boolean;
  canDelete: boolean;
}>();

const emit = defineEmits<{
  nodeClick: [roleId: number];
  createChild: [parentId: number];
  delete: [roleId: number];
}>();

// ========== 状态定义 ==========

const filterText = ref("");
const treeRef = ref();
let filterTimer: ReturnType<typeof setTimeout> | null = null;

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "name",
  value: "id"
};

// ========== Debounced 筛选 ==========

watch(filterText, val => {
  if (filterTimer) {
    clearTimeout(filterTimer);
  }
  filterTimer = setTimeout(() => {
    treeRef.value?.filter(val);
  }, 300);
});

// 清理 timer 防止内存泄漏
onUnmounted(() => {
  if (filterTimer) {
    clearTimeout(filterTimer);
    filterTimer = null;
  }
});

// ========== 树筛选方法 ==========

const filterNode = (value: string, data: RoleTreeNode) => {
  if (!value) return true;
  return data.name.toLowerCase().includes(value.toLowerCase());
};

// ========== 树节点点击 ==========

const handleNodeClick = (data: RoleTreeNode) => {
  emit("nodeClick", data.id);
};

// ========== 新增子节点 ==========

const handleCreateChild = (data: RoleTreeNode) => {
  emit("createChild", data.id);
};

// ========== 删除节点 ==========

const handleDelete = (data: RoleTreeNode) => {
  emit("delete", data.id);
};
</script>

<template>
  <div class="role-tree-container">
    <!-- 搜索框 -->
    <el-input
      v-model="filterText"
      placeholder="搜索角色名称"
      clearable
      class="mb-4"
    />

    <!-- 角色树 -->
    <el-scrollbar class="flex-1">
      <el-tree
        ref="treeRef"
        v-loading="props.loading"
        :data="props.data"
        :props="defaultProps"
        node-key="id"
        :current-node-key="props.selectedRoleId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        class="role-tree"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="custom-tree-node">
            <div class="node-content">
              <span class="truncate">{{ data.name }}</span>
              <el-tag
                :type="getRoleTypeTag(data.roleTypeCode).type"
                size="small"
                class="ml-2"
              >
                {{ getRoleTypeTag(data.roleTypeCode).text }}
              </el-tag>
              <el-tag
                v-if="data.status === 0"
                :type="getStatusTag(data.status).type"
                size="small"
                class="ml-2"
              >
                {{ getStatusTag(data.status).text }}
              </el-tag>
            </div>
            <div class="tree-node-actions">
              <el-button
                v-if="props.canCreate"
                type="primary"
                link
                size="small"
                @click.stop="handleCreateChild(data)"
              >
                新增
              </el-button>
              <el-button
                v-if="props.canDelete"
                type="danger"
                link
                size="small"
                @click.stop="handleDelete(data)"
              >
                删除
              </el-button>
            </div>
          </div>
        </template>
      </el-tree>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.role-tree-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.role-tree {
  :deep(.el-tree-node__content) {
    height: 36px;
  }
}

.custom-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 14px;

  .node-content {
    display: flex;
    align-items: center;
    flex: 1;
  }

  .tree-node-actions {
    display: none;
    margin-left: 8px;
  }

  &:hover .tree-node-actions {
    display: flex;
    gap: 4px;
  }
}
</style>
