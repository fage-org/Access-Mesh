/**
 * 权限变更日志 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/log/change/list`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/log/change/list`）。
 * 响应统一为后端 R<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.8 permission-change-log 契约要点
 *   （T-PERM-032 收口：端点 /api/perm/log/change/list/筛选全集/createdBy/独立
 *   PERMISSION_CHANGE_LOG:VIEW 门禁）+ §5.8 diff_snapshot 规范 diff_snapshot 规范。
 * 后端实现：access-service LogQueryController（@RequestMapping("/api/perm/log")
 *   + @PostMapping("/change/list")）+ LogQueryAppServiceImpl.listChangeLogs。
 *
 * 后端仅 1 个端点（list），无 detail--ChangeLogResp 已含全部字段（含 diffSnapshot/oldSnapshot/newSnapshot），
 * 详情由前端抽屉展示（无需单独 detail 接口）。
 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
import type { PageResp } from "./role-manage";

// ========== diff_snapshot 结构化类型（对齐 api-contract §5.8 diff_snapshot 规范（原 §6.8）） ==========

/** 事件类型固定枚举（api-contract §5.8 diff_snapshot 规范（原 §6.8）） */
export type DiffEventType =
  | "USER_ROLE_CHANGE"
  | "ROLE_PERMISSION_CHANGE"
  | "ROLE_STATUS_CHANGE"
  | "RESOURCE_STATUS_CHANGE"
  | "CONDITION_CHANGE"
  | "RESOURCE_DEPENDENCY_CHANGE"
  /** 批量删除角色的聚合事件（entityId=0 + operation=BATCH_DELETE；T-PERM-032 契约收口补枚举） */
  | "ROLE_BATCH_DELETE";

/** 变更类型固定枚举（api-contract §5.8 diff_snapshot 规范（原 §6.8）） */
export type DiffChangeType = "ADD" | "REMOVE" | "UPDATE";

/** 权限项业务键（§5.8 diff_snapshot 规范：domainCode+resourceTypeCode+resourceCode+codeType+operationCode+scopeMode） */
export interface DiffPermissionKey {
  domainCode?: string | null;
  resourceTypeCode?: string | null;
  resourceCode?: string | null;
  codeType?: string | null;
  operationCode?: string | null;
  scopeMode?: string | null;
}

/** 角色来源业务键（§5.8 diff_snapshot 规范） */
export interface DiffRoleRef {
  roleTypeCode?: string | null;
  roleExternalId?: string | null;
  roleName?: string | null;
}

/** 资源业务键（§5.8 diff_snapshot 规范（原 §6.8）RESOURCE_STATUS_CHANGE 示例） */
export interface DiffResourceRef {
  domainCode?: string | null;
  resourceTypeCode?: string | null;
  resourceCode?: string | null;
  codeType?: string | null;
}

/** diff_snapshot.items[] 单项（§5.8 diff_snapshot 规范） */
export interface DiffItem {
  changeType: DiffChangeType;
  permission?: DiffPermissionKey | null;
  role?: DiffRoleRef | null;
  resource?: DiffResourceRef | null;
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
  message?: string | null;
}

/** diff_snapshot 顶层结构（§5.8 diff_snapshot 规范：eventType + items[]） */
export interface DiffSnapshot {
  eventType: DiffEventType;
  items: DiffItem[];
}

// ========== 变更日志响应类型（对齐后端 ChangeLogResp） ==========

/** 权限变更日志响应（对齐后端 ChangeLogResp）。
 *  permission_change_log 表记录权限变更的 before/after/diff 详情（与 operation_log 区分：后者记所有写操作轻量日志）。
 *  oldSnapshot/newSnapshot/diffSnapshot 均为 JSON 字符串（后端 String），前端按需 JSON.parse。 */
export type ChangeLogResp = {
  id: number;
  tenantId?: number;
  /** 变更实体类型：user_role/role_resource_permission/abstract_user/abstract_role 等（schema L626） */
  entityType: string;
  /** 变更实体 ID（可空） */
  entityId?: number | null;
  /** 实体层操作：INSERT/UPDATE/DELETE（schema L627，区别于 diff items[].changeType ADD/REMOVE/UPDATE） */
  operation: string;
  /** 变更前快照 JSON 字符串（可空） */
  oldSnapshot?: string | null;
  /** 变更后快照 JSON 字符串（可空） */
  newSnapshot?: string | null;
  /** 结构化变更摘要 JSON 字符串（§5.8 diff_snapshot 规范（原 §6.8），可空）。前端 JSON.parse 后取 eventType + items[] */
  diffSnapshot?: string | null;
  /** 受影响的用户 ID 数组（可空） */
  affectedAbstractUserIds?: number[] | null;
  /** 受影响的角色 ID 数组（可空） */
  affectedAbstractRoleIds?: number[] | null;
  /** 变更原因（可空） */
  changeReason?: string | null;
  /** 变更来源：MANUAL/SERVICE_SYNC（后端复用 PermConstants.MaintainSource；schema 注释已随 T-PERM-032 修正） */
  changeSource?: string | null;
  /** 操作人 ID（表 created_by，抽象用户 ID；T-PERM-032 暴露） */
  createdBy?: number | null;
  /** 请求/追踪 ID（可空） */
  requestId?: string | null;
  /** 创建时间（对齐后端 LocalDateTime createdAt） */
  createdAt?: string;
};

/** 变更日志列表查询请求（对齐后端 ChangeLogListReq，T-PERM-032 收口：筛选全集）。
 *  全部过滤维度可选；pageNum/pageSize 必填（后端 @NotNull），服务端分页。
 *  维度对齐 schema 索引：eventType（diff_snapshot 表达式索引）/affectedUser·Role（GIN 包含）/
 *  since·until（时间索引，ISO 无偏移墙钟字符串，数字对齐展示）；changeSource 精确匹配。 */
export type ChangeLogListReq = {
  entityType?: string;
  entityId?: number;
  eventType?: string;
  changeSource?: string;
  affectedUserId?: number;
  affectedRoleId?: number;
  since?: string;
  until?: string;
  pageNum: number;
  pageSize: number;
};

// ========== API 函数 ==========

/** 查询权限变更日志列表（POST /perm/api/perm/log/change/list，契约路径已随 T-PERM-032 修正）。
 *  多维度过滤 + 服务端分页，返回 PageResp<ChangeLogResp>。
 *  权限门禁：独立 PERMISSION_CHANGE_LOG:VIEW（T-PERM-032 审计分离，对齐操作日志 OPERATION_LOG:VIEW 先例）。 */
export const getChangeLogList = async (
  params: ChangeLogListReq
): Promise<PageResp<ChangeLogResp>> => {
  const res = await http.request<R<PageResp<ChangeLogResp>>>(
    "post",
    "/perm/api/perm/log/change/list",
    { data: params }
  );
  return unwrap(res);
};

/** diff items[].changeType 合法枚举（api-contract §5.8 diff_snapshot 规范（原 §6.8）），超出此集合的元素视为非法并过滤 */
const VALID_CHANGE_TYPES: ReadonlySet<string> = new Set([
  "ADD",
  "REMOVE",
  "UPDATE"
]);

/** 安全解析 diffSnapshot JSON 字符串为结构化对象（解析失败返回 null）。
 *  逐项校验 items：仅保留非空对象且 changeType 属于 ADD/REMOVE/UPDATE 的元素，
 *  避免历史/异常数据（如 items:[null]）在 DiffSnapshotPanel 渲染时访问 item.changeType 抛错。
 *  eventType 仅校验为 string（不限制枚举值）--后端批量删除角色实际写 "ROLE_BATCH_DELETE"
 *  契约 §5.8 diff_snapshot 规范（原 §6.8） 七枚举之一（T-PERM-032 增补），EVENT_TYPE_META 有正式条目；未知值仍 fallback 显示原值。 */
export function parseDiffSnapshot(
  raw: string | null | undefined
): DiffSnapshot | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as DiffSnapshot;
    if (
      !parsed ||
      typeof parsed.eventType !== "string" ||
      !Array.isArray(parsed.items)
    ) {
      return null;
    }
    const items = parsed.items.filter(
      (it): it is DiffItem =>
        it != null &&
        typeof it === "object" &&
        typeof (it as DiffItem).changeType === "string" &&
        VALID_CHANGE_TYPES.has((it as DiffItem).changeType)
    );
    return { ...parsed, items };
  } catch {
    return null;
  }
}

export { type PageResp };
