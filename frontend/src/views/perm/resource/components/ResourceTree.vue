<script setup lang="ts">
import { ref, onUnmounted } from "vue";
import {
  type ResourceTreeNode,
  getResourceStatusTag,
  getResourceTypeTag
} from "@/api/perm/resource";

defineOptions({
  name: "ResourceTree"
});

const props = defineProps<{
  data: Array<ResourceTreeNode>;
  loading: boolean;
  selectedResourceId: number | null;
  canCreate: boolean;
  canDelete: boolean;
}>();

const emit = defineEmits<{
  nodeClick: [resourceId: number];
  createChild: [parentId: number];
  delete: [resourceId: number];
}>();

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "name",
  value: "id"
};

// ========== 搜索过滤 ==========

const filterText = ref("");
let filterTimer: number | null = null;

const filterNode = (value: string, data: ResourceTreeNode): boolean => {
  if (!value) return true;
  return data.name.includes(value) || data.code.includes(value);
};

const handleFilterChange = (value: string) => {
  if (filterTimer) clearTimeout(filterTimer);
  filterTimer = window.setTimeout(() => {
    treeRef.value?.filter(value);
  }, 300);
};

onUnmounted(() => {
  if (filterTimer) clearTimeout(filterTimer);
});

// ========== 树引用 ==========

const treeRef = ref();

// ========== 节点点击 ==========

const handleNodeClick = (data: ResourceTreeNode) => {
  emit("nodeClick", data.id);
};

// ========== 操作按钮 ==========

const handleCreateChild = (parentId: number) => {
  emit("createChild", parentId);
};

const handleDelete = (resourceId: number) => {
  emit("delete", resourceId);
};
</script>

<template>
  <div class="resource-tree">
    <!-- 搜索栏 -->
    <div class="p-2 border-b">
      <el-input
        v-model="filterText"
        placeholder="搜索资源名称/编码"
        clearable
        size="small"
        @input="handleFilterChange"
      />
    </div>

    <!-- 资源树 -->
    <el-scrollbar class="flex-1">
      <el-tree
        ref="treeRef"
        v-loading="props.loading"
        :data="props.data"
        :props="defaultProps"
        :filter-node-method="filterNode"
        node-key="id"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="flex items-center justify-between w-full pr-2 group">
            <div class="flex items-center gap-2 overflow-hidden">
              <span class="truncate">{{ data.name }}</span>
              <el-tag
                :type="getResourceTypeTag(data.resourceTypeCode).type"
                size="small"
              >
                {{ getResourceTypeTag(data.resourceTypeCode).text }}
              </el-tag>
              <el-tag
                :type="getResourceStatusTag(data.status).type"
                size="small"
              >
                {{ getResourceStatusTag(data.status).text }}
              </el-tag>
            </div>
            <!-- 操作按钮 -->
            <div
              class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
            >
              <el-button
                v-if="props.canCreate"
                type="primary"
                link
                size="small"
                @click.stop="handleCreateChild(data.id)"
              >
                新增
              </el-button>
              <el-button
                v-if="props.canDelete"
                type="danger"
                link
                size="small"
                @click.stop="handleDelete(data.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </template>
      </el-tree>

      <div
        v-if="props.data.length === 0 && !props.loading"
        class="text-center py-10 text-gray-500"
      >
        暂无资源数据
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.resource-tree {
  display: flex;
  flex-direction: column;
  height: 100%;

  :deep(.el-tree-node__content) {
    height: 36px;
  }
}
</style>
