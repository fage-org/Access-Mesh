<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  getServiceConfigDetail,
  syncServiceConfig,
  getServiceApis,
  type ServiceConfigItem,
  type ApiMappingItem,
  type ServiceSyncResult,
  getServiceStatusTag,
  getHttpMethodTag
} from "@/api/perm/service";
import ApiServiceTree from "./ApiServiceTree.vue";
import SyncResultDialog from "./SyncResultDialog.vue";

defineOptions({
  name: "ServiceDetail"
});

const props = defineProps<{
  serviceCode: string;
  canUpdate: boolean;
  canSync: boolean;
}>();

const emit = defineEmits<{
  edit: [service: ServiceConfigItem];
  syncComplete: [result: ServiceSyncResult];
}>();

// ========== 状态定义 ==========

const loading = ref(false);
const syncLoading = ref(false);
const serviceDetail = ref<ServiceConfigItem | null>(null);
const apiList = ref<Array<ApiMappingItem>>([]);
const syncResultDialogRef = ref();

// ========== 数据加载 ==========

const loadServiceDetail = async () => {
  loading.value = true;
  try {
    const res = await getServiceConfigDetail({
      serviceCode: props.serviceCode
    });
    if (res.success) {
      serviceDetail.value = res.data;
    }
  } catch {
    ElMessage.error("加载服务详情失败");
  } finally {
    loading.value = false;
  }
};

const loadApiList = async () => {
  try {
    const res = await getServiceApis({ serviceCode: props.serviceCode });
    if (res.success) {
      apiList.value = res.data.items || [];
    }
  } catch {
    // 静默处理
  }
};

const loadAllData = async () => {
  await Promise.all([loadServiceDetail(), loadApiList()]);
};

// ========== 监听 serviceCode 变化 ==========

watch(
  () => props.serviceCode,
  newCode => {
    if (newCode) {
      loadAllData();
    } else {
      serviceDetail.value = null;
      apiList.value = [];
    }
  },
  { immediate: true }
);

// ========== 操作 ==========

const handleEdit = () => {
  if (serviceDetail.value) {
    emit("edit", serviceDetail.value);
  }
};

const handleSync = async () => {
  if (!serviceDetail.value) return;

  syncLoading.value = true;
  try {
    const res = await syncServiceConfig({
      serviceCode: props.serviceCode
    });
    if (res.success) {
      const result = res.data;
      // 显示同步结果弹窗
      syncResultDialogRef.value.openDialog(result);
      // 刷新API列表
      await loadApiList();
      // 触发完成事件
      emit("syncComplete", result);
    }
  } catch {
    ElMessage.error("同步接口失败");
  } finally {
    syncLoading.value = false;
  }
};
</script>

<template>
  <div v-loading="loading" class="service-detail">
    <div v-if="serviceDetail">
      <!-- 服务详情卡片 -->
      <div class="bg-white border rounded p-4 mb-4">
        <div class="flex items-center justify-between mb-4">
          <h4 class="font-medium">{{ serviceDetail.name }}</h4>
          <div class="flex items-center gap-2">
            <el-button
              v-if="props.canUpdate"
              type="primary"
              size="small"
              @click="handleEdit"
            >
              编辑
            </el-button>
            <el-button
              v-if="props.canSync"
              type="success"
              size="small"
              :loading="syncLoading"
              @click="handleSync"
            >
              同步接口
            </el-button>
          </div>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="服务编码">
            {{ serviceDetail.serviceCode }}
          </el-descriptions-item>
          <el-descriptions-item label="基础路径">
            {{ serviceDetail.basePath || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              :type="getServiceStatusTag(serviceDetail.status).type"
              size="small"
            >
              {{ getServiceStatusTag(serviceDetail.status).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ serviceDetail.createdAt }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ serviceDetail.description || "-" }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 接口资源树 -->
      <div class="bg-white border rounded">
        <div class="p-3 border-b bg-gray-50">
          <span class="font-medium">接口资源映射</span>
          <span class="text-gray-500 ml-2">共 {{ apiList.length }} 个</span>
        </div>
        <ApiServiceTree :data="apiList" />
      </div>
    </div>

    <!-- 同步结果弹窗 -->
    <SyncResultDialog ref="syncResultDialogRef" />
  </div>
</template>
