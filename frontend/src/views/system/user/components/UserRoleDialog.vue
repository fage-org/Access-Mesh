<script setup lang="ts">
import { ref } from "vue";

defineOptions({
  name: "UserRoleDialog"
});

const emit = defineEmits<{
  success: [];
}>();

// ========== 状态定义 ==========

const dialogVisible = ref(false);
const userId = ref<number | null>(null);

// ========== 打开弹窗(预留,Phase 3 完整实现) ==========

const openDialog = (id: number) => {
  userId.value = id;
  dialogVisible.value = true;
  // TODO: Phase 3 实现角色分配逻辑
};

// ========== 暴露方法 ==========

defineExpose({
  openDialog
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="分配角色"
    width="600px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="text-center py-10 text-gray-500">
      角色分配功能将在 Phase 3 实现
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>
