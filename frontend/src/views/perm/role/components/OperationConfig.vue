<script setup lang="ts">
import { ref, computed, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  type ResourceTreeNode,
  getResourceStatusTag
} from "@/api/perm/resource";
import {
  type OperationPermission,
  getOperationTagType
} from "@/api/perm/operation";
import {
  type RolePermissionItem,
  type GrantAddItem,
  isPermissionConfigured
} from "@/api/perm/rolePermission";

defineOptions({
  name: "OperationConfig"
});

const props = defineProps<{
  resource: ResourceTreeNode;
  operations: Array<OperationPermission>;
  existingPermissions: Array<RolePermissionItem>;
  saving: boolean;
  canAssign: boolean;
}>();

const emit = defineEmits<{
  save: [addItems: Array<GrantAddItem>, removeIds: Array<number>];
}>();

// ========== 配置状态 ==========

interface ConfigItem {
  operationId: number;
  operationCode: string;
  operationName: string;
  checked: boolean;
  canGrant: boolean;
  conditionCode: string;
  existingId: number | null;
}

const configItems = ref<Array<ConfigItem>>([]);

// ========== 初始化配置项 ==========

const initConfigItems = () => {
  configItems.value = props.operations.map(op => {
    const existing = props.existingPermissions.find(
      p => p.resourceCode === props.resource.code && p.operationCode === op.code
    );
    return {
      operationId: op.id,
      operationCode: op.code,
      operationName: op.name,
      checked: !!existing,
      canGrant: existing?.canGrant || false,
      conditionCode: existing?.conditionCode || "",
      existingId: existing?.id || null
    };
  });
};

watch(
  [
    () => props.resource,
    () => props.operations,
    () => props.existingPermissions
  ],
  () => {
    initConfigItems();
  },
  { immediate: true }
);

// ========== 全选/全不选 ==========

const allChecked = computed(() => {
  return (
    configItems.value.length > 0 &&
    configItems.value.every(item => item.checked)
  );
});

const handleToggleAll = () => {
  const newState = !allChecked.value;
  configItems.value.forEach(item => {
    item.checked = newState;
  });
};

// ========== 计算变更 ==========

const changedItems = computed(() => {
  const add: Array<GrantAddItem> = [];
  const remove: Array<number> = [];

  configItems.value.forEach(item => {
    if (item.checked && !item.existingId) {
      // 新增
      add.push({
        resourceTypeCode: props.resource.resourceTypeCode,
        resourceCode: props.resource.code,
        codeType: props.resource.codeType,
        operationCode: item.operationCode,
        canGrant: item.canGrant,
        conditionCode: item.conditionCode || undefined,
        scopeAll: true
      });
    } else if (!item.checked && item.existingId) {
      // 删除
      remove.push(item.existingId);
    }
  });

  return { add, remove };
});

const hasChanges = computed(() => {
  return (
    changedItems.value.add.length > 0 || changedItems.value.remove.length > 0
  );
});

// ========== 保存 ==========

const handleSave = () => {
  if (!hasChanges.value) {
    ElMessage.warning("无变更需要保存");
    return;
  }

  emit("save", changedItems.value.add, changedItems.value.remove);
};

// ========== 统计 ==========

const checkedCount = computed(
  () => configItems.value.filter(item => item.checked).length
);

const totalCount = computed(() => configItems.value.length);
</script>

<template>
  <div class="operation-config">
    <!-- 资源信息 -->
    <div class="p-4 mb-4 bg-white border rounded">
      <div class="flex items-center justify-between">
        <div>
          <h4 class="font-medium">{{ props.resource.name }}</h4>
          <div class="flex items-center gap-2 mt-1">
            <el-tag size="small" type="info">
              {{ props.resource.resourceTypeCode }}
            </el-tag>
            <span class="text-gray-500 text-sm">
              编码: {{ props.resource.code }}
            </span>
          </div>
        </div>
        <el-tag :type="getResourceStatusTag(props.resource.status).type">
          {{ getResourceStatusTag(props.resource.status).text }}
        </el-tag>
      </div>
    </div>

    <!-- 操作列表 -->
    <div class="bg-white border rounded">
      <!-- 标题栏 -->
      <div class="flex items-center justify-between p-3 border-b bg-gray-50">
        <div class="flex items-center gap-2">
          <el-checkbox
            :model-value="allChecked"
            :disabled="!props.canAssign"
            @change="handleToggleAll"
          >
            全选
          </el-checkbox>
          <span class="text-gray-500">
            已选 {{ checkedCount }}/{{ totalCount }} 项操作
          </span>
        </div>
        <el-button
          type="primary"
          size="small"
          :loading="props.saving"
          :disabled="!props.canAssign || !hasChanges"
          @click="handleSave"
        >
          保存变更
        </el-button>
      </div>

      <!-- 操作项列表 -->
      <div class="p-3">
        <div
          v-for="item in configItems"
          :key="item.operationId"
          class="flex items-center gap-3 py-2 border-b last:border-b-0"
        >
          <el-checkbox v-model="item.checked" :disabled="!props.canAssign" />

          <div class="flex-1">
            <div class="flex items-center gap-2">
              <span>{{ item.operationName }}</span>
              <el-tag
                :type="getOperationTagType(item.operationCode)"
                size="small"
              >
                {{ item.operationCode }}
              </el-tag>
            </div>
          </div>

          <!-- 可授权选项 -->
          <div v-if="item.checked" class="flex items-center gap-2">
            <el-checkbox
              v-model="item.canGrant"
              :disabled="!props.canAssign"
              size="small"
            >
              可授权
            </el-checkbox>

            <!-- 条件码输入 -->
            <el-input
              v-model="item.conditionCode"
              placeholder="条件码(可选)"
              size="small"
              class="w-[150px]"
              :disabled="!props.canAssign"
            />
          </div>

          <!-- 已配置标记 -->
          <el-tag v-if="item.existingId" type="success" size="small">
            已配置
          </el-tag>
        </div>

        <div
          v-if="configItems.length === 0"
          class="text-center py-10 text-gray-500"
        >
          该资源类型暂无操作权限定义
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.operation-config {
  :deep(.el-checkbox__label) {
    font-size: 14px;
  }
}
</style>
