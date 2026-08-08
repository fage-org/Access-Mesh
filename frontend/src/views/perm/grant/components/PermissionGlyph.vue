<script setup lang="ts">
/**
 * 权限矩阵共享图标原语。
 * MatrixCell 与工具栏图例共用，确保示例与真实单元格的颜色、箭头和附加状态一致。
 */
withDefaults(
  defineProps<{
    variant: "valid" | "update" | "remove";
    solid?: boolean;
    arrow?: "none" | "up" | "right" | "combined";
    striped?: boolean;
    bold?: boolean;
    title?: string;
  }>(),
  {
    solid: true,
    arrow: "none",
    striped: false,
    bold: false,
    title: ""
  }
);
</script>

<template>
  <span
    class="permission-glyph"
    :class="[
      variant,
      {
        solid,
        inherited: !solid,
        striped,
        bold,
        combined: arrow === 'combined'
      }
    ]"
    :title="title || undefined"
    aria-hidden="true"
  >
    <svg
      v-if="arrow === 'combined'"
      class="arrow-svg combined-arrow"
      viewBox="0 0 18 18"
      focusable="false"
    >
      <path
        d="M2.5 13.5 H9 V4.5 M5.8 7.5 L9 4.3 L12.2 7.5"
        stroke="currentColor"
        stroke-width="1.9"
        fill="none"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <svg
      v-else-if="arrow === 'up'"
      class="arrow-svg"
      viewBox="0 0 15 15"
      focusable="false"
    >
      <path
        d="M7.5 13 V3.5 M4.8 6.2 L7.5 3.5 L10.2 6.2"
        stroke="currentColor"
        stroke-width="1.8"
        fill="none"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <svg
      v-else-if="arrow === 'right'"
      class="arrow-svg"
      viewBox="0 0 15 15"
      focusable="false"
    >
      <path
        d="M1.5 7.5 H11.5 M8.2 4.2 L11.5 7.5 L8.2 10.8"
        stroke="currentColor"
        stroke-width="1.8"
        fill="none"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <span v-else class="dot" />
  </span>
</template>

<style lang="scss" scoped>
.permission-glyph {
  --glyph-solid: var(--el-color-success);
  --glyph-light: var(--el-color-success-light-7);
  --glyph-stripe-light: var(--el-color-success-light-5);
  --glyph-accent: var(--el-color-success);
  --glyph-border: var(--el-color-success-dark-2);

  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-full);

  .arrow-svg {
    width: 15px;
    height: 15px;
  }

  .combined-arrow {
    width: 17px;
    height: 17px;
  }

  .dot {
    width: 10px;
    height: 10px;
    background: currentcolor;
    border-radius: var(--radius-full);
  }

  &.update {
    --glyph-solid: var(--el-color-warning);
    --glyph-light: var(--el-color-warning-light-7);
    --glyph-stripe-light: var(--el-color-warning-light-5);
    --glyph-accent: var(--el-color-warning);
    --glyph-border: var(--el-color-warning-dark-2);
  }

  &.remove {
    --glyph-solid: var(--el-color-danger);
    --glyph-light: var(--el-color-danger-light-7);
    --glyph-stripe-light: var(--el-color-danger-light-5);
    --glyph-accent: var(--el-color-danger);
    --glyph-border: var(--el-color-danger-dark-2);
  }

  &.solid {
    color: var(--el-color-white);
    background: var(--glyph-solid);
  }

  &.inherited {
    color: var(--glyph-accent);
    background: var(--glyph-light);
  }

  &.combined {
    color: var(--glyph-accent);
  }

  &.striped {
    border: 1px solid var(--glyph-border);

    &.solid {
      background: repeating-linear-gradient(
        -45deg,
        var(--glyph-solid),
        var(--glyph-solid) 3px,
        var(--el-color-white) 3px,
        var(--el-color-white) 6px
      );
    }

    &.inherited {
      background: repeating-linear-gradient(
        -45deg,
        var(--glyph-stripe-light),
        var(--glyph-stripe-light) 3px,
        var(--el-color-white) 3px,
        var(--el-color-white) 6px
      );
    }
  }

  &.bold {
    border: 2px solid var(--el-text-color-primary);
    box-shadow: 0 0 0 1px var(--el-bg-color) inset;
  }
}
</style>
