<script setup lang="ts">
import {
  type ServiceConfigItem,
  getServiceStatusTag
} from "@/api/perm/service";

defineOptions({
  name: "ServiceCard"
});

const props = defineProps<{
  data: Array<ServiceConfigItem>;
  loading: boolean;
  selectedServiceCode: string | null;
  canUpdate: boolean;
  canDelete: boolean;
}>();

const emit = defineEmits<{
  select: [serviceCode: string];
  edit: [service: ServiceConfigItem];
  delete: [service: ServiceConfigItem];
}>();

// ========== 卡片点击 ==========

const handleCardClick = (serviceCode: string) => {
  emit("select", serviceCode);
};

const handleEdit = (service: ServiceConfigItem) => {
  emit("edit", service);
};

const handleDelete = (service: ServiceConfigItem) => {
  emit("delete", service);
};
</script>

<template>
  <div class="service-card">
    <el-scrollbar class="flex-1">
      <div v-loading="props.loading" class="p-2">
        <div
          v-for="service in props.data"
          :key="service.id"
          class="mb-2 p-3 border rounded cursor-pointer hover:bg-gray-50 transition-colors"
          :class="{
            'bg-blue-50 border-blue-200':
              service.serviceCode === props.selectedServiceCode
          }"
          @click="handleCardClick(service.serviceCode)"
        >
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="font-medium">{{ service.name }}</div>
              <div class="text-gray-500 text-sm mt-1">
                {{ service.serviceCode }}
              </div>
              <div class="flex items-center gap-2 mt-2">
                <el-tag
                  :type="getServiceStatusTag(service.status).type"
                  size="small"
                >
                  {{ getServiceStatusTag(service.status).text }}
                </el-tag>
                <span v-if="service.basePath" class="text-gray-400 text-xs">
                  {{ service.basePath }}
                </span>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <el-button
                v-if="props.canUpdate"
                type="primary"
                link
                size="small"
                @click.stop="handleEdit(service)"
              >
                编辑
              </el-button>
              <el-button
                v-if="props.canDelete"
                type="danger"
                link
                size="small"
                @click.stop="handleDelete(service)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>

        <div
          v-if="props.data.length === 0 && !props.loading"
          class="text-center py-10 text-gray-500"
        >
          暂无服务配置
        </div>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped lang="scss">
.service-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
