<script setup lang="ts">
import { h, ref } from "vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { message } from "@/utils/message";
import CycleCheckDialog from "./components/CycleCheckDialog.vue";
import DependencyGraph from "./components/DependencyGraph.vue";
import DeclarationStatusDrawer from "./components/DeclarationStatusDrawer.vue";
import ExplainViewerDrawer from "./components/ExplainViewerDrawer.vue";
import { useResourceDependency } from "./utils/hook";
import {
  type ResourceDependencyResp,
  getDependencyGraph
} from "@/api/resource-dependency";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import Share from "~icons/ep/share";
import WarningFilled from "~icons/ep/warning-filled";
import View from "~icons/ep/view";
import Tickets from "~icons/ep/tickets";

defineOptions({ name: "SystemResourceDependency" });

const {
  canView,
  loading,
  search,
  filteredList,
  resetFilters,
  loadList,
  resourceList,
  operationList,
  resourceTypeOptions,
  resolveResourceName,
  resolveResourceTypeCode,
  bitsToOpNames
} = useResourceDependency();

// 依赖页只读；声明变更由所属服务发布。

// ========== 列定义 ==========
const columns = [
  { label: "依赖关系", prop: "relation", minWidth: 280, slot: "relation" },
  { label: "触发操作", prop: "sourceOps", width: 140, slot: "sourceOps" },
  { label: "要求操作", prop: "requiredOps", width: 140, slot: "requiredOps" },
  { label: "描述", prop: "description", minWidth: 160, slot: "description" },
  { label: "创建时间", prop: "createdAt", width: 160, slot: "createdAt" }
];

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

// ========== 声明诊断 + 来源解释（T-PERM-073，只读诊断抽屉） ==========
const declarationStatusVisible = ref(false);
const explainVisible = ref(false);
</script>

<template>
  <div class="resource-dependency-page">
    <el-empty
      v-if="!canView"
      description="你没有查看资源依赖的权限"
      class="dependency-empty"
    />
    <div v-else class="table-wrap">
      <el-alert
        title="依赖声明由所属业务服务发布，本页提供只读查看与检查。"
        type="info"
        :closable="false"
      />
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
            :icon="useRenderIcon(Tickets)"
            @click="declarationStatusVisible = true"
          >
            声明状态
          </el-button>
          <el-button
            v-if="canView"
            :icon="useRenderIcon(View)"
            @click="explainVisible = true"
          >
            来源解释
          </el-button>
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
                    row.sourceResourceName ||
                    resolveResourceName(row.resourceEntityId)
                  }}</span>
                  <el-tag size="small" type="info" effect="plain">
                    {{
                      row.sourceResourceTypeCode ??
                      resolveResourceTypeCode(row.resourceEntityId) ??
                      "-"
                    }}
                  </el-tag>
                </div>
                <span class="relation-arrow">-&gt;</span>
                <div class="resource-node">
                  <span class="resource-name">{{
                    row.targetResourceName ||
                    resolveResourceName(row.dependsOnResourceEntityId)
                  }}</span>
                  <el-tag size="small" type="success" effect="plain">
                    {{
                      row.targetResourceTypeCode ??
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
                  row.sourceResourceTypeCode ??
                    resolveResourceTypeCode(row.resourceEntityId)
                )
              }}</span>
            </template>
            <template #requiredOps="{ row }">
              <span class="text-sm">{{
                bitsToOpNames(
                  row.requiredOperationBits,
                  row.targetResourceTypeCode ??
                    resolveResourceTypeCode(row.dependsOnResourceEntityId)
                )
              }}</span>
            </template>
            <template #description="{ row }">
              <span class="text-sm text-gray-500">
                {{ row.description || "-" }}
              </span>
            </template>
            <template #createdAt="{ row }">
              <span class="time-cell">{{ row.createdAt }}</span>
            </template>
            <template #empty>
              <el-empty
                :image-size="60"
                description="暂无依赖声明，请由所属服务发布"
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

    <!-- 声明诊断抽屉（T-PERM-073：发布状态 + REJECTED 原因，只读） -->
    <DeclarationStatusDrawer v-model="declarationStatusVisible" />

    <!-- 来源解释抽屉（T-PERM-073：角色自动授权共享 DAG，只读） -->
    <ExplainViewerDrawer
      v-model="explainVisible"
      :resource-type-options="resourceTypeOptions"
      :resource-list="resourceList"
      :operation-list="operationList"
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
