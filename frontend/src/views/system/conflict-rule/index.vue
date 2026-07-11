<script setup lang="ts">
import { h } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import ConflictForm from "./components/ConflictForm.vue";
import DetectDialog from "./components/DetectDialog.vue";
import { useConflictRule } from "./utils/hook";
import { CONFLICT_TYPE_LABEL } from "@/api/conflict-rule";
import { type ConflictRuleResp } from "@/api/conflict-rule";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import WarningFilled from "~icons/ep/warning-filled";

defineOptions({ name: "SystemConflictRule" });

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
  submitConflictRule,
  deleteConflictRule,
  roleOptions,
  operationOptions,
  resourceTypeOptions,
  resolveFirstName,
  resolveSecondName,
  resolveResourceTypeName
} = useConflictRule();

// ========== 权限门控 ==========
// 与 docs/design/frontend/conflict-rule.md §权限接线 对齐。
// CONFLICT_RULE:VIEW 门控路由可达性；CREATE/UPDATE/DELETE 三档独立门控写按钮（非 MANAGE，对齐后端）。
// detect 按钮复用 VIEW 门控（后端 detect 无独立权限校验，🔧 登记同 T-PERM-030）。

// ========== 列定义 ==========
const columns = [
  { label: "冲突类型", prop: "conflictType", width: 100, slot: "conflictType" },
  {
    label: "规则内容",
    prop: "ruleContent",
    minWidth: 240,
    slot: "ruleContent"
  },
  { label: "描述", prop: "description", minWidth: 180, slot: "description" },
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
  let formRef: DialogForm<import("./utils/types").ConflictFormData> | null =
    null;
  addDialog({
    title: "新增冲突规则",
    width: "600px",
    contentRenderer: () =>
      h(ConflictForm, {
        ref: (element: unknown) => {
          formRef =
            getDialogForm<import("./utils/types").ConflictFormData>(element);
        },
        mode: "create",
        roleOptions: roleOptions.value,
        operationOptions: operationOptions.value,
        resourceTypeOptions: resourceTypeOptions.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitConflictRule(formRef.getFormData(), "create");
      if (saved) done();
      else closeLoading();
    }
  });
}

function openEdit(row: ConflictRuleResp) {
  let formRef: DialogForm<import("./utils/types").ConflictFormData> | null =
    null;
  addDialog({
    title: `编辑冲突规则 · #${row.id}`,
    width: "600px",
    contentRenderer: () =>
      h(ConflictForm, {
        ref: (element: unknown) => {
          formRef =
            getDialogForm<import("./utils/types").ConflictFormData>(element);
        },
        mode: "edit",
        initialData: row,
        roleOptions: roleOptions.value,
        operationOptions: operationOptions.value,
        resourceTypeOptions: resourceTypeOptions.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitConflictRule(
        formRef.getFormData(),
        "edit",
        row.id
      );
      if (saved) done();
      else closeLoading();
    }
  });
}

function openDetect() {
  addDialog({
    title: "冲突检测",
    width: "720px",
    hideFooter: true,
    contentRenderer: () =>
      h(DetectDialog, {
        operationOptions: operationOptions.value,
        resourceTypeOptions: resourceTypeOptions.value
      })
  });
}

function onDelete(row: ConflictRuleResp) {
  deleteConflictRule(row);
}
</script>

<template>
  <div class="conflict-rule-page">
    <el-empty
      v-if="!canView"
      description="你没有查看冲突规则的权限"
      class="conflict-empty"
    />
    <div v-else class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="loadList">
        <template #title>
          <el-form :inline="true" class="search-form-inline">
            <el-form-item label="类型" class="mb-0!">
              <el-select
                v-model="search.conflictType"
                placeholder="全部"
                clearable
                class="w-36!"
              >
                <el-option label="角色互斥" value="ROLE_MUTEX" />
                <el-option label="权限互斥" value="PERM_MUTEX" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词" class="mb-0!">
              <el-input
                v-model="search.keyword"
                placeholder="对象名称或描述"
                clearable
                class="w-52!"
                :prefix-icon="useRenderIcon(Search)"
              />
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
            v-if="canView"
            :icon="useRenderIcon(WarningFilled)"
            @click="openDetect"
          >
            冲突检测
          </el-button>
          <el-button
            v-if="canCreate"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openCreate"
          >
            新增规则
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
            <template #conflictType="{ row }">
              <el-tag
                :type="row.conflictType === 'ROLE_MUTEX' ? 'warning' : 'danger'"
                size="small"
                effect="light"
              >
                {{ CONFLICT_TYPE_LABEL[row.conflictType] ?? row.conflictType }}
              </el-tag>
            </template>
            <template #ruleContent="{ row }">
              <div class="rule-content">
                <span class="rule-name">{{ resolveFirstName(row) }}</span>
                <span class="rule-sep">↔</span>
                <span class="rule-name">{{ resolveSecondName(row) }}</span>
                <el-tag
                  v-if="row.conflictType === 'PERM_MUTEX'"
                  size="small"
                  type="info"
                  effect="plain"
                  class="rule-type-tag"
                >
                  {{ resolveResourceTypeName(row) }}
                </el-tag>
              </div>
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
                description="暂无冲突规则，可手工新增"
              />
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.conflict-rule-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.conflict-empty {
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

.rule-content {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  align-items: center;
}

.rule-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rule-sep {
  font-size: 13px;
  color: var(--el-color-danger);
}

.rule-type-tag {
  margin-left: var(--space-1);
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
div.conflict-rule-page.main-content {
  margin: var(--space-3);
}
</style>
