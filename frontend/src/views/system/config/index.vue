<script setup lang="ts">
import { ref, h, computed } from "vue";
import { useSystemConfig } from "./utils/hook";
import ConfigForm from "./components/ConfigForm.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { SYSTEM_CONFIG_PERMS } from "./utils/perms";
import type { SystemConfigResp } from "@/api/system-config";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import AddFill from "~icons/ri/add-circle-line";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import EditPen from "~icons/ep/edit-pen";

defineOptions({
  name: "SystemConfig"
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
  onPageSizeChange,
  handleSubmitForm
} = useSystemConfig();

const tableRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/system-config.md §权限接线 对齐。
// 后端 save 走 MANAGE（无独立 CREATE/UPDATE），前端新增/编辑统一 CONFIG_SAVE。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canSave = computed(() => hasPerms(SYSTEM_CONFIG_PERMS.CONFIG_SAVE));

// ========== 列定义 ==========
const columns = [
  { label: "配置键", prop: "configKey", minWidth: 200, slot: "configKey" },
  {
    label: "配置值",
    prop: "configValue",
    minWidth: 240,
    slot: "configValue"
  },
  { label: "描述", prop: "description", minWidth: 200 },
  { label: "更新时间", prop: "updatedAt", width: 170 },
  {
    label: "操作",
    prop: "operation",
    width: 100,
    fixed: "right" as const,
    slot: "operation"
  }
];

// ========== 新建/编辑弹窗 ==========
// 新建/编辑共用一表单，提交统一走 saveSystemConfig（upsert 幂等，后端无 create/update/remove）。
function openForm(mode: "create" | "edit", row?: SystemConfigResp) {
  let formRef: any = null;
  addDialog({
    title: mode === "edit" ? "编辑配置" : "新增配置",
    width: "520px",
    contentRenderer: () =>
      h(ConfigForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        initialData: mode === "edit" ? row : null
      }),
    beforeSure: async (done: Function, { closeLoading }: any) => {
      if (!formRef) {
        done();
        return;
      }
      const valid = await formRef.validate();
      if (!valid) {
        closeLoading();
        return;
      }
      const formData = formRef.getFormData();
      const ok = await handleSubmitForm(formData);
      if (ok) done();
      else closeLoading();
    }
  });
}
</script>

<template>
  <div class="system-config-page">
    <div class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="onSearch">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="配置键/描述" class="mb-0!">
              <el-input
                v-model="searchForm.keyword"
                placeholder="请输入配置键或描述"
                clearable
                class="w-50!"
                @keyup.enter="onSearch"
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
        <template #buttons>
          <el-button
            v-if="canSave"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openForm('create')"
          >
            新增配置
          </el-button>
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
            <template #configKey="{ row }">
              <span class="font-mono text-sm">{{ row.configKey }}</span>
            </template>
            <template #configValue="{ row }">
              <!-- JSON 文本展示，过长用 tooltip 完整查看，单元格内截断避免撑爆表格 -->
              <el-tooltip
                :content="row.configValue"
                placement="top"
                :show-after="300"
              >
                <span class="config-value-cell font-mono text-sm">
                  {{ row.configValue }}
                </span>
              </el-tooltip>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canSave"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(EditPen)"
                @click="openForm('edit', row)"
              >
                编辑
              </el-button>
              <span v-if="!canSave" class="text-sm text-gray-400"> — </span>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.system-config-page {
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

/* configValue 单元格截断 */
.config-value-cell {
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
div.system-config-page.main-content {
  margin: var(--space-3);
}
</style>
