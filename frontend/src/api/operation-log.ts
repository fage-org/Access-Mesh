/**
 * 操作日志 API
 * 经 @/utils/http 调用 permission-center 端点（`/api/perm/log/operation/list`）；
 * Phase 1 由 mock/operation-log.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.8（操作日志仅 1 行表格条目，
 *   且路径写为 /api/perm/operation-log/list——与后端实现不符，无独立字段契约章节。
 *   🔧 登记 T-PERM-025：Phase 2 修正契约路径 + 补字段契约）
 * 后端实现：permission-center LogQueryController（@RequestMapping("/api/perm/log")）
 *   + LogQueryAppServiceImpl.listOperationLogs
 *
 * 后端仅 1 个端点（list），无 detail——OperationLogResp 已含全部字段，
 * 详情由前端抽屉展示（无需单独 detail 接口）。
 *
 * 路径说明：后端 LogQueryController 实际路径为 /api/perm/log/operation/list
 *   （@RequestMapping("/api/perm/log") + @PostMapping("/operation/list")），
 *   非 api-contract §5.8 表格写的 /api/perm/operation-log/list。前端按后端实现对接，
 *   联调时直接对真后端无需改路径；契约路径错误登记 T-PERM-025 🔧。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { PaginatedResp } from "./role-manage";

// ========== 操作日志定义 ==========

/** 操作日志响应（对齐后端 OperationLogResp）。
 *  operation_log 表记录所有写操作的轻量日志（与 permission_change_log 区分：后者只记权限变更详情）。 */
export type OperationLogResp = {
  id: number;
  tenantId?: number;
  /** 所属模块，如 type_definition / abstract_role / abstract_user / system_config 等 */
  module: string;
  /** 操作类型，如 CREATE / UPDATE / DELETE / SYNC / ASSIGN 等 */
  action: string;
  /** 操作目标类型（可空） */
  targetType?: string | null;
  /** 操作目标 ID（可空） */
  targetId?: number | null;
  /** 操作摘要（可空，长文本） */
  summary?: string | null;
  /** 操作人 ID（可空） */
  operatorId?: number | null;
  /** 操作人名称（可空） */
  operatorName?: string | null;
  /** 操作人 IP（可空） */
  ipAddress?: string | null;
  /** 请求/追踪 ID（可空，用于关联追踪） */
  requestId?: string | null;
  /** 创建时间（对齐后端 LocalDateTime createdAt） */
  createdAt?: string;
};

/** 操作日志列表查询请求（对齐后端 OperationLogListReq）。
 *  module/action 可选过滤；pageNum/pageSize 必填（后端 @NotNull），服务端分页。
 *  🔧 API 核对项（登记 T-PERM-025）：后端 Req 只支持 module/action 两个筛选维度，
 *  schema 有 operator_id/created_at/target_type 等可用筛选字段未暴露。
 *  Phase 2 后端补 operatorId/createdAt 时间范围/targetType 等筛选维度。 */
export type OperationLogListReq = {
  module?: string;
  action?: string;
  pageNum: number;
  pageSize: number;
};

// ========== API 函数 ==========

/** 查询操作日志列表（POST /api/perm/log/operation/list）。
 *  后端按 module/action 过滤 + 服务端分页，返回 PaginatedResp<OperationLogResp>。
 *  权限门禁：后端 LogQueryAppServiceImpl 以 SYSTEM_CONFIG:VIEW 校验（复用系统配置 VIEW，无独立权限码）。
 *  🔧 筛选维度不足 + 契约路径错误登记于 T-PERM-025。 */
export const getOperationLogList = async (
  params: OperationLogListReq
): Promise<PaginatedResp<OperationLogResp>> => {
  const res = await http.request<PermResult<PaginatedResp<OperationLogResp>>>(
    "post",
    "/api/perm/log/operation/list",
    { data: params }
  );
  return unwrap(res);
};

export { type PaginatedResp };
