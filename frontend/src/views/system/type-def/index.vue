<script setup lang="ts">
import { ref, h, computed } from "vue";
import { useTypeDef } from "./utils/hook";
import TypeForm from "./components/TypeForm.vue";
import { PureTableBar } from "@/components/RePureTableBar";
import { addDialog } from "@/components/ReDialog";
import { hasPerms } from "@/utils/auth";
import { TYPE_DEF_PERMS } from "./utils/perms";
import { TYPE_KEY_LABEL, type TypeDefResp, type TypeKey } from "@/api/type-def";
import { TYPE_KEY_OPTIONS, isSystemPreset } from "./utils/types";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import AddFill from "~icons/ri/add-circle-line";
import Search from "~icons/ep/search";
import Refresh from "~icons/ep/refresh";
import Delete from "~icons/ep/delete";
import EditPen from "~icons/ep/edit-pen";

defineOptions({
  name: "SystemTypeDef"
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
  handleSubmitForm,
  handleDelete
} = useTypeDef();

const tableRef = ref();

// ========== 权限门控 ==========
// 与 docs/design/frontend/type-def.md §权限接线 对齐。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canAdd = computed(() => hasPerms(TYPE_DEF_PERMS.TYPE_ADD));
const canEdit = computed(() => hasPerms(TYPE_DEF_PERMS.TYPE_EDIT));
const canDelete = computed(() => hasPerms(TYPE_DEF_PERMS.TYPE_DELETE));

// ========== 列定义 ==========
const columns = [
  { label: "类型分组", prop: "typeKey", width: 110, slot: "typeKey" },
  { label: "编码", prop: "typeCode", minWidth: 140 },
  { label: "名称", prop: "name", minWidth: 120 },
  { label: "内部值", prop: "typeValue", width: 90 },
  { label: "属性", prop: "isSystem", width: 100, slot: "isSystem" },
  { label: "排序", prop: "sortOrder", width: 80 },
  {
    label: "操作",
    prop: "operation",
    width: 120,
    fixed: "right" as const,
    slot: "operation"
  }
];

// ========== 新建/编辑弹窗 ==========
function openForm(mode: "create" | "edit", row?: TypeDefResp) {
  let formRef: any = null;
  addDialog({
    title: mode === "edit" ? "编辑类型" : "新增类型",
    width: "480px",
    contentRenderer: () =>
      h(TypeForm, {
        ref: (el: any) => {
          formRef = el;
        },
        mode,
        initialData: mode === "edit" ? row : null,
        defaultTypeKey:
          mode === "create" ? (searchForm.typeKey as TypeKey | "") : ""
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
      const ok = await handleSubmitForm(mode, formData, row?.id);
      if (ok) done();
      else closeLoading();
    }
  });
}
</script>

<template>
  <div class="type-def-page">
    <div class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="onSearch">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="类型分组" class="mb-0!">
              <el-select
                v-model="searchForm.typeKey"
                placeholder="全部"
                clearable
                class="w-30!"
                @change="onSearch"
              >
                <el-option
                  v-for="opt in TYPE_KEY_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="名称/编码" class="mb-0!">
              <el-input
                v-model="searchForm.keyword"
                placeholder="请输入名称或编码"
                clearable
                class="w-40!"
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
            v-if="canAdd"
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openForm('create')"
          >
            新增类型
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
            <template #typeKey="{ row }">
              <el-tag size="small" effect="plain">
                {{ TYPE_KEY_LABEL[row.typeKey] || row.typeKey }}
              </el-tag>
            </template>
            <template #isSystem="{ row }">
              <el-tag
                size="small"
                :type="row.isSystem ? 'info' : 'success'"
                effect="light"
              >
                {{ row.isSystem ? "系统预置" : "自定义" }}
              </el-tag>
            </template>
            <template #operation="{ row }">
              <el-button
                v-if="canEdit && !isSystemPreset(row)"
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(EditPen)"
                @click="openForm('edit', row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canDelete && !isSystemPreset(row)"
                class="reset-margin"
                link
                type="danger"
                :size="size"
                :icon="useRenderIcon(Delete)"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
              <span
                v-if="isSystemPreset(row) && !canEdit && !canDelete"
                class="text-sm text-gray-400"
              >
                —
              </span>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.type-def-page {
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
</style>

<style>
/* 覆写 layout 的 .main-content[data-v-x] { margin: 24px }
   特异性：(class+class) 0,2,0 vs (class+attr) 0,2,0 → 同级看顺序，再加 tag 提升至 0,2,1 */
div.type-def-page.main-content {
  margin: var(--space-3);
}
</style>
