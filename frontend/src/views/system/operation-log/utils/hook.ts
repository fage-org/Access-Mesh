import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getOperationLogList,
  type OperationLogResp
} from "@/api/operation-log";
import { createEmptySearchForm } from "./types";

/**
 * 操作日志页 hook（分页表格 + 服务端分页）。
 *
 * 范式对齐 type-def/utils/hook.ts，但本页为**服务端分页**（后端 OperationLogListReq 支持
 * pageNum/pageSize，返回 PaginatedResp），非 type-def 的全量本地过滤。
 * 只读查询页：无 handleSubmitForm/handleDelete（无写操作）。
 *
 * 🔧 后端 Req 只支持 module/action 两筛选维度（登记 T-PERM-025），前端筛选表单仅此两项。
 */
export function useOperationLog() {
  const tableData = ref<OperationLogResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive(createEmptySearchForm());
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  async function loadTable() {
    loading.value = true;
    try {
      // 后端 /api/perm/log/operation/list 返回 PaginatedResp（服务端分页 + module/action 过滤）。
      // 前端不做本地过滤/切片——服务端已分页。
      const res = await getOperationLogList({
        module: searchForm.module || undefined,
        action: searchForm.action || undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      });
      tableData.value = res.items;
      pagination.total = res.total;
    } catch (e: any) {
      message(e.message || "加载操作日志失败", { type: "error" });
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.module = null;
    searchForm.action = null;
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
