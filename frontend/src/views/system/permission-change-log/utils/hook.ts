import { ref, reactive, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getChangeLogList,
  type ChangeLogResp
} from "@/api/permission-change-log";
import { createEmptySearchForm } from "./types";
import { formatToWallClockIso } from "@/utils/wall-clock";

/**
 * 权限变更日志页 hook（分页表格 + 服务端分页）。
 *
 * 范式对齐 operation-log/utils/hook.ts，本页同样为**服务端分页**（后端 ChangeLogListReq 支持
 * pageNum/pageSize，返回 PaginatedResp）。
 * 只读查询页：无 handleSubmitForm/handleDelete（无写操作）。
 *
 * T-PERM-032 收口：筛选全集（entityType/entityId/eventType/changeSource/受影响 user·role/
 * 时间范围），维度对齐 schema 索引；时间序列化取墙钟分量对齐表格展示数字。
 */
export function usePermissionChangeLog() {
  const tableData = ref<ChangeLogResp[]>([]);
  const loading = ref(false);
  const searchForm = reactive(createEmptySearchForm());
  const pagination = reactive({ page: 1, size: 15, total: 0 });

  // 请求序号：仅采纳最新一次请求的结果，避免并发请求时较早请求后返回覆盖精确查询结果
  // （审计页优先保证不展示与当前筛选条件不符的数据）。
  let reqSeq = 0;

  async function loadTable() {
    const seq = ++reqSeq;
    loading.value = true;
    try {
      // 后端 /api/perm/log/change/list 返回 PaginatedResp（服务端分页 + 全维度过滤）。
      // 前端不做本地过滤/切片--服务端已分页。
      const res = await getChangeLogList({
        entityType: searchForm.entityType || undefined,
        entityId: searchForm.entityId ?? undefined,
        eventType: searchForm.eventType || undefined,
        changeSource: searchForm.changeSource || undefined,
        affectedUserId: searchForm.affectedUserId ?? undefined,
        affectedRoleId: searchForm.affectedRoleId ?? undefined,
        since: searchForm.timeRange?.[0]
          ? formatToWallClockIso(searchForm.timeRange[0])
          : undefined,
        until: searchForm.timeRange?.[1]
          ? formatToWallClockIso(searchForm.timeRange[1])
          : undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      });
      // 过期请求静默丢弃（用户已发起更新的查询）
      if (seq !== reqSeq) return;
      tableData.value = res.items;
      pagination.total = res.total;
    } catch (e: any) {
      // 过期请求静默丢弃
      if (seq !== reqSeq) return;
      // 最新请求失败：清空旧数据，避免展示与当前筛选条件不符的数据
      tableData.value = [];
      pagination.total = 0;
      message(e.message || "加载变更日志失败", { type: "error" });
    } finally {
      // 仅最新请求复位 loading，过期请求不干扰
      if (seq === reqSeq) loading.value = false;
    }
  }

  function onSearch() {
    pagination.page = 1;
    loadTable();
  }

  function onReset() {
    searchForm.entityType = null;
    searchForm.entityId = null;
    searchForm.eventType = null;
    searchForm.changeSource = null;
    searchForm.affectedUserId = null;
    searchForm.affectedRoleId = null;
    searchForm.timeRange = null;
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
