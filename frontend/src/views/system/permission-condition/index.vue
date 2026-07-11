<script setup lang="ts">
import { h } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import ConditionForm from "./components/ConditionForm.vue";
import { usePermissionCondition } from "./utils/hook";
import { CONDITION_PERMS } from "./utils/perms";
import { summarizeRules, type ConditionFormData } from "./utils/types";
import { type ConditionResp } from "@/api/permission-condition";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";

defineOptions({ name: "SystemPermissionCondition" });

const {
  canView,
  canCreate,
  canEdit,
  canDelete,
  loading,
  search,
  filteredList,
  resetFilters,
  loadList,
  submitCondition,
  deleteCondition
} = usePermissionCondition();

// ========== 权限门控 ==========
// 与 docs/design/frontend/permission-condition.md §权限接线 对齐。
// CONDITION:VIEW 门控路由可达性；CREATE/UPDATE/DELETE 三档独立门控写按钮（非 MANAGE，对齐后端）。
// canView/canCreate/canEdit/canDelete 来自 hook，computed 包装响应 store.permissions 变化。

// ========== 列定义 ==========
const columns = [
  { label: "编码", prop: "code", minWidth: 140, slot: "code" },
  { label: "名称", prop: "name", minWidth: 120 },
  { label: "启用", prop: "enabled", width: 80, slot: "enabled" },
  {
    label: "Gateway 评估",
    prop: "gatewayEvaluable",
    width: 120,
    slot: "gatewayEvaluable"
  },
  { label: "规则", prop: "rules", minWidth: 220, slot: "rules" },
  { label: "描述", prop: "description", minWidth: 160, slot: "description" },
  { label: "创建时间", prop: "createdAt", width: 160, slot: "createdAt" },
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

// ========== 弹窗 ==========
function openCreate() {
  let formRef: DialogForm<ConditionFormData> | null = null;
  addDialog({
    title: "新增条件",
    width: "640px",
    contentRenderer: () =>
      h(ConditionForm, {
        ref: (element: unknown) => {
          formRef = getDialogForm<ConditionFormData>(element);
        },
        mode: "create"
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitCondition(formRef.getFormData(), "create");
      if (saved) done();
      else closeLoading();
    }
  });
}

function openEdit(row: ConditionResp) {
  let formRef: DialogForm<ConditionFormData> | null = null;
  addDialog({
    title: `编辑条件 · ${row.code}`,
    width: "640px",
    contentRenderer: () =>
      h(ConditionForm, {
        ref: (element: unknown) => {
          formRef = getDialogForm<ConditionFormData>(element);
        },
        mode: "edit",
        initialData: row
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitCondition(
        formRef.getFormData(),
        "edit",
        row.id
      );
      if (saved) done();
      else closeLoading();
    }
  });
}

function onDelete(row: ConditionResp) {
  deleteCondition(row);
}
</script>

<template>
  <div class="permission-condition-page">
    <el-empty
      v-if="!canView"
      description="你没有查看权限条件的权限"
      class="permission-empty"
    />
    <div v-else class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="loadList">
        <template #title>
          <el-form :inline="true" class="search-form-inline">
            <el-form-item label="编码/名称" class="mb-0!">
              <el-input
                v-model="search.keyword"
                placeholder="条件编码或名称"
                clearable
                class="w-52!"
                :prefix-icon="useRenderIcon(Search)"
              />
            </el-form-item>
            <el-form-item label="状态" class="mb-0!">
              <el-select
                v-model="search.enabled"
                placeholder="全部"
                clearable
                class="w-32!"
              >
                <el-option label="启用" value="true" />
                <el-option label="停用" value="false" />
              </el-select>
            </el-form-item>
            <el-form-item class="mb-0!">
              <el-button :icon="useRenderIcon(Refresh)" @click="resetFilters">
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </template>
        <template #buttons>
          <el-button
            v-if="canCreate"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openCreate"
          >
            新增条件
          </el-button>
        </template>
        <template v-slot="{ size, dynamicColumns }">
          <pure-table
            row-key="id"
            align-whole="center"
            table-layout="auto"
            :loading="loading"
            :size="size"
            :data="filteredList"
            :columns="dynamicColumns"
            :header-cell-style="{
              background: 'var(--el-fill-color-light)',
              color: 'var(--el-text-color-primary)'
            }"
          >
            <template #code="{ row }">
              <span class="font-mono font-600">{{ row.code }}</span>
            </template>
            <template #enabled="{ row }">
              <el-tag
                :type="row.enabled ? 'success' : 'info'"
                size="small"
                effect="light"
              >
                {{ row.enabled ? "启用" : "停用" }}
              </el-tag>
            </template>
            <template #gatewayEvaluable="{ row }">
              <el-tag
                :type="row.gatewayEvaluable ? 'warning' : 'info'"
                size="small"
                effect="plain"
              >
                {{ row.gatewayEvaluable ? "已下发" : "实时鉴权" }}
              </el-tag>
            </template>
            <template #rules="{ row }">
              <el-popover
                trigger="hover"
                placement="top"
                :width="360"
                :show-after="200"
              >
                <template #reference>
                  <span class="rules-summary">{{
                    summarizeRules(row.conditionRules)
                  }}</span>
                </template>
                <pre class="rules-json">{{ row.conditionRules }}</pre>
              </el-popover>
            </template>
            <template #description="{ row }">
              <span class="text-sm text-gray-500">
                {{ row.description || "-" }}
              </span>
            </template>
            <template #createdAt="{ row }">
              <span class="time-cell">{{ row.createdAt }}</span>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canEdit"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(EditPen)"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canDelete"
                class="reset-margin"
                link
                type="danger"
                :size="size"
                :icon="useRenderIcon(Delete)"
                @click="onDelete(row)"
              >
                删除
              </el-button>
              <span v-if="!canEdit && !canDelete" class="text-sm text-gray-400">
                -
              </span>
            </template>
            <template #empty>
              <el-empty
                :image-size="60"
                description="暂无权限条件，可手工新增"
              />
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.permission-condition-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.permission-empty {
  flex: 1;
  place-self: center center;
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

.table-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* PureTableBar 内部布局 */
.table-wrap :deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
}

.table-wrap :deep(.el-scrollbar__wrap) {
  height: 100%;
}

.table-wrap :deep(.el-scrollbar__view) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.table-wrap :deep(.pure-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-wrap :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

.table-wrap :deep(.el-table__body-wrapper) {
  max-height: calc(100vh - var(--table-offset));
  overflow-y: auto;
}

.rules-summary {
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.rules-json {
  max-height: 240px;
  margin: 0;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
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
div.permission-condition-page.main-content {
  margin: var(--space-3);
}
</style>
