<script setup lang="ts">
import { computed, ref } from "vue";
import type { SummaryItem } from "@/utils/permission-grant-types";

defineOptions({ name: "PermissionSummaryCell" });

/**
 * 中栏操作权限摘要单元（T-FE-025）。
 * 只负责展示与 emit，不 inject store / 不 import 页面工具（窄接口）。
 * 入参 items 由 PermissionMatrixPanel 规范化（normalizeSummary）并排序（sortSummary）后传入。
 */
const props = withDefaults(
  defineProps<{
    items: SummaryItem[];
    maxVisible?: number;
  }>(),
  { maxVisible: 3 }
);

const emit = defineEmits<{
  "open-adjust": [item: SummaryItem];
  "locate-all": [operationCode: string];
}>();

function hasStatus(item: SummaryItem): boolean {
  return item.effective !== "UNAUTHORIZED" || item.draftChange !== null;
}

/** 全部操作未授权（无有效状态、无草稿变更）时仅展示单个 · */
const allUnauthorized = computed(
  () => props.items.length > 0 && props.items.every(i => !hasStatus(i))
);

const visible = computed(() =>
  props.items.filter(hasStatus).slice(0, props.maxVisible)
);
const visibleCodes = computed(
  () => new Set(visible.value.map(i => i.operationCode))
);

/** 其余所有操作（含未授权），+N 统计全部剩余；基于 operationCode 去重，避免排序导致重复/遗漏 */
const hidden = computed(() =>
  props.items.filter(i => !visibleCodes.value.has(i.operationCode))
);
const hiddenCount = computed(() => hidden.value.length);

// 状态符号 / 文字 / 草稿符号（§16.3.1 七类，色+符+字三合一）
// DERIVED/⊕ 渲染能力保留（normalizer 当前不产生，R1/R7 待 T-PERM-034）
const SYMBOL: Record<SummaryItem["effective"], string> = {
  DIRECT: "✓",
  CONDITIONAL: "◑",
  ALL_COVERED: "★",
  DERIVED: "⊕",
  NOT_GRANTABLE: "⊘",
  UNAUTHORIZED: "·"
};
const WORD: Record<SummaryItem["effective"], string> = {
  DIRECT: "直接",
  CONDITIONAL: "条件",
  ALL_COVERED: "ALL覆盖",
  DERIVED: "派生",
  NOT_GRANTABLE: "不可授",
  UNAUTHORIZED: "未授权"
};
const DRAFT_SYMBOL: Record<NonNullable<SummaryItem["draftChange"]>, string> = {
  ADD: "＋",
  REMOVE: "－",
  MODIFY: "✎"
};

// Hover 与 Focus 等价（§16.3.2 / R2）：trigger 数组同时响应 hover + focus
const HOVER_FOCUS: Array<"hover" | "focus"> = ["hover", "focus"];
const moreViz = ref(false);

function stateClass(item: SummaryItem): string {
  return `sum-tag sum-${item.effective.toLowerCase().replace("_", "-")}`;
}

/** ● 有直接记录副标记：与草稿符号正交共存（R6） */
function hasDirectMark(item: SummaryItem): boolean {
  return item.allCovered && item.hasBaselineDirectRecord;
}

function ariaLabel(item: SummaryItem): string {
  const parts = [item.operationName, WORD[item.effective]];
  if (item.draftChange) {
    const dc =
      item.draftChange === "ADD"
        ? "待新增"
        : item.draftChange === "REMOVE"
          ? "待移除"
          : "已修改";
    parts.push(dc);
  }
  if (hasDirectMark(item)) parts.push("有直接记录");
  if (item.conditionSummary) parts.push(`条件:${item.conditionSummary}`);
  if (item.canGrant) parts.push("可转授");
  if (item.childCount > 0) parts.push(`子权限${item.childCount}`);
  if (item.denyReason) parts.push(`不可授予:${item.denyReason}`);
  return parts.join("，");
}

function popoverContent(item: SummaryItem): string {
  const lines: string[] = [`${item.operationName}（${item.operationCode}）`];
  lines.push(`状态：${WORD[item.effective]}`);
  if (item.draftChange) {
    const dc =
      item.draftChange === "ADD"
        ? "待新增"
        : item.draftChange === "REMOVE"
          ? "待移除"
          : "已修改";
    lines.push(`草稿：${dc}`);
  }
  if (item.allCovered) lines.push("被 ALL 覆盖");
  if (item.hasBaselineDirectRecord) lines.push("baseline 有直接记录");
  if (item.conditionSummary) lines.push(`条件：${item.conditionSummary}`);
  lines.push(`可转授：${item.canGrant ? "是" : "否"}`);
  if (item.childCount > 0) lines.push(`子权限：${item.childCount}`);
  if (item.denyReason) lines.push(`不可授予：${item.denyReason}`);
  return lines.join("\n");
}

function onItemClick(item: SummaryItem) {
  // ALL 覆盖且无直接记录（无草稿变更）-> 定位 ALL 节点，不进入移除（§16.3.5）
  if (
    item.effective === "ALL_COVERED" &&
    !item.hasBaselineDirectRecord &&
    !item.draftChange
  ) {
    emit("locate-all", item.operationCode);
    return;
  }
  emit("open-adjust", item);
}
</script>

<template>
  <div class="sum-cell">
    <span
      v-if="allUnauthorized"
      class="sum-none"
      role="img"
      aria-label="全部操作未授权"
      >·</span
    >
    <template v-else>
      <el-popover
        v-for="item in visible"
        :key="item.operationCode"
        :trigger="HOVER_FOCUS"
        placement="top"
        :width="260"
        :show-after="150"
        :hide-after="100"
      >
        <template #reference>
          <button
            type="button"
            :class="stateClass(item)"
            :aria-label="ariaLabel(item)"
            @click="onItemClick(item)"
          >
            <span class="sum-symbol" aria-hidden="true">{{
              SYMBOL[item.effective]
            }}</span>
            <span class="sum-text"
              >{{ item.operationName }}·{{ WORD[item.effective] }}</span
            >
            <span
              v-if="item.draftChange"
              class="sum-draft"
              :class="`sum-draft-${item.draftChange.toLowerCase()}`"
              aria-hidden="true"
              >{{ DRAFT_SYMBOL[item.draftChange] }}</span
            >
            <span
              v-if="hasDirectMark(item)"
              class="sum-direct-mark"
              aria-hidden="true"
              >●</span
            >
          </button>
        </template>
        <pre class="sum-popover-content">{{ popoverContent(item) }}</pre>
      </el-popover>

      <el-popover
        v-if="hiddenCount > 0"
        v-model:visible="moreViz"
        :trigger="HOVER_FOCUS"
        placement="top"
        :width="300"
      >
        <template #reference>
          <button
            type="button"
            class="sum-more"
            :aria-label="`还有 ${hiddenCount} 个操作`"
          >
            +{{ hiddenCount }}
          </button>
        </template>
        <div class="sum-more-list">
          <button
            v-for="item in hidden"
            :key="item.operationCode"
            type="button"
            :class="[stateClass(item), 'sum-more-item']"
            :aria-label="ariaLabel(item)"
            @click="
              onItemClick(item);
              moreViz = false;
            "
          >
            <span class="sum-row">
              <span class="sum-symbol" aria-hidden="true">{{
                SYMBOL[item.effective]
              }}</span>
              <span class="sum-text"
                >{{ item.operationName }}·{{ WORD[item.effective] }}</span
              >
              <span
                v-if="item.draftChange"
                class="sum-draft"
                :class="`sum-draft-${item.draftChange.toLowerCase()}`"
                aria-hidden="true"
                >{{ DRAFT_SYMBOL[item.draftChange] }}</span
              >
              <span
                v-if="hasDirectMark(item)"
                class="sum-direct-mark"
                aria-hidden="true"
                >●</span
              >
            </span>
            <span v-if="item.conditionSummary" class="sum-extra"
              >条件：{{ item.conditionSummary }}</span
            >
            <span v-if="item.canGrant" class="sum-extra">可转授</span>
            <span v-if="item.childCount > 0" class="sum-extra"
              >子权限 {{ item.childCount }}</span
            >
            <span v-if="item.denyReason" class="sum-extra sum-extra-danger">{{
              item.denyReason
            }}</span>
          </button>
        </div>
      </el-popover>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.sum-cell {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  align-items: center;
}

.sum-none {
  font-size: 16px;
  color: var(--el-text-color-placeholder);
}

// tag = 真实可聚焦 button + focus ring
.sum-tag {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  padding: 2px var(--space-1);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  transition: all 0.15s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 1px;
  }
}

.sum-symbol {
  font-size: 13px;
  font-weight: 700;
}

// 文字用 primary 文字色（确保对比度 >= 4.5:1，颜色非唯一含义）
.sum-text {
  color: var(--el-text-color-primary);
}

// 状态色：符号 + 边框 + 浅背景（深色模式自动适配）
.sum-direct {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);

  .sum-symbol {
    color: var(--el-color-success);
  }
}

.sum-conditional {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);

  .sum-symbol {
    color: var(--el-color-warning);
  }
}

.sum-all-covered {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);

  .sum-symbol {
    color: var(--el-color-primary);
  }
}

.sum-derived {
  background: var(--el-color-info-light-9);
  border-color: var(--el-color-info-light-5);

  .sum-symbol {
    color: var(--el-color-info);
  }
}

.sum-not-grantable {
  background: var(--el-color-info-light-9);
  border-color: var(--el-color-info-light-5);

  .sum-symbol {
    color: var(--el-color-info);
  }
}

.sum-unauthorized {
  color: var(--el-text-color-placeholder);
  background: transparent;
  border-style: dashed;
}

.sum-draft {
  margin-left: 2px;
  font-weight: 700;
}

.sum-draft-add {
  color: var(--el-color-success);
}

.sum-draft-remove {
  color: var(--el-color-danger);
}

.sum-draft-modify {
  color: var(--el-color-warning);
}

.sum-direct-mark {
  margin-left: 2px;
  font-size: 10px;
  color: var(--el-color-info);
}

.sum-more {
  padding: 2px var(--space-1);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;

  &:hover {
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 1px;
  }
}

.sum-more-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

// +N 列表项：多行内联等价信息（与 visible 项 popover 详情等价，P2-4）
.sum-more-item {
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
}

.sum-row {
  display: inline-flex;
  gap: 2px;
  align-items: center;
}

.sum-extra {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.sum-extra-danger {
  color: var(--el-color-danger);
}

.sum-popover-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
