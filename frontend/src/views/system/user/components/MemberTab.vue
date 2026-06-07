<script setup lang="ts">
import { ref, reactive, h, watch } from "vue";
import { useUserManage } from "../utils/hook";
import { PureTableBar } from "@/components/RePureTableBar";
import UserForm from "../form.vue";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { ElMessageBox } from "element-plus";
import type { UserFormData } from "../utils/types";
import Delete from "~icons/ep/delete";
import EditPen from "~icons/ep/edit-pen";
import Refresh from "~icons/ep/refresh";
import AddFill from "~icons/ri/add-circle-line";
import Search from "~icons/ep/search";

defineOptions({
  name: "MemberTab"
});

const props = defineProps<{
  orgId: number | null;
  orgTree?: any[];
}>();

const emit = defineEmits<{
  (e: "open-user-detail", row: any): void;
}>();

type UserFormInstance = InstanceType<typeof UserForm>;

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
  handleCreate,
  handleUpdate,
  handleDelete
} = useUserManage();

const tableRef = ref();
const dialogFormRef = ref<UserFormInstance | null>(null);

// 监听 orgId 变化，重新加载表格
watch(
  () => props.orgId,
  () => {
    onSearch();
  },
  { immediate: true }
);

// Dialog form data
const dialogFormData = reactive<UserFormData>({
  username: "",
  name: "",
  phone: "",
  email: "",
  orgId: null
});

function updateDialogFormData(value: UserFormData) {
  Object.assign(dialogFormData, value);
}

function openCreateDialog() {
  dialogFormData.username = "";
  dialogFormData.name = "";
  dialogFormData.phone = "";
  dialogFormData.email = "";
  dialogFormData.orgId = null;
  addDialog({
    title: "新增用户",
    width: "460px",
    contentRenderer: () =>
      h(UserForm, {
        ref: dialogFormRef,
        mode: "create",
        formData: dialogFormData,
        "onUpdate:formData": updateDialogFormData
      }),
    beforeSure: async (done, { closeLoading }) => {
      const valid = await dialogFormRef.value?.validate();
      if (!valid) {
        closeLoading();
        return;
      }

      try {
        const result = await handleCreate({
          username: dialogFormData.username,
          name: dialogFormData.name,
          phone: dialogFormData.phone || undefined,
          email: dialogFormData.email || undefined,
          orgId: dialogFormData.orgId ?? undefined
        });

        await ElMessageBox.alert(
          `初始密码：${result.initialPassword}\n\n请将密码通知用户，登录后自行修改。`,
          "用户创建成功",
          {
            confirmButtonText: "知道了",
            type: "success",
            dangerouslyUseHTMLString: false
          }
        );
        done();
      } catch {
        closeLoading();
      }
    }
  });
}

function openEditDialog(row: any) {
  dialogFormData.username = row.username;
  dialogFormData.name = row.name;
  dialogFormData.phone = row.phone ?? "";
  dialogFormData.email = row.email ?? "";
  addDialog({
    title: "编辑用户",
    width: "460px",
    contentRenderer: () =>
      h(UserForm, {
        ref: dialogFormRef,
        mode: "edit",
        row,
        formData: dialogFormData,
        "onUpdate:formData": updateDialogFormData
      }),
    beforeSure: async (done, { closeLoading }) => {
      const valid = await dialogFormRef.value?.validate();
      if (!valid) {
        closeLoading();
        return;
      }

      try {
        await handleUpdate({
          id: row.id,
          name: dialogFormData.name,
          phone: dialogFormData.phone || null,
          email: dialogFormData.email || null
        });
        done();
      } catch {
        closeLoading();
      }
    }
  });
}

const columns = [
  { label: "姓名", prop: "name", width: 100 },
  { label: "用户名", prop: "username", width: 120 },
  { label: "邮箱", prop: "email", minWidth: 160 },
  { label: "状态", prop: "status", width: 80, slot: "status" },
  { label: "主组织", prop: "primaryOrg", width: 120, slot: "primaryOrg" },
  { label: "创建时间", prop: "createdAt", width: 120, slot: "createdAt" },
  {
    label: "操作",
    prop: "operation",
    width: 140,
    fixed: "right" as const,
    slot: "operation"
  }
];
</script>

<template>
  <div class="member-tab">
    <!-- 搜索栏 -->
    <el-form :inline="true" :model="searchForm" class="search-form">
      <el-form-item label="姓名">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入姓名"
          clearable
          class="w-35!"
          @keyup.enter="onSearch"
        />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input
          v-model="searchForm.email"
          placeholder="请输入邮箱"
          clearable
          class="w-45!"
          @keyup.enter="onSearch"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="searchForm.phone"
          placeholder="请输入手机号"
          clearable
          class="w-35!"
          @keyup.enter="onSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="searchForm.status"
          placeholder="全部"
          clearable
          class="w-30!"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
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

    <!-- 表格 -->
    <div class="table-wrap">
      <PureTableBar title="成员管理" :columns="columns" @refresh="onSearch">
        <template #buttons>
          <el-button
            type="primary"
            :icon="useRenderIcon(AddFill)"
            @click="openCreateDialog"
          >
            新增成员
          </el-button>
        </template>
        <template v-slot="{ size, dynamicColumns }">
          <pure-table
            ref="tableRef"
            row-key="id"
            adaptive
            :adaptiveConfig="{ offsetBottom: 108 }"
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
            @row-click="(row: any) => emit('open-user-detail', row)"
          >
            <template #status="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'danger'"
                size="small"
                effect="plain"
              >
                {{ row.status === 1 ? "启用" : "禁用" }}
              </el-tag>
            </template>
            <template #primaryOrg="{ row }">
              <span class="text-sm">
                {{ row.orgs.find((o: any) => o.isPrimary)?.orgName || "-" }}
              </span>
            </template>
            <template #createdAt="{ row }">
              {{ row.createdAt.substring(0, 10) }}
            </template>
            <template #operation="{ row }">
              <el-button
                class="reset-margin"
                link
                type="primary"
                :size="size"
                :icon="useRenderIcon(EditPen)"
                @click.stop="openEditDialog(row)"
              >
                修改
              </el-button>
              <el-popconfirm
                :title="`确认删除用户 ${row.name}？`"
                width="200"
                @confirm="handleDelete(row)"
              >
                <template #reference>
                  <el-button
                    class="reset-margin"
                    link
                    type="primary"
                    :size="size"
                    :icon="useRenderIcon(Delete)"
                    @click.stop
                  >
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </pure-table>
        </template>
      </PureTableBar>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.member-tab {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}

.search-form {
  padding: 8px 16px 0;

  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
