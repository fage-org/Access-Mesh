<script setup lang="ts">
/**
 * 查看矩阵（§3：资源树形行 × 可配置操作列；el-table-v2 虚拟滚动承载 >500 节点，S7）。
 * - 行：类型分组行（首层，默认展开）→ ALL 虚拟行（承载 scopeMode=ALL，与实例行互斥）+ 资源实例树形行
 * - 列：操作列 union by code 跨类型去重展示（§3.2），可配置增删（localStorage 按 subjectType 隔离）
 * - 单元格：MatrixCell（图标正交模型：聚合有效图标 + 撤销图标并列 + 悬浮详情 + diff 背景，🔧 T-FE-039）
 * - 继承开关 ×2（树级/操作，默认开）为查看态过滤；页头标注"含继承视图（模拟 CHILD 展开，非运行时默认）"
 * - 点击：有权限 = 详情层；无权限 = 授权弹窗（推荐路径，§3.3 实现时定采纳）
 */
import { computed, ref, watch } from "vue";
import { useElementSize } from "@vueuse/core";
import { Search, Setting } from "@element-plus/icons-vue";
import type { ResourceTreeNode } from "@/api/resource-operation";
import type { TypeDefResp } from "@/api/type-def";
import {
  allRowKey,
  instanceRowKey,
  type SourceChainResult
} from "../utils/source-chain";
import type { CellSource } from "../utils/source-chain";
import MatrixCell from "./MatrixCell.vue";
import PermissionIconLegend from "./PermissionIconLegend.vue";

type UnionColumn = { code: string; name: string };

const props = defineProps<{
  sourceChain: SourceChainResult;
  resourceForest: ResourceTreeNode[];
  /** 资源类型定义全集（类型下拉候选，§2.2；候选=全集，防首次授权死锁） */
  typeCandidates: TypeDefResp[];
  /** 🔧 T-FE-018（理解 A）：缺少 TYPE_DEFINITION:VIEW 软依赖——类型下拉与矩阵区禁用+重试，不误报空态 */
  typePermDenied: boolean;
  /** 当前矩阵类型（MatrixContext，§2.2） */
  currentTypeCode: string | null;
  /** 主体已有权限类型（仅用于下拉标记与排序——有权限的排前，§2.2） */
  permissionTypeCodes: string[];
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
  /** 矩阵数据加载中（§3.6 中栏统一骨架屏） */
  matrixLoading: boolean;
  /** 已选中可授权主体 */
  hasSubject: boolean;
  /** 当前主体展示名；紧凑展示在矩阵标题行，避免恢复占高的页面标题卡片 */
  subjectName: string | null;
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
  /** 类型切换（MatrixContext，§2.2；未保存变更确认由页面层完成） */
  (e: "switchType", typeCode: string): void;
  /** 🔧 T-FE-018（理解 A）：TYPE_DEFINITION:VIEW 降级态重试（重新探查权限并加载依赖） */
  (e: "retryDeps"): void;
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
  kind: "all" | "instance";
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
  const typeCode = props.currentTypeCode;
  if (!typeCode) return [];
  const keyword = props.keyword.trim();
  const roots = forestByType.value.get(typeCode) ?? [];
  const filtered = keyword ? filterTreeByKeyword(roots, keyword) : roots;
  // 搜索无命中 → 空（模板显示"未找到匹配的资源"）
  if (keyword && filtered.length === 0) return [];
  // 单类型矩阵（§2.2/§3.1）：ALL 虚拟行 + 当前类型资源实例树（无类型分组行）
  return [
    {
      key: allRowKey(typeCode),
      kind: "all" as const,
      resourceTypeCode: typeCode,
      label: "全部资源（ALL）"
    },
    ...toInstanceRows(filtered)
  ];
});

/** 当前类型是否有资源实例行（空态判断：该类型无任何资源） */
const hasResourceRows = computed(() =>
  rows.value.some(r => r.kind === "instance")
);

// ========== 展开状态（单类型：实例树默认收起至第一层，§3.1；切换类型清空） ==========

const expandedRowKeys = ref<string[]>([]);

watch(
  () => props.currentTypeCode,
  () => {
    // 单类型矩阵无类型分组行；实例节点默认收起（只显示根节点）
    expandedRowKeys.value = [];
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
  // 列对行类型的适用性：当前类型的合并列中无此操作 → 不适用（空白占位）
  const merged = props.sourceChain.columnsByType.get(row.resourceTypeCode);
  if (!merged?.some(c => c.code === opCode)) return [];
  return props.sourceChain.cells.get(row.key)?.get(opCode)?.sources ?? [];
}

function columnApplicable(row: MatrixRow, opCode: string): boolean {
  return (
    props.sourceChain.columnsByType
      .get(row.resourceTypeCode)
      ?.some(c => c.code === opCode) ?? false
  );
}

function handleCellSelect(row: MatrixRow, opCode: string) {
  if (!columnApplicable(row, opCode)) return;
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

// ========== 类型下拉（§2.2：候选=资源类型定义全集；已有权限类型仅标记与排序——有权限的排前） ==========

const typeOptions = computed(() => {
  const withPerm = new Set(props.permissionTypeCodes);
  return [...props.typeCandidates]
    .sort((a, b) => {
      const ap = withPerm.has(a.typeCode) ? 0 : 1;
      const bp = withPerm.has(b.typeCode) ? 0 : 1;
      return ap - bp || a.sortOrder - b.sortOrder;
    })
    .map(t => ({
      typeCode: t.typeCode,
      label: withPerm.has(t.typeCode)
        ? `${t.name}（${t.typeCode} · 已授权）`
        : `${t.name}（${t.typeCode}）`
    }));
});

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
          <span
            v-if="subjectName"
            class="subject-status"
            :title="`当前主体：${subjectName}`"
          >
            当前：<strong>{{ subjectName }}</strong>
          </span>
          <el-tag
            v-if="hasSubject"
            size="small"
            :type="capability === 'edit' ? 'success' : 'info'"
            effect="plain"
            class="capability-tag"
          >
            {{ capability === "edit" ? "可编辑" : "只读" }}
          </el-tag>
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
        <el-tooltip
          content="矩阵一次只呈现一个资源类型；候选 = 资源类型定义全集，主体已有权限类型仅标记与排序"
          placement="top"
        >
          <el-select
            :model-value="currentTypeCode"
            placeholder="资源类型"
            class="toolbar-type-select"
            :disabled="!hasSubject || typeCandidates.length === 0"
            @update:model-value="val => val && emit('switchType', String(val))"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t.typeCode"
              :label="t.label"
              :value="t.typeCode"
            />
          </el-select>
        </el-tooltip>
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
              :label="`${col.name}（${col.code}）`"
              @update:model-value="val => toggleColumn(col.code, !!val)"
            />
          </div>
        </el-popover>
        <PermissionIconLegend />
      </div>
    </div>

    <!-- 主体区 -->
    <div ref="tableWrapRef" class="matrix-body">
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
      <!-- 🔧 T-FE-018（理解 A）：TYPE_DEFINITION:VIEW 软依赖缺失——明确提示权限不足并允许重试，
           不得误报为「暂无资源类型配置」空态（设计 §10 已知缺口的联调定案落地） -->
      <el-result
        v-else-if="typePermDenied"
        icon="warning"
        title="无法加载资源类型"
        sub-title="当前账号缺少类型定义查看权限（TYPE_DEFINITION:VIEW），请联系管理员开通后重试"
      >
        <template #extra>
          <el-button type="primary" @click="emit('retryDeps')">重试</el-button>
        </template>
      </el-result>
      <!-- 类型候选为空（§2.2：候选=全集为空时矩阵区 el-empty 空态） -->
      <el-empty
        v-else-if="typeCandidates.length === 0"
        description="暂无资源类型配置"
      />
      <!-- 类型加载中：统一骨架屏（§3.6） -->
      <el-skeleton
        v-else-if="matrixLoading"
        :rows="9"
        animated
        class="matrix-skeleton"
      />
      <!-- 当前类型无资源（§3.1 空态；矩阵始终渲染当前类型，S11 首次授权不受阻） -->
      <el-empty
        v-else-if="currentTypeCode == null || !hasResourceRows"
        :description="
          keyword.trim() ? '未找到匹配的资源' : '该资源类型暂无资源'
        "
      />

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
            <span v-if="rowData.kind === 'all'" class="all-row">
              {{ rowData.label }}
            </span>
            <span v-else class="instance-row">
              <span class="instance-name">{{ rowData.label }}</span>
              <span class="instance-code">{{ rowData.subLabel }}</span>
            </span>
          </template>
          <!-- 操作列单元格 -->
          <template v-else>
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
                :mark-info="markInfo"
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
          min-width: 0;

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

          .subject-status {
            max-width: 220px;
            padding-left: var(--space-3);
            overflow: hidden;
            text-overflow: ellipsis;
            font-size: 12px;
            color: var(--el-text-color-secondary);
            white-space: nowrap;
            border-left: 1px solid var(--el-border-color-lighter);

            strong {
              font-weight: 600;
              color: var(--el-text-color-primary);
            }
          }

          .capability-tag {
            flex-shrink: 0;
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

        .toolbar-type-select {
          width: 210px;
        }
      }
    }
  }

  .matrix-body {
    position: relative;
    flex: 1;
    min-height: 0;
  }

  .matrix-skeleton {
    padding: var(--space-4);
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
