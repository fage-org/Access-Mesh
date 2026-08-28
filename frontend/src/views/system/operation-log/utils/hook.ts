import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getOperationLogList,
  getOperationLogActionOptions,
  type OperationLogResp
} from "@/api/operation-log";
import { createEmptySearchForm } from "./types";

/**
 * 操作日志页 hook（分页表格 + 服务端分页 + 动态 action 字典）。
 *
 * 范式对齐 type-def/utils/hook.ts，但本页为**服务端分页**（后端 OperationLogListReq 支持
 * pageNum/pageSize，返回 PaginatedResp），非 type-def 的全量本地过滤。
 * 只读查询页：无 handleSubmitForm/handleDelete（无写操作）。
 *
 * T-PERM-025 收口：筛选维度扩展（module/action/operatorId/时间范围/targetType）+
 * action 下拉由 /log/operation/action-options 动态拉取（全量字典，含 module 过滤参数备用）。
 */
export function useOperationLog() {
  const tableData = ref<OperationLogResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive(createEmptySearchForm());
  const pagination = reactive({ page: 1, size: 15, total: 0 });
  /** action 字典选项（后端实际存在的去重事件码，label=value=code） */
  const actionOptions = ref<ReadonlyArray<{ label: string; value: string }>>([]);

  async function loadActionOptions() {
    try {
      const res = await getOperationLogActionOptions();
      actionOptions.value = res.items.map(code => ({ label: code, value: code }));
    } catch {
      // 字典加载失败不阻塞列表（下拉为空仍可看全量日志），下次进页重试
      actionOptions.value = [];
    }
  }

  async function loadTable() {
    loading.value = true;
    try {
      const res = await getOperationLogList({
        module: searchForm.module || undefined,
        action: searchForm.action || undefined,
        operatorId: searchForm.operatorId ?? undefined,
        since: searchForm.timeRange?.[0]
          ? formatToUtcIso(searchForm.timeRange[0])
          : undefined,
        until: searchForm.timeRange?.[1]
          ? formatToUtcIso(searchForm.timeRange[1])
          : undefined,
        targetType: searchForm.targetType || undefined,
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

  /** Date → 后端 LocalDateTime ISO 格式（YYYY-MM-DDTHH:mm:ss，取 UTC 分量——
   *  全链路 UTC（project-rules §7.4）：表格 createdAt 原样展示后端 UTC 串，
   *  筛选值与展示值同参照系，用户按所见数字筛选即命中 */
  function formatToUtcIso(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, "0");
    return (
      `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}` +
      `T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`
    );
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.module = null;
    searchForm.action = null;
    searchForm.operatorId = null;
    searchForm.timeRange = null;
    searchForm.targetType = null;
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
    loadActionOptions();
  });

  return {
    tableData,
    loading,
    searchForm,
    pagination,
    actionOptions,
    loadTable,
    onSearch,
    onReset,
    onPageChange,
    onPageSizeChange,
    createEmptyForm: createEmptySearchForm
  };
}
