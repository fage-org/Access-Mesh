<script setup lang="ts">
import { computed } from "vue";
import {
  type ResourceDetail,
  getResourceStatusTag,
  getResourceTypeTag
} from "@/api/perm/resource";
import DependencyConfig from "./DependencyConfig.vue";

defineOptions({
  name: "ResourceDetail"
});

const props = defineProps<{
  resourceDetail: ResourceDetail;
  resourceId: number | null;
  canUpdate: boolean;
  activeTab: string;
}>();

const emit = defineEmits<{
  edit: [];
  tabChange: [tab: string];
}>();

// ========== 状态显示 ==========

const statusDisplay = computed(() =>
  getResourceStatusTag(props.resourceDetail.status)
);
const typeDisplay = computed(() =>
  getResourceTypeTag(props.resourceDetail.resourceTypeCode)
);

// ========== 操作 ==========

const handleEdit = () => {
  emit("edit");
};

const handleTabChange = (tab: string) => {
  emit("tabChange", tab);
};
</script>

<template>
  <div class="resource-detail">
    <!-- Tab 切换 -->
    <el-tabs
      :model-value="props.activeTab"
      class="mb-4"
      @update:model-value="handleTabChange"
    >
      <el-tab-pane label="资源详情" name="detail" />
      <el-tab-pane label="资源依赖" name="dependency" />
    </el-tabs>

    <!-- 资源详情 Tab -->
    <div
      v-if="props.activeTab === 'detail'"
      class="bg-white border rounded p-4"
    >
      <div class="flex items-center justify-between mb-4">
        <h4 class="font-medium">{{ props.resourceDetail.name }}</h4>
        <el-button
          v-if="props.canUpdate"
          type="primary"
          size="small"
          @click="handleEdit"
        >
          编辑
        </el-button>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="资源编码">
          {{ props.resourceDetail.code }}
        </el-descriptions-item>
        <el-descriptions-item label="编码类型">
          {{ props.resourceDetail.codeType || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="资源类型">
          <el-tag :type="typeDisplay.type" size="small">
            {{ typeDisplay.text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusDisplay.type" size="small">
            {{ statusDisplay.text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="父资源ID">
          {{ props.resourceDetail.parentId || "根资源" }}
        </el-descriptions-item>
        <el-descriptions-item label="路径">
          {{ props.resourceDetail.path || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="排序">
          {{ props.resourceDetail.sortOrder }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ props.resourceDetail.createdAt }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ props.resourceDetail.updatedAt || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="扩展配置" :span="2">
          {{ props.resourceDetail.extra || "-" }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 资源依赖 Tab -->
    <div v-if="props.activeTab === 'dependency'">
      <DependencyConfig
        :resource-id="props.resourceId"
        :resource-detail="props.resourceDetail"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.resource-detail {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}
</style>
