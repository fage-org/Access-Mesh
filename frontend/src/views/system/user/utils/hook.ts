import { ref, reactive } from "vue";
import { ElMessageBox } from "element-plus";
import type { UserItem } from "./types";
import {
  getUserPage,
  createUser,
  updateUser,
  deleteUser
} from "@/api/user-manage";
import { message } from "@/utils/message";

export function useUserManage() {
  const selectedOrgId = ref<number | null>(null);
  const tableData = ref<UserItem[]>([]);
  const loading = ref(false);
  const searchForm = reactive({
    name: "",
    email: "",
    phone: "",
    status: null as number | null
  });

  const pagination = reactive({
    page: 1,
    size: 15,
    total: 0
  });

  async function loadTable() {
    loading.value = true;
    try {
      const result = await getUserPage({
        pageNum: pagination.page,
        pageSize: pagination.size,
        name: searchForm.name || undefined,
        email: searchForm.email || undefined,
        phone: searchForm.phone || undefined,
        status: searchForm.status ?? undefined,
        orgId: selectedOrgId.value ?? undefined
      });
      tableData.value = result.items;
      pagination.total = result.total;
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.name = "";
    searchForm.email = "";
    searchForm.phone = "";
    searchForm.status = null;
    pagination.page = 1;
    loadTable();
  }

  function onPageChange(page: number) {
    pagination.page = page;
    loadTable();
  }

  function onPageSizeChange(size: number) {
    pagination.size = size;
    pagination.page = 1;
    loadTable();
  }

  async function handleCreate(form: {
    username: string;
    name: string;
    phone?: string;
    email?: string;
    orgId?: number;
  }) {
    const result = await createUser(form);
    loadTable();
    return result;
  }

  async function handleUpdate(data: {
    id: number;
    name?: string;
    phone?: string | null;
    email?: string | null;
  }) {
    await updateUser(data);
    message("用户更新成功", { type: "success" });
    loadTable();
  }

  /** 删除用户（T-FE-047：销毁性操作先二次确认，取消静默返回；失败透出后端原因——如 CANNOT_DELETE_SELF） */
  async function handleDelete(user: UserItem) {
    try {
      await ElMessageBox.confirm(
        `确认删除用户 "${user.name}"？删除后该用户将无法登录，所属组织与岗位关联将一并移除。`,
        "删除确认",
        {
          confirmButtonText: "确认删除",
          cancelButtonText: "取消",
          type: "warning"
        }
      );
    } catch {
      return; // 取消
    }
    try {
      await deleteUser([user.id]);
      message("用户已删除", { type: "success" });
      loadTable();
    } catch (error: any) {
      message(error.message || "删除失败", { type: "error" });
    }
  }

  return {
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
  };
}
