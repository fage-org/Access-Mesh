<script setup lang="ts">
/** 子权限为独立数量维度，不叠加到权限来源/状态图标上。 */
defineProps<{
  count: number;
  title?: string;
}>();
</script>

<template>
  <span
    class="child-permission-indicator"
    :title="title || `${count} 条子权限`"
    :aria-label="title || `${count} 条子权限`"
  >
    <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M3 3 V8 H8 M8 8 V4 H13 M8 8 V12 H13" />
      <circle cx="3" cy="3" r="1.5" />
      <circle cx="13" cy="4" r="1.5" />
      <circle cx="13" cy="12" r="1.5" />
    </svg>
    <span class="child-count">{{ count > 99 ? "99+" : count }}</span>
  </span>
</template>

<style lang="scss" scoped>
.child-permission-indicator {
  display: inline-flex;
  flex-shrink: 0;
  gap: 2px;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 22px;
  padding: 0 5px 0 3px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: var(--radius-sm);

  svg {
    width: 14px;
    height: 14px;
    overflow: visible;
    fill: none;
    stroke: currentcolor;
    stroke-width: 1.4;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  circle {
    fill: currentcolor;
  }

  .child-count {
    min-width: 7px;
    font-size: 10px;
    font-weight: 700;
    line-height: 1;
    text-align: center;
  }
}
</style>
