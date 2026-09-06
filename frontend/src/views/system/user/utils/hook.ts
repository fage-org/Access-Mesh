import { ref, reactive } from "vue";
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

  async function handleDelete(user: UserItem) {
    await deleteUser([user.id]);
    message("用户已删除", { type: "success" });
    loadTable();
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
