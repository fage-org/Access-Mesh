<script setup lang="ts">
import { computed } from "vue";
import type { PermissionCellContext } from "../utils/types";

defineOptions({ name: "PermissionCell" });

const props = defineProps<{ context: PermissionCellContext }>();
const emit = defineEmits<{
  toggle: [];
  openSetting: [];
  openChild: [];
}>();

const disabled = computed(
  () => props.context.readonly || !props.context.grantableByOperator
);
const cellClass = computed(
  () => `cell cell-${props.context.state.toLowerCase()}`
);

const label = computed(() => {
  switch (props.context.state) {
    case "GRANTED":
      return "✓";
    case "PENDING_ADD":
      return "+";
    case "PENDING_REMOVE":
      return "×";
    case "MODIFIED":
      return "~";
    case "ALL_COVERED":
      return "ALL";
    default:
      return "";
  }
});

const showMore = computed(
  () =>
    !props.context.readonly &&
    (["GRANTED", "PENDING_ADD", "MODIFIED"].includes(props.context.state) ||
      props.context.childCount > 0)
);

const tooltip = computed(() => {
  if (props.context.denyReason) return `不可授予：${props.context.denyReason}`;
  if (props.context.allCovered) return "被 ALL 覆盖";
  if (props.context.conditionSummary)
    return `条件：${props.context.conditionSummary}`;
  return "";
});
</script>

<template>
  <div class="perm-cell-wrap">
    <el-tooltip
      :content="tooltip"
      :disabled="!tooltip"
      placement="top"
      :show-after="300"
    >
      <button
        type="button"
        :class="[
          cellClass,
          { 'cell-disabled': disabled, 'cell-readonly': context.readonly }
        ]"
        :disabled="disabled && context.state === 'UNAUTHORIZED'"
        @click="emit('toggle')"
      >
        <span class="cell-label">{{ label }}</span>
        <span v-if="context.childCount > 0" class="cell-badge">{{
          context.childCount
        }}</span>
      </button>
    </el-tooltip>
    <el-dropdown
      v-if="showMore"
      trigger="click"
      @command="
        cmd => (cmd === 'setting' ? emit('openSetting') : emit('openChild'))
      "
    >
      <span class="cell-more">⋯</span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-if="
              ['GRANTED', 'PENDING_ADD', 'MODIFIED'].includes(context.state)
            "
            command="setting"
          >
            附加设置
            <span v-if="context.conditionSummary" class="dropdown-hint">{{
              context.conditionSummary
            }}</span>
          </el-dropdown-item>
          <el-dropdown-item command="child">
            子权限<span v-if="context.childCount > 0"
              >({{ context.childCount }})</span
            >
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style lang="scss" scoped>
.perm-cell-wrap {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.cell {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 28px;
  padding: 0 var(--space-1);
  font-size: 13px;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  transition: all 0.15s;

  &:hover:not(:disabled) {
    border-color: var(--el-color-primary-light-5);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
}

.cell-unauthorized {
  color: var(--el-text-color-placeholder);
  background: transparent;
  border-style: dashed;
}

.cell-granted {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.cell-pending-add {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.cell-pending-remove {
  color: var(--el-color-danger);
  text-decoration: line-through;
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
}

.cell-modified {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}

.cell-all-covered {
  font-size: 11px;
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.cell-disabled {
  opacity: 0.4;
}

.cell-readonly {
  cursor: not-allowed;
}

.cell-label {
  font-weight: 600;
}

.cell-badge {
  padding: 1px 4px;
  margin-left: var(--space-1);
  font-size: 10px;
  line-height: 1;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  border-radius: 8px;
}

.cell-more {
  font-size: 16px;
  line-height: 1;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--el-color-primary);
  }
}

.dropdown-hint {
  margin-left: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
