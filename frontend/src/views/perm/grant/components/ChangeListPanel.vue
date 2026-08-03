<script setup lang="ts">
/**
 * 右栏变更清单（§6.3：分组清单 + 定位/逐条撤销；删除红色醒目提示，方案二决策）。
 * 分组：新增 N / 修改 M / 删除 K / 替换 J；多选新增按"操作+条件+canGrant+scopeMode"聚合
 * （如"VIEW · 无条件 · 3 个资源"，点开看明细，避免 N 行刷屏，第十四轮）。
 * 失败处理（§6.4 请求粒度）：saveFailed 时全部条目标红、整体重试（单事务原子无部分成功）。
 */
import { computed, ref } from "vue";
import { LocationInformation } from "@element-plus/icons-vue";
import type { AddChange, DraftChange, SubmitState } from "../utils/types";
import { changeGroupKey } from "../utils/types";

const props = defineProps<{
  changes: DraftChange[];
  submit: SubmitState;
  /** 冻结态（saving/切换期间禁用撤销，评审问题 4） */
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "locate", change: DraftChange): void;
  (e: "revert", change: DraftChange): void;
}>();

// ========== 分组 ==========

type AddGroup = {
  key: string;
  items: AddChange[];
  label: string;
};

const addGroups = computed<AddGroup[]>(() => {
  const map = new Map<string, AddChange[]>();
  for (const change of props.changes) {
    if (change.kind !== "add") continue;
    const key = changeGroupKey(change.summary);
    const list = map.get(key) ?? [];
    list.push(change);
    map.set(key, list);
  }
  return [...map.values()].map(items => {
    const first = items[0];
    const condition = first.summary.conditionCode ?? "无条件";
    return {
      key: changeGroupKey(first.summary),
      items,
      label: `${first.summary.operationCode ?? "组合位"} · ${condition} · ${items.length} 个资源`
    };
  });
});

const updateChanges = computed(() =>
  props.changes.filter(
    (c): c is Extract<DraftChange, { kind: "update" }> => c.kind === "update"
  )
);

const removeChanges = computed(() =>
  props.changes.filter(
    (c): c is Extract<DraftChange, { kind: "remove" }> => c.kind === "remove"
  )
);

const replaceChanges = computed(() =>
  props.changes.filter(
    (c): c is Extract<DraftChange, { kind: "replace" }> => c.kind === "replace"
  )
);

const expandedAddGroups = ref<string[]>([]);

function toggleAddGroup(key: string) {
  expandedAddGroups.value = expandedAddGroups.value.includes(key)
    ? expandedAddGroups.value.filter(k => k !== key)
    : [...expandedAddGroups.value, key];
}

/** 变更摘要文本（修改：旧 → 新） */
function updateSummary(
  change: Extract<DraftChange, { kind: "update" }>
): string {
  const parts: string[] = [];
  if (change.before.canGrant !== change.after.canGrant) {
    parts.push(
      `可转授：${change.before.canGrant ? "是" : "否"} → ${change.after.canGrant ? "是" : "否"}`
    );
  }
  if (change.before.conditionCode !== change.after.conditionCode) {
    parts.push(
      `条件：${change.before.conditionCode ?? "无"} → ${change.after.conditionCode ?? "无"}`
    );
  }
  return parts.join("；") || "内容调整";
}

const failed = computed(() => props.submit.kind === "saveFailed");
</script>

<template>
  <div class="change-list-panel" :class="{ failed }">
    <div class="panel-title">变更清单（{{ changes.length }}）</div>

    <el-alert
      v-if="failed && submit.kind === 'saveFailed'"
      type="error"
      :closable="false"
      :title="submit.message"
      :description="
        submit.unknownOutcome
          ? '保存结果未知，请刷新页面确认当前状态后再决定重试。'
          : '全部条目已保留，可修正后整体重试。'
      "
      class="failed-alert"
    />

    <el-empty
      v-if="changes.length === 0"
      description="暂无变更"
      :image-size="48"
    />

    <div v-else class="change-groups">
      <!-- 新增（绿） -->
      <div v-if="addGroups.length" class="change-group">
        <div class="group-title add">
          新增 {{ changes.filter(c => c.kind === "add").length }}
        </div>
        <div
          v-for="group in addGroups"
          :key="group.key"
          class="change-item add"
        >
          <div class="item-head" @click="toggleAddGroup(group.key)">
            <span class="item-label">
              {{ group.label }}
            </span>
            <span class="item-actions" @click.stop>
              <el-tooltip content="定位到矩阵单元格" placement="top">
                <el-button
                  size="small"
                  text
                  :icon="LocationInformation"
                  @click="emit('locate', group.items[0])"
                />
              </el-tooltip>
            </span>
          </div>
          <div
            v-if="
              expandedAddGroups.includes(group.key) || group.items.length === 1
            "
            class="item-details"
          >
            <div
              v-for="item in group.items"
              :key="item.changeId"
              class="detail-row"
            >
              <span>{{ item.summary.resourceLabel }}</span>
              <el-button
                size="small"
                text
                type="danger"
                :disabled="props.disabled"
                @click="emit('revert', item)"
                >撤销</el-button
              >
            </div>
          </div>
          <div v-else class="item-expand" @click="toggleAddGroup(group.key)">
            展开 {{ group.items.length }} 条明细 ▾
          </div>
        </div>
      </div>

      <!-- 修改（黄） -->
      <div v-if="updateChanges.length" class="change-group">
        <div class="group-title update">修改 {{ updateChanges.length }}</div>
        <div
          v-for="change in updateChanges"
          :key="change.changeId"
          class="change-item update"
        >
          <div class="item-head">
            <span class="item-label">
              {{ change.summary.resourceLabel }} ·
              {{ change.summary.operationCode }}
              <span class="item-summary">{{ updateSummary(change) }}</span>
            </span>
            <span class="item-actions">
              <el-button
                size="small"
                text
                :icon="LocationInformation"
                @click="emit('locate', change)"
              />
              <el-button
                size="small"
                text
                type="danger"
                :disabled="props.disabled"
                @click="emit('revert', change)"
                >撤销</el-button
              >
            </span>
          </div>
        </div>
      </div>

      <!-- 删除（红，醒目） -->
      <div v-if="removeChanges.length" class="change-group">
        <div class="group-title remove">删除 {{ removeChanges.length }}</div>
        <div
          v-for="change in removeChanges"
          :key="change.changeId"
          class="change-item remove"
        >
          <div class="item-head">
            <span class="item-label">
              {{ change.summary.resourceLabel }} ·
              {{ change.summary.operationCode }}
              <span class="item-summary">
                {{
                  change.records.length > 1
                    ? `移除 ${change.records.length} 个分支`
                    : "移除该授权"
                }}
              </span>
            </span>
            <span class="item-actions">
              <el-button
                size="small"
                text
                :icon="LocationInformation"
                @click="emit('locate', change)"
              />
              <el-button
                size="small"
                text
                type="danger"
                :disabled="props.disabled"
                @click="emit('revert', change)"
                >撤销</el-button
              >
            </span>
          </div>
          <div v-if="change.cascadeChildCount > 0" class="cascade-hint">
            该权限下 {{ change.cascadeChildCount }} 条子权限将随主权限一并移除
          </div>
        </div>
      </div>

      <!-- 替换（橙：跨键变更 = 移除旧 + 创建新） -->
      <div v-if="replaceChanges.length" class="change-group">
        <div class="group-title replace">替换 {{ replaceChanges.length }}</div>
        <div
          v-for="change in replaceChanges"
          :key="change.changeId"
          class="change-item replace"
        >
          <div class="item-head">
            <span class="item-label">
              {{ change.summary.resourceLabel }} ·
              {{ change.summary.operationCode }}
              <span class="item-summary"
                >跨键替换（移除旧 + 创建新，同事务原子）</span
              >
            </span>
            <span class="item-actions">
              <el-button
                size="small"
                text
                :icon="LocationInformation"
                @click="emit('locate', change)"
              />
              <el-button
                size="small"
                text
                type="danger"
                :disabled="props.disabled"
                @click="emit('revert', change)"
                >撤销</el-button
              >
            </span>
          </div>
          <div v-if="change.cascadeChildCount > 0" class="cascade-hint">
            子权限不迁移：{{ change.cascadeChildCount }}
            条子权限随主权限一并移除
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.change-list-panel {
  height: 100%;
  padding: var(--space-2);
  overflow: auto;

  &.failed {
    .change-item {
      background: var(--el-color-danger-light-9);
      border-color: var(--el-color-danger-light-5);
    }
  }

  .panel-title {
    margin-bottom: var(--space-2);
    font-weight: 600;
  }

  .failed-alert {
    margin-bottom: var(--space-2);
  }

  .change-group {
    margin-bottom: var(--space-3);

    .group-title {
      margin-bottom: var(--space-1);
      font-size: 12px;
      font-weight: 600;

      &.add {
        color: var(--el-color-success);
      }

      &.update {
        color: var(--el-color-warning);
      }

      &.remove {
        color: var(--el-color-danger);
      }

      &.replace {
        color: var(--el-color-warning-dark-2);
      }
    }
  }

  .change-item {
    padding: 6px 8px;
    margin-bottom: var(--space-1);
    border: 1px solid var(--el-border-color-lighter);
    border-left-width: 3px;
    border-radius: var(--radius-sm);

    &.add {
      border-left-color: var(--el-color-success);
    }

    &.update {
      border-left-color: var(--el-color-warning);
    }

    &.remove {
      background: var(--el-color-danger-light-9);
      // 删除红色醒目提示（方案二决策）
      border-left-color: var(--el-color-danger);

      .item-label {
        font-weight: 600;
        color: var(--el-color-danger);
        text-decoration: line-through;
      }
    }

    &.replace {
      border-left-color: var(--el-color-warning-dark-2);
    }

    .item-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;

      .item-label {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 13px;
        white-space: nowrap;
      }

      .item-summary {
        display: block;
        font-size: 12px;
        font-weight: 400;
        color: var(--el-text-color-secondary);
      }

      .item-actions {
        flex-shrink: 0;
      }
    }

    .item-details {
      padding-top: 4px;
      margin-top: 4px;
      border-top: 1px dashed var(--el-border-color-lighter);

      .detail-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 12px;
      }
    }

    .item-expand {
      margin-top: 4px;
      font-size: 12px;
      color: var(--el-color-primary);
      cursor: pointer;
    }

    .cascade-hint {
      margin-top: 4px;
      font-size: 12px;
      color: var(--el-color-danger);
    }
  }
}
</style>
