<script setup lang="ts">
import { ref } from "vue";

defineOptions({
  name: "OrgTreeConfig"
});

const props = defineProps<{
  orgId: number | null;
}>();
</script>

<template>
  <div class="org-tree-config">
    <div class="text-center py-10 text-gray-500">
      组织树配置功能将在后续 Phase 实现
    </div>
  </div>
</template>

<style scoped lang="scss">
.org-tree-config {
  min-height: 300px;
}
</style>
