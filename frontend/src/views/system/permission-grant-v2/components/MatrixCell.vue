<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from "vue";
import { type CellDisplay } from "../utils/cell-summary";

defineOptions({ name: "MatrixCellV2" });

const props = defineProps<{
  display: CellDisplay;
  /** 矩阵整体只读（SAVING / READONLY） */
  readonly?: boolean;
  /** 批量多选选中（T-FE-032） */
  selected?: boolean;
}>();

const emit = defineEmits<{
  (e: "main-click"): void;
  (
    e: "menu-action",
    action: "grant-config" | "edit-branches" | "locate-source"
  ): void;
  /** 批量多选：Ctrl/Shift 修饰键点击（T-FE-032） */
  (e: "select", payload: { ctrl: boolean; shift: boolean }): void;
}>();

const cellRef = ref<HTMLElement | null>(null);

// 触屏长按计时（500ms 触发上下文菜单）
let longPressTimer: ReturnType<typeof setTimeout> | null = null;
let touchStartX = 0;
let touchStartY = 0;
const TOUCH_MOVE_THRESHOLD = 10;

const menuVisible = ref(false);
const menuPos = ref({ top: "0px", left: "0px" });

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer);
    longPressTimer = null;
  }
}

function onMainClick() {
  if (menuVisible.value) return;
  if (props.readonly) return;
  emit("main-click");
}

/**
 * 鼠标点击路由（T-FE-032）：
 * - Ctrl/Meta+点击 -> 加入/移出选择集（toggle）
 * - Shift+点击 -> 矩形范围选择（由父组件用 selectionAnchor 计算）
 * - 无修饰键 -> 主区域点击（切换授权/展开分支列表）
 * 只读矩阵不响应多选（批量操作无意义）。
 */
function onCellClick(e: MouseEvent) {
  if (menuVisible.value) return;
  if (props.readonly) return;
  if (e.ctrlKey || e.metaKey || e.shiftKey) {
    e.preventDefault();
    emit("select", {
      ctrl: e.ctrlKey || e.metaKey,
      shift: e.shiftKey
    });
    return;
  }
  emit("main-click");
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    onMainClick();
    return;
  }
  // P2 修复：键盘可打开上下文菜单（ContextMenu 键 / Shift+F10）
  if (e.key === "ContextMenu" || (e.shiftKey && e.key === "F10")) {
    e.preventDefault();
    if (props.readonly) return;
    openMenuAtCell();
  }
}

function openMenuAtCell() {
  const el = cellRef.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  openMenu(rect.left + rect.width / 2, rect.bottom);
}

function openMenu(x: number, y: number) {
  if (!hasMenuActions.value) return; // 无可用动作不打开菜单
  const menuW = 168;
  const menuH = 140;
  const left = Math.min(x, window.innerWidth - menuW - 8);
  const top = Math.min(y, window.innerHeight - menuH - 8);
  menuPos.value = { top: `${top}px`, left: `${left}px` };
  menuVisible.value = true;
}

function onContextMenu(e: MouseEvent) {
  if (props.readonly) return;
  e.preventDefault();
  openMenu(e.clientX, e.clientY);
}

function onTouchStart(e: TouchEvent) {
  if (props.readonly) return;
  const touch = e.touches[0];
  if (touch) {
    touchStartX = touch.clientX;
    touchStartY = touch.clientY;
  }
  clearLongPress();
  longPressTimer = setTimeout(() => {
    if (touch) openMenu(touch.clientX, touch.clientY);
  }, 500);
}

// P2 修复：移动超阈值 / touchcancel 取消长按
function onTouchMove(e: TouchEvent) {
  const touch = e.touches[0];
  if (!touch) return;
  const dx = Math.abs(touch.clientX - touchStartX);
  const dy = Math.abs(touch.clientY - touchStartY);
  if (dx > TOUCH_MOVE_THRESHOLD || dy > TOUCH_MOVE_THRESHOLD) {
    clearLongPress();
  }
}

function onTouchEnd() {
  clearLongPress();
}

function onTouchCancel() {
  clearLongPress();
}

// 菜单焦点管理：打开后聚焦首个菜单项
const menuRef = ref<HTMLElement | null>(null);

watch(menuVisible, v => {
  if (v) {
    nextTick(() => {
      menuRef.value?.querySelector<HTMLElement>(".menu-item")?.focus();
    });
  }
});

function onMenuKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") {
    e.preventDefault();
    closeMenu();
  } else if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    e.preventDefault();
    const items = Array.from(
      menuRef.value?.querySelectorAll<HTMLElement>(".menu-item") ?? []
    );
    if (items.length === 0) return;
    const currentIndex = items.indexOf(document.activeElement as HTMLElement);
    const next =
      e.key === "ArrowDown"
        ? items[(currentIndex + 1) % items.length]
        : items[(currentIndex - 1 + items.length) % items.length];
    next?.focus();
  }
}

function closeMenu() {
  menuVisible.value = false;
  cellRef.value?.focus();
}

function doMenuAction(
  action: "grant-config" | "edit-branches" | "locate-source"
) {
  menuVisible.value = false;
  emit("menu-action", action);
  // P2 修复：菜单动作后恢复焦点到单元格；locate-source 由父组件聚焦目标单元格
  if (action !== "locate-source") {
    nextTick(() => cellRef.value?.focus());
  }
}

const showGrantConfig = computed(
  () =>
    props.display.summary.effective === "UNAUTHORIZED" &&
    props.display.grantableByOperator
);
const showEditBranches = computed(
  () =>
    props.display.summary.effective !== "UNAUTHORIZED" ||
    props.display.summary.draftChange === "REMOVE"
);
const showLocateSource = computed(
  () =>
    props.display.allCovered ||
    props.display.summary.effective === "INHERITED" ||
    props.display.summary.effective === "DERIVED"
);

const hasMenuActions = computed(
  () =>
    showGrantConfig.value || showEditBranches.value || showLocateSource.value
);

const ariaLabel = computed(() => props.display.popoverLines.join("；"));

onBeforeUnmount(() => {
  clearLongPress();
});
</script>

<template>
  <!-- P2 修复：hover/focus 等价 -- trigger 数组同时支持鼠标悬停与键盘聚焦 -->
  <el-popover
    :trigger="['hover', 'focus']"
    placement="top"
    :width="240"
    :show-after="150"
    :hide-after="100"
    :disabled="menuVisible"
  >
    <template #reference>
      <div
        ref="cellRef"
        class="matrix-cell"
        :class="{
          readonly,
          expanded: display.expanded,
          selected,
          'has-overflow': display.overflowCount > 0
        }"
        :data-cellkey="display.cellKeyStr"
        role="button"
        tabindex="0"
        :aria-label="ariaLabel"
        :aria-disabled="readonly"
        :aria-haspopup="hasMenuActions"
        :aria-expanded="menuVisible"
        :aria-selected="selected"
        @click="onCellClick"
        @keydown="onKeyDown"
        @contextmenu="onContextMenu"
        @touchstart.passive="onTouchStart"
        @touchmove.passive="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchCancel"
      >
        <span
          v-for="mark in display.marks"
          :key="mark.key"
          class="cell-mark"
          :class="mark.tagType ? `tag-${mark.tagType}` : ''"
        >
          <span class="mark-symbol" aria-hidden="true">{{ mark.symbol }}</span>
          <span v-if="mark.label" class="mark-label">{{ mark.label }}</span>
        </span>
        <span v-if="display.overflowCount > 0" class="overflow-badge">
          +{{ display.overflowCount }}
        </span>
      </div>
    </template>
    <div class="popover-content">
      <div
        v-for="line in display.popoverLines"
        :key="line"
        class="popover-line"
      >
        {{ line }}
      </div>
    </div>
  </el-popover>

  <Teleport to="body">
    <div
      v-if="menuVisible"
      class="cell-menu-overlay"
      @click="closeMenu"
      @contextmenu.prevent="closeMenu"
    >
      <div
        ref="menuRef"
        class="cell-menu"
        role="menu"
        :style="menuPos"
        @click.stop
        @keydown="onMenuKeydown"
      >
        <button
          v-if="showGrantConfig"
          class="menu-item"
          type="button"
          role="menuitem"
          tabindex="0"
          @click="doMenuAction('grant-config')"
        >
          授予并配置
        </button>
        <button
          v-if="showEditBranches"
          class="menu-item"
          type="button"
          role="menuitem"
          tabindex="0"
          @click="doMenuAction('edit-branches')"
        >
          查看/编辑分支
        </button>
        <button
          v-if="showLocateSource"
          class="menu-item"
          type="button"
          role="menuitem"
          tabindex="0"
          @click="doMenuAction('locate-source')"
        >
          定位来源
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style lang="scss" scoped>
@media (prefers-reduced-motion: reduce) {
  .matrix-cell {
    transition: none;
  }
}

.matrix-cell {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 2px 4px;
  cursor: pointer;
  user-select: none;
  border-radius: var(--el-border-radius-small);
  transition: background-color 0.12s ease-out;

  &:hover {
    background: var(--el-fill-color-light);
  }

  // P2 修复：焦点指示器对比度 >= 3:1（用 primary 深色 outline，不移除默认 outline）
  &:focus-visible {
    outline: 2px solid var(--el-color-primary-dark-2);
    outline-offset: -1px;
    background: var(--el-fill-color-light);
  }

  &.readonly {
    cursor: not-allowed;
  }

  &.expanded {
    background: var(--el-color-primary-light-9);
    box-shadow: inset 0 0 0 1px var(--el-color-primary-light-5);
  }

  // T-FE-032 批量选中：蓝色描边 + 背景区分（与 expanded 可共存）
  &.selected {
    background: var(--el-color-primary-light-8);
    box-shadow: inset 0 0 0 2px var(--el-color-primary);
  }
}

// P2 修复：WCAG AA -- 文字/符号用高对比正文色，状态色仅用于背景
.cell-mark {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  padding: 1px 6px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color);
  border-radius: 3px;

  &.tag-success {
    background: var(--el-color-success-light-9);
  }

  &.tag-warning {
    background: var(--el-color-warning-light-9);
  }

  &.tag-danger {
    background: var(--el-color-danger-light-9);
  }

  &.tag-primary {
    background: var(--el-color-primary-light-9);
  }

  &.tag-info {
    background: var(--el-color-info-light-9);
  }
}

.mark-symbol {
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-text-color-primary);
}

.mark-label {
  font-size: 11px;
  color: var(--el-text-color-regular);
}

.overflow-badge {
  padding: 0 4px;
  font-size: 11px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color);
  border-radius: 8px;
}

.popover-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.popover-line {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.cell-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--el-index-popper);
}

.cell-menu {
  position: fixed;
  display: flex;
  flex-direction: column;
  min-width: 152px;
  padding: 4px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
}

.menu-item {
  padding: 6px 12px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: var(--el-border-radius-small);

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }

  &:focus-visible {
    color: var(--el-color-primary);
    outline: 2px solid var(--el-color-primary-dark-2);
    outline-offset: -1px;
    background: var(--el-fill-color-light);
  }
}
</style>
