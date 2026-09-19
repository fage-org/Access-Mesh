import { reactive, onMounted } from "vue";
import {
  getChangeLogList,
  type ChangeLogResp
} from "@/api/permission-change-log";
import { usePagedList } from "@/utils/list-load";
import { createEmptySearchForm } from "./types";
import { formatToWallClockIso } from "@/utils/wall-clock";

/**
 * 权限变更日志页 hook（分页表格 + 服务端分页）。
 *
 * 范式对齐 operation-log/utils/hook.ts，本页同样为**服务端分页**（后端 ChangeLogListReq 支持
 * pageNum/pageSize，返回 PageResp）。只读查询页：无 handleSubmitForm/handleDelete（无写操作）。
 *
 * T-PERM-032 收口：筛选全集（entityType/entityId/eventType/changeSource/受影响 user·role/
 * 时间范围），维度对齐 schema 索引；时间序列化取墙钟分量对齐表格展示数字。
 *
 * T-FE-051：本页 reqSeq 代际守卫先例收敛到 usePagedList（语义不变：迟到响应丢弃、
 * loading 仅最新复位）；失败语义按全仓统一口径改为保留旧数据 + 提示（原为清空）。
 */
export function usePermissionChangeLog() {
  const searchForm = reactive(createEmptySearchForm());

  const {
    tableData,
    loading,
    pagination,
    loadTable,
    onSearch,
    onPageChange,
    onPageSizeChange
  } = usePagedList<ChangeLogResp>({
    errorText: "加载变更日志失败",
    fetcher: (page, size) =>
      // 后端 /api/access/log/change/list 返回 PageResp（服务端分页 + 全维度过滤）。
      // 前端不做本地过滤/切片——服务端已分页。
      getChangeLogList({
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
        pageNum: page,
        pageSize: size
      })
  });

  function onReset() {
    searchForm.entityType = null;
    searchForm.entityId = null;
    searchForm.eventType = null;
    searchForm.changeSource = null;
    searchForm.affectedUserId = null;
    searchForm.affectedRoleId = null;
    searchForm.timeRange = null;
    onSearch();
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
