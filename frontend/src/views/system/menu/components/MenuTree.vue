<script setup lang="ts">
import { ref, watch, onUnmounted } from "vue";
import { type MenuNode } from "@/api/admin/menu";

defineOptions({
  name: "MenuTree"
});

const props = defineProps<{
  data: Array<MenuNode>;
  loading: boolean;
  selectedMenuId: number | null;
  canCreate: boolean;
  canDelete: boolean;
}>();

const emit = defineEmits<{
  nodeClick: [menuId: number];
  createChild: [parentId: number];
  delete: [menuId: number];
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

const filterNode = (value: string, data: MenuNode) => {
  if (!value) return true;
  return data.name.toLowerCase().includes(value.toLowerCase());
};

// ========== 类型标签 ==========

const MENU_TYPE_TAG = {
  0: { text: "目录", type: "primary" as const },
  1: { text: "菜单", type: "success" as const },
  2: { text: "按钮", type: "warning" as const }
};

const getTypeTag = (
  type: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    MENU_TYPE_TAG[type as keyof typeof MENU_TYPE_TAG] || {
      text: "未知",
      type: "info" as const
    }
  );
};

// ========== 树节点点击 ==========

const handleNodeClick = (data: MenuNode) => {
  emit("nodeClick", data.id);
};

// ========== 新增子节点 ==========

const handleCreateChild = (data: MenuNode) => {
  emit("createChild", data.id);
};

// ========== 删除节点 ==========

const handleDelete = (data: MenuNode) => {
  emit("delete", data.id);
};
</script>

<template>
  <div class="menu-tree-container">
    <!-- 搜索框 -->
    <el-input
      v-model="filterText"
      placeholder="搜索菜单名称"
      clearable
      class="mb-4"
    />

    <!-- 菜单树 -->
    <el-scrollbar class="flex-1">
      <el-tree
        ref="treeRef"
        v-loading="props.loading"
        :data="props.data"
        :props="defaultProps"
        node-key="id"
        :current-node-key="props.selectedMenuId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        class="menu-tree"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="custom-tree-node">
            <div class="node-content">
              <span class="truncate">{{ data.name }}</span>
              <el-tag
                :type="getTypeTag(data.type).type"
                size="small"
                class="ml-2"
              >
                {{ getTypeTag(data.type).text }}
              </el-tag>
              <el-tag
                v-if="data.status === 0"
                type="danger"
                size="small"
                class="ml-2"
              >
                停用
              </el-tag>
            </div>
            <div class="tree-node-actions">
              <el-button
                v-if="data.type !== 2 && props.canCreate"
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
.menu-tree-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.menu-tree {
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
    flex: 1;
    align-items: center;
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
