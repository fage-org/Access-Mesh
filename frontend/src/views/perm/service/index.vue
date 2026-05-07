<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getServiceConfigList,
  deleteServiceConfig,
  type ServiceConfigItem
} from "@/api/perm/service";
import ServiceCard from "./components/ServiceCard.vue";
import ServiceDetail from "./components/ServiceDetail.vue";
import ServiceForm from "./components/ServiceForm.vue";
import SyncResultDialog from "./components/SyncResultDialog.vue";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";

defineOptions({
  name: "PermService"
});

const serviceList = ref<Array<ServiceConfigItem>>([]);
const selectedServiceCode = ref<string | null>(null);
const loading = ref(false);
const serviceFormRef = ref<InstanceType<typeof ServiceForm> | null>(null);
const syncResultRef = ref<InstanceType<typeof SyncResultDialog> | null>(null);

// ========== 权限计算 ==========

const canCreate = hasPerms(PERM_CODES.PERM_SERVICE_CREATE);
const canUpdate = hasPerms(PERM_CODES.PERM_SERVICE_UPDATE);
const canDelete = hasPerms(PERM_CODES.PERM_SERVICE_DELETE);
const canSync = hasPerms(PERM_CODES.PERM_SERVICE_SYNC);

// ========== 数据加载 ==========

const loadServiceList = async () => {
  loading.value = true;
  try {
    const res = await getServiceConfigList();
    if (res.success) {
      serviceList.value = res.data.items || [];
    }
  } catch (error) {
    console.error("加载服务列表失败:", error);
    ElMessage.error("加载服务列表失败");
  } finally {
    loading.value = false;
  }
};

// ========== 服务交互 ==========

const handleServiceSelect = (serviceCode: string) => {
  selectedServiceCode.value = serviceCode;
};

const handleCreateService = () => {
  serviceFormRef.value.openDialog(null);
};

const handleEditService = (service: ServiceConfigItem) => {
  serviceFormRef.value.openDialog(service);
};

const handleDeleteService = async (service: ServiceConfigItem) => {
  try {
    await ElMessageBox.confirm(
      `确认删除服务 "${service.name}"? 该操作将删除关联的接口资源`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await deleteServiceConfig({ ids: [service.id] });
    if (res.success) {
      ElMessage.success("删除成功");
      await loadServiceList();
      if (selectedServiceCode.value === service.serviceCode) {
        selectedServiceCode.value = null;
      }
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除服务失败:", error);
      ElMessage.error("删除服务失败");
    }
  }
};

const handleFormSuccess = async () => {
  await loadServiceList();
};

const handleSyncComplete = (result: {
  createdResources: number;
  createdMappings: number;
}) => {
  if (result.createdResources > 0 || result.createdMappings > 0) {
    ElMessage.success(
      `同步完成: 新增 ${result.createdResources} 资源, ${result.createdMappings} 映射`
    );
  } else {
    ElMessage.success("同步完成: 无变更");
  }
};

// ========== 初始化 ==========

onMounted(() => {
  loadServiceList();
});
</script>

<template>
  <div class="service-config">
    <div class="flex h-full">
      <!-- 左侧服务卡片列表 -->
      <div class="w-[300px] border-r flex flex-col">
        <div class="p-4 border-b">
          <el-button
            type="primary"
            size="small"
            :disabled="!canCreate"
            @click="handleCreateService"
          >
            新增服务
          </el-button>
        </div>
        <ServiceCard
          :data="serviceList"
          :loading="loading"
          :selected-service-code="selectedServiceCode"
          :can-update="canUpdate"
          :can-delete="canDelete"
          @select="handleServiceSelect"
          @edit="handleEditService"
          @delete="handleDeleteService"
        />
      </div>

      <!-- 右侧详情区域 -->
      <div class="flex-1 p-4">
        <div
          v-if="!selectedServiceCode"
          class="text-center py-10 text-gray-500"
        >
          请选择服务
        </div>

        <ServiceDetail
          v-else
          :service-code="selectedServiceCode"
          :can-update="canUpdate"
          :can-sync="canSync"
          @edit="handleEditService"
          @sync-complete="handleSyncComplete"
        />
      </div>

      <!-- 服务表单弹窗 -->
      <ServiceForm ref="serviceFormRef" @success="handleFormSuccess" />

      <!-- 同步结果弹窗 -->
      <SyncResultDialog ref="syncResultRef" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.service-config {
  padding: 20px;
  height: calc(100vh - 100px);
}
</style>
