<script setup lang="ts">
import { h, ref } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { message } from "@/utils/message";
import DependencyForm from "./components/DependencyForm.vue";
import CycleCheckDialog from "./components/CycleCheckDialog.vue";
import DependencyGraph from "./components/DependencyGraph.vue";
import { useResourceDependency } from "./utils/hook";
import {
  type ResourceDependencyResp,
  getDependencyGraph
} from "@/api/resource-dependency";
import AddFill from "~icons/ri/add-circle-line";
import EditPen from "~icons/ep/edit-pen";
import Delete from "~icons/ep/delete";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import Share from "~icons/ep/share";
import WarningFilled from "~icons/ep/warning-filled";

defineOptions({ name: "SystemResourceDependency" });

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
  submitDependency,
  deleteDependency,
  resourceList,
  operationList,
  resourceTypeOptions,
  resolveResourceName,
  resolveResourceTypeCode,
  bitsToOpNames
} = useResourceDependency();

// ========== 权限门控 ==========
// 与 docs/design/frontend/resource-dependency.md §权限接线 对齐。
// DEPENDENCY:VIEW 门控路由可达性；CREATE/UPDATE/DELETE 三档独立门控写按钮（非 MANAGE，对齐后端）。
// SYNC 权限码已定义但 batch-sync P0 标 TODO，不暴露按钮。
// 环检测/依赖图复用 VIEW 门控（后端 check/graph 无独立权限校验，🔧 登记同 T-PERM-031）。

// ========== 列定义 ==========
const columns = [
  { label: "依赖关系", prop: "relation", minWidth: 280, slot: "relation" },
  { label: "触发操作", prop: "sourceOps", width: 140, slot: "sourceOps" },
  { label: "要求操作", prop: "requiredOps", width: 140, slot: "requiredOps" },
  { label: "自动", prop: "autoGrant", width: 80, slot: "autoGrant" },
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
  let formRef: DialogForm<import("./utils/types").DependencyFormData> | null =
    null;
  addDialog({
    title: "新增资源依赖",
    width: "600px",
    contentRenderer: () =>
      h(DependencyForm, {
        ref: (element: unknown) => {
          formRef =
            getDialogForm<import("./utils/types").DependencyFormData>(element);
        },
        mode: "create",
        resourceTypeOptions: resourceTypeOptions.value,
        resourceList: resourceList.value,
        operationList: operationList.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitDependency(formRef.getFormData(), "create");
      if (saved) done();
      else closeLoading();
    }
  });
}

function openEdit(row: ResourceDependencyResp) {
  let formRef: DialogForm<import("./utils/types").DependencyFormData> | null =
    null;
  addDialog({
    title: `编辑资源依赖 · #${row.id}`,
    width: "600px",
    contentRenderer: () =>
      h(DependencyForm, {
        ref: (element: unknown) => {
          formRef =
            getDialogForm<import("./utils/types").DependencyFormData>(element);
        },
        mode: "edit",
        initialData: row,
        resourceTypeOptions: resourceTypeOptions.value,
        resourceList: resourceList.value,
        operationList: operationList.value
      }),
    beforeSure: async (done, { closeLoading }) => {
      if (!formRef || !(await formRef.validate())) {
        closeLoading();
        return;
      }
      const saved = await submitDependency(
        formRef.getFormData(),
        "edit",
        row.id
      );
      if (saved) done();
      else closeLoading();
    }
  });
}

function openCycleCheck() {
  addDialog({
    title: "循环依赖检测",
    width: "600px",
    hideFooter: true,
    contentRenderer: () =>
      h(CycleCheckDialog, {
        resourceTypeOptions: resourceTypeOptions.value,
        resourceList: resourceList.value
      })
  });
}

// ========== 依赖图 ==========
const graphVisible = ref(false);
const graphDeps = ref<ResourceDependencyResp[]>([]);

async function openGraph() {
  try {
    const res = await getDependencyGraph({});
    graphDeps.value = res.items;
  } catch (e: any) {
    message(e.message || "加载依赖图失败", { type: "error" });
    return;
  }
  graphVisible.value = true;
}

function onDelete(row: ResourceDependencyResp) {
  deleteDependency(row);
}
</script>

<template>
  <div class="resource-dependency-page">
    <el-empty
      v-if="!canView"
      description="你没有查看资源依赖的权限"
      class="dependency-empty"
    />
    <div v-else class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="loadList">
        <template #title>
          <el-form :inline="true" class="search-form-inline">
            <el-form-item label="关键词" class="mb-0!">
              <el-input
                v-model="search.keyword"
                placeholder="资源名称/编码/描述"
                clearable
                class="w-56!"
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
            @click="openCycleCheck"
          >
            环检测
          </el-button>
          <el-button
            v-if="canView"
            :icon="useRenderIcon(Share)"
            @click="openGraph"
          >
            依赖图
          </el-button>
          <el-button
            v-if="canCreate"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openCreate"
          >
            新增依赖
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
            <template #relation="{ row }">
              <div class="relation-content">
                <div class="resource-node">
                  <span class="resource-name">{{
                    resolveResourceName(row.resourceEntityId)
                  }}</span>
                  <el-tag size="small" type="info" effect="plain">
                    {{ resolveResourceTypeCode(row.resourceEntityId) ?? "-" }}
                  </el-tag>
                </div>
                <span class="relation-arrow">-&gt;</span>
                <div class="resource-node">
                  <span class="resource-name">{{
                    resolveResourceName(row.dependsOnResourceEntityId)
                  }}</span>
                  <el-tag size="small" type="success" effect="plain">
                    {{
                      resolveResourceTypeCode(row.dependsOnResourceEntityId) ??
                      "-"
                    }}
                  </el-tag>
                </div>
              </div>
            </template>
            <template #sourceOps="{ row }">
              <span class="text-sm">{{
                bitsToOpNames(
                  row.sourceOperationBits,
                  resolveResourceTypeCode(row.resourceEntityId)
                )
              }}</span>
            </template>
            <template #requiredOps="{ row }">
              <span class="text-sm">{{
                bitsToOpNames(
                  row.requiredOperationBits,
                  resolveResourceTypeCode(row.dependsOnResourceEntityId)
                )
              }}</span>
            </template>
            <template #autoGrant="{ row }">
              <el-tag
                :type="row.autoGrant ? 'success' : 'info'"
                size="small"
                effect="light"
              >
                {{ row.autoGrant ? "自动" : "手动" }}
              </el-tag>
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
                description="暂无资源依赖，可手工新增"
              />
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>

    <!-- 依赖图抽屉 -->
    <DependencyGraph
      v-model="graphVisible"
      :deps="graphDeps"
      :resource-list="resourceList"
    />
  </div>
</template>

<style lang="scss" scoped>
.resource-dependency-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.dependency-empty {
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

.relation-content {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  align-items: center;
}

.resource-node {
  display: inline-flex;
  gap: var(--space-1);
  align-items: center;
}

.resource-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.relation-arrow {
  font-size: 13px;
  color: var(--el-color-primary);
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
div.resource-dependency-page.main-content {
  margin: var(--space-3);
}
</style>
