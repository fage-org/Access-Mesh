<script setup lang="ts">
import { ref } from "vue";
import {
  type ServiceSyncResult,
  getTotalSyncChanges
} from "@/api/perm/service";

defineOptions({
  name: "SyncResultDialog"
});

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const syncResult = ref<ServiceSyncResult | null>(null);

// ========== 打开弹窗 ==========

const openDialog = (result: ServiceSyncResult) => {
  syncResult.value = result;
  dialogVisible.value = true;
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="同步结果"
    width="500px"
    :close-on-click-modal="false"
  >
    <div v-if="syncResult" class="sync-result">
      <!-- 变更统计 -->
      <div class="grid grid-cols-2 gap-4 mb-4">
        <div class="p-3 border rounded bg-green-50">
          <div class="text-green-600 font-medium">新增</div>
          <div class="mt-2">
            <div class="flex items-center gap-2">
              <span class="text-gray-600">资源:</span>
              <span class="text-green-600 font-bold">
                {{ syncResult.createdResources }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-gray-600">映射:</span>
              <span class="text-green-600 font-bold">
                {{ syncResult.createdMappings }}
              </span>
            </div>
          </div>
        </div>
        <div class="p-3 border rounded bg-yellow-50">
          <div class="text-yellow-600 font-medium">更新</div>
          <div class="mt-2">
            <div class="flex items-center gap-2">
              <span class="text-gray-600">资源:</span>
              <span class="text-yellow-600 font-bold">
                {{ syncResult.updatedResources }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-gray-600">映射:</span>
              <span class="text-yellow-600 font-bold">
                {{ syncResult.updatedMappings }}
              </span>
            </div>
          </div>
        </div>
        <div class="p-3 border rounded bg-red-50">
          <div class="text-red-600 font-medium">删除</div>
          <div class="mt-2">
            <div class="flex items-center gap-2">
              <span class="text-gray-600">资源:</span>
              <span class="text-red-600 font-bold">
                {{ syncResult.deletedResources }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-gray-600">映射:</span>
              <span class="text-red-600 font-bold">
                {{ syncResult.deletedMappings }}
              </span>
            </div>
          </div>
        </div>
        <div class="p-3 border rounded bg-gray-50">
          <div class="text-gray-600 font-medium">总变更</div>
          <div class="mt-2">
            <span class="text-2xl font-bold">
              {{ getTotalSyncChanges(syncResult) }}
            </span>
            <span class="text-gray-500 ml-1">项</span>
          </div>
        </div>
      </div>

      <!-- 无变更提示 -->
      <div
        v-if="getTotalSyncChanges(syncResult) === 0"
        class="text-center py-4 text-gray-500"
      >
        本次同步无任何变更，接口资源已保持最新状态
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="dialogVisible = false">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
