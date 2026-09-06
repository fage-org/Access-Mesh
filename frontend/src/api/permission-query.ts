/**
 * 权限排查 API
 *
 * 归并后取消聚合层（T-ACCESS-012 决策）：权限排查页直连既有契约端点，无新增
 * PermissionQueryController / Gateway 路由。T-FE-019 联调已切 Gateway 外部路径
 * （Phase 1 mock 本地路径 /permission-query/* 已退役）：
 * - /perm/api/perm/permission-view/effective-permissions（§6.8 管理端分页排查视图，USER/ROLE）
 * - /perm/api/perm/auth/query-scopes（§6.7 范围权限四态，仅 USER）
 * - /perm/api/perm/permission-view/explain（§6.8 单权限解释 + 近期影响事件，USER/ROLE）
 *
 * 主体模型（核实 access-service 后端 PermissionQueryAppServiceImpl:126 / PermissionViewAppServiceImpl:698）：
 * - query-resources/query-scopes：仅支持用户主体（subjectTypeCode + subjectExternalId）
 * - effective-permissions/explain：支持 targetType=USER/ROLE
 *   - USER：subjectTypeCode（LOCAL_USER/USER）+ subjectExternalId
 *   - ROLE：roleTypeCode（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）+ roleExternalId + domainCode
 *
 * T-PERM-033 收口（2026-08-29，详见 docs/tasks/T-PERM-033.md 与 design/frontend/permission-query.md §8-9）：
 * - 门禁设计定案：无独立排查码（原预案 PERMISSION_QUERY:VIEW 否决），explain/recent-changes 门禁
 *   = 被查目标实例 USER:VIEW/ROLE:VIEW；query-scopes 维持运行时语义无排查门禁
 * - explain DTO 扩展已实现：context.clientIp 输入（缺省回退当前请求）+ evaluationContextSource/
 *   evaluatedClientIp/conditionEvaluations（IP 掩码脱敏）/conflictDrops，展示随 T-FE-019 联调
 * - recentChanges 按权限键 6 字段过滤（USER 目标保留角色分配/回收事件）
 * - LOCAL_USER external_id=sys_user.id 字符串（本地投影同源）；query-resources 字段与契约一致；
 *   permission-view/* 唯一差异 resource-users 已登记
 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
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
  /** USER 分支：用户主体类型码（LOCAL_USER/USER） */
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
  /** 用户主体类型码（LOCAL_USER/USER） */
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
}

/** query-scopes 响应 */
export interface QueryScopesResp {
  /** 拒绝原因：null=正常 | NO_PERMISSION | USER_NOT_FOUND | OBJECT_KEY_NOT_FOUND */
  reason: string | null;
  matchedParentOperations: string[];
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
  /** 条件评估上下文（T-PERM-033）：管理员输入的模拟 clientIp，未传时后端回退当前请求；
   *  响应 evaluationContextSource 标注实际来源；日期/时间类条件按服务时钟评估不可模拟 */
  context?: { clientIp?: string | null };
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
  /** 影响级别（契约 §6.8 固定枚举）：DIRECT=直接命中查询对象 | POSSIBLE=间接可能影响 */
  impactLevel: "DIRECT" | "POSSIBLE";
  message: string;
  permission?: ExplainPermission;
  sourceRole?: SourceRole;
  operatorId: number | null;
  operatorName: string | null;
  changeReason: string | null;
  createdAt: string;
}

/** explain 条件项评估结果（T-PERM-033；值已脱敏：IP 掩码主机段、日期/时间原样） */
export interface ConditionItemEvaluation {
  /** IP_WHITELIST | IP_BLACKLIST | DATE_RANGE | TIME_RANGE */
  type: string;
  /** 脱敏后的参数摘要 */
  maskedParams: string | null;
  /** 该项在评估上下文下是否满足 */
  matched: boolean;
}

/** explain 条件评估明细（T-PERM-033） */
export interface ConditionEvaluation {
  conditionId: number;
  permissionId: number | null;
  roleId: number | null;
  /** 条件加载状态：OK | DISABLED | NOT_FOUND | INVALID（非 OK 恒 fail-close） */
  status: "OK" | "DISABLED" | "NOT_FOUND" | "INVALID";
  /** 条件逻辑；脏数据时后端原样回传非法值（fail-close 拒绝但保留排查线索），故不限枚举 */
  logic: string | null;
  passed: boolean;
  items: ConditionItemEvaluation[];
}

/** explain 被权限互斥规则丢弃的候选命中条目（T-PERM-033） */
export interface ConflictDrop {
  permissionId: number | null;
  roleId: number | null;
  ruleId: number | null;
  firstOperationCode: string | null;
  secondOperationCode: string | null;
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
  /** 近期影响事件：按权限键过滤（USER 目标保留角色分配/回收） */
  recentChanges: RecentChange[];
  /** 条件评估上下文来源（T-PERM-033）：ADMIN_INPUT=管理员输入 | CURRENT_REQUEST=回退当前请求 */
  evaluationContextSource?: "ADMIN_INPUT" | "CURRENT_REQUEST";
  /** 实际参与 IP 类条件评估的客户端 IP（无 IP 条件上下文时 null） */
  evaluatedClientIp?: string | null;
  /** 条件评估明细（候选命中条目中挂条件的逐项评估过程） */
  conditionEvaluations?: ConditionEvaluation[];
  /** 被权限互斥规则丢弃的候选命中条目及命中规则 */
  conflictDrops?: ConflictDrop[];
}

// ========== API 函数 ==========

/** 分页查询用户/角色当前有效权限（§6.8 管理端排查视图） */
export const getEffectivePermissions = async (
  data: EffectivePermissionsReq
): Promise<EffectivePermissionsResp> => {
  const res = await http.request<R<EffectivePermissionsResp>>(
    "post",
    "/perm/api/perm/permission-view/effective-permissions",
    { data }
  );
  return unwrap(res);
};

/** 查询用户在主资源上下文内的范围权限四态（§6.7） */
export const getQueryScopes = async (
  data: QueryScopesReq
): Promise<QueryScopesResp> => {
  const res = await http.request<R<QueryScopesResp>>(
    "post",
    "/perm/api/perm/auth/query-scopes",
    { data }
  );
  return unwrap(res);
};

/** 解释单个用户/角色对某资源操作的当前权限 + 近期影响事件（§6.8） */
export const getExplain = async (data: ExplainReq): Promise<ExplainResp> => {
  const res = await http.request<R<ExplainResp>>(
    "post",
    "/perm/api/perm/permission-view/explain",
    { data }
  );
  return unwrap(res);
};
