/**
 * 权限排查 API
 *
 * 前端不直连 access-service（architecture.md §1.5：管理端前端统一通过 Gateway 访问
 * access-service）。本页前瞻性采用未来 access-service 聚合路径 `/permission-query/*`，
 * Phase 1 mock 直接模拟该路径；T-PERM-033 实现 access-service PermissionQueryController
 * 聚合层后，前端无需改路径。
 *
 * 与现有 `/api/perm/*` 直连 access-service 的页面（冲突规则/业务域/变更日志等）不同：
 * 本页作为管理端排查视图，应走 access-service 聚合层 + 统一门禁（PERMISSION_QUERY:VIEW）。
 * 现有 `/api/perm/*` 页面的聚合层迁移登记为独立技术债，不在本任务范围。
 *
 * 三个端点（对齐 api-contract.md §6.6-6.8）：
 * - POST /permission-query/effective-permissions（§6.8 L1353 管理端分页排查视图，USER/ROLE）
 * - POST /permission-query/query-scopes（§6.7 L1254 范围权限四态，仅 USER）
 * - POST /permission-query/explain（§6.8 L1458 单权限解释 + 近期影响事件，USER/ROLE）
 *
 * 主体模型（核实 access-service 后端 PermissionQueryAppServiceImpl:126 / PermissionViewAppServiceImpl:698）：
 * - query-resources/query-scopes：仅支持用户主体（subjectTypeCode + subjectExternalId）
 * - effective-permissions/explain：支持 targetType=USER/ROLE
 *   - USER：subjectTypeCode（ADMIN_USER/USER）+ subjectExternalId
 *   - ROLE：roleTypeCode（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）+ roleExternalId + domainCode
 *
 * 🔧 T-PERM-033 登记项（详见 docs/tasks/T-PERM-033.md）：
 * - access-service 聚合入口 + PermissionQueryController + 聚合 DTO
 * - 统一门禁 PERMISSION_QUERY:VIEW 全链路（资源类型常量+种子+默认角色授权+access-service 白名单）
 * - explain DTO 扩展（命中条件/条件评估过程/冲突详情 + 评估上下文来源 + IP/时间条件 + 敏感值脱敏）
 * - recentChanges 按完整权限键过滤（domainCode+resourceTypeCode+resourceCode+codeType+operationCode+scopeMode）
 * - ADMIN_USER/USER 主体来源与候选查询方式核对
 * - query-resources API 核对 + treeMode TODO + 全部 permission-view/* 契约差异
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ScopeMode } from "@/utils/scope-mode";

// ========== 公共类型 ==========

/** 排查目标类型（explain/effective-permissions 支持 USER/ROLE；query-scopes 仅 USER） */
export type TargetType = "USER" | "ROLE";

/** 来源角色业务键（effective-permissions/explain 的 sourceRoles[]） */
export interface SourceRole {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  /** 继承路径（角色继承链），空数组表示直接授权 */
  via: string[];
}

// ========== effective-permissions（§6.8 L1353）==========

/** effective-permissions 请求（管理端分页排查视图，支持 USER/ROLE） */
export interface EffectivePermissionsReq {
  targetType: TargetType;
  /** USER 分支：用户主体类型码（ADMIN_USER/USER） */
  subjectTypeCode?: string;
  /** USER 分支：用户外部 ID */
  subjectExternalId?: string;
  /** ROLE 分支：角色类型码（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE） */
  roleTypeCode?: string;
  /** ROLE 分支：角色外部 ID */
  roleExternalId?: string;
  /** 业务域码（ROLE 分支必填用于解析；USER 分支可选筛选） */
  domainCode?: string;
  /** 筛选：资源类型码列表 */
  resourceTypeCodes?: string[];
  /** 筛选：操作码列表 */
  operationCodes?: string[];
  /** 筛选：资源关键字模糊匹配 */
  resourceKeyword?: string;
  /** 筛选：来源角色外部 ID */
  sourceRoleExternalId?: string;
  /** 是否展开数据范围/子权限（默认 false） */
  includeScopes?: boolean;
  /** 是否返回 API 类型资源（默认 false） */
  includeApiResources?: boolean;
  /** 是否返回来源角色摘要（默认 false） */
  includeSourceRoles?: boolean;
  /** 来源角色最大条数 */
  sourceRoleLimit?: number;
  pageNum: number;
  pageSize: number;
}

/** effective-permissions 权限条目 */
export interface EffectivePermissionItem {
  resourceTypeCode: string;
  /** 资源编码，scopeMode=ALL 时为 null */
  resourceCode: string | null;
  /** 资源名称，scopeMode=ALL 时为 null */
  resourceName: string | null;
  /** 编码类型，scopeMode=ALL 时为 null */
  codeType: string | null;
  operationCodes: string[];
  /** INSTANCE=具体实例 | ALL=全量范围（§6.8 effective-permissions 仅允许此两态） */
  scopeMode: ScopeMode;
  /** 来源角色摘要（includeSourceRoles=true 时返回，最多 sourceRoleLimit 条） */
  sourceRoles: SourceRole[];
  /** 来源角色总数 */
  sourceRoleCount: number;
  /** 来源角色是否被截断 */
  sourceRolesTruncated: boolean;
  matchedPermissionIds: number[];
}

/** effective-permissions 响应 */
export interface EffectivePermissionsResp {
  targetType: TargetType;
  items: EffectivePermissionItem[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
}

// ========== query-scopes（§6.7 L1254）==========

/** query-scopes 请求（范围权限四态，仅 USER 主体） */
export interface QueryScopesReq {
  /** 用户主体类型码（ADMIN_USER/USER） */
  subjectTypeCode: string;
  /** 用户外部 ID */
  subjectExternalId: string;
  domainCode?: string;
  /** 主资源类型码 */
  parentResourceTypeCode: string;
  /** 主资源编码 */
  parentResourceCode: string;
  /** 主资源编码类型 */
  parentCodeType?: string;
  /** 主操作码列表 */
  parentOperationCodes: string[];
  /** 范围资源类型码列表（与 scopeOperationCodes 笛卡尔积） */
  scopeResourceTypeCodes: string[];
  /** 范围操作码列表（与 scopeResourceTypeCodes 笛卡尔积） */
  scopeOperationCodes: string[];
  /** 范围资源编码类型 */
  scopeCodeType?: string;
  context?: Record<string, unknown>;
}

/** query-scopes 范围实例条目（scopeMode=INSTANCE 时非空） */
export interface ScopeItem {
  resourceCode: string;
  codeType: string;
  resourceName: string | null;
}

/** query-scopes 范围分组（资源类型×操作笛卡尔积，四态独立判定） */
export interface ScopeGroup {
  resourceTypeCode: string;
  operationCode: string;
  /** DENIED=无权限 | INSTANCE=实例授权 | ALL=全量授权 | EMPTY=有权限无数据 */
  scopeMode: ScopeMode;
  /** scopeMode=INSTANCE 时非空，其余为空 */
  items: ScopeItem[];
  matchedRoleIds: number[];
  matchedPermissionIds: number[];
  dependOnPermissionIds: number[];
}

/** query-scopes 响应 */
export interface QueryScopesResp {
  /** 拒绝原因：null=正常 | NO_PERMISSION | USER_NOT_FOUND | OBJECT_KEY_NOT_FOUND */
  reason: string | null;
  matchedParentOperations: string[];
  parentPermissionIds: number[];
  scopeGroups: ScopeGroup[];
  cacheTtlSeconds: number;
}

// ========== explain（§6.8 L1458）==========

/** explain 请求（单权限解释，支持 USER/ROLE） */
export interface ExplainReq {
  targetType: TargetType;
  /** USER 分支：用户主体类型码 */
  subjectTypeCode?: string;
  /** USER 分支：用户外部 ID */
  subjectExternalId?: string;
  /** ROLE 分支：角色类型码 */
  roleTypeCode?: string;
  /** ROLE 分支：角色外部 ID */
  roleExternalId?: string;
  domainCode?: string;
  /** 目标资源类型码 */
  resourceTypeCode: string;
  /** 目标资源编码（scopeMode=INSTANCE 时必填，ALL 时不传） */
  resourceCode?: string;
  /** 目标编码类型（scopeMode=INSTANCE 时必填，ALL 时不传） */
  codeType?: string;
  /** 目标操作码 */
  operationCode: string;
  /** INSTANCE=解释具体实例（须传 resourceCode/codeType）| ALL=解释全量范围（不传 resourceCode/codeType）
   *  §6.8 L1514：explain 请求侧 scopeMode 只允许 INSTANCE/ALL（非完整四态），用 string union 精确约束 */
  scopeMode: "INSTANCE" | "ALL";
  /** 是否返回来源角色 */
  includeSourceRoles?: boolean;
  /** 是否返回近期影响事件 */
  includeRecentChanges?: boolean;
  /** 近期事件窗口天数（默认 30） */
  recentDays?: number;
}

/** explain 权限键 */
export interface ExplainPermission {
  domainCode: string;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  operationCode: string;
  scopeMode: ScopeMode;
}

/** explain 近期影响事件 */
export interface RecentChange {
  changeLogId: number;
  eventType: string;
  changeType: string;
  /** 影响级别：POSSIBLE=可能影响 | CONFIRMED=确认影响 | NONE=无影响 */
  impactLevel: string;
  message: string;
  permission?: ExplainPermission;
  sourceRole?: SourceRole;
  operatorId: number | null;
  operatorName: string | null;
  changeReason: string | null;
  createdAt: string;
}

/** explain 响应 */
export interface ExplainResp {
  targetType: TargetType;
  allowed: boolean;
  /** 拒绝原因：null=允许 | NO_PERMISSION | USER_NOT_FOUND | ROLE_NOT_FOUND */
  reason: string | null;
  permission: ExplainPermission;
  sourceRoles: SourceRole[];
  matchedPermissionIds: number[];
  recentChanges: RecentChange[];
}

// ========== API 函数 ==========

/** 分页查询用户/角色当前有效权限（§6.8 管理端排查视图） */
export const getEffectivePermissions = async (
  data: EffectivePermissionsReq
): Promise<EffectivePermissionsResp> => {
  const res = await http.request<PermResult<EffectivePermissionsResp>>(
    "post",
    "/permission-query/effective-permissions",
    { data }
  );
  return unwrap(res);
};

/** 查询用户在主资源上下文内的范围权限四态（§6.7） */
export const getQueryScopes = async (
  data: QueryScopesReq
): Promise<QueryScopesResp> => {
  const res = await http.request<PermResult<QueryScopesResp>>(
    "post",
    "/permission-query/query-scopes",
    { data }
  );
  return unwrap(res);
};

/** 解释单个用户/角色对某资源操作的当前权限 + 近期影响事件（§6.8） */
export const getExplain = async (data: ExplainReq): Promise<ExplainResp> => {
  const res = await http.request<PermResult<ExplainResp>>(
    "post",
    "/permission-query/explain",
    { data }
  );
  return unwrap(res);
};
