<script setup lang="ts">
import {
  ref,
  computed,
  inject,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick
} from "vue";
import { usePermissionGrantV2 } from "../utils/hook";
import { type PermCellKey } from "@/utils/permission-grant-types";
import {
  type ResourceTreeNode,
  type OperationItem,
  type GrantScopeMode
} from "@/api/permission-grant";
import { message } from "@/utils/message";
import { permCellKeyStr } from "../utils/grant-variant";
import { type CellDisplay } from "../utils/cell-summary";
import { type GrantVariantId } from "../utils/v2-types";
import MatrixCell from "./MatrixCell.vue";
import BranchListPanel from "./BranchListPanel.vue";
import BatchConfigDrawer from "./BatchConfigDrawer.vue";
import {
  useDragSelect,
  cellsInBox,
  rectsIntersect,
  type DragBox,
  type CellRect
} from "../utils/drag-select";

defineOptions({ name: "PermissionMatrixPanelV2" });

type Store = ReturnType<typeof usePermissionGrantV2>;
const store = inject<Store>("pgV2Store")!;

const keyword = ref("");

// ========== 资源行稳定键：codeType|resourceCode（P1 修复：避免跨 codeType 碰撞） ==========

function rowNodeKey(node: ResourceTreeNode): string {
  return `${node.codeType}|${node.resourceCode}`;
}

interface FlatRow {
  node: ResourceTreeNode;
  depth: number;
  key: string;
}

// ========== 展开状态（P1 修复：资源树表可展开/收起） ==========

/** 展开的节点复合键集合；默认展开根节点 */
const expandedRowKeys = ref<Set<string>>(new Set());

function collectAllKeys(nodes: ResourceTreeNode[]): Set<string> {
  const keys = new Set<string>();
  function walk(ns: ResourceTreeNode[]) {
    for (const n of ns) {
      keys.add(rowNodeKey(n));
      if (n.children?.length) walk(n.children);
    }
  }
  walk(nodes);
  return keys;
}

/** resourceTree 变化时默认展开根节点 */
watch(
  () => store.resourceTree.value,
  nodes => {
    const keys = new Set<string>();
    for (const n of nodes) keys.add(rowNodeKey(n));
    expandedRowKeys.value = keys;
  },
  { immediate: true }
);

function isRowExpanded(key: string): boolean {
  return expandedRowKeys.value.has(key);
}

function toggleRowExpand(key: string) {
  const s = new Set(expandedRowKeys.value);
  if (s.has(key)) s.delete(key);
  else s.add(key);
  expandedRowKeys.value = s;
}

function expandAll() {
  expandedRowKeys.value = collectAllKeys(store.resourceTree.value);
}

function collapseAll() {
  expandedRowKeys.value = new Set();
}

/** 按展开状态扁平化（非搜索态） */
function flatten(nodes: ResourceTreeNode[], depth = 0): FlatRow[] {
  const rows: FlatRow[] = [];
  for (const n of nodes) {
    const key = rowNodeKey(n);
    rows.push({ node: n, depth, key });
    if (n.children?.length && expandedRowKeys.value.has(key)) {
      rows.push(...flatten(n.children, depth + 1));
    }
  }
  return rows;
}

const flatRows = computed<FlatRow[]>(() => flatten(store.resourceTree.value));

/** 搜索过滤：匹配节点 + 祖先链（用复合键），搜索态强制展开命中链 */
const filteredRows = computed<FlatRow[]>(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return flatRows.value;
  const matchSet = new Set<string>();
  const ancestorMap = new Map<string, string>();
  function walk(nodes: ResourceTreeNode[], parentKey: string | null) {
    for (const n of nodes) {
      const composite = rowNodeKey(n);
      if (parentKey) ancestorMap.set(composite, parentKey);
      const matched =
        n.resourceName.toLowerCase().includes(kw) ||
        n.resourceCode.toLowerCase().includes(kw);
      if (matched) {
        matchSet.add(composite);
        let p = parentKey;
        while (p) {
          matchSet.add(p);
          p = ancestorMap.get(p) ?? null;
        }
      }
      if (n.children?.length) walk(n.children, composite);
    }
  }
  walk(store.resourceTree.value, null);
  // 搜索态：只渲染命中链节点（matchSet 已含祖先），强制展开
  function flattenSearch(nodes: ResourceTreeNode[], depth = 0): FlatRow[] {
    const rows: FlatRow[] = [];
    for (const n of nodes) {
      const key = rowNodeKey(n);
      if (matchSet.has(key)) {
        rows.push({ node: n, depth, key });
        if (n.children?.length) {
          rows.push(...flattenSearch(n.children, depth + 1));
        }
      }
    }
    return rows;
  }
  return flattenSearch(store.resourceTree.value);
});

const isSearching = computed(() => keyword.value.trim().length > 0);

const operations = computed<OperationItem[]>(() => store.operationList.value);

const showAllRow = computed(
  () => store.currentResourceType.value?.supportsAll === true
);

const supportsCondition = computed(
  () => store.currentResourceType.value?.supportsCondition === true
);
const supportsDelegation = computed(
  () => store.currentResourceType.value?.supportsDelegation === true
);

// ========== 单元格构造与解析 ==========

function makeCell(
  scopeMode: GrantScopeMode,
  resourceCode: string | null,
  codeType: string | null,
  operationCode: string
): PermCellKey {
  return {
    domainCode: store.currentDomainCode.value,
    resourceTypeCode: store.currentResourceTypeCode.value,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  };
}

function resolveDisplay(cell: PermCellKey): CellDisplay {
  return store.resolveCell(cell);
}

// ========== 展开行定位（复合键） ==========

interface CellEntry {
  cell: PermCellKey;
  resourceName: string | null;
  rowKey: string;
}

const allCellEntries = computed<CellEntry[]>(() => {
  const entries: CellEntry[] = [];
  if (showAllRow.value) {
    for (const op of operations.value) {
      entries.push({
        cell: makeCell("ALL", null, null, op.operationCode),
        resourceName: "全部",
        rowKey: "__ALL__"
      });
    }
  }
  // P1 修复：遍历完整资源树（不受折叠/搜索影响），确保任意 cell 可解析展开定位
  function walk(nodes: ResourceTreeNode[]) {
    for (const n of nodes) {
      for (const op of operations.value) {
        entries.push({
          cell: makeCell(
            "INSTANCE",
            n.resourceCode,
            n.codeType,
            op.operationCode
          ),
          resourceName: n.resourceName,
          rowKey: rowNodeKey(n)
        });
      }
      if (n.children?.length) walk(n.children);
    }
  }
  walk(store.resourceTree.value);
  return entries;
});

const expandedEntry = computed<CellEntry | null>(() => {
  const key = store.expandedCell.value;
  if (!key) return null;
  return allCellEntries.value.find(e => permCellKeyStr(e.cell) === key) ?? null;
});

const expandedDisplay = computed<CellDisplay | null>(() =>
  expandedEntry.value ? store.resolveCell(expandedEntry.value.cell) : null
);

function isExpandedInAllRow(): boolean {
  return expandedEntry.value?.rowKey === "__ALL__";
}

function isExpandedInNode(row: FlatRow): boolean {
  return expandedEntry.value?.rowKey === row.key;
}

// ========== 事件处理 ==========

function onMainClick(cell: PermCellKey, resourceName: string | null) {
  // 拖拽框选释放后抑制 click（拖拽不触发单元格切换）
  if (drag.isJustDragged()) return;
  store.handleMainClick(cell, resourceName);
}

function onMenuAction(
  action: "grant-config" | "edit-branches" | "locate-source",
  cell: PermCellKey,
  resourceName: string | null
) {
  if (action === "grant-config") {
    // P2 修复：redundant 候选不直接 grantUnconditional（会 redundantSkipped），
    // 展开列表走 R11 决策（跳过/仍创建）
    if (!store.redundantCandidate(cell)) {
      store.grantUnconditional(cell, resourceName);
    }
    if (store.expandedCell.value !== permCellKeyStr(cell))
      store.toggleExpand(cell);
  } else if (action === "edit-branches") {
    if (store.expandedCell.value !== permCellKeyStr(cell))
      store.toggleExpand(cell);
  } else if (action === "locate-source") {
    onLocateSource(cell);
  }
}

/** P2 修复：ALL_COVERED 来源本地可定位；仅 INHERITED/DERIVED 依赖 T-PERM-034 */
function onLocateSource(cell: PermCellKey) {
  const display = store.resolveCell(cell);
  if (display.allCovered && cell.scopeMode === "INSTANCE") {
    const allCell = makeCell("ALL", null, null, cell.operationCode);
    const allKey = permCellKeyStr(allCell);
    nextTick(() => {
      const el = findCellEl(allKey);
      const reduceMotion = window.matchMedia(
        "(prefers-reduced-motion: reduce)"
      ).matches;
      el?.scrollIntoView({
        block: "center",
        behavior: reduceMotion ? "auto" : "smooth"
      });
      el?.focus();
    });
    return;
  }
  message("来源定位依赖 T-PERM-034（操作继承/派生精确建模），当前不可达", {
    type: "info"
  });
}

function onAddBranch(payload: {
  conditionCode: string | null;
  canGrant: boolean;
  keepDirect: boolean;
}) {
  const entry = expandedEntry.value;
  if (!entry) return;
  const r = store.addBranch(
    entry.cell,
    payload.conditionCode,
    payload.canGrant,
    entry.resourceName,
    payload.keepDirect
  );
  if (!r.ok) message(r.reason ?? "添加分支失败", { type: "warning" });
}

function onEditBranch(payload: {
  variantId: GrantVariantId;
  conditionCode: string | null;
  canGrant: boolean;
}) {
  const entry = expandedEntry.value;
  if (!entry) return;
  const r = store.updateVariant(
    payload.variantId,
    payload.conditionCode,
    payload.canGrant,
    entry.cell
  );
  if (!r.ok) message(r.reason ?? "编辑失败", { type: "warning" });
}

function onRevokeBranch(variantId: GrantVariantId) {
  const entry = expandedEntry.value;
  if (!entry) return;
  store.removeVariant(variantId, entry.cell);
}

function onRestoreBranch(variantId: GrantVariantId) {
  const r = store.restoreVariant(variantId);
  if (!r.ok) message(r.reason ?? "恢复失败", { type: "warning" });
}

function onRestoreModify(variantId: GrantVariantId) {
  const r = store.restoreModify(variantId);
  if (!r.ok) message(r.reason ?? "恢复失败", { type: "warning" });
}

function onClose() {
  store.collapseExpanded();
}

async function onSwitchType(code: string) {
  clearSelection();
  store.collapseExpanded();
  await store.switchResourceType(code);
}

// ========== ESC 收起 + 焦点返回（dataset 精确比较，避免非法 selector） ==========

function findCellEl(key: string): HTMLElement | null {
  const els = document.querySelectorAll<HTMLElement>("[data-cellkey]");
  return Array.from(els).find(el => el.dataset.cellkey === key) ?? null;
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  // 批量配置侧拉打开时 ESC 交由 Drawer 关闭，不清选择
  if (batchDrawerVisible.value) return;
  // T-FE-032：有批量选择时 ESC 优先清选择
  if (selectedCells.value.size > 0) {
    clearSelection();
    return;
  }
  if (!store.expandedCell.value) return;
  const prevKey = store.expandedCell.value;
  store.collapseExpanded();
  nextTick(() => {
    findCellEl(prevKey)?.focus();
  });
}

onMounted(() => {
  window.addEventListener("keydown", onKeydown);
});
onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  drag.dispose();
});

// ========== grid 列样式 ==========

const gridStyle = computed(() => ({
  gridTemplateColumns: `minmax(160px, 220px) repeat(${operations.value.length}, minmax(72px, 1fr))`
}));

const noMatrix = computed(
  () =>
    !store.currentRole.value ||
    store.loadingContext.value ||
    store.loadingMatrix.value ||
    operations.value.length === 0
);

const noMatrixDescription = computed(() => {
  if (store.loadingContext.value || store.loadingMatrix.value) return "加载中…";
  if (!store.currentRole.value) return "请选择角色";
  return "该资源类型无操作";
});

const hasTree = computed(() => store.resourceTree.value.length > 0);

// ========== T-FE-032 批量多选 ==========

/** 选中集：PermCellKeyStr -> PermCellKey（Map 便于 O(1) toggle + 反查 cell 列表） */
const selectedCells = ref<Map<string, PermCellKey>>(new Map());
/** Shift 矩形选择锚点（行列索引） */
const selectionAnchor = ref<{ rowIdx: number; colIdx: number } | null>(null);

const selectedCount = computed(() => selectedCells.value.size);
const selectedCellList = computed<PermCellKey[]>(() =>
  Array.from(selectedCells.value.values())
);

function isSelected(key: string): boolean {
  return selectedCells.value.has(key);
}

function clearSelection(): void {
  selectedCells.value = new Map();
  selectionAnchor.value = null;
}

// ========== T-FE-032 拖拽框选（Q2 收敛：仅桌面、仅当前可视矩阵、不自动滚动） ==========

/** 拖拽命中缓存（mousedown 时填充可视可选 cell 的 rect） */
const matrixBodyRef = ref<HTMLElement | null>(null);
const matrixGridRef = ref<HTMLElement | null>(null);
let dragCellCache: CellRect[] = [];

const drag = useDragSelect({
  getCellsInBox: (box: DragBox) => cellsInBox(box, dragCellCache),
  onSelect: (cells: PermCellKey[]) => {
    // 拖拽框选替换选择集（仅可选 cell，已由缓存构建时过滤）
    const s = new Map<string, PermCellKey>();
    for (const c of cells) s.set(permCellKeyStr(c), c);
    selectedCells.value = s;
  },
  enabled: () => !store.readonly.value
});

/**
 * mousedown：填充可视可选 cell 缓存 + 启动拖拽（P2 修复：一次 querySelectorAll +
 * 滚动视口 rect 过滤，仅当前可视矩阵，符合 Q2 收敛）。
 */
function onMatrixMouseDown(e: MouseEvent) {
  dragCellCache = [];
  const gridEl = matrixGridRef.value;
  const bodyEl = matrixBodyRef.value;
  if (!gridEl) return;
  // 一次遍历 allCellEntries 建 key->cell 映射（避免每格 findCellEl 全局 querySelectorAll）
  const cellMap = new Map<string, PermCellKey>();
  for (const entry of allCellEntries.value) {
    cellMap.set(permCellKeyStr(entry.cell), entry.cell);
  }
  const viewRect = bodyEl?.getBoundingClientRect();
  const els = gridEl.querySelectorAll<HTMLElement>("[data-cellkey]");
  for (const el of Array.from(els)) {
    const key = el.dataset.cellkey;
    if (!key) continue;
    const cell = cellMap.get(key);
    if (!cell || !isSelectable(cell)) continue;
    const r = el.getBoundingClientRect();
    // 仅保留与滚动视口相交的 cell（可视区）
    if (
      viewRect &&
      !rectsIntersect(
        {
          startX: viewRect.left,
          startY: viewRect.top,
          endX: viewRect.right,
          endY: viewRect.bottom
        },
        { left: r.left, right: r.right, top: r.top, bottom: r.bottom }
      )
    )
      continue;
    dragCellCache.push({
      cell,
      rect: { left: r.left, right: r.right, top: r.top, bottom: r.bottom }
    });
  }
  drag.onDragStart(e);
}

/** 选框浮层样式（视口坐标，position: fixed） */
const boxStyle = computed(() => {
  const b = drag.selectionBox.value;
  if (!b) return {};
  return {
    left: `${Math.min(b.startX, b.endX)}px`,
    top: `${Math.min(b.startY, b.endY)}px`,
    width: `${Math.abs(b.endX - b.startX)}px`,
    height: `${Math.abs(b.endY - b.startY)}px`
  } as Record<string, string>;
});

/** 选框 ref（解构供模板自动 unwrap） */
const dragSelectionBox = drag.selectionBox;

/**
 * 可选判定（acceptance ⑦：不可授予/派生/ALL 行不可选）。
 * - ALL 行 scopeMode=ALL 不可选
 * - DERIVED/INHERITED 只读不可选（当前不可达，前瞻保留）
 * - 不可授予（grantableByOperator=false）不可选；其既有权限须逐分支撤销（canGrant 不约束回收）
 */
function isSelectable(cell: PermCellKey): boolean {
  if (cell.scopeMode === "ALL") return false;
  const display = store.resolveCell(cell);
  if (
    display.summary.effective === "DERIVED" ||
    display.summary.effective === "INHERITED"
  )
    return false;
  return display.grantableByOperator;
}

/** cell -> 当前行列索引（基于可见 filteredRows；ALL 行 rowIdx=0 若 showAllRow） */
function findRowCol(cell: PermCellKey): {
  rowIdx: number;
  colIdx: number;
} | null {
  const colIdx = operations.value.findIndex(
    op => op.operationCode === cell.operationCode
  );
  if (colIdx < 0) return null;
  const offset = showAllRow.value ? 1 : 0;
  if (cell.scopeMode === "ALL") {
    return showAllRow.value ? { rowIdx: 0, colIdx } : null;
  }
  const rowIdx = filteredRows.value.findIndex(
    row =>
      row.node.resourceCode === cell.resourceCode &&
      row.node.codeType === cell.codeType
  );
  if (rowIdx < 0) return null;
  return { rowIdx: rowIdx + offset, colIdx };
}

/** 行列索引 -> cell（ALL 行 rowIdx=0 不可选，但仍可定位） */
function getCellAt(rowIdx: number, colIdx: number): PermCellKey | null {
  const op = operations.value[colIdx];
  if (!op) return null;
  const offset = showAllRow.value ? 1 : 0;
  if (showAllRow.value && rowIdx === 0) {
    return makeCell("ALL", null, null, op.operationCode);
  }
  const row = filteredRows.value[rowIdx - offset];
  if (!row) return null;
  return makeCell(
    "INSTANCE",
    row.node.resourceCode,
    row.node.codeType,
    op.operationCode
  );
}

/**
 * 单元格多选路由（MatrixCell @select）。
 * - Ctrl/Meta+点击 -> toggle 单格 + 设锚点
 * - Shift+点击 -> 锚点到当前格矩形范围内可选单元格全选
 * - 无锚点 Shift -> 降级为 toggle
 */
function onCellSelect(
  cell: PermCellKey,
  payload: { ctrl: boolean; shift: boolean }
): void {
  // 拖拽框选释放后抑制 click（拖拽不触发 Ctrl/Shift 选择）
  if (drag.isJustDragged()) return;
  if (store.readonly.value) return;
  if (!isSelectable(cell)) return;
  const rc = findRowCol(cell);
  if (!rc) return;
  if (payload.shift && selectionAnchor.value) {
    const s = new Map(selectedCells.value);
    const r1 = Math.min(selectionAnchor.value.rowIdx, rc.rowIdx);
    const r2 = Math.max(selectionAnchor.value.rowIdx, rc.rowIdx);
    const c1 = Math.min(selectionAnchor.value.colIdx, rc.colIdx);
    const c2 = Math.max(selectionAnchor.value.colIdx, rc.colIdx);
    for (let r = r1; r <= r2; r++) {
      for (let c = c1; c <= c2; c++) {
        const at = getCellAt(r, c);
        if (at && isSelectable(at)) s.set(permCellKeyStr(at), at);
      }
    }
    selectedCells.value = s;
    return;
  }
  const key = permCellKeyStr(cell);
  const s = new Map(selectedCells.value);
  if (s.has(key)) s.delete(key);
  else s.set(key, cell);
  selectedCells.value = s;
  selectionAnchor.value = rc;
}

/** 行头 Ctrl+点击 / Enter：整行可选单元格 toggle（全选则移除，否则全选） */
function toggleRowSelection(row: FlatRow): void {
  if (store.readonly.value) return;
  const s = new Map(selectedCells.value);
  const cells: PermCellKey[] = [];
  for (const op of operations.value) {
    const cell = makeCell(
      "INSTANCE",
      row.node.resourceCode,
      row.node.codeType,
      op.operationCode
    );
    if (isSelectable(cell)) cells.push(cell);
  }
  const allSelected = cells.every(c => s.has(permCellKeyStr(c)));
  if (allSelected) cells.forEach(c => s.delete(permCellKeyStr(c)));
  else cells.forEach(c => s.set(permCellKeyStr(c), c));
  selectedCells.value = s;
}

/** 列头 Ctrl+点击 / Enter：整列可选单元格 toggle */
function toggleColSelection(op: OperationItem): void {
  if (store.readonly.value) return;
  const s = new Map(selectedCells.value);
  const cells: PermCellKey[] = [];
  for (const row of filteredRows.value) {
    const cell = makeCell(
      "INSTANCE",
      row.node.resourceCode,
      row.node.codeType,
      op.operationCode
    );
    if (isSelectable(cell)) cells.push(cell);
  }
  const allSelected = cells.every(c => s.has(permCellKeyStr(c)));
  if (allSelected) cells.forEach(c => s.delete(permCellKeyStr(c)));
  else cells.forEach(c => s.set(permCellKeyStr(c), c));
  selectedCells.value = s;
}

function onRowHeaderClick(row: FlatRow, e: MouseEvent): void {
  if (!(e.ctrlKey || e.metaKey)) return;
  e.preventDefault();
  toggleRowSelection(row);
}

function onColHeaderClick(op: OperationItem, e: MouseEvent): void {
  if (!(e.ctrlKey || e.metaKey)) return;
  e.preventDefault();
  toggleColSelection(op);
}

// 切资源类型/角色时清空选择（选中是当前资源类型下单元格）
watch(
  () => store.currentResourceTypeCode.value,
  () => {
    clearSelection();
  }
);

// ========== 批量工具栏动作 ==========

const batchDrawerVisible = ref(false);
const batchPresetCanGrant = ref(false);

function onBatchGrantAll(): void {
  const r = store.batchGrantAll(selectedCellList.value);
  let msg = `已授予 ${r.granted} 项`;
  if (r.redundantCandidates > 0)
    msg += `，${r.redundantCandidates} 项被 ALL 覆盖已跳过`;
  message(msg, { type: r.granted > 0 ? "success" : "info" });
}

function onBatchRemove(): void {
  const r = store.batchRemove(selectedCellList.value);
  message(`已撤销 ${r.removed} 项`, {
    type: r.removed > 0 ? "success" : "info"
  });
}

function openBatchConfig(presetCanGrant: boolean): void {
  batchPresetCanGrant.value = presetCanGrant;
  batchDrawerVisible.value = true;
}

function onBatchConfirm(payload: {
  conditionCode: string | null;
  canGrant: boolean;
  keepDirect: boolean;
}): void {
  const r = store.batchAddBranch(
    selectedCellList.value,
    payload.conditionCode,
    payload.canGrant,
    payload.keepDirect
  );
  let msg = `已新增 ${r.added} 条分支`;
  if (r.redundantSkipped > 0)
    msg += `，${r.redundantSkipped} 项被 ALL 覆盖已跳过`;
  if (r.failures.length > 0)
    msg += `，${r.failures.length} 项被跳过（重复或不可新增）`;
  message(msg, { type: r.added > 0 ? "success" : "warning" });
  batchDrawerVisible.value = false;
}
</script>

<template>
  <div class="matrix-panel">
    <div class="panel-header">
      <span class="panel-title">权限矩阵</span>
      <el-select
        v-if="store.resourceTypes.value.length > 0"
        :model-value="store.currentResourceTypeCode.value"
        size="small"
        class="type-select"
        @change="onSwitchType"
      >
        <el-option
          v-for="t in store.resourceTypes.value"
          :key="t.resourceTypeCode"
          :label="t.resourceTypeName"
          :value="t.resourceTypeCode"
        />
      </el-select>
      <template v-if="hasTree && !isSearching">
        <el-button size="small" link @click="expandAll">展开全部</el-button>
        <el-button size="small" link @click="collapseAll">收起全部</el-button>
      </template>
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索资源"
        clearable
        class="search-input"
      />
    </div>

    <!-- T-FE-032 批量工具栏（选中>=1 浮出，sticky 锚定矩阵顶部） -->
    <div v-if="selectedCount > 0" class="batch-toolbar">
      <span class="batch-count">已选 {{ selectedCount }} 个</span>
      <el-button size="small" type="primary" @click="onBatchGrantAll">
        授予全部
      </el-button>
      <el-button size="small" type="danger" plain @click="onBatchRemove">
        撤销全部
      </el-button>
      <el-button size="small" @click="openBatchConfig(false)">
        设置条件…
      </el-button>
      <el-button size="small" @click="openBatchConfig(true)">
        设置转授权…
      </el-button>
      <el-button size="small" link @click="clearSelection">
        清除选择
      </el-button>
    </div>

    <div ref="matrixBodyRef" class="matrix-body">
      <el-empty
        v-if="noMatrix"
        :description="noMatrixDescription"
        :image-size="80"
      />
      <el-scrollbar v-else>
        <div
          ref="matrixGridRef"
          class="matrix-grid"
          :style="gridStyle"
          @mousedown="onMatrixMouseDown"
        >
          <!-- 表头 -->
          <div class="cell-header col-resource">资源</div>
          <div
            v-for="op in operations"
            :key="op.operationCode"
            class="cell-header col-op-header"
            role="button"
            tabindex="0"
            :title="`Ctrl+点击选择整列（${op.operationName}）`"
            @click="onColHeaderClick(op, $event)"
            @keydown.enter.prevent="toggleColSelection(op)"
            @keydown.space.prevent="toggleColSelection(op)"
          >
            {{ op.operationName }}
          </div>

          <!-- ALL 行 -->
          <template v-if="showAllRow">
            <div class="cell-resource all-row-label">全部 [ALL]</div>
            <div
              v-for="op in operations"
              :key="op.operationCode"
              class="cell-wrap"
            >
              <MatrixCell
                :display="
                  resolveDisplay(makeCell('ALL', null, null, op.operationCode))
                "
                :readonly="store.readonly.value"
                :selected="
                  isSelected(
                    permCellKeyStr(
                      makeCell('ALL', null, null, op.operationCode)
                    )
                  )
                "
                @main-click="
                  onMainClick(
                    makeCell('ALL', null, null, op.operationCode),
                    '全部'
                  )
                "
                @menu-action="
                  (a: 'grant-config' | 'edit-branches' | 'locate-source') =>
                    onMenuAction(
                      a,
                      makeCell('ALL', null, null, op.operationCode),
                      '全部'
                    )
                "
                @select="
                  (p: { ctrl: boolean; shift: boolean }) =>
                    onCellSelect(
                      makeCell('ALL', null, null, op.operationCode),
                      p
                    )
                "
              />
            </div>
            <div
              v-if="isExpandedInAllRow() && expandedDisplay"
              class="expand-row"
            >
              <BranchListPanel
                :key="expandedDisplay.cellKeyStr"
                :display="expandedDisplay"
                :supports-condition="supportsCondition"
                :supports-delegation="supportsDelegation"
                :condition-options="store.conditionOptions.value"
                @add-branch="onAddBranch"
                @edit-branch="onEditBranch"
                @revoke-branch="onRevokeBranch"
                @restore-branch="onRestoreBranch"
                @restore-modify="onRestoreModify"
                @close="onClose"
              />
            </div>
          </template>

          <!-- 资源行 -->
          <template v-for="row in filteredRows" :key="row.key">
            <div
              class="cell-resource"
              :style="{ paddingLeft: `${row.depth * 16 + 8}px` }"
              :title="`${row.node.resourceName}（Ctrl+点击选择整行）`"
              role="button"
              tabindex="0"
              @click="onRowHeaderClick(row, $event)"
              @keydown.enter.prevent="toggleRowSelection(row)"
              @keydown.space.prevent="toggleRowSelection(row)"
            >
              <span
                v-if="row.node.children?.length && !isSearching"
                class="expand-toggle"
                role="button"
                tabindex="0"
                :aria-label="isRowExpanded(row.key) ? '收起' : '展开'"
                @click.stop="toggleRowExpand(row.key)"
                @keydown.enter.prevent="toggleRowExpand(row.key)"
                @keydown.space.prevent="toggleRowExpand(row.key)"
              >
                {{ isRowExpanded(row.key) ? "▾" : "▸" }}
              </span>
              <span v-else class="expand-placeholder" aria-hidden="true" />
              {{ row.node.resourceName }}
            </div>
            <div
              v-for="op in operations"
              :key="op.operationCode"
              class="cell-wrap"
            >
              <MatrixCell
                :display="
                  resolveDisplay(
                    makeCell(
                      'INSTANCE',
                      row.node.resourceCode,
                      row.node.codeType,
                      op.operationCode
                    )
                  )
                "
                :readonly="store.readonly.value"
                :has-children="
                  store.hasChildren(
                    makeCell(
                      'INSTANCE',
                      row.node.resourceCode,
                      row.node.codeType,
                      op.operationCode
                    )
                  )
                "
                :selected="
                  isSelected(
                    permCellKeyStr(
                      makeCell(
                        'INSTANCE',
                        row.node.resourceCode,
                        row.node.codeType,
                        op.operationCode
                      )
                    )
                  )
                "
                @main-click="
                  onMainClick(
                    makeCell(
                      'INSTANCE',
                      row.node.resourceCode,
                      row.node.codeType,
                      op.operationCode
                    ),
                    row.node.resourceName
                  )
                "
                @menu-action="
                  (a: 'grant-config' | 'edit-branches' | 'locate-source') =>
                    onMenuAction(
                      a,
                      makeCell(
                        'INSTANCE',
                        row.node.resourceCode,
                        row.node.codeType,
                        op.operationCode
                      ),
                      row.node.resourceName
                    )
                "
                @select="
                  (p: { ctrl: boolean; shift: boolean }) =>
                    onCellSelect(
                      makeCell(
                        'INSTANCE',
                        row.node.resourceCode,
                        row.node.codeType,
                        op.operationCode
                      ),
                      p
                    )
                "
              />
            </div>
            <div
              v-if="isExpandedInNode(row) && expandedDisplay"
              class="expand-row"
            >
              <BranchListPanel
                :key="expandedDisplay.cellKeyStr"
                :display="expandedDisplay"
                :supports-condition="supportsCondition"
                :supports-delegation="supportsDelegation"
                :condition-options="store.conditionOptions.value"
                @add-branch="onAddBranch"
                @edit-branch="onEditBranch"
                @revoke-branch="onRevokeBranch"
                @restore-branch="onRestoreBranch"
                @restore-modify="onRestoreModify"
                @close="onClose"
              />
            </div>
          </template>
        </div>
      </el-scrollbar>
    </div>

    <!-- T-FE-032 批量配置侧拉 -->
    <BatchConfigDrawer
      v-model:visible="batchDrawerVisible"
      :cells="selectedCellList"
      :preset-can-grant="batchPresetCanGrant"
      :supports-condition="supportsCondition"
      :supports-delegation="supportsDelegation"
      :condition-options="store.conditionOptions.value"
      @confirm="onBatchConfirm"
    />

    <!-- T-FE-032 拖拽选框浮层（视口坐标，Teleport 至 body） -->
    <Teleport to="body">
      <div
        v-if="dragSelectionBox"
        class="drag-selection-box"
        :style="boxStyle"
      />
    </Teleport>
  </div>
</template>

<style lang="scss" scoped>
@media (prefers-reduced-motion: reduce) {
  .matrix-grid * {
    transition: none !important;
  }
}

.matrix-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.panel-header {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.type-select {
  width: 140px;
}

.search-input {
  width: 180px;
  margin-left: auto;
}

.matrix-body {
  flex: 1;
  min-height: 0;
}

.matrix-grid {
  display: grid;
  min-width: max-content;
}

.cell-header,
.cell-resource,
.cell-wrap {
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.cell-header {
  position: sticky;
  top: 0;
  z-index: 1;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.col-resource {
  position: sticky;
  left: 0;
  z-index: 2;
  justify-content: flex-start;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.cell-resource {
  justify-content: flex-start;
  font-size: 13px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);

  &[role="button"] {
    cursor: pointer;

    &:hover {
      background: var(--el-fill-color-light);
    }

    &:focus-visible {
      outline: 2px solid var(--el-color-primary-dark-2);
      outline-offset: -1px;
    }
  }
}

.all-row-label {
  font-weight: 600;
  color: var(--el-color-primary);
}

.expand-toggle {
  display: inline-flex;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-right: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 1px;
  }
}

.expand-placeholder {
  display: inline-block;
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-right: 4px;
}

.cell-wrap {
  justify-content: center;
}

.expand-row {
  grid-column: 1 / -1;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

// T-FE-032 列头可交互（Ctrl+点击选整列）
.col-op-header {
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary-dark-2);
    outline-offset: -1px;
  }
}

// T-FE-032 批量工具栏
.batch-toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  padding: var(--space-1) var(--space-3);
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-7);
}

.batch-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
}

// T-FE-032 拖拽选框浮层（pointer-events:none 避免拦截 mousemove）
.drag-selection-box {
  position: fixed;
  z-index: var(--el-index-popper);
  pointer-events: none;
  background: var(--el-color-primary-light-8);
  border: 1px solid var(--el-color-primary);
}
</style>
