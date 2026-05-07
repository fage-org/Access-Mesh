<script setup lang="ts">
import { ref, watch } from "vue";
import { type OrgNode } from "@/api/admin/org";

defineOptions({
  name: "OrgTreePanel"
});

const props = defineProps<{
  treeData: Array<OrgNode>;
  selectedOrgId: number | null;
}>();

const emit = defineEmits<{
  select: [orgId: number | null];
  clear: [];
}>();

// ========== 状态定义 ==========

const filterText = ref("");
const treeRef = ref();
const currentSelectedId = ref<number | null>(null);

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
  currentSelectedId.value = data.id;
  emit("select", data.id);
};

// ========== 清除选择 ==========

const handleClear = () => {
  currentSelectedId.value = null;
  treeRef.value?.setCurrentKey(null);
  emit("clear");
};

// ========== 暴露方法 ==========

defineExpose({
  handleClear
});
</script>

<template>
  <el-card class="org-tree-panel" shadow="never">
    <template #header>
      <div class="flex justify-between items-center">
        <span class="font-bold">组织筛选</span>
        <el-button
          v-if="selectedOrgId"
          type="primary"
          link
          size="small"
          @click="handleClear"
        >
          清除选择
        </el-button>
      </div>
    </template>

    <!-- 搜索框 -->
    <el-input
      v-model="filterText"
      placeholder="搜索组织名称"
      clearable
      class="mb-4"
    />

    <!-- 组织树 -->
    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="defaultProps"
      node-key="id"
      :current-node-key="selectedOrgId"
      default-expand-all
      :expand-on-click-node="false"
      :filter-node-method="filterNode"
      highlight-current
      class="org-tree"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <span class="custom-tree-node">
          <span>{{ data.orgName }}</span>
          <span v-if="data.status === 0" class="ml-2">
            <el-tag type="danger" size="small">停用</el-tag>
          </span>
        </span>
      </template>
    </el-tree>
  </el-card>
</template>

<style scoped lang="scss">
.org-tree-panel {
  width: 260px;
  min-height: 500px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.org-tree {
  :deep(.el-tree-node__content) {
    height: 32px;
  }
}

.custom-tree-node {
  display: flex;
  align-items: center;
  font-size: 14px;
}
</style>
