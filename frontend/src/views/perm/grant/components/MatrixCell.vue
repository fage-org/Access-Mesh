<script setup lang="ts">
/**
 * 矩阵单元格（§3.3 查看态 + §6.2 diff 标记，🔧 T-FE-039 图标正交模型）。
 *
 * 正交模型（2026-08-05 二轮评审映射定稿，不叠加角标体系）：
 * - 有效图标：直接=绿实心无箭头 / 资源继承=淡绿+上箭头 / 操作继承=淡绿+右箭头 /
 *   双重继承（资源+操作两段）=淡绿+组合箭头；高对比条纹=全部来源均带条件；
 *   粗黑边框=可转授 canGrant（任一来源 true 即显示）。
 * - 多来源聚合归并：每种状态最多一个图标；绿=有效/新增、黄=待更新、红=待撤销；
 *   颜色（任一直接->实色）/箭头（两段->组合）/粗黑边框（任一 canGrant）/条件构成；
 *   AUTO_DEP 不占独立来源图标但参与全部聚合计算，来源属性在悬浮详情标注。
 * - 待更新/待撤销完整复用直接/继承/条纹/粗边框形态，只切换黄色/红色色板；
 *   不使用减号/删除线/粗黑边框=删除等额外撤销符号。
 * - 子权限数量使用独立蓝色标识，与来源形态和状态颜色正交。
 * - diff 背景只表达 add/partial-add；update/remove 由黄/红图标表达。
 */
import { computed } from "vue";
import type { CellSource } from "../utils/source-chain";
import {
  aggregatePermissionIcon,
  cellBackgroundMark,
  childPermissionCountOf,
  splitSourcesByDraft,
  type SourceDraftMark
} from "../utils/cell-visual";
import ChildPermissionIndicator from "./ChildPermissionIndicator.vue";
import PermissionGlyph from "./PermissionGlyph.vue";

const props = defineProps<{
  sources: CellSource[];
  /** 草稿 diff 标记（add/update/remove → changeId）；MatrixCell 自行拆分为有效/被撤销两侧 */
  markInfo: Map<number, SourceDraftMark>;
  /** 页面能力（view 时无权限格不响应点击授权） */
  capability: "edit" | "view";
}>();

const emit = defineEmits<{
  (e: "select"): void;
}>();

/** 有效来源（add/update/未标记）与被撤销来源（remove 标记）拆分 */
const split = computed(() =>
  splitSourcesByDraft(props.sources, props.markInfo)
);
const current = computed(() => split.value.current);
const updated = computed(() => split.value.updated);
const removed = computed(() => split.value.removed);

const hasPermission = computed(() => props.sources.length > 0);

/** 每种状态独立聚合；形态相同，仅颜色区分绿/黄/红。 */
const currentIcon = computed(() => aggregatePermissionIcon(current.value));
const updateIcon = computed(() => aggregatePermissionIcon(updated.value));
const removeIcon = computed(() => aggregatePermissionIcon(removed.value));

/** 子权限数量按状态统计，最终合并到独立标识。 */
const currentChildCount = computed(() => childPermissionCountOf(current.value));
const updateChildCount = computed(() => childPermissionCountOf(updated.value));
const removeChildCount = computed(() => childPermissionCountOf(removed.value));
const totalChildCount = computed(
  () =>
    currentChildCount.value + updateChildCount.value + removeChildCount.value
);
const childPermissionTitle = computed(() => {
  const parts: string[] = [];
  if (currentChildCount.value > 0) {
    parts.push(`有效 ${currentChildCount.value}`);
  }
  if (updateChildCount.value > 0) {
    parts.push(`待更新 ${updateChildCount.value}`);
  }
  if (removeChildCount.value > 0) {
    parts.push(`待撤销 ${removeChildCount.value}`);
  }
  return `子权限共 ${totalChildCount.value} 条（${parts.join(" · ")}）`;
});

/**
 * 单元格 diff 背景仅表达 add/partial-add；update/remove 由同形异色图标表达。
 * 评审 P2-1：基于有效侧标记独立计算——撤销与新增混合时新增背景不被 remove 吞掉。
 */
const backgroundMark = computed(() =>
  cellBackgroundMark(props.sources, props.markInfo)
);

function groupTitle(
  sources: CellSource[],
  status: "current" | "update" | "remove",
  striped: boolean,
  childCount: number
): string {
  if (sources.length === 0) return "";
  const statusLabel =
    status === "update" ? "待更新" : status === "remove" ? "待撤销" : "";
  const parts = [statusLabel, sourceLabel(sources[0])].filter(Boolean);
  if (striped) parts.push("带条件");
  let summary = parts.join(" · ");
  if (sources.length > 1) summary += `；等 ${sources.length} 条来源`;
  if (childCount > 0) summary += `；含 ${childCount} 条子权限`;
  return `${summary}（点击查看完整来源链）`;
}

const currentTitle = computed(() =>
  groupTitle(
    current.value,
    "current",
    currentIcon.value?.striped ?? false,
    currentChildCount.value
  )
);
const updateTitle = computed(() =>
  groupTitle(
    updated.value,
    "update",
    updateIcon.value?.striped ?? false,
    updateChildCount.value
  )
);
const removeTitle = computed(() =>
  groupTitle(
    removed.value,
    "remove",
    removeIcon.value?.striped ?? false,
    removeChildCount.value
  )
);

function sourceLabel(s: CellSource): string {
  const parts: string[] = [];
  if (s.grantSource === "AUTO_DEP") parts.push("由资源依赖自动补全");
  else if (s.grantSource === "AUTHORITY_ROOT") parts.push("类型授权根种子");
  else if (isDirect(s)) parts.push("直接授权");
  if (s.nodeInheritFromCode) {
    parts.push(`资源继承自 ${s.nodeInheritFromName ?? s.nodeInheritFromCode}`);
  }
  if (s.opInheritFromCode) {
    parts.push(`操作继承自 ${s.opInheritFromCode}`);
  }
  if (s.combinationBit) parts.push("组合位");
  return parts.join("；") || "直接授权";
}

function isDirect(s: CellSource): boolean {
  return s.nodeInheritFromCode == null && s.opInheritFromCode == null;
}

function handleClick() {
  emit("select");
}
</script>

<template>
  <div
    class="matrix-cell"
    :class="[
      backgroundMark ? `mark-${backgroundMark}` : '',
      {
        empty: !hasPermission,
        editable: capability === 'edit'
      }
    ]"
    @click="handleClick"
  >
    <template v-if="hasPermission">
      <el-popover trigger="hover" placement="right" :width="360">
        <template #reference>
          <span class="cell-body">
            <!-- 有效/新增：绿色；形态表达来源与属性。 -->
            <PermissionGlyph
              v-if="currentIcon"
              variant="valid"
              :solid="currentIcon.solid"
              :arrow="currentIcon.arrow"
              :striped="currentIcon.striped"
              :bold="currentIcon.boldBorder"
              :title="currentTitle"
            />

            <!-- 待更新：沿用授权形态，仅改为黄色。 -->
            <PermissionGlyph
              v-if="updateIcon"
              variant="update"
              :solid="updateIcon.solid"
              :arrow="updateIcon.arrow"
              :striped="updateIcon.striped"
              :bold="updateIcon.boldBorder"
              :title="updateTitle"
            />

            <!-- 待撤销：沿用授权形态，仅改为红色。 -->
            <PermissionGlyph
              v-if="removeIcon"
              variant="remove"
              :solid="removeIcon.solid"
              :arrow="removeIcon.arrow"
              :striped="removeIcon.striped"
              :bold="removeIcon.boldBorder"
              :title="removeTitle"
            />

            <!-- 子权限数量为独立维度，不改变任一权限状态图标。 -->
            <ChildPermissionIndicator
              v-if="totalChildCount > 0"
              :count="totalChildCount"
              :title="childPermissionTitle"
            />
          </span>
        </template>

        <!-- 悬浮详情：完整来源链（条件/范围/canGrant/子权限/创建时间/自动补全/授权根标注） -->
        <div class="cell-detail">
          <div
            v-for="s in sources"
            :key="s.recordId"
            class="source-item"
            :class="{
              'auto-dep':
                s.grantSource === 'AUTO_DEP' ||
                s.grantSource === 'AUTHORITY_ROOT'
            }"
          >
            <div class="source-line">
              <el-tag
                size="small"
                :type="s.grantSource === 'MANUAL' ? 'primary' : 'info'"
                effect="plain"
              >
                {{
                  s.grantSource === "AUTO_DEP"
                    ? "自动补全"
                    : s.grantSource === "AUTHORITY_ROOT"
                      ? "授权根"
                      : "手动"
                }}
              </el-tag>
              <span>{{ sourceLabel(s) }}</span>
              <el-tag
                v-if="markInfo.get(s.recordId)?.mark === 'remove'"
                size="small"
                type="danger"
                effect="plain"
              >
                待撤销
              </el-tag>
            </div>
            <div class="source-meta">
              <span>条件：{{ s.conditionCode ?? (s.inlineCondition != null ? `内联:${s.inlineCondition.name}` : "无") }}</span>
              <span>范围：{{ s.scopeMode === "ALL" ? "全量" : "实例" }}</span>
              <span>可转授：{{ s.canGrant ? "是" : "否" }}</span>
              <span v-if="s.childCount > 0">子权限：{{ s.childCount }}</span>
              <span v-if="s.createdAt">创建：{{ s.createdAt }}</span>
            </div>
            <div
              v-if="
                s.grantSource === 'AUTO_DEP' ||
                s.grantSource === 'AUTHORITY_ROOT'
              "
              class="readonly-hint"
            >
              {{
                s.grantSource === "AUTHORITY_ROOT"
                  ? "授权根种子行只读（随类型生命周期维护），不可编辑/删除"
                  : "自动补全记录只读，不可编辑/删除"
              }}
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
  transition:
    background-color 0.15s,
    border-color 0.15s;

  /* 空单元格：极淡背景，hover 显示可授权提示（仅编辑态可点击） */
  &.empty {
    color: var(--el-text-color-placeholder);
    cursor: default;

    &.editable:hover {
      cursor: pointer;
      background: var(--el-fill-color-light);
    }
  }

  .empty-hint {
    visibility: hidden;
    font-size: 14px;
    font-weight: 300;
  }

  &.empty.editable:hover .empty-hint {
    visibility: visible;
  }

  /* 有权限：细边框小圆角块（商务风授权标记） */
  &:not(.empty) {
    border: 1px solid var(--el-border-color-lighter);

    &:hover {
      background: var(--el-fill-color-light);
      border-color: var(--el-color-primary-light-5);
    }
  }

  .cell-body {
    display: inline-flex;
    gap: 4px;
    align-items: center;
    padding: 0 2px;
  }

  /* §6.2 diff 背景仅提示新增；update/remove 由黄/红同形图标表达。 */
  &.mark-add {
    background: var(--el-color-success-light-8);
    border-color: var(--el-color-success-light-6);
  }

  &.mark-partial-add {
    background: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-7);
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
