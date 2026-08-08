<script setup lang="ts">
/**
 * 矩阵单元格（§3.3 查看态 + §6.2 diff 标记，🔧 T-FE-039 图标正交模型）。
 *
 * 正交模型（2026-08-05 二轮评审映射定稿，不叠加角标体系）：
 * - 有效图标：直接=绿实心无箭头 / 资源继承=淡绿+上箭头 / 操作继承=淡绿+右箭头 /
 *   双重继承（资源+操作两段）=淡绿+组合箭头；条纹=有条件（任一来源无条件按实色展示）；
 *   粗黑边框=可转授 canGrant（任一来源 true 即显示）。
 * - 多来源聚合归并（三轮评审）：正常态最多一个聚合有效图标；撤销时最多再一个撤销图标；
 *   颜色（任一直接->实色）/箭头（两段->组合）/粗黑边框（任一 canGrant）/条纹（全部有条件）；
 *   AUTO_DEP 不占独立来源图标但参与全部聚合计算，来源属性在悬浮详情标注。
 * - 撤销图标：红=撤销直接 / 淡红=撤销继承 / 红白·淡红白条纹=撤销来源带条件（对称归并）；
 *   撤销后仍有其他有效授权 -> 有效图标 + 撤销图标并列；撤销后无有效授权 -> 只显示撤销图标；
 *   不使用减号/删除线/粗黑边框=删除等额外撤销符号。
 * - 子权限下沿分叉（精确投影）：仅当当前格存在 childCount>0 的直接主权限记录时显示，
 *   继承投影不复制来源记录的分叉；级联撤销时分叉附着在红色撤销图标上。
 * - diff 背景（§6.2）：add=绿底 / partial-add=淡绿底 / update=黄底+黄描边；
 *   remove 由撤销图标表达，不加删除线/斜纹背景。
 */
import { computed } from "vue";
import type { CellSource } from "../utils/source-chain";
import {
  aggregateRemoveIcon,
  aggregateValidIcon,
  cellBackgroundMark,
  forkAttachmentOf,
  splitSourcesByDraft,
  type SourceDraftMark
} from "../utils/cell-visual";

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
const valid = computed(() => split.value.valid);
const removed = computed(() => split.value.removed);

const hasPermission = computed(() => props.sources.length > 0);

/** 聚合有效图标（最多一个；null = 无有效来源） */
const validIcon = computed(() => aggregateValidIcon(valid.value));

/** 聚合撤销图标（最多一个；null = 无被撤销来源） */
const removeIcon = computed(() => aggregateRemoveIcon(removed.value));

/** 子权限分叉附着（存续侧 / 撤销侧 childCount 之和） */
const fork = computed(() => forkAttachmentOf(valid.value, removed.value));

/**
 * 单元格 diff 背景（add 绿底 / partial-add 淡绿底 / update 黄底；remove 无背景由撤销图标表达）。
 * 评审 P2-1：基于有效侧标记独立计算——撤销与新增混合时新增背景不被 remove 吞掉。
 */
const backgroundMark = computed(() =>
  cellBackgroundMark(props.sources, props.markInfo)
);

/** 有效图标 hover 摘要（首条来源 + 条数） */
const validTitle = computed(() => {
  if (valid.value.length === 0) return "";
  const first = sourceLabel(valid.value[0]);
  return valid.value.length > 1
    ? `${first}；等 ${valid.value.length} 条来源（点击查看完整来源链）`
    : `${first}（点击查看完整来源链）`;
});

/** 撤销图标 hover 摘要（直接/继承 + 条件 + 级联子权限） */
const removeTitle = computed(() => {
  if (removed.value.length === 0) return "";
  const r = removeIcon.value!;
  const parts: string[] = [];
  parts.push(r.red ? "撤销直接授权" : "撤销继承授权");
  if (r.striped) parts.push("（带条件）");
  if (removed.value.length > 1) parts.push(`，共 ${removed.value.length} 条`);
  if (fork.value.removedCount > 0) {
    parts.push(`，含 ${fork.value.removedCount} 条子权限一并移除`);
  }
  return parts.join("");
});

/** 多分支并存数（有效 MANUAL 来源记录数 > 1 时在悬浮详情汇总展示） */
const branchCount = computed(() => {
  const manualIds = new Set(
    valid.value.filter(s => s.grantSource === "MANUAL").map(s => s.recordId)
  );
  return manualIds.size;
});

function sourceLabel(s: CellSource): string {
  const parts: string[] = [];
  if (s.grantSource === "AUTO_DEP") parts.push("由资源依赖自动补全");
  else if (isDirect(s)) parts.push("直接授权");
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
            <!-- 聚合有效图标（正常态最多一个） -->
            <span
              v-if="validIcon"
              class="cell-icon valid"
              :class="{
                solid: validIcon.solid,
                inherited: !validIcon.solid,
                striped: validIcon.striped,
                bold: validIcon.boldBorder
              }"
              :title="validTitle"
            >
              <!-- 组合箭头：资源段 + 操作段两段继承（单个弯箭头 ⤴ 形状——一段路径先向右再向上，
                   对应"操作覆盖（右）+ 资源继承（上）"，不拼合两个箭头） -->
              <svg
                v-if="validIcon.arrow === 'combined'"
                class="arrow-svg"
                viewBox="0 0 15 15"
              >
                <path
                  d="M2 8 H8.5 V3.5 M6 6.5 L8.5 3.5 L11 6.5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  fill="none"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              <!-- 上箭头：资源继承 -->
              <svg
                v-else-if="validIcon.arrow === 'up'"
                class="arrow-svg"
                viewBox="0 0 15 15"
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
              <!-- 右箭头：操作继承 -->
              <svg
                v-else-if="validIcon.arrow === 'right'"
                class="arrow-svg"
                viewBox="0 0 15 15"
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
              <!-- 直接授权：无箭头（实心圆点） -->
              <span v-else class="dot" />
              <!-- 子权限下沿分叉：仅直接主权限记录（存续侧） -->
              <svg
                v-if="fork.validCount > 0"
                class="fork-svg"
                viewBox="0 0 12 6"
                :title="`${fork.validCount} 条子权限`"
              >
                <path
                  d="M6 0 V2.5 M6 2.5 H2.5 V6 M6 2.5 H9.5 V6"
                  stroke="currentColor"
                  stroke-width="1.2"
                  fill="none"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>

            <!-- 聚合撤销图标（撤销时最多再一个；无有效来源时仅此图标） -->
            <span
              v-if="removeIcon"
              class="cell-icon remove"
              :class="{
                'light-red': !removeIcon.red,
                striped: removeIcon.striped
              }"
              :title="removeTitle"
            >
              <!-- 级联撤销：分叉附着在撤销图标上 -->
              <svg
                v-if="fork.removedCount > 0"
                class="fork-svg"
                viewBox="0 0 12 6"
                :title="`含 ${fork.removedCount} 条子权限一并移除`"
              >
                <path
                  d="M6 0 V2.5 M6 2.5 H2.5 V6 M6 2.5 H9.5 V6"
                  stroke="currentColor"
                  stroke-width="1.2"
                  fill="none"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
          </span>
        </template>

        <!-- 悬浮详情：分支汇总 + 完整来源链（条件/范围/canGrant/子权限/创建时间/自动补全标注） -->
        <div class="cell-detail">
          <div v-if="branchCount > 1" class="branch-summary">
            ⧉ {{ branchCount }} 个分支并存
          </div>
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

  /* ===== 图标正交模型（🔧 T-FE-039） ===== */

  .cell-icon {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: var(--radius-full);
    flex-shrink: 0;

    .arrow-svg {
      width: 15px;
      height: 15px;
    }

    .dot {
      width: 10px;
      height: 10px;
      border-radius: var(--radius-full);
      background: currentColor;
    }

    /* 下沿分叉（子权限；仅直接主权限记录；22px 图标在 36px 行高内下探 3px 不溢出） */
    .fork-svg {
      position: absolute;
      bottom: -3px;
      left: 50%;
      transform: translateX(-50%);
      width: 13px;
      height: 6px;
    }
  }

  /* 有效图标：直接 = 实色（绿）；继承 = 淡色（淡绿）；设计定稿 §3.3，非 primary 蓝 */
  .cell-icon.valid {
    &.solid {
      color: var(--el-color-white);
      background: var(--el-color-success);
    }

    &.inherited {
      color: var(--el-color-success);
      background: var(--el-color-success-light-7);
    }

    /* 条纹 = 有条件（全部有效来源带条件；任一来源无条件按实色展示） */
    &.striped {
      &.solid {
        background: repeating-linear-gradient(
          -45deg,
          var(--el-color-success),
          var(--el-color-success) 3px,
          var(--el-color-success-light-4) 3px,
          var(--el-color-success-light-4) 6px
        );
      }

      &.inherited {
        background: repeating-linear-gradient(
          -45deg,
          var(--el-color-success-light-6),
          var(--el-color-success-light-6) 3px,
          var(--el-color-success-light-9) 3px,
          var(--el-color-success-light-9) 6px
        );
      }
    }

    /* 粗黑边框 = 可转授 canGrant（能力属性，任一来源 true 即显示） */
    &.bold {
      border: 2px solid var(--el-color-black);
      box-shadow: 0 0 0 1px var(--el-color-white) inset;
    }
  }

  /* 撤销图标：红=直接 / 淡红=继承 / 红白·淡红白条纹=带条件；纯色圆角方块（不加符号） */
  .cell-icon.remove {
    border-radius: 4px;

    &.light-red {
      background: var(--el-color-danger-light-5);
    }

    &:not(.light-red) {
      background: var(--el-color-danger);
    }

    &.striped {
      background: repeating-linear-gradient(
        -45deg,
        var(--el-color-danger),
        var(--el-color-danger) 3px,
        var(--el-color-white) 3px,
        var(--el-color-white) 6px
      );

      &.light-red {
        background: repeating-linear-gradient(
          -45deg,
          var(--el-color-danger-light-4),
          var(--el-color-danger-light-4) 3px,
          var(--el-color-white) 3px,
          var(--el-color-white) 6px
        );
      }
    }
  }

  /* §6.2 diff 背景：add=绿底 / partial-add=淡绿底 / update=黄底+黄描边；
     remove 由撤销图标表达，不叠加删除线/斜纹（正交模型） */
  &.mark-add {
    background: var(--el-color-success-light-8);
    border-color: var(--el-color-success-light-6);
  }

  &.mark-partial-add {
    background: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-7);
  }

  &.mark-update {
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-6);
  }
}

.cell-detail {
  .branch-summary {
    padding: 2px 0 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    border-bottom: 1px solid var(--el-border-color-lighter);
    margin-bottom: 4px;
  }

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
