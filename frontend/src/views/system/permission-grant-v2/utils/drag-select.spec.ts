import { describe, it, expect, vi } from "vitest";
import {
  rectsIntersect,
  cellsInBox,
  useDragSelect,
  type CellRect,
  type EventSink
} from "./drag-select";
import type { PermCellKey } from "@/utils/permission-grant-types";

function mockEvent(overrides: Partial<MouseEvent> = {}): MouseEvent {
  return {
    button: 0,
    clientX: 0,
    clientY: 0,
    target: { closest: () => null } as unknown as HTMLElement,
    preventDefault: () => {},
    ...overrides
  } as unknown as MouseEvent;
}

const cell = (code: string): PermCellKey => ({
  domainCode: "HR",
  resourceTypeCode: "MENU",
  scopeMode: "INSTANCE",
  resourceCode: code,
  codeType: "MENU",
  operationCode: "VIEW"
});

function mockWin(): EventSink {
  return {
    addEventListener: vi.fn(),
    removeEventListener: vi.fn()
  } as unknown as EventSink;
}

describe("drag-select 纯函数", () => {
  it("rectsIntersect: 相交", () => {
    expect(
      rectsIntersect(
        { startX: 0, startY: 0, endX: 10, endY: 10 },
        { left: 5, right: 15, top: 5, bottom: 15 }
      )
    ).toBe(true);
  });

  it("rectsIntersect: 不相交", () => {
    expect(
      rectsIntersect(
        { startX: 0, startY: 0, endX: 10, endY: 10 },
        { left: 20, right: 30, top: 20, bottom: 30 }
      )
    ).toBe(false);
  });

  it("rectsIntersect: 反向拖拽（end<start）也命中", () => {
    expect(
      rectsIntersect(
        { startX: 15, startY: 15, endX: 0, endY: 0 },
        { left: 3, right: 8, top: 3, bottom: 8 }
      )
    ).toBe(true);
  });

  it("cellsInBox: 命中框内 cell，排除框外", () => {
    const cells: CellRect[] = [
      { cell: cell("a"), rect: { left: 0, right: 10, top: 0, bottom: 10 } },
      { cell: cell("b"), rect: { left: 20, right: 30, top: 20, bottom: 30 } }
    ];
    const r = cellsInBox({ startX: 0, startY: 0, endX: 15, endY: 15 }, cells);
    expect(r.length).toBe(1);
    expect(r[0].resourceCode).toBe("a");
  });
});

describe("useDragSelect 状态机", () => {
  it("超阈值启动 + 实时选中 + 释放清理（监听注册与移除）", () => {
    const win = mockWin();
    const hitCells = [cell("a")];
    const getCellsInBox = vi.fn(() => hitCells);
    const onSelect = vi.fn();
    const ds = useDragSelect(
      { getCellsInBox, onSelect, enabled: () => true },
      win
    );

    ds.onDragStart(mockEvent({ clientX: 0, clientY: 0 }));
    expect(win.addEventListener).toHaveBeenCalledTimes(2);
    // 未超阈值：不启动
    ds.onDragMove(mockEvent({ clientX: 2, clientY: 2 }));
    expect(ds.isDragging.value).toBe(false);
    // 超阈值：启动 + 实时选中
    ds.onDragMove(mockEvent({ clientX: 100, clientY: 100 }));
    expect(ds.isDragging.value).toBe(true);
    expect(ds.selectionBox.value).not.toBeNull();
    expect(onSelect).toHaveBeenCalledWith(hitCells);
    // 释放清理：监听移除 + 状态重置 + justDragged
    ds.onDragUp();
    expect(ds.isDragging.value).toBe(false);
    expect(ds.selectionBox.value).toBeNull();
    expect(ds.isJustDragged()).toBe(true);
    expect(win.removeEventListener).toHaveBeenCalledTimes(2);
  });

  it("dispose 显式清理 window 监听", () => {
    const win = mockWin();
    const ds = useDragSelect(
      {
        getCellsInBox: () => [],
        onSelect: () => {},
        enabled: () => true
      },
      win
    );
    ds.onDragStart(mockEvent({ clientX: 0, clientY: 0 }));
    ds.dispose();
    expect(win.removeEventListener).toHaveBeenCalledWith(
      "mousemove",
      expect.any(Function)
    );
    expect(win.removeEventListener).toHaveBeenCalledWith(
      "mouseup",
      expect.any(Function)
    );
  });

  it("enabled=false 不启动（只读矩阵）", () => {
    const win = mockWin();
    const ds = useDragSelect(
      {
        getCellsInBox: () => [],
        onSelect: () => {},
        enabled: () => false
      },
      win
    );
    ds.onDragStart(mockEvent({ clientX: 0, clientY: 0 }));
    ds.onDragMove(mockEvent({ clientX: 100, clientY: 100 }));
    expect(ds.isDragging.value).toBe(false);
  });

  it("非左键不启动", () => {
    const win = mockWin();
    const ds = useDragSelect(
      {
        getCellsInBox: () => [],
        onSelect: () => {},
        enabled: () => true
      },
      win
    );
    ds.onDragStart(mockEvent({ button: 2, clientX: 0, clientY: 0 }));
    ds.onDragMove(mockEvent({ clientX: 100, clientY: 100 }));
    expect(ds.isDragging.value).toBe(false);
  });

  it("未超阈值释放不设 justDragged（不抑制后续 click）", () => {
    const win = mockWin();
    const ds = useDragSelect(
      {
        getCellsInBox: () => [],
        onSelect: () => {},
        enabled: () => true
      },
      win
    );
    ds.onDragStart(mockEvent({ clientX: 0, clientY: 0 }));
    ds.onDragMove(mockEvent({ clientX: 2, clientY: 2 }));
    ds.onDragUp();
    expect(ds.isJustDragged()).toBe(false);
  });
});
