<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getUserPage,
  deleteUser,
  enableUser,
  resetPassword,
  type UserPageItem,
  type UserPageRequest
} from "@/api/admin/user";
import { getOrgTree, type OrgNode } from "@/api/admin/org";
import { PERM_CODES } from "@/constants/permission";
import { hasPerms } from "@/utils/auth";
import UserForm from "./components/UserForm.vue";
import OrgTreePanel from "./components/OrgTreePanel.vue";
import UserOrgDialog from "./components/UserOrgDialog.vue";
import UserRoleDialog from "./components/UserRoleDialog.vue";

defineOptions({
  name: "UserManagement"
});

// ========== 状态定义 ==========

const loading = ref(false);
const tableData = ref<Array<UserPageItem>>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

// 搜索条件
const searchForm = reactive({
  username: "",
  name: "",
  phone: "",
  status: null as number | null
});

// 组织筛选
const selectedOrgId = ref<number | null>(null);
const orgTreeData = ref<Array<OrgNode>>([]);

// 弹窗引用
const userFormRef = ref();
const orgTreePanelRef = ref();
const userOrgDialogRef = ref();
const userRoleDialogRef = ref();

// 当前编辑ID
const currentEditId = ref<number | null>(null);

// ========== 表格列定义 ==========

interface TableColumn {
  label: string;
  prop?: string;
  minWidth?: number;
  fixed?: "left" | "right";
  slot?: string;
}

const columns = ref<Array<TableColumn>>([
  { label: "用户名", prop: "username", minWidth: 120 },
  { label: "姓名", prop: "name", minWidth: 100 },
  { label: "手机号", prop: "phone", minWidth: 120 },
  { label: "邮箱", prop: "email", minWidth: 150 },
  {
    label: "所属组织",
    prop: "orgs",
    minWidth: 150,
    slot: "orgs"
  },
  {
    label: "状态",
    prop: "status",
    minWidth: 80,
    slot: "status"
  },
  {
    label: "创建时间",
    prop: "createdAt",
    minWidth: 160,
    slot: "createdAt"
  },
  {
    label: "操作",
    fixed: "right",
    minWidth: 280,
    slot: "action"
  }
]);

// ========== 数据加载 ==========

const loadOrgTree = async () => {
  try {
    const res = await getOrgTree();
    if (res.success) {
      orgTreeData.value = res.data;
    }
  } catch (error) {
    console.error("[UserManagement] Load org tree failed:", error);
    ElMessage.error("加载组织树失败");
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const params: UserPageRequest = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      username: searchForm.username || undefined,
      name: searchForm.name || undefined,
      phone: searchForm.phone || undefined,
      status: searchForm.status ?? undefined,
      orgId: selectedOrgId.value ?? undefined
    };

    const res = await getUserPage(params);
    if (res.success) {
      tableData.value = res.data.list;
      total.value = res.data.total;
    }
  } catch (error) {
    console.error("[UserManagement] Load user list failed:", error);
    ElMessage.error("加载用户列表失败");
  } finally {
    loading.value = false;
  }
};

const handleRefresh = () => {
  loadData();
};

// ========== 搜索功能 ==========

const handleSearch = () => {
  currentPage.value = 1;
  loadData();
};

const handleReset = () => {
  searchForm.username = "";
  searchForm.name = "";
  searchForm.phone = "";
  searchForm.status = null;
  currentPage.value = 1;
  loadData();
};

// ========== 分页 ==========

const handlePageChange = (val: number) => {
  currentPage.value = val;
  loadData();
};

const handleSizeChange = (val: number) => {
  pageSize.value = val;
  currentPage.value = 1;
  loadData();
};

// ========== 组织筛选 ==========

const handleOrgSelect = (orgId: number | null) => {
  selectedOrgId.value = orgId;
  currentPage.value = 1;
  loadData();
};

const handleClearOrgSelect = () => {
  selectedOrgId.value = null;
  currentPage.value = 1;
  loadData();
};

// ========== CRUD 操作 ==========

const handleCreate = () => {
  currentEditId.value = null;
  userFormRef.value.openDialog();
};

const handleEdit = (row: UserPageItem) => {
  currentEditId.value = row.id;
  userFormRef.value.openDialog();
};

const handleDelete = async (row: UserPageItem) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户 "${row.username}" 吗?`, "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning"
    });

    const res = await deleteUser({ ids: [row.id] });
    if (res.success) {
      ElMessage.success("删除成功");
      loadData();
    }
  } catch (error) {
    // 区分用户取消和API错误
    if (error !== "cancel") {
      console.error("[UserManagement] Delete user failed:", error);
      ElMessage.error("删除用户失败");
    }
  }
};

const handleToggleStatus = async (row: UserPageItem) => {
  const action = row.status === 1 ? "停用" : "启用";
  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 "${row.username}" 吗?`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    const res = await enableUser({ ids: [row.id] });
    if (res.success) {
      ElMessage.success(`${action}成功`);
      loadData();
    }
  } catch (error) {
    // 区分用户取消和API错误
    if (error !== "cancel") {
      console.error("[UserManagement] Toggle user status failed:", error);
      ElMessage.error(`${action}用户失败`);
    }
  }
};

const handleResetPassword = async (row: UserPageItem) => {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户 "${row.username}" 的密码吗?`,
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      }
    );

    // 生成随机密码(使用 crypto.getRandomValues 确保安全)
    const newPassword = generateRandomPassword();
    const res = await resetPassword({ userId: row.id, newPassword });
    if (res.success) {
      // 复制密码到剪贴板，不直接显示明文
      try {
        await navigator.clipboard.writeText(newPassword);
        ElMessage.success("密码已重置并复制到剪贴板");
      } catch {
        // 剪贴板不可用时显示密码
        ElMessageBox.alert(
          `新密码: ${newPassword}\n\n请立即保存此密码`,
          "密码已重置",
          { confirmButtonText: "确定" }
        );
      }
    }
  } catch (error) {
    // 区分用户取消和API错误
    if (error !== "cancel") {
      console.error("[UserManagement] Reset password failed:", error);
    }
  }
};

/** 生成随机密码(使用 crypto.getRandomValues 确保安全) */
const generateRandomPassword = (): string => {
  const chars =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const array = new Uint8Array(12);
  crypto.getRandomValues(array);
  return Array.from(array, byte => chars.charAt(byte % chars.length)).join("");
};

// ========== 组织配置 ==========

const handleConfigOrg = (row: UserPageItem) => {
  userOrgDialogRef.value.openDialog(row.id);
};

// ========== 角色分配 ==========

const handleAssignRole = (row: UserPageItem) => {
  userRoleDialogRef.value.openDialog(row.id);
};

// ========== 表单成功回调 ==========

const handleFormSuccess = () => {
  loadData();
};

// ========== 初始化 ==========

onMounted(() => {
  loadOrgTree();
  loadData();
});

// ========== 权限计算 ==========

const canCreate = computed(() => hasPerms(PERM_CODES.SYS_USER_CREATE));
const canUpdate = computed(() => hasPerms(PERM_CODES.SYS_USER_UPDATE));
const canDelete = computed(() => hasPerms(PERM_CODES.SYS_USER_DELETE));
const canEnable = computed(() => hasPerms(PERM_CODES.SYS_USER_ENABLE));
const canResetPassword = computed(() =>
  hasPerms(PERM_CODES.SYS_USER_RESET_PASSWORD)
);
const canConfigOrg = computed(() => hasPerms(PERM_CODES.SYS_USER_CONFIG_ORG));
const canAssignRole = computed(() => hasPerms(PERM_CODES.SYS_USER_ASSIGN_ROLE));
</script>

<template>
  <div class="user-management">
    <div class="flex gap-4">
      <!-- 左侧组织树 -->
      <OrgTreePanel
        ref="orgTreePanelRef"
        :tree-data="orgTreeData"
        :selected-org-id="selectedOrgId"
        @select="handleOrgSelect"
        @clear="handleClearOrgSelect"
      />

      <!-- 右侧用户列表 -->
      <div class="flex-1">
        <!-- 搜索栏 -->
        <el-card class="mb-4">
          <el-form :inline="true" :model="searchForm" class="search-form">
            <el-form-item label="用户名">
              <el-input
                v-model="searchForm.username"
                placeholder="请输入用户名"
                clearable
                class="w-[180px]"
              />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input
                v-model="searchForm.name"
                placeholder="请输入姓名"
                clearable
                class="w-[180px]"
              />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input
                v-model="searchForm.phone"
                placeholder="请输入手机号"
                clearable
                class="w-[180px]"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="searchForm.status"
                placeholder="请选择状态"
                clearable
                class="w-[120px]"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 表格区域 -->
        <el-card>
          <!-- 工具栏 -->
          <div class="mb-4 flex justify-between">
            <el-button
              type="primary"
              :disabled="!canCreate"
              @click="handleCreate"
            >
              新增用户
            </el-button>
            <el-button @click="handleRefresh">刷新</el-button>
          </div>

          <!-- 表格 -->
          <el-table
            v-loading="loading"
            :data="tableData"
            border
            stripe
            row-key="id"
          >
            <el-table-column
              v-for="col in columns"
              :key="col.prop"
              :prop="col.prop"
              :label="col.label"
              :min-width="col.minWidth"
              :fixed="col.fixed"
            >
              <template #orgs="{ row }">
                <span v-if="row.orgs && row.orgs.length > 0">
                  {{ row.orgs.find(o => o.isPrimary)?.orgName || "-" }}
                </span>
                <span v-else>-</span>
              </template>

              <template #status="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                  {{ row.status === 1 ? "启用" : "停用" }}
                </el-tag>
              </template>

              <template #createdAt="{ row }">
                {{
                  row.createdAt ? new Date(row.createdAt).toLocaleString() : "-"
                }}
              </template>

              <template #action="{ row }">
                <el-button
                  v-if="canUpdate"
                  type="primary"
                  link
                  size="small"
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="canConfigOrg"
                  type="success"
                  link
                  size="small"
                  @click="handleConfigOrg(row)"
                >
                  配置组织
                </el-button>
                <el-button
                  v-if="canAssignRole"
                  type="info"
                  link
                  size="small"
                  @click="handleAssignRole(row)"
                >
                  分配角色
                </el-button>
                <el-button
                  v-if="canEnable"
                  type="primary"
                  link
                  size="small"
                  @click="handleToggleStatus(row)"
                >
                  {{ row.status === 1 ? "停用" : "启用" }}
                </el-button>
                <el-button
                  v-if="canResetPassword"
                  type="warning"
                  link
                  size="small"
                  @click="handleResetPassword(row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-if="canDelete"
                  type="danger"
                  link
                  size="small"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            class="mt-4"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </el-card>
      </div>
    </div>

    <!-- 弹窗组件 -->
    <UserForm
      ref="userFormRef"
      :edit-id="currentEditId"
      @success="handleFormSuccess"
    />
    <UserOrgDialog ref="userOrgDialogRef" @success="handleFormSuccess" />
    <UserRoleDialog ref="userRoleDialogRef" @success="handleFormSuccess" />
  </div>
</template>

<style scoped lang="scss">
.user-management {
  padding: 20px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
