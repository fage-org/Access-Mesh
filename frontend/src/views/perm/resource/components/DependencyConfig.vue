<script setup lang="ts">
import { ref, watch, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getDependencyList,
  createDependency,
  deleteDependency,
  type ResourceDependencyItem
} from "@/api/perm/resourceDependency";
import {
  getResourceTree,
  transformResourceTreeResponse,
  flattenResourceTree,
  type ResourceTreeNode,
  getResourceTypeTag
} from "@/api/perm/resource";
import {
  getOperationList,
  type OperationPermission
} from "@/api/perm/operation";
import { type ResourceDetail } from "@/api/perm/resource";

defineOptions({
  name: "DependencyConfig"
});

const props = defineProps<{
  resourceId: number | null;
  resourceDetail: ResourceDetail | null;
}>();

// ========== 状态定义 ==========

const loading = ref(false);
const saving = ref(false);
const dependencies = ref<Array<ResourceDependencyItem>>([]);
const resourceTreeData = ref<Array<ResourceTreeNode>>([]);
const operationList = ref<Array<OperationPermission>>([]);
const showAddDialog = ref(false);

// ========== 添加表单 ==========

const addForm = ref({
  targetResourceCode: "",
  requiredOperationCodes: [] as Array<string>,
  description: ""
});

// ========== 数据加载 ==========

const loadDependencies = async () => {
  if (!props.resourceId) return;
  loading.value = true;
  try {
    const res = await getDependencyList({ resourceEntityId: props.resourceId });
    if (res.success) {
      dependencies.value = res.data.items || [];
    }
  } catch {
    ElMessage.error("加载资源依赖失败");
  } finally {
    loading.value = false;
  }
};

const loadResourceTree = async () => {
  try {
    const res = await getResourceTree();
    if (res.success) {
      resourceTreeData.value = transformResourceTreeResponse(res);
    }
  } catch {
    ElMessage.error("加载资源树失败");
  }
};

const loadOperations = async () => {
  if (!props.resourceDetail?.resourceTypeCode) return;
  try {
    const res = await getOperationList({
      resourceTypeCode: props.resourceDetail.resourceTypeCode
    });
    if (res.success) {
      operationList.value = res.data.items || [];
    }
  } catch {
    ElMessage.error("加载操作权限失败");
  }
};

// ========== 监听 resourceId 变化 ==========

watch(
  () => props.resourceId,
  newId => {
    if (newId) {
      loadDependencies();
    } else {
      dependencies.value = [];
    }
  },
  { immediate: true }
);

// ========== 打开添加弹窗 ==========

const handleOpenAdd = async () => {
  addForm.value = {
    targetResourceCode: "",
    requiredOperationCodes: [],
    description: ""
  };
  await Promise.all([loadResourceTree(), loadOperations()]);
  showAddDialog.value = true;
};

// ========== 提交添加 ==========

const handleAddSubmit = async () => {
  if (!props.resourceDetail) return;
  if (!addForm.value.targetResourceCode) {
    ElMessage.warning("请选择目标资源");
    return;
  }
  if (addForm.value.requiredOperationCodes.length === 0) {
    ElMessage.warning("请选择需要补全的操作");
    return;
  }

  saving.value = true;
  try {
    const res = await createDependency({
      sourceResourceTypeCode: props.resourceDetail.resourceTypeCode,
      sourceResourceCode: props.resourceDetail.code,
      targetResourceTypeCode: findResourceTypeByCode(
        addForm.value.targetResourceCode
      ),
      targetResourceCode: addForm.value.targetResourceCode,
      requiredOperationCodes: addForm.value.requiredOperationCodes,
      description: addForm.value.description || undefined
    });
    if (res.success) {
      ElMessage.success("添加成功");
      showAddDialog.value = false;
      await loadDependencies();
    }
  } catch {
    ElMessage.error("添加失败");
  } finally {
    saving.value = false;
  }
};

// ========== 删除依赖 ==========

const handleDelete = async (dep: ResourceDependencyItem) => {
  try {
    await ElMessageBox.confirm("确认删除该依赖配置?", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });

    const res = await deleteDependency({ ids: [dep.id] });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadDependencies();
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("删除失败");
    }
  }
};

// ========== 工具函数 ==========

const flatResources = computed(() =>
  flattenResourceTree(resourceTreeData.value)
);

const findResourceTypeByCode = (code: string): string => {
  const resource = flatResources.value.find(r => r.code === code);
  return resource?.resourceTypeCode || "";
};

const getResourceNameByCode = (code: string): string => {
  const resource = flatResources.value.find(r => r.code === code);
  return resource?.name || code;
};
</script>

<template>
  <div class="dependency-config">
    <!-- 标题栏 -->
    <div class="flex items-center justify-between mb-4">
      <div>
        <h4 class="font-medium">资源依赖配置</h4>
        <p class="text-gray-500 text-sm mt-1">
          当源资源授权后，自动补全目标资源的权限
        </p>
      </div>
      <el-button type="primary" size="small" @click="handleOpenAdd">
        添加依赖
      </el-button>
    </div>

    <!-- 依赖列表 -->
    <el-table v-loading="loading" :data="dependencies" border stripe>
      <el-table-column prop="sourceResourceCode" label="源资源" width="150">
        <template #default="{ row }">
          <span>{{ row.sourceResourceCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="depResourceCode" label="目标资源" width="150">
        <template #default="{ row }">
          <span>{{ row.depResourceCode }}</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="requiredOperationBits"
        label="补全操作位"
        width="120"
      >
        <template #default="{ row }">
          <el-tag type="info" size="small">{{
            row.requiredOperationBits
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="autoGrant" label="自动授权" width="80">
        <template #default="{ row }">
          <el-tag :type="row.autoGrant ? 'success' : 'info'" size="small">
            {{ row.autoGrant ? "是" : "否" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" />
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="dependencies.length === 0 && !loading"
      class="text-center py-10 text-gray-500"
    >
      暂无依赖配置
    </div>

    <!-- 添加依赖弹窗 -->
    <el-dialog
      v-model="showAddDialog"
      title="添加资源依赖"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="源资源">
          <div class="text-gray-600">
            {{ props.resourceDetail?.name }} ({{ props.resourceDetail?.code }})
          </div>
        </el-form-item>
        <el-form-item label="目标资源">
          <el-tree-select
            v-model="addForm.targetResourceCode"
            :data="resourceTreeData"
            :props="{ children: 'children', label: 'name', value: 'code' }"
            node-key="code"
            check-strictly
            placeholder="请选择目标资源"
            filterable
            clearable
          />
        </el-form-item>
        <el-form-item label="补全操作">
          <el-select
            v-model="addForm.requiredOperationCodes"
            multiple
            placeholder="请选择需要补全的操作"
          >
            <el-option
              v-for="op in operationList"
              :key="op.id"
              :label="op.name"
              :value="op.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="addForm.description"
            type="textarea"
            :rows="2"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAddSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
