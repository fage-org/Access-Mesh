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
  store.handleMainClick(cell, resourceName);
}

function onMenuAction(
  action: "grant-config" | "edit-branches" | "locate-source",
  cell: PermCellKey,
  resourceName: string | null
) {
  if (action === "grant-config") {
    store.grantUnconditional(cell, resourceName);
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
}) {
  const entry = expandedEntry.value;
  if (!entry) return;
  const r = store.addBranch(
    entry.cell,
    payload.conditionCode,
    payload.canGrant,
    entry.resourceName
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
  store.updateVariant(
    payload.variantId,
    payload.conditionCode,
    payload.canGrant,
    entry.cell
  );
}

function onRevokeBranch(variantId: GrantVariantId) {
  const entry = expandedEntry.value;
  if (!entry) return;
  store.removeVariant(variantId, entry.cell);
}

function onRestoreBranch(variantId: GrantVariantId) {
  store.restoreVariant(variantId);
}

function onRestoreModify(variantId: GrantVariantId) {
  store.restoreModify(variantId);
}

function onClose() {
  store.collapseExpanded();
}

async function onSwitchType(code: string) {
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

    <div class="matrix-body">
      <el-empty
        v-if="noMatrix"
        :description="noMatrixDescription"
        :image-size="80"
      />
      <el-scrollbar v-else>
        <div class="matrix-grid" :style="gridStyle">
          <!-- 表头 -->
          <div class="cell-header col-resource">资源</div>
          <div
            v-for="op in operations"
            :key="op.operationCode"
            class="cell-header"
            :title="op.operationName"
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
              :title="row.node.resourceName"
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
  </div>
</template>

<style lang="scss" scoped>
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

@media (prefers-reduced-motion: reduce) {
  .matrix-grid * {
    transition: none !important;
  }
}
</style>
