<script setup lang="ts">
import { ref, reactive, h, watch, computed } from "vue";
import { useUserManage } from "../utils/hook";
import { PureTableBar } from "@/components/RePureTableBar";
import UserForm from "../form.vue";
import { addDialog } from "@/components/ReDialog";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { ElMessageBox } from "element-plus";
import { message } from "@/utils/message";
import { hasPerms } from "@/utils/auth";
import { ORG_USER_PERMS } from "../utils/perms";
import type { UserFormData } from "../utils/types";
import Delete from "~icons/ep/delete";
import EditPen from "~icons/ep/edit-pen";
import Refresh from "~icons/ep/refresh";
import AddFill from "~icons/ri/add-circle-line";
import Search from "~icons/ep/search";
import View from "~icons/ep/view";
import More from "~icons/ep/more-filled";
import Key from "~icons/ep/key";

import { enableUsers, resetUserPassword } from "@/api/user-manage";

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
  selectedOrgId,
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

// 监听 orgId 变化，同步到 useUserManage 并重新加载表格
watch(
  () => props.orgId,
  newOrgId => {
    selectedOrgId.value = newOrgId;
    onSearch();
  },
  { immediate: true }
);

// ========== 权限门控 ==========
// 与 docs/design/org-user-permission-contract.md §4 矩阵 B/C 区对齐。
// computed 包装而非顶层 const，是为了响应 store.permissions 变化（角色切换时刷新）。
const canCreateUser = computed(() => hasPerms(ORG_USER_PERMS.USER_ADD));
const canEditUser = computed(() => hasPerms(ORG_USER_PERMS.USER_EDIT));
const canDeleteUser = computed(() => hasPerms(ORG_USER_PERMS.USER_DELETE));
const canToggleUserStatus = computed(() =>
  hasPerms(ORG_USER_PERMS.USER_ENABLE)
);
const canResetPwd = computed(() => hasPerms(ORG_USER_PERMS.USER_RESET_PWD));
/** 行内可见操作数量，===1 时直渲单按钮，>1 才用 dropdown 避免「更多」二字虚假预期 */
const visibleMoreCount = computed(() => {
  let c = 0;
  if (canEditUser.value) c++;
  if (canResetPwd.value) c++;
  if (canDeleteUser.value) c++;
  return c;
});
const canShowMoreMenu = computed(() => visibleMoreCount.value > 1);
const canShowSingleMore = computed(() => visibleMoreCount.value === 1);

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

// 下拉菜单命令处理
function handleCommand(command: string, row: any) {
  // 纵深防御：v-if 已在 dropdown-item 上隐藏，但保留二次校验防 future 调用路径漏门控
  switch (command) {
    case "edit":
      if (canEditUser.value) openEditDialog(row);
      break;
    case "resetPwd":
      if (canResetPwd.value) handleResetPassword(row);
      break;
    case "delete":
      if (canDeleteUser.value) handleDelete(row);
      break;
  }
}
async function handleToggleStatus(row: any, newVal: number) {
  const newStatus: 0 | 1 = newVal === 1 ? 1 : 0;
  const actionText = newStatus === 1 ? "启用" : "禁用";
  // 禁用方向需二次确认（影响登录），启用方向直通
  if (newStatus === 0) {
    try {
      await ElMessageBox.confirm(
        `确认禁用用户 "${row.name}"？禁用后该用户将无法登录。`,
        "禁用确认",
        {
          confirmButtonText: "确认禁用",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      // 取消时恢复 switch
      row.status = 1;
      return;
    }
  }
  try {
    await enableUsers({ ids: [row.id], status: newStatus });
    row.status = newStatus;
    message(`${actionText}成功`, { type: "success" });
  } catch {
    // 失败时恢复状态
    row.status = newStatus === 1 ? 0 : 1;
    message(`${actionText}失败`, { type: "error" });
  }
}

// 重置密码
async function handleResetPassword(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认重置用户 "${row.name}" 的密码？`,
      "重置密码",
      {
        confirmButtonText: "确认重置",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
    const result = await resetUserPassword({ userId: row.id });
    await ElMessageBox.alert(
      `新密码：${result.newPassword}\n\n请将密码通知用户，登录后自行修改。`,
      "密码重置成功",
      {
        confirmButtonText: "知道了",
        type: "success"
      }
    );
  } catch (e: any) {
    if (e !== "cancel") {
      message("重置密码失败", { type: "error" });
    }
  }
}

const columns = [
  { label: "姓名", prop: "name", width: 100 },
  { label: "用户名", prop: "username", width: 120 },
  { label: "邮箱", prop: "email", minWidth: 160 },
  { label: "状态", prop: "status", width: 100, slot: "status" },
  { label: "主组织", prop: "primaryOrg", width: 120, slot: "primaryOrg" },
  { label: "创建时间", prop: "createdAt", width: 120, slot: "createdAt" },
  {
    label: "操作",
    prop: "operation",
    width: 120,
    fixed: "right" as const,
    slot: "operation"
  }
];
</script>

<template>
  <div class="member-tab">
    <!-- 表格 -->
    <div class="table-wrap">
      <PureTableBar title="" :columns="columns" @refresh="onSearch">
        <template #title>
          <el-form
            :inline="true"
            :model="searchForm"
            class="search-form-inline"
          >
            <el-form-item label="姓名" class="mb-0!">
              <el-input
                v-model="searchForm.name"
                placeholder="请输入姓名"
                clearable
                class="w-25!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="邮箱" class="mb-0!">
              <el-input
                v-model="searchForm.email"
                placeholder="请输入邮箱"
                clearable
                class="w-30!"
                @keyup.enter="onSearch"
              />
            </el-form-item>
            <el-form-item label="状态" class="mb-0!">
              <el-select
                v-model="searchForm.status"
                placeholder="全部"
                clearable
                class="w-20!"
              >
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
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
            v-if="canCreateUser"
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
            <template #status="{ row }">
              <el-tooltip
                :disabled="canToggleUserStatus"
                content="无权限切换状态"
                placement="top"
              >
                <el-switch
                  v-model="row.status"
                  class="status-switch"
                  :active-value="1"
                  :inactive-value="0"
                  :disabled="!canToggleUserStatus"
                  inline-prompt
                  active-text="启用"
                  inactive-text="禁用"
                  @change="(val: number) => handleToggleStatus(row, val)"
                />
              </el-tooltip>
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
                :icon="useRenderIcon(View)"
                @click.stop="emit('open-user-detail', row)"
              >
                查看
              </el-button>
              <!-- 仅一项可见时直渲单按钮（避免「更多」字面虚假预期） -->
              <template v-if="canShowSingleMore">
                <el-button
                  v-if="canEditUser"
                  class="reset-margin"
                  link
                  type="primary"
                  :size="size"
                  :icon="useRenderIcon(EditPen)"
                  @click="openEditDialog(row)"
                >
                  修改
                </el-button>
                <el-button
                  v-else-if="canResetPwd"
                  class="reset-margin"
                  link
                  type="primary"
                  :size="size"
                  :icon="useRenderIcon(Key)"
                  @click="handleResetPassword(row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-else-if="canDeleteUser"
                  class="reset-margin"
                  link
                  type="danger"
                  :size="size"
                  :icon="useRenderIcon(Delete)"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
              <el-dropdown
                v-else-if="canShowMoreMenu"
                :size="size"
                trigger="click"
                @command="(cmd: string) => handleCommand(cmd, row)"
              >
                <el-button
                  class="reset-margin"
                  link
                  type="primary"
                  :size="size"
                  :icon="useRenderIcon(More)"
                >
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canEditUser" command="edit">
                      <el-icon><EditPen /></el-icon>
                      <span class="ml-1">修改</span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="canResetPwd" command="resetPwd">
                      <el-icon><Key /></el-icon>
                      <span class="ml-1">重置密码</span>
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="canDeleteUser"
                      command="delete"
                      divided
                    >
                      <el-icon><Delete /></el-icon>
                      <span class="ml-1" style="color: var(--el-color-danger)"
                        >删除</span
                      >
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
  height: 100%;
  overflow: hidden;
}

.status-switch {
  --el-switch-on-color: var(--el-color-success);
  --el-switch-off-color: var(--el-color-danger);
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

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
