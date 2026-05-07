<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getResourceTree,
  getResourceDetail,
  deleteResource,
  transformResourceTreeResponse,
  type ResourceTreeNode,
  type ResourceDetail,
  RESOURCE_TYPE_CODES,
  getResourceTypeTag
} from "@/api/perm/resource";
import ResourceTree from "./components/ResourceTree.vue";
import ResourceDetailPanel from "./components/ResourceDetail.vue";
import ResourceForm from "./components/ResourceForm.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "PermResource"
});

const treeData = ref<Array<ResourceTreeNode>>([]);
const selectedResourceId = ref<number | null>(null);
const selectedResourceDetail = ref<ResourceDetail | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const activeTab = ref("detail");
const resourceFormRef = ref();

// ========== 权限计算 ==========

const canCreate = hasPerms(PERM_CODES.PERM_RESOURCE_CREATE);
const canUpdate = hasPerms(PERM_CODES.PERM_RESOURCE_UPDATE);
const canDelete = hasPerms(PERM_CODES.PERM_RESOURCE_DELETE);

// ========== 筛选条件 ==========

const filterResourceType = ref<string>("");
const filterDomainCode = ref<string>("");

// ========== 数据加载 ==========

const loadResourceTree = async () => {
  loading.value = true;
  try {
    const res = await getResourceTree({
      resourceTypeCode: filterResourceType.value || undefined,
      domainCode: filterDomainCode.value || undefined
    });
    if (res.success) {
      treeData.value = transformResourceTreeResponse(res);
    }
  } catch {
    ElMessage.error("加载资源树失败");
  } finally {
    loading.value = false;
  }
};

const loadResourceDetail = async (resourceId: number) => {
  detailLoading.value = true;
  try {
    const res = await getResourceDetail({ id: resourceId });
    if (res.success) {
      selectedResourceDetail.value = res.data;
    }
  } catch {
    ElMessage.error("加载资源详情失败");
  } finally {
    detailLoading.value = false;
  }
};

// ========== 资源树交互 ==========

const handleResourceNodeClick = async (resourceId: number) => {
  selectedResourceId.value = resourceId;
  activeTab.value = "detail";
  await loadResourceDetail(resourceId);
};

const handleCreateRoot = () => {
  resourceFormRef.value.openDialog(null);
};

const handleCreateChild = (parentId: number) => {
  resourceFormRef.value.openDialog(parentId);
};

const handleEdit = () => {
  if (selectedResourceDetail.value) {
    resourceFormRef.value.openDialog(
      selectedResourceDetail.value.parentId,
      selectedResourceDetail.value
    );
  }
};

const handleDelete = async (resourceId: number) => {
  try {
    await ElMessageBox.confirm(
      "确认删除该资源?删除后子资源将一并删除",
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await deleteResource({ ids: [resourceId] });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadResourceTree();
      if (selectedResourceId.value === resourceId) {
        selectedResourceId.value = null;
        selectedResourceDetail.value = null;
      }
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("删除资源失败");
    }
  }
};

const handleFormSuccess = async () => {
  const currentResourceId = selectedResourceId.value;
  await loadResourceTree();
  if (currentResourceId) {
    const resourceExists = findResourceInTree(
      treeData.value,
      currentResourceId
    );
    if (resourceExists) {
      await loadResourceDetail(currentResourceId);
    } else {
      selectedResourceId.value = null;
      selectedResourceDetail.value = null;
    }
  }
};

// ========== 筛选变更 ==========

const handleFilterChange = () => {
  loadResourceTree();
};

// ========== 递归查找资源 ==========

const findResourceInTree = (
  nodes: Array<ResourceTreeNode>,
  targetId: number
): ResourceTreeNode | null => {
  for (const node of nodes) {
    if (node.id === targetId) return node;
    if (node.children) {
      const found = findResourceInTree(node.children, targetId);
      if (found) return found;
    }
  }
  return null;
};

// ========== Tab 切换 ==========

const handleTabChange = (tab: string) => {
  activeTab.value = tab;
};

// ========== 初始化 ==========

onMounted(() => {
  loadResourceTree();
});
</script>

<template>
  <div class="resource-management">
    <div class="flex h-full">
      <!-- 左侧资源树 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <!-- 资源类型筛选 -->
          <el-select
            v-model="filterResourceType"
            placeholder="资源类型"
            clearable
            size="small"
            class="w-full mb-2"
            @change="handleFilterChange"
          >
            <el-option
              v-for="(text, code) in RESOURCE_TYPE_CODES"
              :key="code"
              :label="getResourceTypeTag(text).text"
              :value="text"
            />
          </el-select>
          <!-- 业务域筛选 -->
          <el-input
            v-model="filterDomainCode"
            placeholder="业务域编码"
            clearable
            size="small"
            class="w-full"
            @change="handleFilterChange"
          />
          <el-button
            type="primary"
            size="small"
            class="mt-2"
            :disabled="!canCreate"
            @click="handleCreateRoot"
          >
            新增根资源
          </el-button>
        </div>
        <ResourceTree
          :data="treeData"
          :loading="loading"
          :selected-resource-id="selectedResourceId"
          :can-create="canCreate"
          :can-delete="canDelete"
          @node-click="handleResourceNodeClick"
          @create-child="handleCreateChild"
          @delete="handleDelete"
        />
      </div>

      <!-- 右侧详情区域 -->
      <div v-loading="detailLoading" class="flex-1 p-4">
        <ResourceDetailPanel
          v-if="selectedResourceDetail"
          :resource-detail="selectedResourceDetail"
          :resource-id="selectedResourceId"
          :can-update="canUpdate"
          :active-tab="activeTab"
          @edit="handleEdit"
          @tab-change="handleTabChange"
        />

        <div v-if="!selectedResourceId" class="text-center py-10 text-gray-500">
          请选择资源节点
        </div>
      </div>

      <!-- 资源表单弹窗 -->
      <ResourceForm ref="resourceFormRef" @success="handleFormSuccess" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.resource-management {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
