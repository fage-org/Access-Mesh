<script setup lang="ts">
import { ref } from "vue";

defineOptions({
  name: "PositionTab"
});

const props = defineProps<{
  orgId: number | null;
}>();

// TODO: P0-8 实现岗位折叠卡片
// - 按选中组织筛选岗位（orgType=2）
// - 折叠卡片展示：岗位名、所属组织路径、已分配人数
// - 展开后显示用户列表 + 添加成员
</script>

<template>
  <div class="position-tab">
    <el-empty description="岗位管理功能开发中..." />
  </div>
</template>

<style lang="scss" scoped>
.position-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
