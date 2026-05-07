<script setup lang="ts">
import { ref, watch } from "vue";
import { type OrgNode } from "@/api/admin/org";

defineOptions({
  name: "OrgTree"
});

const props = defineProps<{
  data: Array<OrgNode>;
  loading: boolean;
  selectedOrgId: number | null;
}>();

const emit = defineEmits<{
  nodeClick: [orgId: number];
  createChild: [parentId: number];
  delete: [orgId: number];
}>();

// ========== 状态定义 ==========

const filterText = ref("");
const treeRef = ref();

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "orgName",
  value: "id"
};

// ========== 监听筛选文本 ==========

watch(filterText, val => {
  treeRef.value?.filter(val);
});

// ========== 树筛选方法 ==========

const filterNode = (value: string, data: OrgNode) => {
  if (!value) return true;
  return data.orgName.toLowerCase().includes(value.toLowerCase());
};

// ========== 树节点点击 ==========

const handleNodeClick = (data: OrgNode) => {
  emit("nodeClick", data.id);
};

// ========== 新增子节点 ==========

const handleCreateChild = (data: OrgNode) => {
  emit("createChild", data.id);
};

// ========== 删除节点 ==========

const handleDelete = (data: OrgNode) => {
  emit("delete", data.id);
};
</script>

<template>
  <div class="org-tree-container">
    <!-- 搜索框 -->
    <el-input
      v-model="filterText"
      placeholder="搜索组织名称"
      clearable
      class="mb-4"
    />

    <!-- 组织树 -->
    <el-scrollbar class="flex-1">
      <el-tree
        ref="treeRef"
        v-loading="props.loading"
        :data="props.data"
        :props="defaultProps"
        node-key="id"
        :current-node-key="props.selectedOrgId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        class="org-tree"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="custom-tree-node">
            <span class="truncate">{{ data.orgName }}</span>
            <span v-if="data.status === 0" class="ml-2">
              <el-tag type="danger" size="small">停用</el-tag>
            </span>
            <div class="tree-node-actions">
              <el-button
                type="primary"
                link
                size="small"
                @click.stop="handleCreateChild(data)"
              >
                新增
              </el-button>
              <el-button
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
.org-tree-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.org-tree {
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
