<script setup lang="ts">
/**
 * 查看矩阵（§3：资源树形行 × 可配置操作列；el-table-v2 虚拟滚动承载 >500 节点，S7）。
 * - 行：类型分组行（首层，默认展开）→ ALL 虚拟行（承载 scopeMode=ALL，与实例行互斥）+ 资源实例树形行
 * - 列：操作列 union by code 跨类型去重展示（§3.2），可配置增删（localStorage 按 subjectType 隔离）
 * - 单元格：MatrixCell（有/无 + 来源图标 + 角标 + 悬浮详情 + diff 标记）
 * - 继承开关 ×2（树级/操作，默认开）为查看态过滤；页头标注"含继承视图（模拟 CHILD 展开，非运行时默认）"
 * - 点击：有权限 = 详情层；无权限 = 授权弹窗（推荐路径，§3.3 实现时定采纳）
 */
import { computed, ref, watch } from "vue";
import { useElementSize } from "@vueuse/core";
import { Search, Setting } from "@element-plus/icons-vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import {
  allRowKey,
  instanceRowKey,
  type SourceChainResult
} from "../utils/source-chain";
import { cellDraftMark } from "../utils/grant-plan";
import type { CellSource } from "../utils/source-chain";
import MatrixCell from "./MatrixCell.vue";

type UnionColumn = { code: string; name: string; globalFallback: boolean };

const props = defineProps<{
  sourceChain: SourceChainResult;
  resourceForest: ResourceTreeNode[];
  matrixTypeCodes: string[];
  visibleColumns: UnionColumn[];
  unionColumns: UnionColumn[];
  hiddenColumnCodes: string[];
  includeResourceInherit: boolean;
  includeOpInherit: boolean;
  keyword: string;
  markInfo: Map<
    number,
    { mark: "add" | "update" | "remove"; changeId: string }
  >;
  capability: "edit" | "view";
  loading: boolean;
  /** 已选中可授权主体 */
  hasSubject: boolean;
  /** GROUP_ROLE 选中提示（分组节点本身无权限矩阵） */
  groupHint: string | null;
  locateRequest: {
    resourceTypeCode: string;
    resourceCode: string | null;
    codeType: string | null;
    operationCode: string | null;
    ts: number;
  } | null;
}>();

const emit = defineEmits<{
  (e: "update:hiddenColumnCodes", value: string[]): void;
  (e: "update:includeResourceInherit", value: boolean): void;
  (e: "update:includeOpInherit", value: boolean): void;
  (e: "update:keyword", value: string): void;
  (
    e: "cellDetail",
    target: {
      resourceTypeCode: string;
      resourceCode: string | null;
      codeType: string | null;
      resourceName: string;
      operationCode: string;
      scopeMode: "INSTANCE" | "ALL";
    }
  ): void;
  (
    e: "cellGrant",
    initial: {
      operationCode: string;
      resourceTypeCode: string;
      resourceCode: string | null;
      codeType: string | null;
      scopeMode: "INSTANCE" | "ALL";
    }
  ): void;
  (e: "grant"): void;
}>();

// ========== 行模型 ==========

type MatrixRow = {
  key: string;
  kind: "type" | "all" | "instance";
  resourceTypeCode: string;
  label: string;
  subLabel?: string;
  resourceCode?: string | null;
  codeType?: string | null;
  children?: MatrixRow[];
};

/** 类型 → 资源树根（资源树返回全类型森林，按根节点类型分组；树内子节点同类型） */
const forestByType = computed(() => {
  const map = new Map<string, ResourceTreeNode[]>();
  for (const root of props.resourceForest) {
    const list = map.get(root.resourceTypeCode) ?? [];
    list.push(root);
    map.set(root.resourceTypeCode, list);
  }
  return map;
});

/** 搜索过滤：命中节点及其祖先链展开显示（§3.1） */
function filterTreeByKeyword(
  nodes: ResourceTreeNode[],
  keyword: string
): ResourceTreeNode[] {
  if (!keyword) return nodes;
  const result: ResourceTreeNode[] = [];
  for (const node of nodes) {
    const children = node.children?.length
      ? filterTreeByKeyword(node.children, keyword)
      : [];
    const hit = node.name.includes(keyword) || node.code.includes(keyword);
    if (hit || children.length > 0) {
      result.push({
        ...node,
        children: hit ? (node.children ?? []) : children
      });
    }
  }
  return result;
}

function toInstanceRows(nodes: ResourceTreeNode[]): MatrixRow[] {
  return nodes.map(node => ({
    key: instanceRowKey(node.resourceTypeCode, node.code, node.codeType),
    kind: "instance" as const,
    resourceTypeCode: node.resourceTypeCode,
    resourceCode: node.code,
    codeType: node.codeType,
    label: node.name,
    subLabel: node.code,
    children: node.children?.length ? toInstanceRows(node.children) : []
  }));
}

const rows = computed<MatrixRow[]>(() => {
  const keyword = props.keyword.trim();
  const result: MatrixRow[] = [];
  for (const typeCode of props.matrixTypeCodes) {
    const roots = forestByType.value.get(typeCode) ?? [];
    const filtered = keyword ? filterTreeByKeyword(roots, keyword) : roots;
    // 搜索时：类型无命中节点且 ALL 行不匹配 → 隐藏类型行
    if (keyword && filtered.length === 0) continue;
    result.push({
      key: `TYPE:${typeCode}`,
      kind: "type",
      resourceTypeCode: typeCode,
      label: typeCode,
      children: [
        {
          key: allRowKey(typeCode),
          kind: "all",
          resourceTypeCode: typeCode,
          label: "全部资源（ALL）"
        },
        ...toInstanceRows(filtered)
      ]
    });
  }
  return result;
});

// ========== 展开状态（默认收起至第一层：类型行展开、实例树收起） ==========

const expandedRowKeys = ref<string[]>([]);

watch(
  () => props.matrixTypeCodes,
  codes => {
    const typeKeys = codes.map(c => `TYPE:${c}`);
    // 保留已展开实例节点 + 确保类型行默认展开
    const kept = expandedRowKeys.value.filter(k => !k.startsWith("TYPE:"));
    expandedRowKeys.value = [...typeKeys, ...kept];
  },
  { immediate: true }
);

// ========== 列模型（名称列 + 可见操作列；opCode 为自定义列属性，cell slot 消费） ==========

const columns = computed<any[]>(() => [
  {
    key: "name",
    dataKey: "name",
    title: "资源",
    width: 280,
    fixed: true
  },
  ...props.visibleColumns.map(col => ({
    key: `op:${col.code}`,
    dataKey: col.code,
    title: `${col.name}（${col.code}）`,
    width: 120,
    align: "center" as const,
    opCode: col.code
  }))
]);

// ========== 单元格状态 ==========

function sourcesOf(row: MatrixRow, opCode: string): CellSource[] {
  if (row.kind === "type") return [];
  // 列对行类型的适用性：行类型的合并列中无此操作 → 不适用（空白占位）
  const merged = props.sourceChain.columnsByType.get(row.resourceTypeCode);
  if (!merged?.some(c => c.code === opCode)) return [];
  return props.sourceChain.cells.get(row.key)?.get(opCode)?.sources ?? [];
}

function columnApplicable(row: MatrixRow, opCode: string): boolean {
  if (row.kind === "type") return false;
  return (
    props.sourceChain.columnsByType
      .get(row.resourceTypeCode)
      ?.some(c => c.code === opCode) ?? false
  );
}

function markOf(row: MatrixRow, opCode: string) {
  return cellDraftMark({
    sources: sourcesOf(row, opCode),
    markInfo: props.markInfo
  });
}

function handleCellSelect(row: MatrixRow, opCode: string) {
  if (row.kind === "type" || !columnApplicable(row, opCode)) return;
  const sources = sourcesOf(row, opCode);
  if (sources.length > 0) {
    emit("cellDetail", {
      resourceTypeCode: row.resourceTypeCode,
      resourceCode: row.resourceCode ?? null,
      codeType: row.codeType ?? null,
      resourceName: row.label,
      operationCode: opCode,
      scopeMode: row.kind === "all" ? "ALL" : "INSTANCE"
    });
  } else {
    emit("cellGrant", {
      operationCode: opCode,
      resourceTypeCode: row.resourceTypeCode,
      resourceCode: row.resourceCode ?? null,
      codeType: row.codeType ?? null,
      scopeMode: row.kind === "all" ? "ALL" : "INSTANCE"
    });
  }
}

// ========== 操作列配置（§3.2 增删列，localStorage 刷新保留） ==========

const columnConfigVisible = ref(false);

function toggleColumn(code: string, checked: boolean) {
  const next = checked
    ? props.hiddenColumnCodes.filter(c => c !== code)
    : [...props.hiddenColumnCodes, code];
  emit("update:hiddenColumnCodes", next);
}

// ========== 变更清单定位（滚动到对应单元格并闪烁） ==========

const tableWrapRef = ref<HTMLElement>();
const { width: wrapWidth, height: wrapHeight } = useElementSize(tableWrapRef);
const tableRef = ref();
const flashKey = ref<string | null>(null);

/** 可见行扁平化（与 table-v2 内部展开顺序一致：先序、展开时子随父） */
function flattenVisible(list: MatrixRow[], expanded: Set<string>): MatrixRow[] {
  const result: MatrixRow[] = [];
  const walk = (items: MatrixRow[]) => {
    for (const item of items) {
      result.push(item);
      if (item.children?.length && expanded.has(item.key)) {
        walk(item.children);
      }
    }
  };
  walk(list);
  return result;
}

watch(
  () => props.locateRequest,
  req => {
    if (!req) return;
    const targetKey =
      req.resourceCode != null
        ? instanceRowKey(
            req.resourceTypeCode,
            req.resourceCode,
            req.codeType ?? "default"
          )
        : allRowKey(req.resourceTypeCode);
    // 展开祖先链：类型行 + 资源祖先
    const expand = new Set(expandedRowKeys.value);
    expand.add(`TYPE:${req.resourceTypeCode}`);
    if (req.resourceCode != null) {
      const path = findNodePath(
        forestByType.value.get(req.resourceTypeCode) ?? [],
        req.resourceCode,
        req.codeType ?? "default"
      );
      for (const node of path.slice(0, -1)) {
        expand.add(
          instanceRowKey(node.resourceTypeCode, node.code, node.codeType)
        );
      }
    }
    expandedRowKeys.value = [...expand];
    // 滚动 + 闪烁
    requestAnimationFrame(() => {
      const flat = flattenVisible(rows.value, new Set(expandedRowKeys.value));
      const index = flat.findIndex(r => r.key === targetKey);
      if (index >= 0) {
        // table-v2 暴露 scrollToRow（行索引定位）
        tableRef.value?.scrollToRow?.(index, "auto");
      }
      flashKey.value = `${targetKey}|${req.operationCode ?? ""}`;
      window.setTimeout(() => {
        if (flashKey.value === `${targetKey}|${req.operationCode ?? ""}`) {
          flashKey.value = null;
        }
      }, 1600);
    });
  }
);

function findNodePath(
  nodes: ResourceTreeNode[],
  code: string,
  codeType: string
): ResourceTreeNode[] {
  for (const node of nodes) {
    if (node.code === code && node.codeType === codeType) return [node];
    if (node.children?.length) {
      const sub = findNodePath(node.children, code, codeType);
      if (sub.length) return [node, ...sub];
    }
  }
  return [];
}

function cellFlashClass(row: MatrixRow, opCode: string): string {
  return flashKey.value === `${row.key}|${opCode}` ? "cell-flash" : "";
}
</script>

<template>
  <div class="grant-matrix-panel">
    <!-- 工具栏：标题行 + 搜索/过滤行 -->
    <div class="matrix-toolbar">
      <div class="toolbar-row primary">
        <div class="panel-heading">
          <div class="panel-title">权限矩阵</div>
          <span class="view-note"
            >含继承视图（模拟 CHILD 展开，非运行时默认）</span
          >
        </div>
        <el-button
          v-if="capability === 'edit'"
          type="primary"
          :disabled="!hasSubject"
          @click="emit('grant')"
        >
          授权
        </el-button>
      </div>
      <div class="toolbar-row filters">
        <el-input
          :model-value="keyword"
          placeholder="搜索资源名称/编码"
          clearable
          :prefix-icon="Search"
          class="toolbar-search"
          @update:model-value="val => emit('update:keyword', String(val ?? ''))"
        />
        <div class="filter-divider" />
        <el-tooltip
          content="树级继承：子孙行显示来自祖先资源的授权（查看态过滤）"
          placement="top"
        >
          <span class="toolbar-switch">
            <el-switch
              :model-value="includeResourceInherit"
              inline-prompt
              active-text="树继承"
              inactive-text="树继承"
              @update:model-value="
                val => emit('update:includeResourceInherit', !!val)
              "
            />
          </span>
        </el-tooltip>
        <el-tooltip
          content="操作继承：被高级操作 inheritMask 覆盖的列显示为继承（查看态过滤）"
          placement="top"
        >
          <span class="toolbar-switch">
            <el-switch
              :model-value="includeOpInherit"
              inline-prompt
              active-text="操作继承"
              inactive-text="操作继承"
              @update:model-value="
                val => emit('update:includeOpInherit', !!val)
              "
            />
          </span>
        </el-tooltip>
        <el-popover
          v-model:visible="columnConfigVisible"
          placement="bottom-end"
          :width="240"
          trigger="click"
        >
          <template #reference>
            <el-button :icon="Setting" text>操作列</el-button>
          </template>
          <div class="column-config">
            <div class="config-title">显示操作列（刷新保留）</div>
            <el-checkbox
              v-for="col in unionColumns"
              :key="col.code"
              :model-value="!hiddenColumnCodes.includes(col.code)"
              :label="`${col.name}（${col.code}）${col.globalFallback ? ' · 全局' : ''}`"
              @update:model-value="val => toggleColumn(col.code, !!val)"
            />
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 主体区 -->
    <div ref="tableWrapRef" v-loading="loading" class="matrix-body">
      <!-- 未选主体 -->
      <el-empty
        v-if="!hasSubject && !groupHint"
        description="请选择左侧角色主体查看权限矩阵"
      />
      <!-- GROUP_ROLE 节点本身无权限矩阵 -->
      <el-result
        v-else-if="groupHint"
        icon="info"
        :title="`分组角色「${groupHint}」无独立权限矩阵`"
        sub-title="请展开该分组，选择其基础角色查看/授予权限（授权目标 = 基础角色本身）"
      />
      <!-- 空态：主体无任何权限 -->
      <el-empty
        v-else-if="matrixTypeCodes.length === 0"
        description="该主体暂无权限配置"
      >
        <el-button
          v-if="capability === 'edit'"
          type="primary"
          @click="emit('grant')"
        >
          去授权
        </el-button>
      </el-empty>

      <el-table-v2
        v-else
        ref="tableRef"
        v-model:expanded-row-keys="expandedRowKeys"
        :columns="columns"
        :data="rows"
        :width="wrapWidth"
        :height="wrapHeight"
        :row-height="36"
        row-key="key"
        :expand-column-key="'name'"
        fixed
      >
        <template #cell="{ column, rowData }">
          <!-- 资源名称列 -->
          <template v-if="column.key === 'name'">
            <span v-if="rowData.kind === 'type'" class="type-row">
              {{ rowData.label }}
            </span>
            <span v-else-if="rowData.kind === 'all'" class="all-row">
              {{ rowData.label }}
              <el-tag size="small" type="warning" effect="plain" class="ml-1"
                >A</el-tag
              >
            </span>
            <span v-else class="instance-row">
              <span class="instance-name">{{ rowData.label }}</span>
              <span class="instance-code">{{ rowData.subLabel }}</span>
            </span>
          </template>
          <!-- 操作列单元格 -->
          <template v-else-if="rowData.kind !== 'type'">
            <span
              v-if="!columnApplicable(rowData, column.opCode)"
              class="cell-na"
            >
              —
            </span>
            <span
              v-else
              class="cell-wrap"
              :class="cellFlashClass(rowData, column.opCode)"
            >
              <MatrixCell
                :sources="sourcesOf(rowData, column.opCode)"
                :mark="markOf(rowData, column.opCode).mark"
                :removed-count="markOf(rowData, column.opCode).removedCount"
                :total-count="markOf(rowData, column.opCode).totalCount"
                :capability="capability"
                @select="handleCellSelect(rowData, column.opCode)"
              />
            </span>
          </template>
        </template>
      </el-table-v2>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.grant-matrix-panel {
  @keyframes cell-flash {
    0%,
    60% {
      background: var(--el-color-warning-light-5);
    }

    100% {
      background: transparent;
    }
  }

  /* 面板卡片：与页面其他面板统一（白底 + 浅边框 + 轻阴影） */
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);

  .matrix-toolbar {
    flex-shrink: 0;
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--el-border-color-lighter);

    .toolbar-row {
      display: flex;
      gap: var(--space-3);
      align-items: center;

      &.primary {
        justify-content: space-between;
        margin-bottom: var(--space-3);

        .panel-heading {
          display: flex;
          gap: var(--space-3);
          align-items: baseline;

          .panel-title {
            font-size: 14px;
            font-weight: 600;
            line-height: 1.4;
            color: var(--el-text-color-primary);
          }

          .view-note {
            font-size: 12px;
            color: var(--el-text-color-secondary);
          }
        }
      }

      &.filters {
        flex-wrap: wrap;

        .toolbar-search {
          width: 220px;
        }

        .filter-divider {
          width: 1px;
          height: 20px;
          margin: 0 var(--space-1);
          background: var(--el-border-color-lighter);
        }

        .toolbar-switch {
          display: inline-flex;
        }
      }
    }
  }

  .matrix-body {
    position: relative;
    flex: 1;
    min-height: 0;
  }

  .type-row {
    display: inline-flex;
    align-items: center;
    height: 24px;
    padding: 0 var(--space-2);
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    border-radius: var(--radius-md);
  }

  .all-row {
    font-style: italic;
    color: var(--el-text-color-secondary);
  }

  .instance-row {
    display: inline-flex;
    gap: 6px;
    align-items: baseline;
    min-width: 0;

    .instance-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .instance-code {
      font-size: 11px;
      color: var(--el-text-color-secondary);
    }
  }

  .cell-na {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    color: var(--el-text-color-placeholder);
  }

  .cell-wrap {
    display: flex;
    width: 100%;
    height: 100%;
  }

  .cell-flash {
    animation: cell-flash 1.6s ease;
  }

  .column-config {
    display: flex;
    flex-direction: column;

    .config-title {
      margin-bottom: var(--space-1);
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  /* ---- el-table-v2 局部细化（仅本页矩阵） ---- */
  :deep(.el-table-v2__header-row) {
    height: 36px;
  }

  :deep(.el-table-v2__header-cell) {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-lighter);
  }

  :deep(.el-table-v2__row) {
    transition: background-color 0.15s;

    &:hover {
      background: var(--el-fill-color-light);
    }
  }

  :deep(.el-table-v2__row-cell) {
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }
}
</style>
