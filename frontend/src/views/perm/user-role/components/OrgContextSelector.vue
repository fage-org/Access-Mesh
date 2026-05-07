<script setup lang="ts">
import { ref, onMounted } from "vue";
import { getOrgTree, type OrgNode } from "@/api/admin/org";

defineOptions({
  name: "OrgContextSelector"
});

const props = defineProps<{
  selectedOrgId: number | null;
}>();

const emit = defineEmits<{
  select: [orgId: number];
}>();

// ========== 状态定义 ==========

const orgTreeData = ref<Array<OrgNode>>([]);
const loading = ref(false);

// ========== 树属性配置 ==========

const defaultProps = {
  children: "children",
  label: "name",
  value: "id"
};

// ========== 数据加载 ==========

const loadOrgTree = async () => {
  loading.value = true;
  try {
    const res = await getOrgTree();
    if (res.success) {
      orgTreeData.value = res.data;
    }
  } catch {
    // 加载组织树失败，静默处理，不影响用户操作
  } finally {
    loading.value = false;
  }
};

// ========== 组织选择 ==========

const handleOrgSelect = (orgId: number) => {
  emit("select", orgId);
};

// ========== 初始化 ==========

onMounted(() => {
  loadOrgTree();
});
</script>

<template>
  <div class="org-context-selector">
    <div class="flex items-center gap-2">
      <span class="text-gray-600">组织上下文:</span>
      <el-tree-select
        v-loading="loading"
        :model-value="props.selectedOrgId"
        :data="orgTreeData"
        :props="defaultProps"
        node-key="id"
        check-strictly
        placeholder="请选择组织上下文"
        clearable
        filterable
        @update:model-value="handleOrgSelect"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.org-context-selector {
  .el-tree-select {
    width: 300px;
  }
}
</style>
