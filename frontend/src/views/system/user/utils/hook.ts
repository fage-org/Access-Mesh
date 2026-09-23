import { ref, reactive } from "vue";
import { ElMessageBox } from "element-plus";
import type { UserItem } from "./types";
import {
  getUserPage,
  createUser,
  updateUser,
  deleteUser,
  enableUsers
} from "@/api/user-manage";
import { message } from "@/utils/message";
import { toErrorMessage } from "@/api/_envelope";
import { usePagedList } from "@/utils/list-load";

export function useUserManage() {
  const selectedOrgId = ref<number | null>(null);
  const searchForm = reactive({
    name: "",
    email: "",
    phone: "",
    status: null as number | null
  });

  // T-FE-051：列表加载统一接线 usePagedList（失败提示保留旧数据 + latest-wins 请求代际）
  // T-FE-059：成员表归属选中组织（子树）——切组织取数发起即清空旧组织行、失败不回填，
  // 旧组织成员行不得在新组织下可写（F011 同模式）
  const {
    tableData,
    loading,
    pagination,
    loadTable,
    onSearch,
    onPageChange,
    onPageSizeChange
  } = usePagedList<UserItem>({
    errorText: "加载用户列表失败",
    contextKey: () => selectedOrgId.value,
    fetcher: (page, size) =>
      getUserPage({
        pageNum: page,
        pageSize: size,
        name: searchForm.name || undefined,
        email: searchForm.email || undefined,
        phone: searchForm.phone || undefined,
        status: searchForm.status ?? undefined,
        orgId: selectedOrgId.value ?? undefined
      })
  });

  function onReset() {
    searchForm.name = "";
    searchForm.email = "";
    searchForm.phone = "";
    searchForm.status = null;
    onSearch();
  }

  /** 创建用户（T-FE-051：失败提示后端原因并返回 null——弹窗保持打开，调用方以返回值决定 closeLoading） */
  async function handleCreate(form: {
    username: string;
    name: string;
    phone?: string;
    email?: string;
    orgId?: number;
  }): Promise<{ initialPassword: string } | null> {
    try {
      const result = await createUser(form);
      loadTable();
      return result;
    } catch (e) {
      message(toErrorMessage(e, "创建用户失败"), { type: "error" });
      return null;
    }
  }

  /** 更新用户（T-FE-051：失败提示后端原因并返回 false，弹窗保持打开）。
   *  openedAtOrgId（T-FE-059，必传）：编辑弹窗打开时的选中组织——提交时与当前不一致
   *  即拒绝（弹窗存续期间页面上下文变化的窄路径，如后退键离开再回）。 */
  async function handleUpdate(
    data: {
      id: number;
      name?: string;
      phone?: string | null;
      email?: string | null;
    },
    openedAtOrgId: number | null
  ): Promise<boolean> {
    if (!Object.is(openedAtOrgId, selectedOrgId.value)) {
      message("该用户不属于当前选中组织，请刷新后重试", { type: "warning" });
      return false;
    }
    try {
      await updateUser(data);
      message("用户更新成功", { type: "success" });
      loadTable();
      return true;
    } catch (e) {
      message(toErrorMessage(e, "用户更新失败"), { type: "error" });
      return false;
    }
  }

  /** 删除用户（T-FE-047：销毁性操作先二次确认，取消静默返回；失败透出后端原因——如 CANNOT_DELETE_SELF） */
  async function handleDelete(user: UserItem) {
    // 入口捕获当前选中组织（T-FE-059）：确认框期间上下文可能已变，提交前核对
    const openedAtOrgId = selectedOrgId.value;
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
    if (!Object.is(openedAtOrgId, selectedOrgId.value)) {
      message("该用户不属于当前选中组织，请刷新后重试", { type: "warning" });
      return;
    }
    try {
      await deleteUser([user.id]);
      message("用户已删除", { type: "success" });
      loadTable();
    } catch (e) {
      message(toErrorMessage(e, "删除失败"), { type: "error" });
    }
  }

  /**
   * 启停用户（T-FE-051 自 MemberTab 移入 hook，与 handleDelete 同形可测；
   * 停用影响登录需二次确认，取消恢复开关；失败透出后端原因并回滚开关——
   * 对齐 role 页 handleToggleStatus 先例，替换原「只显泛文案不透 error.message」双标准）
   */
  async function handleToggleStatus(row: UserItem, newVal: number) {
    const newStatus: 0 | 1 = newVal === 1 ? 1 : 0;
    const actionText = newStatus === 1 ? "启用" : "停用";
    // 入口捕获当前选中组织（T-FE-059）：停用确认框期间上下文可能已变，提交前核对
    // （启用路径无确认框、捕获与核对同栈，守卫为对称防御）
    const openedAtOrgId = selectedOrgId.value;
    if (newStatus === 0) {
      try {
        await ElMessageBox.confirm(
          `确认停用用户 "${row.name}"？停用后该用户将无法登录。`,
          "停用确认",
          {
            confirmButtonText: "确认停用",
            cancelButtonText: "取消",
            type: "warning"
          }
        );
      } catch {
        row.status = 1; // 取消时恢复 switch
        return;
      }
    }
    if (!Object.is(openedAtOrgId, selectedOrgId.value)) {
      row.status = newStatus === 1 ? 0 : 1; // 拒绝时恢复 switch
      message("该用户不属于当前选中组织，请刷新后重试", { type: "warning" });
      return;
    }
    try {
      await enableUsers({ ids: [row.id], status: newStatus });
      row.status = newStatus;
      message(`${actionText}成功`, { type: "success" });
    } catch (e) {
      row.status = newStatus === 1 ? 0 : 1; // 失败时恢复状态
      message(toErrorMessage(e, `${actionText}失败`), { type: "error" });
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
    handleDelete,
    handleToggleStatus
  };
}
