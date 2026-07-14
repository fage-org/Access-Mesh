<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { inject } from "vue";
import { message } from "@/utils/message";
import { usePermissionGrant } from "../utils/hook";
import { PermissionSummaryCell } from "@/components/PermissionSummaryCell";
import { normalizeSummary, sortSummary } from "../utils/summary";
import type { SummaryItem } from "@/utils/permission-grant-types";
import type {
  GrantTriggerPayload,
  AdjustTriggerPayload
} from "@/utils/permission-grant-types";
import type { ResourceTreeNode, GrantScopeMode } from "@/api/permission-grant";

defineOptions({ name: "PermissionMatrixPanel" });

const store = inject<ReturnType<typeof usePermissionGrant>>("pgStore")!;
const emit = defineEmits<{
  "open-grant": [payload: GrantTriggerPayload];
  "open-adjust": [payload: AdjustTriggerPayload];
}>();

interface TableRow extends ResourceTreeNode {
  rowKey: string;
  isAllRow?: boolean;
  children: TableRow[];
}

const ALL_ROW_KEY = "__ALL__";
const tableRef = ref();
const expandedKeys = ref<string[]>([]);
const keyword = ref("");
// 搜索前展开快照（清空搜索时恢复，§16.3.3 展开状态稳定）
const savedExpandedKeys = ref<string[] | null>(null);
// 受控选中行（过滤重建行后高亮稳定，§16.3.3 选中状态稳定）
const selectedRowKey = ref<string>("");

function rowKeyOf(row: TableRow): string {
  return row.rowKey;
}

// 搜索：递归生成过滤树，保留祖先路径（§16.3.3）
function filterTree(nodes: ResourceTreeNode[], kw: string): ResourceTreeNode[] {
  if (!kw.trim()) return nodes;
  const lower = kw.trim().toLowerCase();
  const walk = (list: ResourceTreeNode[]): ResourceTreeNode[] => {
    const result: ResourceTreeNode[] = [];
    for (const n of list) {
      const matched =
        n.resourceName.toLowerCase().includes(lower) ||
        n.resourceCode.toLowerCase().includes(lower);
      const children = walk(n.children);
      if (matched || children.length > 0) {
        result.push({ ...n, children });
      }
    }
    return result;
  };
  return walk(nodes);
}

const filteredTree = computed(() =>
  filterTree(store.resourceTree.value, keyword.value)
);

const tableData = computed<TableRow[]>(() => {
  const rt = store.currentResourceType.value;
  const rows: TableRow[] = [];
  if (rt?.supportsAll) {
    rows.push({
      resourceCode: "__ALL__",
      codeType: "default",
      resourceName: `全部 ${rt.resourceTypeName} 资源`,
      parentCode: null,
      children: [],
      rowKey: ALL_ROW_KEY,
      isAllRow: true
    });
  }
  const build = (nodes: ResourceTreeNode[]): TableRow[] =>
    nodes.map(n => ({
      ...n,
      rowKey: `${n.codeType}|${n.resourceCode}`,
      children: n.children?.length ? build(n.children) : []
    }));
  rows.push(...build(filteredTree.value));
  return rows;
});

// 可展开节点 key（有子节点）
const allExpandableKeys = computed(() => {
  const keys: string[] = [];
  const walk = (rows: TableRow[]) => {
    for (const r of rows) {
      if (r.children?.length) {
        keys.push(r.rowKey);
        walk(r.children);
      }
    }
  };
  walk(tableData.value);
  return keys;
});

const allExpanded = computed(
  () =>
    allExpandableKeys.value.length > 0 &&
    allExpandableKeys.value.every(k => expandedKeys.value.includes(k))
);

// 树表 expand-change：第二参数为 expanded: boolean（非展开行数组）
function onExpandChange(row: TableRow, expanded: boolean) {
  const key = row.rowKey;
  if (expanded) {
    if (!expandedKeys.value.includes(key)) {
      expandedKeys.value = [...expandedKeys.value, key];
    }
  } else {
    expandedKeys.value = expandedKeys.value.filter(k => k !== key);
  }
}

function toggleExpandAll() {
  expandedKeys.value = allExpanded.value ? [] : [...allExpandableKeys.value];
}

// 搜索时展开命中祖先；清空时恢复搜索前展开状态
watch(keyword, kw => {
  if (kw.trim()) {
    if (savedExpandedKeys.value === null) {
      savedExpandedKeys.value = [...expandedKeys.value];
    }
    expandedKeys.value = [...allExpandableKeys.value];
  } else if (savedExpandedKeys.value !== null) {
    expandedKeys.value = savedExpandedKeys.value;
    savedExpandedKeys.value = null;
  }
});

// 切换资源类型时清理展开/选中/搜索状态（避免旧类型 key 残留到新树）
watch(
  () => store.currentResourceTypeCode.value,
  () => {
    expandedKeys.value = [];
    savedExpandedKeys.value = null;
    selectedRowKey.value = "";
    keyword.value = "";
  }
);

function onRowClick(row: TableRow) {
  selectedRowKey.value = row.rowKey;
}

// 摘要缓存（rowKey -> SummaryItem[]，避免模板重复计算）
const summaryMap = computed(() => {
  const map = new Map<string, SummaryItem[]>();
  const walk = (rows: TableRow[]) => {
    for (const r of rows) {
      map.set(r.rowKey, buildSummary(r));
      if (r.children?.length) walk(r.children);
    }
  };
  walk(tableData.value);
  return map;
});

function buildSummary(row: TableRow): SummaryItem[] {
  const rt = store.currentResourceType.value;
  if (!rt) return [];
  const scopeMode: GrantScopeMode = row.isAllRow ? "ALL" : "INSTANCE";
  const resourceCode = row.isAllRow ? null : row.resourceCode;
  const codeType = row.isAllRow ? null : row.codeType;
  const items = store.operations.value.map(op => {
    const ctx = store.buildMainCellContext(
      store.currentResourceTypeCode.value,
      scopeMode,
      resourceCode,
      codeType,
      op.operationCode
    );
    // ALL 覆盖时取 ALL 来源 ctx（条件/canGrant/子权限来自 ALL 权限，非 INSTANCE）
    const allSourceCtx = ctx.allCovered
      ? store.buildMainCellContext(
          store.currentResourceTypeCode.value,
          "ALL",
          null,
          null,
          op.operationCode
        )
      : null;
    return normalizeSummary({
      domainCode: store.currentDomainCode.value,
      resourceTypeCode: store.currentResourceTypeCode.value,
      scopeMode,
      resourceCode,
      codeType,
      ctx,
      allSourceCtx,
      op
    });
  });
  return sortSummary(items);
}

function getSummary(row: TableRow): SummaryItem[] {
  return summaryMap.value.get(row.rowKey) ?? [];
}

function rowDraftCount(row: TableRow): number {
  return getSummary(row).filter(i => i.draftChange !== null).length;
}

const isGroupReadOnly = computed(
  () =>
    !!store.currentRole.value &&
    !store.currentRole.value.directGrantable &&
    !store.currentRole.value.roleExternalId.startsWith("__virtual_root_")
);

const grantDisabled = computed(
  () => store.readonly.value || isGroupReadOnly.value
);

// 授权按钮禁用原因（§16.3.4 禁用并解释原因）
const grantDisabledReason = computed<string | null>(() => {
  if (isGroupReadOnly.value) return "组合角色不可直接配置权限";
  return store.readonlyReason.value;
});

function grantBase() {
  const role = store.currentRole.value;
  return {
    domainCode: store.currentDomainCode.value,
    roleExternalId: role?.roleExternalId ?? "",
    roleTypeCode: role?.roleTypeCode ?? "",
    roleName: role?.roleName ?? "",
    resourceTypeCode: store.currentResourceTypeCode.value
  };
}

function onGrantHeader() {
  emit("open-grant", { ...grantBase(), scopeMode: "INSTANCE" });
}

function onGrantAll() {
  emit("open-grant", { ...grantBase(), scopeMode: "ALL" });
}

function onAdjust(item: SummaryItem) {
  emit("open-adjust", {
    ...grantBase(),
    scopeMode: item.trigger.scopeMode,
    resourceCode: item.trigger.resourceCode,
    codeType: item.trigger.codeType,
    operationCode: item.trigger.operationCode,
    conditionCode: item.trigger.conditionCode,
    draft: item.trigger.draft
  });
}

function onLocateAll(operationCode: string) {
  const rt = store.currentResourceType.value;
  message(
    `操作「${operationCode}」的权限来自全部${rt?.resourceTypeName ?? ""}资源的 ALL 授权，可在「全部资源」行查看`,
    { type: "info" }
  );
}
</script>

<template>
  <div class="matrix-panel">
    <div class="matrix-header">
      <el-select
        :model-value="store.currentResourceTypeCode.value"
        size="small"
        class="type-switcher"
        @change="(v: string) => store.switchResourceType(v)"
      >
        <el-option
          v-for="t in store.resourceTypes.value"
          :key="t.resourceTypeCode"
          :label="t.resourceTypeName"
          :value="t.resourceTypeCode"
        />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索资源名称或编码"
        size="small"
        clearable
        class="res-search"
      />
      <div class="header-actions">
        <el-tooltip
          :content="grantDisabledReason ?? ''"
          :disabled="!grantDisabledReason"
          placement="top"
        >
          <span class="grant-wrap">
            <el-button
              type="primary"
              size="small"
              :disabled="grantDisabled"
              @click="onGrantHeader"
              >授权</el-button
            >
          </span>
        </el-tooltip>
        <el-button link size="small" @click="toggleExpandAll">{{
          allExpanded ? "收起全部" : "展开全部"
        }}</el-button>
      </div>
    </div>

    <!-- 组合角色只读说明（§3.4，R7：不伪造有效权限摘要） -->
    <el-alert
      v-if="isGroupReadOnly"
      type="info"
      :closable="false"
      show-icon
      title="组合角色不可直接配置权限"
      description="该角色为组合角色（GROUP_ROLE），通过聚合基础角色产生权限，不直接持有权限配置。有效权限展开及管理基础角色入口暂缓。"
    />

    <el-scrollbar class="matrix-scroll">
      <el-table
        v-if="!isGroupReadOnly"
        ref="tableRef"
        :data="tableData"
        :row-key="rowKeyOf"
        :expand-row-keys="expandedKeys"
        :current-row-key="selectedRowKey"
        :tree-props="{ children: 'children' }"
        :expand-on-click-node="false"
        highlight-current-row
        border
        size="small"
        class="matrix-table"
        @expand-change="onExpandChange"
        @row-click="onRowClick"
      >
        <el-table-column prop="resourceName" label="资源" min-width="240">
          <template #default="{ row }">
            <div class="res-cell">
              <span :class="['res-name', { 'res-all': row.isAllRow }]">
                {{ row.resourceName }}
              </span>
              <el-tag
                v-if="rowDraftCount(row) > 0"
                size="small"
                type="warning"
                effect="plain"
                >变更 {{ rowDraftCount(row) }}</el-tag
              >
              <el-tooltip
                v-if="row.isAllRow"
                :content="grantDisabledReason ?? ''"
                :disabled="!grantDisabledReason"
                placement="top"
              >
                <span class="grant-wrap">
                  <el-button
                    link
                    size="small"
                    :disabled="grantDisabled"
                    @click="onGrantAll"
                    >授权</el-button
                  >
                </span>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作权限摘要" min-width="360">
          <template #default="{ row }">
            <PermissionSummaryCell
              :items="getSummary(row)"
              @open-adjust="onAdjust"
              @locate-all="onLocateAll"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.matrix-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.matrix-header {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.type-switcher {
  flex-shrink: 0;
  width: 160px;
}

.res-search {
  flex: 1;
  min-width: 120px;
  max-width: 240px;
}

.header-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
  align-items: center;
}

// tooltip 包裹 disabled button（disabled 不触发 mouseenter）
.grant-wrap {
  display: inline-flex;
  align-items: center;
}

.matrix-scroll {
  flex: 1;
  min-height: 0;
}

.matrix-table {
  width: 100%;

  :deep(.el-table__cell) {
    padding: 4px 0;
  }
}

.res-cell {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.res-name {
  font-size: 13px;
}

.res-all {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
