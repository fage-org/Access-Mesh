<script setup lang="ts">
import { ref, computed } from "vue";
import { usePermissionChangeLog } from "./utils/hook";
import ChangeLogDetailDrawer from "./components/ChangeLogDetailDrawer.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { hasPerms } from "@/utils/auth";
import { PERMISSION_CHANGE_LOG_PERMS } from "./utils/perms";
import {
  ENTITY_TYPE_OPTIONS,
  entityTypeLabel,
  operationLabel,
  changeSourceLabel,
  CHANGE_SOURCE_OPTIONS,
  EVENT_TYPE_META
} from "./utils/types";
import {
  parseDiffSnapshot,
  type ChangeLogResp
} from "@/api/permission-change-log";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import View from "~icons/ep/view";

defineOptions({
  name: "SystemPermissionChangeLog"
});

const {
  tableData,
  loading,
  searchForm,
  pagination,
  loadTable,
  onSearch,
  onReset,
  onPageChange,
  onPageSizeChange
} = usePermissionChangeLog();

const tableRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/permission-change-log.md §权限接线 对齐。
// 后端独立 PERMISSION_CHANGE_LOG:VIEW 门禁（T-PERM-032 审计分离，对齐操作日志先例）。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canView = computed(() => hasPerms(PERMISSION_CHANGE_LOG_PERMS.LOG_VIEW));

// ========== 详情抽屉 ==========
const drawerVisible = ref(false);
const currentLog = ref<ChangeLogResp | null>(null);

function openDetail(row: ChangeLogResp) {
  currentLog.value = row;
  drawerVisible.value = true;
}

// ========== 表格行内派生展示 ==========
/** 解析行 diffSnapshot.eventType 的标签 meta（解析失败回退 null） */
function rowEventTypeMeta(row: ChangeLogResp) {
  const et = parseDiffSnapshot(row.diffSnapshot)?.eventType;
  if (!et) return null;
  return EVENT_TYPE_META[et] ?? { label: et, type: "info" as const };
}

/** 受影响用户数·角色数摘要 */
function impactSummary(row: ChangeLogResp): string {
  const u = row.affectedAbstractUserIds?.length ?? 0;
  const r = row.affectedAbstractRoleIds?.length ?? 0;
  return `用户 ${u} · 角色 ${r}`;
}

// ========== 列定义 ==========
const columns = [
  { label: "时间", prop: "createdAt", width: 170 },
  { label: "事件类型", prop: "eventType", width: 140, slot: "eventType" },
  { label: "变更操作", prop: "operation", width: 100, slot: "operation" },
  { label: "实体类型", prop: "entityType", minWidth: 160, slot: "entityType" },
  { label: "变更来源", prop: "changeSource", width: 110, slot: "changeSource" },
  {
    label: "变更原因",
    prop: "changeReason",
    minWidth: 240,
    slot: "changeReason"
  },
  { label: "影响范围", prop: "impact", width: 130, slot: "impact" },
  {
    label: "操作",
    prop: "operation-col",
    width: 90,
    fixed: "right" as const,
    slot: "operation-col"
  }
];
</script>

<template>
  <div class="change-log-page">
    <div class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="loadTable">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="实体类型" class="mb-0!">
              <el-select
                v-model="searchForm.entityType"
                placeholder="全部"
                clearable
                class="w-40!"
                @change="onSearch"
              >
                <el-option
                  v-for="opt in ENTITY_TYPE_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="实体 ID" class="mb-0!">
              <el-input-number
                v-model="searchForm.entityId"
                :controls="false"
                :min="0"
                placeholder="精确匹配"
                class="w-36!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="事件类型" class="mb-0!">
              <el-select
                v-model="searchForm.eventType"
                placeholder="全部"
                clearable
                filterable
                class="w-44!"
                @change="onSearch"
              >
                <el-option
                  v-for="(meta, key) in EVENT_TYPE_META"
                  :key="key"
                  :label="meta.label"
                  :value="key"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="变更来源" class="mb-0!">
              <el-select
                v-model="searchForm.changeSource"
                placeholder="全部"
                clearable
                class="w-36!"
                @change="onSearch"
              >
                <el-option
                  v-for="opt in CHANGE_SOURCE_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="受影响用户" class="mb-0!">
              <el-input-number
                v-model="searchForm.affectedUserId"
                :controls="false"
                :min="0"
                placeholder="用户 ID"
                class="w-36!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="受影响角色" class="mb-0!">
              <el-input-number
                v-model="searchForm.affectedRoleId"
                :controls="false"
                :min="0"
                placeholder="角色 ID"
                class="w-36!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="时间范围" class="mb-0!">
              <el-date-picker
                v-model="searchForm.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DDTHH:mm:ss"
                class="w-72!"
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
            <template #eventType="{ row }">
              <el-tag
                v-if="rowEventTypeMeta(row)"
                size="small"
                :type="rowEventTypeMeta(row).type"
                effect="light"
              >
                {{ rowEventTypeMeta(row).label }}
              </el-tag>
              <span v-else class="text-gray-400">-</span>
            </template>
            <template #operation="{ row }">
              <el-tag
                size="small"
                type="warning"
                effect="light"
                class="font-mono"
              >
                {{ row.operation }}
              </el-tag>
            </template>
            <template #entityType="{ row }">
              <el-tag size="small" effect="plain" class="font-mono">
                {{ row.entityType }}
              </el-tag>
              <div class="entity-type-label text-gray-500">
                {{ entityTypeLabel(row.entityType) }}
              </div>
            </template>
            <template #changeSource="{ row }">
              <el-tag size="small" type="info" effect="plain" class="font-mono">
                {{ row.changeSource || "-" }}
              </el-tag>
            </template>
            <template #changeReason="{ row }">
              <el-tooltip
                :content="row.changeReason || '-'"
                placement="top"
                :show-after="300"
              >
                <span class="summary-cell">{{ row.changeReason || "-" }}</span>
              </el-tooltip>
            </template>
            <template #impact="{ row }">
              <span class="impact-cell">{{ impactSummary(row) }}</span>
            </template>
            <template #operation-col="{ row }">
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
              <span v-if="!canView" class="text-sm text-gray-400"> - </span>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>

    <!-- 详情抽屉：无 detail 接口，纯展示 list 已返回的全字段 + diff 面板 -->
    <ChangeLogDetailDrawer v-model:visible="drawerVisible" :log="currentLog" />
  </div>
</template>

<style lang="scss" scoped>
.change-log-page {
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

.entity-type-label {
  margin-top: 2px;
  font-size: 12px;
}

.summary-cell {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.impact-cell {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 -> 同级看顺序，再加 tag 提升至 0,2,1 */
div.change-log-page.main-content {
  margin: var(--space-3);
}
</style>
