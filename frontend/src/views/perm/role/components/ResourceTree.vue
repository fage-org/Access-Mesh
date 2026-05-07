<script setup lang="ts">
import { computed } from "vue";
import {
  type ResourceTreeNode,
  flattenResourceTree,
  getResourceStatusTag
} from "@/api/perm/resource";
import { type RolePermissionItem } from "@/api/perm/rolePermission";

defineOptions({
  name: "ResourceTree"
});

const props = defineProps<{
  data: Array<ResourceTreeNode>;
  loading: boolean;
  selectedResource: ResourceTreeNode | null;
  existingPermissions: Array<RolePermissionItem>;
}>();

const emit = defineEmits<{
  select: [resource: ResourceTreeNode | null];
}>();

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "name",
  value: "id"
};

// ========== 节点状态 ==========

const getPermissionCount = (resourceCode: string): number => {
  return props.existingPermissions.filter(p => p.resourceCode === resourceCode)
    .length;
};

const hasPermission = (resourceCode: string): boolean => {
  return getPermissionCount(resourceCode) > 0;
};

// ========== 节点点击 ==========

const handleNodeClick = (data: ResourceTreeNode) => {
  emit("select", data);
};

const handleClearSelect = () => {
  emit("select", null);
};
</script>

<template>
  <div class="resource-tree">
    <!-- 清除选择 -->
    <div v-if="props.selectedResource" class="p-2 border-b bg-blue-50">
      <div class="flex items-center justify-between">
        <span class="text-sm text-blue-600">
          已选: {{ props.selectedResource.name }}
        </span>
        <el-button link size="small" @click="handleClearSelect">
          清除
        </el-button>
      </div>
    </div>

    <!-- 资源树 -->
    <el-scrollbar class="flex-1">
      <el-tree
        v-loading="props.loading"
        :data="props.data"
        :props="defaultProps"
        node-key="id"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="flex items-center justify-between w-full pr-2">
            <div class="flex items-center gap-2">
              <span class="truncate">{{ data.name }}</span>
              <el-tag size="small" type="info">
                {{ data.resourceTypeCode }}
              </el-tag>
            </div>
            <div class="flex items-center gap-1">
              <el-tag
                v-if="hasPermission(data.code)"
                type="success"
                size="small"
              >
                {{ getPermissionCount(data.code) }}项
              </el-tag>
              <el-tag
                :type="getResourceStatusTag(data.status).type"
                size="small"
              >
                {{ getResourceStatusTag(data.status).text }}
              </el-tag>
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
