/**
 * 操作日志 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/log/operation/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/log/operation`）。
 * 响应统一为后端 R<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.8 operation-log 契约要点
 *  （T-PERM-025 收口：五维筛选 + action-options 字典 + OPERATION_LOG:VIEW 审计分离门禁）。
 * 后端实现：access-service LogQueryController（@RequestMapping("/api/perm/log")）
 *   + LogQueryAppServiceImpl（操作日志三方法均 OPERATION_LOG:VIEW 门禁）。
 *
 * 端点两个：list（五维过滤服务端分页）与 action-options（动态字典）；无 detail——
 * OperationLogResp 已含全部字段，详情由前端抽屉展示（无需单独 detail 接口）。
 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
import type { ItemsResp, PageResp } from "./role-manage";

// ========== 操作日志定义 ==========

/** 操作日志响应（对齐后端 OperationLogResp）。
 *  operation_log 表记录所有写操作的轻量日志（与 permission_change_log 区分：后者只记权限变更详情）。 */
export type OperationLogResp = {
  id: number;
  tenantId?: number;
  /** 所属模块，对齐后端 operation_log.module 三值：ADMIN=管理域 / PERMISSION=权限域 / ACCESS=跨域编排 */
  module: string;
  /** 操作类型，如 CREATE / UPDATE / DELETE / SYNC / ASSIGN 等 */
  action: string;
  /** 操作目标类型（可空） */
  targetType?: string | null;
  /** 操作目标 ID（可空；T-ACCESS-002 起为字符串，兼容业务键与数值 ID） */
  targetId?: string | null;
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

/** 操作日志列表查询请求（对齐后端 OperationLogListReq，T-PERM-025 扩展筛选维度）。
 *  module/action/operatorId/since/until/targetType 可选过滤；pageNum/pageSize 必填（后端 @NotNull），
 *  服务端分页；action/module/targetType 精确匹配（保持等值索引语义）。 */
export type OperationLogListReq = {
  module?: string;
  action?: string;
  operatorId?: number;
  /** 创建时间下界（含），ISO 无偏移墙钟（YYYY-MM-DDTHH:mm:ss）——数字与表格展示的
   *  UTC 墙钟对齐（后端 LocalDateTime 语义为 UTC 墙钟，project-rules §7.4） */
  since?: string;
  /** 创建时间上界（含），同上 */
  until?: string;
  targetType?: string;
  pageNum: number;
  pageSize: number;
};

/** action 字典查询请求（POST /log/operation/action-options，module 可选过滤）。 */
export type LogActionOptionsReq = {
  module?: string;
};

// ========== API 函数 ==========

/** 查询操作日志列表（POST /perm/api/perm/log/operation/list）。
 *  后端按 module/action/operatorId/时间范围/targetType 过滤 + 服务端分页，
 *  返回 PageResp<OperationLogResp>。
 *  权限门禁：独立 OPERATION_LOG:VIEW（T-PERM-025 审计分离，不再复用 SYSTEM_CONFIG:VIEW）。 */
export const getOperationLogList = async (
  params: OperationLogListReq
): Promise<PageResp<OperationLogResp>> => {
  const res = await http.request<R<PageResp<OperationLogResp>>>(
    "post",
    "/perm/api/perm/log/operation/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询操作日志 action 字典（POST /perm/api/perm/log/operation/action-options，T-PERM-025）。
 *  返回 operation_log 当前实际存在的 action 去重集合（字典序），供筛选下拉动态拉取
 *  （替代前端硬编码子集——action 由 @OperationLog 注解开放增长，返回实际存在值避免双轨漂移）。
 *  权限门禁：OPERATION_LOG:VIEW。 */
export const getOperationLogActionOptions = async (
  params?: LogActionOptionsReq
): Promise<ItemsResp<string>> => {
  const res = await http.request<R<ItemsResp<string>>>(
    "post",
    "/perm/api/perm/log/operation/action-options",
    { data: params ?? {} }
  );
  return unwrap(res);
};

export { type PageResp, type ItemsResp };
