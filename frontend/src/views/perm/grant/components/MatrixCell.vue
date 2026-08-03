<script setup lang="ts">
/**
 * 矩阵单元格（§3.3 查看态 + §6.2 diff 标记）。
 * 简洁优先：有/无 + 来源图标 + 小角标；细节悬浮（el-popover 来源链 + 条件 + 范围 + canGrant + 子权限数 + 创建时间）。
 * 颜色规则（P1-6）：直接授权 = 实色/深色；继承（资源/操作）= 淡色；来源类型用图标区分。
 * AUTO_DEP：虚线标记 + 只读（P1-5），与 MANUAL 并列展示。
 */
import { computed } from "vue";
import type { CellSource } from "../utils/source-chain";
import type { CellDraftMark } from "../utils/grant-plan";

const props = defineProps<{
  sources: CellSource[];
  /** diff 标记（add/partial-add/update/partial-remove/remove/null） */
  mark: CellDraftMark;
  removedCount?: number;
  totalCount?: number;
  /** 页面能力（view 时无权限格不响应点击授权） */
  capability: "edit" | "view";
}>();

const emit = defineEmits<{
  (e: "select"): void;
}>();

const hasPermission = computed(() => props.sources.length > 0);

/** 主来源（展示优先级：直接 > 资源继承 > 操作继承；AUTO_DEP 虚线独立标注） */
const primary = computed<CellSource | null>(() => {
  if (!hasPermission.value) return null;
  const direct = props.sources.find(
    s => !s.nodeInheritFromCode && !s.opInheritFromCode
  );
  return direct ?? props.sources[0];
});

/** 单元格角标（C/G/A/⧉ + 组合位/全局操作） */
const badges = computed(() => {
  const result: Array<{ key: string; text: string; title: string }> = [];
  const ss = props.sources;
  if (ss.some(s => s.conditionCode)) {
    const names = ss
      .filter(s => s.conditionCode)
      .map(s => s.conditionCode)
      .join("、");
    result.push({ key: "C", text: "C", title: `带条件：${names}` });
  }
  if (ss.some(s => s.canGrant)) {
    result.push({ key: "G", text: "G", title: "可再授予" });
  }
  if (ss.some(s => s.scopeMode === "ALL")) {
    result.push({ key: "A", text: "A", title: "全量范围" });
  }
  // 多分支并存（同单元格 MANUAL 记录数 > 1）
  const manualCount = new Set(
    ss.filter(s => s.grantSource === "MANUAL").map(s => s.recordId)
  ).size;
  if (manualCount > 1) {
    result.push({
      key: "B",
      text: `⧉${manualCount}`,
      title: `${manualCount} 个分支并存（点击进入详情层查看）`
    });
  }
  if (ss.some(s => s.combinationBit)) {
    result.push({ key: "CB", text: "⌗", title: "组合位记录（按位拆解命中）" });
  }
  if (ss.some(s => s.globalOperation)) {
    result.push({
      key: "GL",
      text: "🌐",
      title: "全局操作（专属优先、全局回退）"
    });
  }
  return result;
});

const isAutoDepOnly = computed(
  () =>
    hasPermission.value &&
    props.sources.every(s => s.grantSource === "AUTO_DEP")
);

function sourceLabel(s: CellSource): string {
  const parts: string[] = [];
  if (s.grantSource === "AUTO_DEP") parts.push("由资源依赖自动补全");
  else if (!s.nodeInheritFromCode && !s.opInheritFromCode)
    parts.push("直接授权");
  if (s.nodeInheritFromCode) {
    parts.push(`资源继承自 ${s.nodeInheritFromName ?? s.nodeInheritFromCode}`);
  }
  if (s.opInheritFromCode) {
    parts.push(`操作继承自 ${s.opInheritFromCode}`);
  }
  if (s.combinationBit) parts.push("组合位");
  if (s.globalOperation) parts.push("全局操作");
  return parts.join("；") || "直接授权";
}

function handleClick() {
  emit("select");
}
</script>

<template>
  <div
    class="matrix-cell"
    :class="[
      mark ? `mark-${mark}` : '',
      { empty: !hasPermission, readonly: isAutoDepOnly }
    ]"
    @click="handleClick"
  >
    <template v-if="hasPermission">
      <el-popover trigger="hover" placement="right" :width="360">
        <template #reference>
          <span class="cell-body">
            <!-- 来源图标：直接=实心圆点；资源继承=⤴；操作继承=⇢；AUTO_DEP=虚线圈 -->
            <span
              v-if="primary"
              class="source-dot"
              :class="{
                direct:
                  !primary.nodeInheritFromCode &&
                  !primary.opInheritFromCode &&
                  primary.grantSource === 'MANUAL',
                inherited: !!(
                  primary.nodeInheritFromCode || primary.opInheritFromCode
                ),
                'auto-dep': primary.grantSource === 'AUTO_DEP'
              }"
            >
              <template v-if="primary.grantSource === 'AUTO_DEP'">⌘</template>
              <template v-else-if="primary.nodeInheritFromCode">⤴</template>
              <template v-else-if="primary.opInheritFromCode">⇢</template>
              <template v-else>●</template>
            </span>
            <span class="badge-list">
              <el-tooltip
                v-for="b in badges"
                :key="b.key"
                :content="b.title"
                placement="top"
              >
                <span class="cell-badge">{{ b.text }}</span>
              </el-tooltip>
              <!-- diff 标记角标 -->
              <span v-if="mark === 'add'" class="diff-badge add">＋</span>
              <span v-else-if="mark === 'partial-add'" class="diff-badge add"
                >＋</span
              >
              <span v-else-if="mark === 'update'" class="diff-badge update"
                >改</span
              >
              <span
                v-else-if="mark === 'partial-remove'"
                class="diff-badge partial-remove"
                :title="`移除 ${removedCount}/${totalCount} 分支`"
                >⧄</span
              >
              <span v-else-if="mark === 'remove'" class="diff-badge remove"
                >−</span
              >
            </span>
          </span>
        </template>

        <!-- 悬浮详情：来源链 + 条件 + 范围 + canGrant + 子权限数 + 创建时间 -->
        <div class="cell-detail">
          <div
            v-for="s in sources"
            :key="s.recordId"
            class="source-item"
            :class="{ 'auto-dep': s.grantSource === 'AUTO_DEP' }"
          >
            <div class="source-line">
              <el-tag
                size="small"
                :type="s.grantSource === 'AUTO_DEP' ? 'info' : 'primary'"
                effect="plain"
              >
                {{ s.grantSource === "AUTO_DEP" ? "自动补全" : "手动" }}
              </el-tag>
              <span>{{ sourceLabel(s) }}</span>
            </div>
            <div class="source-meta">
              <span>条件：{{ s.conditionCode ?? "无" }}</span>
              <span>范围：{{ s.scopeMode === "ALL" ? "全量" : "实例" }}</span>
              <span>可转授：{{ s.canGrant ? "是" : "否" }}</span>
              <span v-if="s.childCount > 0">子权限：{{ s.childCount }}</span>
              <span v-if="s.createdAt">创建：{{ s.createdAt }}</span>
            </div>
            <div v-if="s.grantSource === 'AUTO_DEP'" class="readonly-hint">
              自动补全记录只读，不可编辑/删除
            </div>
          </div>
        </div>
      </el-popover>
    </template>

    <!-- 无权限：编辑态悬浮提示可授权 -->
    <span v-else-if="capability === 'edit'" class="empty-hint">+</span>
  </div>
</template>

<style lang="scss" scoped>
.matrix-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  cursor: pointer;
  border-radius: var(--radius-sm);

  &.empty {
    color: var(--el-text-color-placeholder);
    cursor: default;
  }

  &.empty:hover {
    background: var(--el-fill-color-light);
  }

  .empty-hint {
    visibility: hidden;
    font-size: 14px;
  }

  &.empty:hover .empty-hint {
    visibility: visible;
  }

  .cell-body {
    display: inline-flex;
    gap: 4px;
    align-items: center;
  }

  .source-dot {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    font-size: 12px;
    line-height: 1;
    border-radius: 50%;

    &.direct {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-7);
    }

    &.inherited {
      color: var(--el-color-primary-light-3);
      background: var(--el-color-primary-light-9);
    }

    &.auto-dep {
      color: var(--el-text-color-secondary);
      background: transparent;
      border: 1px dashed var(--el-border-color);
    }
  }

  .badge-list {
    display: inline-flex;
    gap: 2px;
    align-items: center;
  }

  .cell-badge {
    padding: 0 3px;
    font-size: 10px;
    line-height: 14px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color);
    border-radius: 3px;
  }

  .diff-badge {
    padding: 0 3px;
    font-size: 10px;
    font-weight: 600;
    line-height: 14px;
    border-radius: 3px;

    &.add {
      color: var(--el-color-success);
      background: var(--el-color-success-light-8);
    }

    &.update {
      color: var(--el-color-warning);
      background: var(--el-color-warning-light-8);
    }

    &.partial-remove {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-8);
    }

    &.remove {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-8);
    }
  }

  /* §6.2 diff 标记：add 绿底 / update 黄底 / remove 删除线淡出 */
  &.mark-add {
    background: var(--el-color-success-light-8);
  }

  &.mark-update {
    background: var(--el-color-warning-light-9);
  }

  &.mark-partial-remove {
    background: repeating-linear-gradient(
      45deg,
      var(--el-color-danger-light-9),
      var(--el-color-danger-light-9) 4px,
      transparent 4px,
      transparent 8px
    );
  }

  &.mark-remove {
    text-decoration: line-through;
    opacity: 0.45;
  }
}

.cell-detail {
  .source-item {
    padding: 6px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: none;
    }

    .source-line {
      display: flex;
      gap: 6px;
      align-items: center;
      margin-bottom: 4px;
    }

    .source-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 10px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .readonly-hint {
      margin-top: 4px;
      font-size: 12px;
      color: var(--el-color-warning);
    }
  }
}
</style>
