<script setup lang="ts">
/**
 * 条件选择器（页面私有轻量组件，T-FE-036 内自建，§9）。
 * 只选已有条件，不编辑规则定义（不复用 ReConditionEditor；ReConditionPicker 已随 v1/v2 删除）。
 * 搜索 + 规则摘要 + 启用状态过滤；数据源 permission-condition/list（CONDITION:VIEW 门控由调用方控制 disabled）。
 */
import { computed, ref } from "vue";
import { Search } from "@element-plus/icons-vue";
import type { ConditionResp } from "@/api/permission-condition";
import { summarizeRules } from "@/utils/condition-rules";

const props = defineProps<{
  modelValue: string | null;
  conditions: ConditionResp[];
  disabled?: boolean;
  placeholder?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string | null): void;
}>();

const keyword = ref("");
const enabledOnly = ref(false);

const selected = computed(
  () => props.conditions.find(c => c.code === props.modelValue) ?? null
);

const filtered = computed(() => {
  const kw = keyword.value.trim();
  return props.conditions.filter(c => {
    if (enabledOnly.value && !c.enabled) return false;
    if (!kw) return true;
    return c.code.includes(kw) || c.name.includes(kw);
  });
});

function handleSelect(code: string | null) {
  emit("update:modelValue", code);
  popoverVisible.value = false;
}

const popoverVisible = ref(false);
</script>

<template>
  <el-popover
    v-model:visible="popoverVisible"
    placement="bottom-start"
    :width="360"
    trigger="click"
  >
    <template #reference>
      <el-button :disabled="disabled" class="condition-trigger">
        <span v-if="selected">
          {{ selected.name }}（{{ selected.code }}）
          <el-tag v-if="!selected.enabled" size="small" type="info" class="ml-1"
            >停用</el-tag
          >
        </span>
        <span v-else class="placeholder">{{
          placeholder ?? "选择条件（默认无条件）"
        }}</span>
      </el-button>
    </template>

    <div class="condition-picker">
      <div class="picker-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索条件编码/名称"
          clearable
          :prefix-icon="Search"
        />
        <el-checkbox v-model="enabledOnly" class="ml-2">仅启用</el-checkbox>
      </div>
      <div class="picker-list">
        <div
          class="picker-item"
          :class="{ active: modelValue === null }"
          @click="handleSelect(null)"
        >
          <span class="item-name">无条件</span>
          <span class="item-summary">不附加条件，始终生效</span>
        </div>
        <div
          v-for="condition in filtered"
          :key="condition.code"
          class="picker-item"
          :class="{ active: modelValue === condition.code }"
          @click="handleSelect(condition.code)"
        >
          <span class="item-name">
            {{ condition.name }}（{{ condition.code }}）
            <el-tag
              size="small"
              :type="condition.enabled ? 'success' : 'info'"
              effect="plain"
            >
              {{ condition.enabled ? "启用" : "停用" }}
            </el-tag>
          </span>
          <span class="item-summary">{{
            summarizeRules(condition.conditionRules)
          }}</span>
        </div>
        <el-empty
          v-if="filtered.length === 0"
          description="无匹配条件"
          :image-size="48"
        />
      </div>
      <div class="picker-footer">
        需新建条件请前往「3.2 权限条件」页维护，本页只选择已有条件。
      </div>
    </div>
  </el-popover>
</template>

<style lang="scss" scoped>
.condition-trigger {
  justify-content: flex-start;
  min-width: 200px;

  .placeholder {
    color: var(--el-text-color-placeholder);
  }
}

.condition-picker {
  .picker-toolbar {
    display: flex;
    align-items: center;
    margin-bottom: var(--space-2);
  }

  .picker-list {
    max-height: 280px;
    overflow: auto;

    .picker-item {
      display: flex;
      flex-direction: column;
      padding: 6px 8px;
      cursor: pointer;
      border-radius: var(--radius-sm);

      &:hover {
        background: var(--el-fill-color-light);
      }

      &.active {
        background: var(--el-color-primary-light-9);
      }

      .item-name {
        display: flex;
        gap: 6px;
        align-items: center;
      }

      .item-summary {
        margin-top: 2px;
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .picker-footer {
    margin-top: var(--space-2);
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
