/**
 * T-FE-032 拖拽框选（Q2 收敛：仅桌面、仅当前可视矩阵、不自动滚动）。
 *
 * - rectsIntersect/cellsInBox：纯函数，便于测试矩形命中
 * - useDragSelect：拖拽状态机（mousedown -> 超阈值启动 -> mousemove 实时选中 -> mouseup 释放清理）
 *   - window 监听 mousemove/mouseup（拖拽中鼠标可移出矩阵）
 *   - 超阈值（5px）才启动，避免误触单元格 click
 *   - 释放后 justDragged 抑制紧随的 click 事件（拖拽不触发单元格切换）
 *   - onBeforeUnmount 清理 window 监听，避免泄漏
 *
 * 选择集由调用方维护（onSelect 回调），本模块只管矩形与状态。
 */
import { ref, type Ref } from "vue";
import type { PermCellKey } from "@/utils/permission-grant-types";

export interface DragBox {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface CellRect {
  cell: PermCellKey;
  rect: { left: number; right: number; top: number; bottom: number };
}

const DRAG_THRESHOLD = 5;

/** 选框矩形与单元格 rect 是否相交（支持反向拖拽） */
export function rectsIntersect(
  box: DragBox,
  rect: { left: number; right: number; top: number; bottom: number }
): boolean {
  const left = Math.min(box.startX, box.endX);
  const right = Math.max(box.startX, box.endX);
  const top = Math.min(box.startY, box.endY);
  const bottom = Math.max(box.startY, box.endY);
  return (
    rect.left < right &&
    rect.right > left &&
    rect.top < bottom &&
    rect.bottom > top
  );
}

/** 选框命中的单元格列表 */
export function cellsInBox(box: DragBox, cells: CellRect[]): PermCellKey[] {
  return cells.filter(c => rectsIntersect(box, c.rect)).map(c => c.cell);
}

export interface EventSink {
  addEventListener: (type: string, fn: (e: MouseEvent) => void) => void;
  removeEventListener: (type: string, fn: (e: MouseEvent) => void) => void;
}

const noopSink = (): EventSink => ({
  addEventListener: () => {},
  removeEventListener: () => {}
});

export function useDragSelect(
  opts: {
    getCellsInBox: (box: DragBox) => PermCellKey[];
    onSelect: (cells: PermCellKey[]) => void;
    enabled: () => boolean;
  },
  win: EventSink = typeof window !== "undefined"
    ? (window as unknown as EventSink)
    : noopSink()
): {
  selectionBox: Ref<DragBox | null>;
  isDragging: Ref<boolean>;
  onDragStart: (e: MouseEvent) => void;
  onDragMove: (e: MouseEvent) => void;
  onDragUp: () => void;
  dispose: () => void;
  isJustDragged: () => boolean;
} {
  const selectionBox = ref<DragBox | null>(null);
  const isDragging = ref(false);
  let dragStartPos: { x: number; y: number } | null = null;
  let justDragged = false;
  let moveHandler: ((e: MouseEvent) => void) | null = null;
  let upHandler: (() => void) | null = null;

  function onDragStart(e: MouseEvent) {
    if (e.button !== 0) return;
    if (!opts.enabled()) return;
    const target = e.target as HTMLElement;
    // 表头/行头/展开三角交给各自 click，不启动框选
    if (target.closest(".cell-header")) return;
    if (target.closest(".cell-resource")) return;
    if (target.closest(".expand-toggle")) return;
    // P2 修复：分支列表 expand-row 及交互控件上不启动框选
    if (target.closest(".expand-row")) return;
    if (
      target.closest(
        "button, input, select, textarea, .el-select, .el-switch, .el-collapse"
      )
    )
      return;
    dragStartPos = { x: e.clientX, y: e.clientY };
    moveHandler = onDragMove;
    upHandler = onDragUp;
    win.addEventListener("mousemove", moveHandler);
    win.addEventListener("mouseup", upHandler);
  }

  function onDragMove(e: MouseEvent) {
    if (!dragStartPos) return;
    const dx = Math.abs(e.clientX - dragStartPos.x);
    const dy = Math.abs(e.clientY - dragStartPos.y);
    // 超阈值才启动，避免单击单元格误触框选
    if (!isDragging.value && (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD)) {
      isDragging.value = true;
      selectionBox.value = {
        startX: dragStartPos.x,
        startY: dragStartPos.y,
        endX: dragStartPos.x,
        endY: dragStartPos.y
      };
    }
    if (isDragging.value && selectionBox.value) {
      e.preventDefault();
      selectionBox.value = {
        ...selectionBox.value,
        endX: e.clientX,
        endY: e.clientY
      };
      opts.onSelect(opts.getCellsInBox(selectionBox.value));
    }
  }

  function onDragUp() {
    if (moveHandler) win.removeEventListener("mousemove", moveHandler);
    if (upHandler) win.removeEventListener("mouseup", upHandler);
    moveHandler = null;
    upHandler = null;
    if (isDragging.value) {
      // 抑制紧随 mouseup 的 click 事件（拖拽不触发单元格切换）
      justDragged = true;
      setTimeout(() => {
        justDragged = false;
      }, 0);
    }
    isDragging.value = false;
    selectionBox.value = null;
    dragStartPos = null;
  }

  /** 显式清理 window 监听（组件 onBeforeUnmount 调用；测试断言用） */
  function dispose() {
    if (moveHandler) win.removeEventListener("mousemove", moveHandler);
    if (upHandler) win.removeEventListener("mouseup", upHandler);
    moveHandler = null;
    upHandler = null;
  }

  return {
    selectionBox,
    isDragging,
    onDragStart,
    onDragMove,
    onDragUp,
    dispose,
    isJustDragged: () => justDragged
  };
}
