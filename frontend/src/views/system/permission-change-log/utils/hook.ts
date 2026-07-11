import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getChangeLogList,
  type ChangeLogResp
} from "@/api/permission-change-log";
import { createEmptySearchForm } from "./types";

/**
 * 权限变更日志页 hook（分页表格 + 服务端分页）。
 *
 * 范式对齐 operation-log/utils/hook.ts，本页同样为**服务端分页**（后端 ChangeLogListReq 支持
 * pageNum/pageSize，返回 PaginatedResp）。
 * 只读查询页：无 handleSubmitForm/handleDelete（无写操作）。
 *
 * 🔧 后端 Req 只支持 entityType/entityId 两筛选维度（登记 T-PERM-032），前端筛选表单仅此两项。
 */
export function usePermissionChangeLog() {
  const tableData = ref<ChangeLogResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive(createEmptySearchForm());
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  async function loadTable() {
    loading.value = true;
    try {
      // 后端 /api/perm/log/change/list 返回 PaginatedResp（服务端分页 + entityType/entityId 过滤）。
      // 前端不做本地过滤/切片--服务端已分页。
      const res = await getChangeLogList({
        entityType: searchForm.entityType || undefined,
        entityId: searchForm.entityId ?? undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      });
      tableData.value = res.items;
      pagination.total = res.total;
    } catch (e: any) {
      message(e.message || "加载变更日志失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.entityType = null;
    searchForm.entityId = null;
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

  onMounted(() => {
    loadTable();
  });

  return {
    tableData,
    loading,
    searchForm,
    pagination,
    loadTable,
    onSearch,
    onReset,
    onPageChange,
    onPageSizeChange,
    createEmptyForm: createEmptySearchForm
  };
}
