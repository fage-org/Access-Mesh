<script setup lang="ts">
/**
 * 条件选择器（页面私有轻量组件，T-FE-036 内自建，§9）。
 * 只选已有条件，不编辑规则定义（不复用 ReConditionEditor；ReConditionPicker 已随 v1/v2 删除）。
 * 搜索 + 规则摘要 + 启用状态过滤；数据源 permission-condition/list。
 * 🔧 T-FE-040 v3.1（S4）：新选限启用中条件——停用条件灰显不可选；存量停用绑定回显标注
 * 「条件已停用」（对应后端 20042 同口径：主权限 conditionCode 新写入或变更必须 enabled=true）。
 * 🔧 T-FE-040 v3.1（S5）：CONDITION:VIEW 读取门禁已移除（2026-08-08 产品确认），条件列表始终可读。
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
  // S4：新选限启用中条件——停用条件灰显不可选（已绑定记录仅展示）
  if (code != null && !props.conditions.find(c => c.code === code)?.enabled) {
    return;
  }
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
          <!-- S4：存量停用绑定回显标注 -->
          <el-tooltip
            v-if="!selected.enabled"
            content="该条件已停用：原绑定授权仍生效，但不可切换回此条件；需修改请先选择启用中的条件"
            placement="top"
          >
            <el-tag size="small" type="info" class="ml-1">条件已停用</el-tag>
          </el-tooltip>
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
          :class="{
            active: modelValue === condition.code,
            disabled: !condition.enabled
          }"
          @click="handleSelect(condition.code)"
        >
          <span class="item-name">
            {{ condition.name }}（{{ condition.code }}）
            <el-tag
              size="small"
              :type="condition.enabled ? 'success' : 'info'"
              effect="plain"
            >
              {{ condition.enabled ? "启用" : "已停用" }}
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
      padding: var(--space-2) var(--space-2);
      cursor: pointer;
      border: 1px solid transparent;
      border-radius: var(--radius-md);
      transition:
        background-color 0.15s,
        border-color 0.15s;

      &:hover {
        background: var(--el-fill-color-light);
      }

      &.active {
        background: var(--el-color-primary-light-9);
        border-color: var(--el-color-primary-light-5);
      }

      &.disabled {
        cursor: not-allowed;
        opacity: 0.5;

        &:hover {
          background: transparent;
        }
      }

      .item-name {
        display: flex;
        gap: 6px;
        align-items: center;
        font-size: 13px;
      }

      .item-summary {
        margin-top: 2px;
        font-size: 12px;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .picker-footer {
    padding-top: var(--space-2);
    margin-top: var(--space-2);
    font-size: 12px;
    color: var(--el-text-color-secondary);
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
</style>
