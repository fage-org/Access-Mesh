<script setup lang="ts">
import { computed, h, ref, watch } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { message } from "@/utils/message";
import ResourceForm from "./components/ResourceForm.vue";
import OperationForm from "./components/OperationForm.vue";
import ResourceMoveForm from "./components/ResourceMoveForm.vue";
import { useResourceOperation } from "./utils/hook";
import { RESOURCE_OPERATION_PERMS } from "./utils/perms";
import { bitOr } from "./utils/types";
import {
  type ResourceFormData,
  type OperationFormData,
  type ResourceMoveFormData
} from "./utils/types";
import {
  getResourceDetail,
  type ResourceResp,
  type ResourceTreeNode,
  type OperationPermissionResp
} from "@/api/resource-operation";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Switch from "~icons/ep/switch";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";

defineOptions({ name: "SystemResourceOperation" });

const {
  resourceTypes,
  selectedResourceTypeCode,
  onResourceTypeChange,
  canViewResource,
  canViewOperation,
  treeData,
  resourceLoading,
  resourceSearch,
  selectedNode,
  selectNode,
  loadTree,
  submitResource,
  deleteResource,
  submitMove,
  operations,
  operationLoading,
  operationSearch,
  filteredOperations,
  resetOperationFilters,
  loadOperations,
  submitOperation,
  deleteOperation,
  refreshAll
} = useResourceOperation();

const treeRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/resource-operation.md §权限接线 对齐。
// RESOURCE:VIEW / OPERATION:VIEW 分别隔离：资源树与操作权限表独立可见性。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canView = computed(() => canViewResource.value || canViewOperation.value);
const canCreateResource = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.RESOURCE_ADD)
);
const canEditResource = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.RESOURCE_EDIT)
);
const canDeleteResource = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.RESOURCE_DELETE)
);
const canMoveResource = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.RESOURCE_MOVE)
);
const canCreateOperation = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.OPERATION_ADD)
);
const canEditOperation = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.OPERATION_EDIT)
);
const canDeleteOperation = computed(() =>
  hasPerms(RESOURCE_OPERATION_PERMS.OPERATION_DELETE)
);

// ========== 资源类型下拉 ==========
const resourceTypeOptions = computed(() =>
  resourceTypes.value.map(t => ({ label: t.name, value: t.typeCode }))
);

// ========== 子表列定义（操作权限） ==========
const operationColumns = [
  { label: "操作编码", prop: "code", minWidth: 120, slot: "code" },
  { label: "名称", prop: "name", minWidth: 100 },
  { label: "独占位", prop: "binaryBit", width: 90, slot: "binaryBit" },
  { label: "继承掩码", prop: "inheritMask", width: 90, slot: "inheritMask" },
  { label: "有效位", prop: "effective", width: 90, slot: "effective" },
  { label: "更新时间", prop: "updatedAt", width: 160, slot: "updatedAt" },
  {
    label: "操作",
    prop: "operation",
    width: 110,
    fixed: "right" as const,
    slot: "operation"
  }
];

type DialogForm<T> = {
  validate: () => Promise<boolean>;
  getFormData: () => T;
};

function getDialogForm<T>(element: unknown): DialogForm<T> | null {
  if (
    element &&
    typeof element === "object" &&
    "validate" in element &&
    "getFormData" in element &&
    typeof element.validate === "function" &&
    typeof element.getFormData === "function"
  ) {
    return element as DialogForm<T>;
  }
  return null;
}

// ========== 资源弹窗 ==========
function openCreateResource(parentNode?: ResourceTreeNode | null) {
  if (!selectedResourceTypeCode.value) return;
  let formRef: DialogForm<ResourceFormData> | null = null;
  addDialog({
    title: parentNode ? `新增子资源 · ${parentNode.name}` : "新增资源",
    width: "560px",
    contentRenderer: () =>
      h(ResourceForm, {
        ref: element => {
          formRef = getDialogForm<ResourceFormData>(element);
        },
        mode: "create",
        defaultResourceTypeCode: selectedResourceTypeCode.value,
        parentNode: parentNode ?? null,
        resourceTree: treeData.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitResource(formRef.getFormData(), "create");
      if (saved) done();
      else closeLoading();
    }
  });
}

async function openEditResource() {
  const node = selectedNode.value;
  if (!node) return;
  // 树节点不含 extra，编辑需完整数据 -> 调 detail 拉取
  let detail: ResourceResp;
  try {
    detail = await getResourceDetail(node.id);
  } catch (e: any) {
    message(e.message || "加载资源详情失败", { type: "error" });
    return;
  }
  let formRef: DialogForm<ResourceFormData> | null = null;
  addDialog({
    title: `编辑资源 · ${detail.code}`,
    width: "560px",
    contentRenderer: () =>
      h(ResourceForm, {
        ref: element => {
          formRef = getDialogForm<ResourceFormData>(element);
        },
        mode: "edit",
        initialData: detail,
        resourceTree: treeData.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitResource(
        formRef.getFormData(),
        "edit",
        detail.id
      );
      if (saved) done();
      else closeLoading();
    }
  });
}

function openMoveForm() {
  const node = selectedNode.value;
  if (!node) return;
  let formRef: DialogForm<ResourceMoveFormData> | null = null;
  addDialog({
    title: `移动资源 · ${node.name}`,
    width: "480px",
    contentRenderer: () =>
      h(ResourceMoveForm, {
        ref: element => {
          formRef = getDialogForm<ResourceMoveFormData>(element);
        },
        currentNode: node,
        resourceTree: treeData.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const moved = await submitMove(formRef.getFormData());
      if (moved) done();
      else closeLoading();
    }
  });
}

function onDeleteResource() {
  const node = selectedNode.value;
  if (!node) return;
  deleteResource(node);
}

// ========== 操作权限弹窗 ==========
function openOperationForm(
  mode: "create" | "edit",
  row?: OperationPermissionResp
) {
  if (!selectedResourceTypeCode.value) return;
  let formRef: DialogForm<OperationFormData> | null = null;
  addDialog({
    title:
      mode === "create"
        ? `新增操作 · ${selectedResourceTypeCode.value}`
        : `编辑操作 · ${row?.code}`,
    width: "520px",
    contentRenderer: () =>
      h(OperationForm, {
        ref: element => {
          formRef = getDialogForm<OperationFormData>(element);
        },
        mode,
        resourceTypeCode: selectedResourceTypeCode.value!,
        initialData: row ?? null
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitOperation(formRef.getFormData(), mode, row?.id);
      if (saved) done();
      else closeLoading();
    }
  });
}

function onDeleteOperation(row: OperationPermissionResp) {
  deleteOperation(row);
}

// ========== 树搜索过滤 ==========
function filterTreeNode(value: string, data: ResourceTreeNode) {
  if (!value) return true;
  return data.name.includes(value) || data.code.includes(value);
}

/** 有效位 = 独占位 | 继承掩码。
 *  BigInt 位或（兼容 63 位 schema）；JS `|` 强转 32 位，>2^31 截断。 */
function effectiveBits(row: OperationPermissionResp): number {
  return bitOr(row.binaryBit, row.inheritMask);
}

watch(resourceSearch, val => {
  treeRef.value?.filter(val);
});
</script>

<template>
  <div class="resource-operation-page">
    <el-empty
      v-if="!canView"
      description="你没有查看资源与操作定义的权限"
      class="permission-empty"
    />
    <template v-else>
      <!-- ========== 左侧：资源类型 + 资源树 ========== -->
      <aside class="resource-panel">
        <div class="panel-header">
          <div class="panel-title">
            资源
            <span v-if="canViewResource" class="panel-count">{{
              treeData.length
            }}</span>
          </div>
          <div class="panel-header-actions">
            <el-button
              size="small"
              :icon="useRenderIcon(Refresh)"
              :loading="resourceLoading"
              title="刷新资源与操作"
              @click="refreshAll"
            />
            <el-button
              v-if="canCreateResource"
              size="small"
              type="primary"
              plain
              :icon="useRenderIcon(AddFill)"
              :disabled="!selectedResourceTypeCode"
              @click="openCreateResource(null)"
            >
              新增
            </el-button>
          </div>
        </div>

        <div class="panel-filter">
          <el-select
            :model-value="selectedResourceTypeCode"
            placeholder="选择资源类型"
            size="small"
            class="w-full!"
            @change="onResourceTypeChange"
          >
            <el-option
              v-for="opt in resourceTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-if="canViewResource"
            v-model="resourceSearch"
            placeholder="名称 / 编码"
            clearable
            size="small"
            :prefix-icon="useRenderIcon(Search)"
          />
        </div>

        <div
          v-if="canViewResource"
          v-loading="resourceLoading"
          class="tree-wrap"
        >
          <el-tree
            v-if="treeData.length > 0"
            ref="treeRef"
            :data="treeData"
            node-key="id"
            :props="{ children: 'children', label: 'name' }"
            :expand-on-click-node="false"
            :filter-node-method="filterTreeNode"
            highlight-current
            default-expand-all
            @node-click="(n: ResourceTreeNode) => selectNode(n)"
          >
            <template #default="{ data }">
              <div class="tree-node" :class="{ disabled: data.status === 0 }">
                <span
                  class="tree-node__dot"
                  :class="data.status === 1 ? 'is-on' : 'is-off'"
                />
                <span class="tree-node__name">{{ data.name }}</span>
                <span class="tree-node__code font-mono">{{ data.code }}</span>
              </div>
            </template>
          </el-tree>
          <el-empty
            v-else
            :image-size="48"
            :description="
              selectedResourceTypeCode ? '该类型下暂无资源' : '请先选择资源类型'
            "
          />
        </div>
        <div v-else class="tree-wrap tree-wrap--placeholder">
          <el-empty :image-size="48" description="无资源查看权限" />
        </div>
      </aside>

      <!-- ========== 右侧：资源详情 + 操作权限表 ========== -->
      <section class="detail-area">
        <!-- 选中资源信息条 -->
        <div v-if="selectedNode" class="resource-info-bar">
          <div class="resource-info">
            <span class="font-mono text-sm">{{
              selectedNode.resourceTypeCode
            }}</span>
            <span class="resource-name">{{ selectedNode.name }}</span>
            <el-tag
              :type="selectedNode.status === 1 ? 'success' : 'info'"
              size="small"
              effect="light"
            >
              {{ selectedNode.status === 1 ? "启用" : "停用" }}
            </el-tag>
            <span class="resource-meta">
              编码
              <span class="font-mono">{{ selectedNode.code }}</span>
            </span>
            <span v-if="selectedNode.path" class="resource-meta">
              路径
              <span class="font-mono">{{ selectedNode.path }}</span>
            </span>
          </div>
          <div class="resource-actions">
            <el-button
              v-if="canCreateResource"
              size="small"
              :icon="useRenderIcon(AddFill)"
              @click="openCreateResource(selectedNode)"
            >
              新增子资源
            </el-button>
            <el-button
              v-if="canEditResource"
              size="small"
              :icon="useRenderIcon(EditPen)"
              @click="openEditResource"
            >
              编辑
            </el-button>
            <el-button
              v-if="canMoveResource"
              size="small"
              :icon="useRenderIcon(Switch)"
              @click="openMoveForm"
            >
              移动
            </el-button>
            <el-button
              v-if="canDeleteResource"
              size="small"
              type="danger"
              plain
              :icon="useRenderIcon(Delete)"
              @click="onDeleteResource"
            >
              删除
            </el-button>
          </div>
        </div>
        <!-- 未选中节点占位（操作权限按资源类型展示，不依赖选中节点） -->
        <div
          v-else-if="selectedResourceTypeCode && canViewResource"
          class="resource-info-bar"
        >
          <span class="text-sm text-gray-400">
            未选中资源节点（操作权限按资源类型展示，无需选中）
          </span>
        </div>

        <!-- 操作权限表（选了资源类型即展示，不依赖 selectedNode） -->
        <div
          v-if="selectedResourceTypeCode && canViewOperation"
          class="operation-section"
        >
          <PureTableBar
            title="操作权限"
            :columns="operationColumns"
            @refresh="loadOperations"
          >
            <template #title>
              <el-form :inline="true" class="search-form-inline">
                <el-form-item label="编码/名称" class="mb-0!">
                  <el-input
                    v-model="operationSearch.keyword"
                    placeholder="操作编码或名称"
                    clearable
                    class="w-52!"
                    :prefix-icon="useRenderIcon(Search)"
                  />
                </el-form-item>
                <el-form-item class="mb-0!">
                  <el-button link type="primary" @click="resetOperationFilters">
                    重置
                  </el-button>
                </el-form-item>
              </el-form>
            </template>
            <template #buttons>
              <el-button
                v-if="canCreateOperation"
                type="primary"
                :icon="useRenderIcon(AddFill)"
                @click="openOperationForm('create')"
              >
                新增操作
              </el-button>
            </template>
            <template v-slot="{ size, dynamicColumns }">
              <pure-table
                row-key="id"
                align-whole="center"
                table-layout="auto"
                :loading="operationLoading"
                :size="size"
                :data="filteredOperations"
                :columns="dynamicColumns"
                :header-cell-style="{
                  background: 'var(--el-fill-color-light)',
                  color: 'var(--el-text-color-primary)'
                }"
              >
                <template #code="{ row }">
                  <span class="font-mono font-600">{{ row.code }}</span>
                </template>
                <template #binaryBit="{ row }">
                  <span class="bit-cell">{{ row.binaryBit }}</span>
                </template>
                <template #inheritMask="{ row }">
                  <span class="bit-cell">{{ row.inheritMask }}</span>
                </template>
                <template #effective="{ row }">
                  <span class="bit-cell effective">{{
                    effectiveBits(row)
                  }}</span>
                </template>
                <template #updatedAt="{ row }">
                  <span class="time-cell">{{ row.updatedAt }}</span>
                </template>
                <template #operation="{ row }">
                  <el-button
                    v-if="canEditOperation"
                    class="reset-margin"
                    link
                    type="primary"
                    :size="size"
                    @click="openOperationForm('edit', row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="canDeleteOperation"
                    class="reset-margin"
                    link
                    type="danger"
                    :size="size"
                    @click="onDeleteOperation(row)"
                  >
                    删除
                  </el-button>
                  <span
                    v-if="!canEditOperation && !canDeleteOperation"
                    class="text-sm text-gray-400"
                  >
                    -
                  </span>
                </template>
                <template #empty>
                  <el-empty
                    :image-size="60"
                    description="该资源类型暂无操作权限，可手工新增"
                  />
                </template>
              </pure-table>
            </template>
          </PureTableBar>
        </div>

        <el-empty
          v-else-if="!selectedResourceTypeCode"
          description="请先选择资源类型"
          class="detail-empty"
        />
        <el-empty
          v-else
          description="你没有查看操作权限的权限"
          class="detail-empty"
        />
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@media (width <= 48rem) {
  .resource-operation-page {
    grid-template-rows: auto 1fr;
    grid-template-columns: 1fr;
  }

  .resource-panel {
    max-height: 16rem;
  }
}

.resource-operation-page {
  display: grid;
  grid-template-columns: minmax(240px, 300px) 1fr;
  gap: var(--space-2);
  height: calc(100vh - var(--header-offset));
  overflow: hidden;
}

.permission-empty {
  grid-column: 1 / -1;
  place-self: center center;
}

/* ========== 左侧资源面板 ========== */
.resource-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.panel-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-header-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
  align-items: center;
}

.panel-title {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.panel-count {
  padding: 0 var(--space-1);
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 10px;
}

.panel-filter {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.tree-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: var(--space-1) var(--space-2);
  overflow: auto;

  :deep(.el-tree) {
    background: transparent;

    .el-tree-node__content {
      height: 32px;
    }
  }
}

.tree-wrap--placeholder {
  align-items: center;
  justify-content: center;
}

.tree-node {
  display: flex;
  flex: 1;
  gap: var(--space-2);
  align-items: center;
  overflow: hidden;

  &.disabled {
    opacity: 0.6;
  }
}

.tree-node__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;

  &.is-on {
    background: var(--el-color-success);
  }

  &.is-off {
    background: var(--el-color-info);
  }
}

.tree-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.tree-node__code {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ========== 右侧详情区 ========== */
.detail-area {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.resource-info-bar {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-3);
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.resource-info {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.resource-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.resource-meta {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.resource-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--space-2);
}

.operation-section {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.search-form-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;

  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    padding-right: 4px;
  }
}

/* PureTableBar 内部布局 */
.operation-section :deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
}

.operation-section :deep(.el-scrollbar__wrap) {
  height: 100%;
}

.operation-section :deep(.el-scrollbar__view) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.operation-section :deep(.pure-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.operation-section :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

.detail-empty {
  flex: 1;
}

.bit-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: var(--el-text-color-regular);

  &.effective {
    font-weight: 600;
    color: var(--el-color-primary);
  }
}

.time-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 -> 同级看顺序，再加 tag 提升至 0,2,1 */
div.resource-operation-page.main-content {
  margin: var(--space-3);
}
</style>
