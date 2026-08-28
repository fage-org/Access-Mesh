<script setup lang="ts">
import { ref, computed } from "vue";
import { useOperationLog } from "./utils/hook";
import LogDetailDrawer from "./components/LogDetailDrawer.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { hasPerms } from "@/utils/auth";
import { OPERATION_LOG_PERMS } from "./utils/perms";
import { MODULE_OPTIONS } from "./utils/types";
import type { OperationLogResp } from "@/api/operation-log";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import View from "~icons/ep/view";

defineOptions({
  name: "SystemOperationLog"
});

const {
  tableData,
  loading,
  searchForm,
  pagination,
  actionOptions,
  loadTable,
  onSearch,
  onReset,
  onPageChange,
  onPageSizeChange
} = useOperationLog();

const tableRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/operation-log.md §权限接线 对齐。
// 独立 OPERATION_LOG:VIEW 门禁（T-PERM-025 审计分离，不再复用 SYSTEM_CONFIG:VIEW）。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canView = computed(() => hasPerms(OPERATION_LOG_PERMS.LOG_VIEW));

// ========== 详情抽屉 ==========
const drawerVisible = ref(false);
const currentLog = ref<OperationLogResp | null>(null);

function openDetail(row: OperationLogResp) {
  currentLog.value = row;
  drawerVisible.value = true;
}

// ========== 列定义 ==========
const columns = [
  { label: "时间", prop: "createdAt", width: 170 },
  { label: "模块", prop: "module", width: 150, slot: "module" },
  { label: "操作", prop: "action", width: 120, slot: "action" },
  { label: "操作人", prop: "operatorName", minWidth: 120 },
  { label: "目标类型", prop: "targetType", minWidth: 140 },
  {
    label: "摘要",
    prop: "summary",
    minWidth: 280,
    slot: "summary"
  },
  {
    label: "操作",
    prop: "operation",
    width: 90,
    fixed: "right" as const,
    slot: "operation"
  }
];
</script>

<template>
  <div class="operation-log-page">
    <div class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="onSearch">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="模块" class="mb-0!">
              <el-select
                v-model="searchForm.module"
                placeholder="全部"
                clearable
                class="w-36!"
                @change="onSearch"
              >
                <el-option
                  v-for="opt in MODULE_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="操作" class="mb-0!">
              <el-select
                v-model="searchForm.action"
                placeholder="全部"
                clearable
                filterable
                class="w-44!"
                @change="onSearch"
              >
                <el-option
                  v-for="opt in actionOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="操作者" class="mb-0!">
              <el-input-number
                v-model="searchForm.operatorId"
                :min="1"
                :controls="false"
                placeholder="用户 ID"
                class="w-30!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="时间" class="mb-0!">
              <el-date-picker
                v-model="searchForm.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                class="w-64!"
                @change="onSearch"
              />
            </el-form-item>
            <el-form-item label="目标类型" class="mb-0!">
              <el-input
                v-model="searchForm.targetType"
                placeholder="如 abstract_role"
                clearable
                class="w-36!"
                @keyup.enter="onSearch"
                @clear="onSearch"
              />
            </el-form-item>
            <el-form-item class="mb-0!">
              <el-button
                type="primary"
                :icon="useRenderIcon(Search)"
                :loading="loading"
                @click="onSearch"
              >
                搜索
              </el-button>
              <el-button :icon="useRenderIcon(Refresh)" @click="onReset()">
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </template>
        <template v-slot="{ size, dynamicColumns }">
          <pure-table
            ref="tableRef"
            row-key="id"
            align-whole="center"
            table-layout="auto"
            :loading="loading"
            :size="size"
            :data="tableData"
            :columns="dynamicColumns"
            :pagination="{
              currentPage: pagination.page,
              pageSize: pagination.size,
              total: pagination.total
            }"
            :header-cell-style="{
              background: 'var(--el-fill-color-light)',
              color: 'var(--el-text-color-primary)'
            }"
            @page-size-change="onPageSizeChange"
            @page-current-change="onPageChange"
          >
            <template #module="{ row }">
              <el-tag size="small" effect="plain" class="font-mono">
                {{ row.module }}
              </el-tag>
            </template>
            <template #action="{ row }">
              <el-tag
                size="small"
                type="warning"
                effect="light"
                class="font-mono"
              >
                {{ row.action }}
              </el-tag>
            </template>
            <template #summary="{ row }">
              <!-- 长摘要用 tooltip 完整查看，单元格内截断避免撑爆表格 -->
              <el-tooltip
                :content="row.summary || '—'"
                placement="top"
                :show-after="300"
              >
                <span class="summary-cell">{{ row.summary || "—" }}</span>
              </el-tooltip>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canView"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(View)"
                @click="openDetail(row)"
              >
                查看
              </el-button>
              <span v-if="!canView" class="text-sm text-gray-400"> — </span>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>

    <!-- 详情抽屉：无 detail 接口，纯展示 list 已返回的全字段 -->
    <LogDetailDrawer v-model:visible="drawerVisible" :log="currentLog" />
  </div>
</template>

<style lang="scss" scoped>
.operation-log-page {
  display: flex;
  flex-direction: column;
  height: 100%;
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

/* 让 pure-table 填充剩余空间 */
.table-wrap :deep(.pure-table) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

/* 表格内容区滚动 */
.table-wrap :deep(.el-table) {
  flex: 1;
  min-height: 0;
}

/* 设置表格最大高度，确保分页可见 */
.table-wrap :deep(.el-table__body-wrapper) {
  max-height: calc(100vh - var(--table-offset));
  overflow-y: auto;
}

/* 摘要单元格截断 */
.summary-cell {
  display: inline-block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 → 同级看顺序，再加 tag 提升至 0,2,1 */
div.operation-log-page.main-content {
  margin: var(--space-3);
}
</style>
