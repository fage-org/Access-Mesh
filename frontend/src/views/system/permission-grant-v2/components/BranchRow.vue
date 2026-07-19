<script setup lang="ts">
import { computed } from "vue";
import { type V2DraftPermission } from "../utils/v2-types";
import { type ConditionOption } from "@/api/permission-grant";

defineOptions({ name: "BranchRowV2" });

const props = defineProps<{
  variant: V2DraftPermission;
  /** baseline=既存未改 / pendingAdd=待新增 / pendingRemove=待移除 / modified=属性已修改 */
  status: "baseline" | "pendingAdd" | "pendingRemove" | "modified";
  supportsCondition: boolean;
  supportsDelegation: boolean;
  conditionOptions: ConditionOption[];
  /** 子权限数量（T-FE-033） */
  childCount?: number;
  /** 子权限矩阵是否展开 */
  childrenExpanded?: boolean;
  /** 是否可展开子权限矩阵（父变体在投影 + 非 pendingRemove） */
  canExpandChildren?: boolean;
}>();

const emit = defineEmits<{
  (
    e: "edit",
    payload: { conditionCode: string | null; canGrant: boolean }
  ): void;
  (e: "revoke"): void;
  (e: "restore"): void;
  (e: "restore-modify"): void;
  (e: "toggle-children"): void;
}>();

const isUnconditional = computed(
  () =>
    props.variant.conditionCode === null || props.variant.conditionCode === ""
);

const conditionValue = computed<string>(
  () => props.variant.conditionCode ?? ""
);

const conditionSelectOptions = computed(() => [
  { label: "无条件", value: "" },
  ...props.conditionOptions.map(c => ({
    label: c.name || c.code,
    value: c.code
  }))
]);

function onConditionChange(val: string) {
  emit("edit", {
    conditionCode: val === "" ? null : val,
    canGrant: props.variant.canGrant
  });
}

function onCanGrantChange(val: boolean) {
  emit("edit", {
    conditionCode: props.variant.conditionCode,
    canGrant: val
  });
}

const statusLabel = computed(() => {
  if (props.status === "pendingAdd") return "待新增";
  if (props.status === "pendingRemove") return "待移除";
  if (props.status === "modified") return "已修改";
  return "";
});

const statusTagType = computed<"success" | "danger" | "warning">(() =>
  props.status === "pendingRemove"
    ? "danger"
    : props.status === "pendingAdd"
      ? "success"
      : "warning"
);
</script>

<template>
  <div class="branch-row" :class="`status-${status}`">
    <span class="branch-label">
      <span v-if="isUnconditional" class="cond-name">无条件</span>
      <span v-else class="cond-name">{{ variant.conditionCode }}</span>
      <el-tag
        v-if="statusLabel"
        size="small"
        :type="statusTagType"
        effect="plain"
      >
        {{ statusLabel }}
      </el-tag>
    </span>
    <el-select
      v-if="supportsCondition && status !== 'pendingRemove'"
      :model-value="conditionValue"
      size="small"
      placeholder="选择条件"
      class="cond-select"
      @change="onConditionChange"
    >
      <el-option
        v-for="opt in conditionSelectOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    <span
      v-if="supportsDelegation && status !== 'pendingRemove'"
      class="can-grant-wrap"
    >
      <span class="can-grant-label">转授</span>
      <el-switch
        :model-value="variant.canGrant"
        size="small"
        @change="onCanGrantChange"
      />
    </span>
    <el-button
      v-if="canExpandChildren"
      size="small"
      link
      class="children-toggle"
      @click="emit('toggle-children')"
    >
      ⌗ 子权限<template v-if="childCount && childCount > 0">
        ({{ childCount }})
      </template>
      {{ childrenExpanded ? "▾" : "▸" }}
    </el-button>
    <span class="branch-actions">
      <el-button
        v-if="status === 'pendingRemove'"
        size="small"
        type="primary"
        link
        @click="emit('restore')"
      >
        恢复
      </el-button>
      <template v-else>
        <el-button
          v-if="status === 'modified'"
          size="small"
          type="warning"
          link
          @click="emit('restore-modify')"
        >
          恢复修改
        </el-button>
        <el-button size="small" type="danger" link @click="emit('revoke')">
          撤销此分支
        </el-button>
      </template>
    </span>
  </div>
</template>

<style lang="scss" scoped>
.branch-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-1) var(--space-2);

  // P2 修复：只弱化非交互内容，恢复按钮保持正常对比度（不用整行 opacity）
  &.status-pendingRemove .cond-name {
    color: var(--el-text-color-secondary);
  }
}

.branch-label {
  display: inline-flex;
  flex-shrink: 0;
  gap: var(--space-1);
  align-items: center;
  min-width: 120px;
}

.cond-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cond-select {
  width: 160px;
}

.can-grant-wrap {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.can-grant-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.branch-actions {
  display: inline-flex;
  flex: 1;
  gap: var(--space-1);
  justify-content: flex-end;
}
</style>
