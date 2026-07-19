<script setup lang="ts">
import { ref, computed, inject, watch } from "vue";
import { usePermissionGrantV2 } from "../utils/hook";
import { type PermCellKey } from "@/utils/permission-grant-types";
import {
  getResourceTree,
  getOperationList,
  type ResourceTreeNode,
  type OperationItem,
  type GrantScopeMode,
  type ResourceTypeItem
} from "@/api/permission-grant";
import { permCellKeyStr } from "../utils/grant-variant";
import { type GrantVariantId } from "../utils/v2-types";
import { message } from "@/utils/message";
import MatrixCell from "./MatrixCell.vue";
import BranchListPanel from "./BranchListPanel.vue";

defineOptions({ name: "ChildMatrixPanelV2" });

type Store = ReturnType<typeof usePermissionGrantV2>;
const store = inject<Store>("pgV2Store")!;

const props = defineProps<{ parentVariantId: GrantVariantId }>();

// 父变体是否在 mainDraft 投影（未进投影 -> 隐藏 + 提示）
const parentProjected = computed(() =>
  store.mainDraft.value.has(props.parentVariantId)
);

// 子资源类型 tab（DomainCapability.childResourceTypeCodes）
const childResourceTypeCodes = computed(
  () => store.domainCapability.value.childResourceTypeCodes ?? []
);
const activeChildType = ref("");
watch(
  childResourceTypeCodes,
  codes => {
    if (codes.length > 0 && !codes.includes(activeChildType.value)) {
      activeChildType.value = codes[0];
    }
  },
  { immediate: true }
);

const childResourceTree = ref<ResourceTreeNode[]>([]);
const childOperationList = ref<OperationItem[]>([]);
const loadingChild = ref(false);
// P2 修复：tab 切换竞态保护（单调 seq，旧响应丢弃，对齐 useV2MatrixData）
let loadChildSeq = 0;

async function loadChildMatrix(code: string) {
  if (!code) return;
  const seq = ++loadChildSeq;
  loadingChild.value = true;
  try {
    const [treeResp, opResp] = await Promise.all([
      getResourceTree({
        domainCode: store.currentDomainCode.value,
        resourceTypeCode: code
      }),
      getOperationList({ resourceTypeCode: code })
    ]);
    if (seq !== loadChildSeq) return;
    childResourceTree.value = treeResp.items;
    childOperationList.value = opResp.items;
  } catch {
    if (seq !== loadChildSeq) return;
    childResourceTree.value = [];
    childOperationList.value = [];
  } finally {
    if (seq === loadChildSeq) loadingChild.value = false;
  }
}

watch(activeChildType, code => loadChildMatrix(code), { immediate: true });

const activeChildTypeItem = computed<ResourceTypeItem | null>(
  () =>
    store.resourceTypes.value.find(
      t => t.resourceTypeCode === activeChildType.value
    ) ?? null
);
const childSupportsCondition = computed(
  () => activeChildTypeItem.value?.supportsCondition ?? false
);
const childSupportsDelegation = computed(
  () => activeChildTypeItem.value?.supportsDelegation ?? false
);

function makeChildCell(
  scopeMode: GrantScopeMode,
  resourceCode: string | null,
  codeType: string | null,
  operationCode: string
): PermCellKey {
  return {
    domainCode: store.currentDomainCode.value,
    resourceTypeCode: activeChildType.value,
    scopeMode,
    resourceCode,
    codeType,
    operationCode
  };
}

function resolveChildDisplay(cell: PermCellKey) {
  return store.resolveChildCell(props.parentVariantId, cell);
}

// P1 修复：子资源类型 ALL 支持 + 资源树递归扁平化（覆盖嵌套资源与 ALL 子权限）
const childSupportsAll = computed(
  () => activeChildTypeItem.value?.supportsAll ?? false
);

interface FlatChildRow {
  node: ResourceTreeNode;
  depth: number;
  key: string;
}
function flattenChild(nodes: ResourceTreeNode[], depth = 0): FlatChildRow[] {
  const rows: FlatChildRow[] = [];
  for (const n of nodes) {
    rows.push({ node: n, depth, key: `${n.codeType}|${n.resourceCode}` });
    if (n.children?.length) rows.push(...flattenChild(n.children, depth + 1));
  }
  return rows;
}
const flatChildRows = computed<FlatChildRow[]>(() =>
  flattenChild(childResourceTree.value)
);

// 子权限 cell 展开（单焦点，同矩阵嵌套层）
const expandedChildCell = ref<string | null>(null);

/**
 * 子权限 cell 点击（interaction §4.4：子权限单元格交互同主权限）。
 * 父变体未进投影时不响应（矩阵已隐藏，二次防御）。
 */
function onChildMainClick(cell: PermCellKey, resourceName: string | null) {
  if (store.readonly.value || !parentProjected.value) return;
  const display = resolveChildDisplay(cell);
  const { summary } = display;
  if (summary.effective === "UNAUTHORIZED") {
    if (summary.draftChange === "REMOVE") {
      for (const v of display.baselineVariants)
        store.restoreChildVariant(v.variantId);
      return;
    }
    if (display.grantableByOperator) {
      const r = store.addChildBranch(
        props.parentVariantId,
        cell,
        null,
        false,
        resourceName
      );
      if (!r.ok) message(r.reason ?? "添加子分支失败", { type: "warning" });
    }
    return;
  }
  if (summary.draftChange === "ADD" && summary.variants.length === 1) {
    store.removeChildVariantOp(summary.variants[0].variantId);
    return;
  }
  const key = permCellKeyStr(cell);
  expandedChildCell.value = expandedChildCell.value === key ? null : key;
}

/** 子权限上下文菜单（P1 修复：grant-config/edit-branches 接线） */
function onChildMenuAction(
  action: "grant-config" | "edit-branches" | "locate-source",
  cell: PermCellKey,
  resourceName: string | null
) {
  if (store.readonly.value || !parentProjected.value) return;
  const key = permCellKeyStr(cell);
  if (action === "grant-config") {
    const display = resolveChildDisplay(cell);
    if (
      display.summary.effective === "UNAUTHORIZED" &&
      display.grantableByOperator
    ) {
      store.addChildBranch(
        props.parentVariantId,
        cell,
        null,
        false,
        resourceName
      );
    }
    if (expandedChildCell.value !== key) expandedChildCell.value = key;
  } else if (action === "edit-branches") {
    if (expandedChildCell.value !== key) expandedChildCell.value = key;
  } else if (action === "locate-source") {
    message("子权限来源定位暂不支持", { type: "info" });
  }
}

interface ChildCellEntry {
  cell: PermCellKey;
  resourceName: string | null;
}
/** 全部子权限 cell 索引（ALL 行 + flatChildRows 嵌套资源，P1 修复：覆盖 ALL/后代） */
const allChildEntries = computed<ChildCellEntry[]>(() => {
  const entries: ChildCellEntry[] = [];
  if (childSupportsAll.value) {
    for (const op of childOperationList.value) {
      entries.push({
        cell: makeChildCell("ALL", null, null, op.operationCode),
        resourceName: "全部"
      });
    }
  }
  for (const row of flatChildRows.value) {
    for (const op of childOperationList.value) {
      entries.push({
        cell: makeChildCell(
          "INSTANCE",
          row.node.resourceCode,
          row.node.codeType,
          op.operationCode
        ),
        resourceName: row.node.resourceName
      });
    }
  }
  return entries;
});
const expandedChildEntry = computed<ChildCellEntry | null>(() => {
  if (!expandedChildCell.value) return null;
  return (
    allChildEntries.value.find(
      e => permCellKeyStr(e.cell) === expandedChildCell.value
    ) ?? null
  );
});

const expandedChildDisplay = computed(() =>
  expandedChildEntry.value
    ? resolveChildDisplay(expandedChildEntry.value.cell)
    : null
);

function onChildAddBranch(payload: {
  conditionCode: string | null;
  canGrant: boolean;
  keepDirect: boolean;
}) {
  if (!expandedChildEntry.value) return;
  const r = store.addChildBranch(
    props.parentVariantId,
    expandedChildEntry.value.cell,
    payload.conditionCode,
    payload.canGrant,
    expandedChildEntry.value.resourceName
  );
  if (!r.ok) message(r.reason ?? "添加子分支失败", { type: "warning" });
}

function onChildEditBranch(payload: {
  variantId: GrantVariantId;
  conditionCode: string | null;
  canGrant: boolean;
}) {
  const r = store.updateChildVariant(
    payload.variantId,
    payload.conditionCode,
    payload.canGrant
  );
  if (!r.ok) message(r.reason ?? "编辑失败", { type: "warning" });
}

function onChildRevokeBranch(variantId: GrantVariantId) {
  store.removeChildVariantOp(variantId);
}

function onChildRestoreBranch(variantId: GrantVariantId) {
  const r = store.restoreChildVariant(variantId);
  if (!r.ok) message(r.reason ?? "恢复失败", { type: "warning" });
}

function onChildRestoreModify(variantId: GrantVariantId) {
  const r = store.restoreChildModify(variantId);
  if (!r.ok) message(r.reason ?? "恢复失败", { type: "warning" });
}

function onChildClose() {
  expandedChildCell.value = null;
}

const childGridStyle = computed(() => ({
  gridTemplateColumns: `minmax(140px, 200px) repeat(${childOperationList.value.length}, minmax(72px, 1fr))`
}));
</script>

<template>
  <div class="child-matrix-panel">
    <div v-if="!parentProjected" class="parent-not-projected">
      父权限未生效，子权限不可配
    </div>
    <template v-else>
      <div class="child-tabs">
        <el-button
          v-for="code in childResourceTypeCodes"
          :key="code"
          size="small"
          :type="code === activeChildType ? 'primary' : ''"
          @click="activeChildType = code"
        >
          {{ code }}
        </el-button>
      </div>
      <div v-if="loadingChild" class="child-loading">加载中…</div>
      <div v-else-if="childOperationList.length === 0" class="child-empty">
        该子资源类型无操作
      </div>
      <div v-else class="child-grid" :style="childGridStyle">
        <div class="cell-header col-resource">子资源</div>
        <div
          v-for="op in childOperationList"
          :key="op.operationCode"
          class="cell-header"
        >
          {{ op.operationName }}
        </div>
        <template v-if="childSupportsAll">
          <div class="cell-resource all-row-label">全部 [ALL]</div>
          <div
            v-for="op in childOperationList"
            :key="op.operationCode"
            class="cell-wrap"
          >
            <MatrixCell
              :display="
                resolveChildDisplay(
                  makeChildCell('ALL', null, null, op.operationCode)
                )
              "
              :readonly="store.readonly.value"
              @main-click="
                onChildMainClick(
                  makeChildCell('ALL', null, null, op.operationCode),
                  '全部'
                )
              "
              @menu-action="
                (a: 'grant-config' | 'edit-branches' | 'locate-source') =>
                  onChildMenuAction(
                    a,
                    makeChildCell('ALL', null, null, op.operationCode),
                    '全部'
                  )
              "
            />
          </div>
        </template>
        <template v-for="row in flatChildRows" :key="row.key">
          <div
            class="cell-resource"
            :style="{ paddingLeft: `${row.depth * 16 + 8}px` }"
          >
            {{ row.node.resourceName }}
          </div>
          <div
            v-for="op in childOperationList"
            :key="op.operationCode"
            class="cell-wrap"
          >
            <MatrixCell
              :display="
                resolveChildDisplay(
                  makeChildCell(
                    'INSTANCE',
                    row.node.resourceCode,
                    row.node.codeType,
                    op.operationCode
                  )
                )
              "
              :readonly="store.readonly.value"
              @main-click="
                onChildMainClick(
                  makeChildCell(
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
                  onChildMenuAction(
                    a,
                    makeChildCell(
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
        </template>
        <div v-if="expandedChildDisplay" class="child-expand-row">
          <BranchListPanel
            :display="expandedChildDisplay"
            :supports-condition="childSupportsCondition"
            :supports-delegation="childSupportsDelegation"
            :condition-options="store.conditionOptions.value"
            :allow-children="false"
            @add-branch="onChildAddBranch"
            @edit-branch="onChildEditBranch"
            @revoke-branch="onChildRevokeBranch"
            @restore-branch="onChildRestoreBranch"
            @restore-modify="onChildRestoreModify"
            @close="onChildClose"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.child-matrix-panel {
  padding: var(--space-2) var(--space-3);
  margin-top: var(--space-1);
  background: var(--el-fill-color-light);
  border-top: 1px dashed var(--el-border-color-lighter);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.parent-not-projected {
  padding: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-tabs {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--space-1);
  margin-bottom: var(--space-2);
}

.child-loading,
.child-empty {
  padding: var(--space-2);
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.child-grid {
  display: grid;
  min-width: max-content;
  padding: var(--space-1);
  background: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
}

.cell-header,
.cell-resource,
.cell-wrap {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.cell-header {
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.col-resource {
  justify-content: flex-start;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cell-resource {
  justify-content: flex-start;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cell-wrap {
  justify-content: center;
}

.child-expand-row {
  grid-column: 1 / -1;
}
</style>
