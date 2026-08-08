/**
 * 权限授予 API（4.1 权限授予页 v3，T-FE-036）
 * 经 @/utils/http 调用 permission-center 端点（`/api/perm/role-resource-permission/*`）。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.5 / §6.4 / §6.5 / §6.5.1
 * 后端实现：permission-center PermissionGrantController + PermissionGrantAppServiceImpl
 *
 * T-PERM-034 已实现的契约要点：
 * - `RolePermissionItem` 统一返回 14 字段；list 支持 resourceTypeCode/includeChildren。
 * - `apply-grant-plan` 是授权页面唯一写入口（记录级 creates/updates/removes，
 *   单事务原子 + 受影响行数断言；无 CAS/幂等表/clientRequestId，第十四轮收窄），
 *   存量 save/revoke/children/add-child/remove-child 仅为其他服务兼容保留，授权页面不调用。
 * - grantedBits 为 63 位位图十进制字符串（避免 JSON number 精度丢失），前端 BigInt 解析。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 常量 ==========

/** 授权范围模式（对齐 ScopeModeSupport.fromScopeAll；授予侧仅 INSTANCE/ALL 两态合法） */
export const GRANT_SCOPE_MODE = {
  INSTANCE: "INSTANCE",
  ALL: "ALL"
} as const;

export type GrantScopeMode =
  (typeof GRANT_SCOPE_MODE)[keyof typeof GRANT_SCOPE_MODE];

/** 授权来源（对齐 permission-center GrantSource 枚举；INHERITED 为查询时克隆、不落库不返回） */
export const GRANT_SOURCE = {
  MANUAL: "MANUAL",
  AUTO_DEP: "AUTO_DEP"
} as const;

export type GrantSource = (typeof GRANT_SOURCE)[keyof typeof GRANT_SOURCE];

/**
 * apply-grant-plan 链路错误码（api-contract §6.5.1 错误码枚举，第十四轮精简；
 * 20037/20039 已砍）。用于页面错误提示映射（DoD-1）。
 */
export const GRANT_ERROR_CODE = {
  ROLE_NOT_FOUND: 20001,
  ROLE_DISABLED: 20003,
  RESOURCE_NOT_FOUND: 20004,
  OPERATION_NOT_FOUND: 20005,
  CONDITION_NOT_FOUND: 20006,
  RESOURCE_TYPE_NOT_FOUND: 20007,
  RESOURCE_TYPE_OPERATION_MISMATCH: 20008,
  PARENT_PERMISSION_NOT_FOUND: 20009,
  PARENT_PERMISSION_NOT_TOP_LEVEL: 20010,
  SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED: 20011,
  RESOURCE_CODE_REQUIRED: 20012,
  DIRECT_PERMISSION_CONFLICT: 20033,
  AUTO_DEP_READONLY: 20034,
  PERMISSION_NOT_FOUND: 20036,
  GRANT_CANNOT_DELEGATE: 20040,
  /** 🔧 T-PERM-041：条件权限不可转授（conditionCode != null 时 canGrant 必须 false） */
  CONDITIONAL_PERMISSION_CANNOT_DELEGATE: 20041
} as const;

export type GrantErrorCode =
  (typeof GRANT_ERROR_CODE)[keyof typeof GRANT_ERROR_CODE];

/** 错误码 → 页面提示文案（DoD-1：20033/20034/20036/20011/20040 + 任务卡 20009/20010） */
export const GRANT_ERROR_MESSAGES: Readonly<Record<number, string>> = {
  [GRANT_ERROR_CODE.ROLE_NOT_FOUND]: "目标角色不存在，请刷新后重试",
  [GRANT_ERROR_CODE.ROLE_DISABLED]: "目标角色已停用，无法授权",
  [GRANT_ERROR_CODE.RESOURCE_NOT_FOUND]: "目标资源不存在，请刷新后重试",
  [GRANT_ERROR_CODE.OPERATION_NOT_FOUND]: "目标操作权限不存在，请刷新后重试",
  [GRANT_ERROR_CODE.CONDITION_NOT_FOUND]: "目标权限条件不存在，请刷新后重试",
  [GRANT_ERROR_CODE.RESOURCE_TYPE_NOT_FOUND]: "资源类型不存在，请刷新后重试",
  [GRANT_ERROR_CODE.RESOURCE_TYPE_OPERATION_MISMATCH]:
    "操作权限与资源类型不匹配",
  [GRANT_ERROR_CODE.PARENT_PERMISSION_NOT_FOUND]: "父权限不存在，请刷新后重试",
  [GRANT_ERROR_CODE.PARENT_PERMISSION_NOT_TOP_LEVEL]:
    "父权限不是主权限，不能挂载子权限",
  [GRANT_ERROR_CODE.SUB_PERMISSION_RESOURCE_TYPE_NOT_ALLOWED]:
    "该资源类型不允许作为子权限（SUB_PERM 配置不允许）",
  [GRANT_ERROR_CODE.RESOURCE_CODE_REQUIRED]: "实例范围授权缺少资源编码",
  [GRANT_ERROR_CODE.DIRECT_PERMISSION_CONFLICT]:
    "同一资源与操作已存在直接授权，请编辑已有授权",
  [GRANT_ERROR_CODE.AUTO_DEP_READONLY]: "自动补全记录只读，不可修改或删除",
  [GRANT_ERROR_CODE.PERMISSION_NOT_FOUND]:
    "目标记录不存在或已被修改，请刷新页面确认当前状态",
  [GRANT_ERROR_CODE.GRANT_CANNOT_DELEGATE]:
    "当前账号无权转授该权限（授权传递校验未通过）",
  [GRANT_ERROR_CODE.CONDITIONAL_PERMISSION_CANNOT_DELEGATE]:
    "条件权限不可转授：带条件的权限不能设置可再授予，请先清除条件后重试"
};

// ========== 类型定义 ==========

/**
 * 角色权限配置项（对齐后端 RolePermissionItemResp，14 字段）。
 * grantSource/grantedBits/createdAt/childCount 已由权限中心列表与提交结果统一返回。
 */
export type RolePermissionItem = {
  /** 记录 id（持久化行键，update/remove 按 id） */
  id: number;
  /** 资源类型码 */
  resourceTypeCode: string;
  /** 资源实例码（scopeMode=ALL 时为 null） */
  resourceCode: string | null;
  /** 资源码类型（scopeMode=ALL 时为 null） */
  codeType: string | null;
  /** 资源名（展示用；scopeMode=ALL 时为 null） */
  resourceName: string | null;
  /** 操作码；组合位无对应操作定义时为 null（配合 grantedBits 兜底） */
  operationCode: string | null;
  /** 是否可再授予 */
  canGrant: boolean;
  /** 条件码；无条件为 null */
  conditionCode: string | null;
  /** 范围模式 INSTANCE / ALL */
  scopeMode: GrantScopeMode;
  /** 父权限 id；主权限为 null */
  dependOn: number | null;
  /** 授权来源 MANUAL / AUTO_DEP */
  grantSource: GrantSource;
  /** 63 位操作位图，十进制字符串（如 "9223372036854775807"），必有值；前端 BigInt 解析 */
  grantedBits: string;
  /** 创建时间，ISO-8601 无时区（如 2026-04-20T10:30:00） */
  createdAt: string;
  /** 直接子权限数（depend_on = 本 id，不含孙代） */
  childCount: number;
};

/** 角色权限配置查询请求（对齐 RolePermissionListReq + T-PERM-034 includeChildren + T-PERM-040 resourceTypeCode） */
export type RolePermissionListReq = {
  /** 可省略或 null：仅校验域存在性，不按域过滤（P1-1）；授权页恒传 null */
  domainCode?: string | null;
  roleTypeCode: string;
  roleExternalId: string;
  /**
   * 可选，默认不传；🔧 T-PERM-040 单类型矩阵上下文：
   * 按资源类型过滤主权限（dependOn==null 且 resource_type 匹配）。
   * 授权矩阵调用必填（矩阵一次只呈现一个类型）；null/缺省 = 不过滤（兼容既有调用方）。
   * includeChildren=true 时返回该类型主权限及其全部子权限，子权限按 depend_on 挂父返回
   * （子权限自身可跨类型，不能按子记录自身类型过滤）。
   */
  resourceTypeCode?: string | null;
  /** 可选，默认 true 兼容现行为；false 时只返回主权限（dependOn==null） */
  includeChildren?: boolean;
};

/**
 * 授权记录键（apply-grant-plan creates 的 key / children 项）。
 * 跨字段约束：scopeMode=INSTANCE -> resourceCode/codeType 必填；scopeMode=ALL -> 两者为 null。
 */
export type GrantRecordKey = {
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  /** 操作码（本页不支持多操作位组合新增，创建必传；对齐设计文档 §12 注） */
  operationCode: string | null;
  scopeMode: GrantScopeMode;
  conditionCode: string | null;
  /** 缺省 false */
  canGrant?: boolean;
};

/** plan.creates[] 项：key + 可选 parentPermissionId（挂已存在父）+ 可选 children（仅主权限一次性建树） */
export type GrantPlanCreate = {
  key: GrantRecordKey;
  /** 仅引用提交前已存在的父记录（协议无 clientTempId/parentTempId） */
  parentPermissionId?: number;
  /** 仅主权限可用；子权限写在父权限 children 数组内，服务端生成父 id 后回填 depend_on */
  children?: GrantRecordKey[];
};

/**
 * plan.updates[] 项（三态协议）：
 * - canGrant：null/缺省=不改 / true / false
 * - conditionCode：缺省或 null=不改 / ""=清除 / 非空=覆盖
 */
export type GrantPlanUpdate = {
  id: number;
  canGrant?: boolean | null;
  conditionCode?: string | null;
};

/** 记录级授权计划（唯一写入口；单事务原子执行，任一失败整体回滚） */
export type GrantPlan = {
  creates?: GrantPlanCreate[];
  updates?: GrantPlanUpdate[];
  /** 记录 id 数组：主权限级联删子、子权限单条删 */
  removes?: number[];
};

/** apply-grant-plan 请求（统一响应壳见 api-contract §0；请求体封闭对象） */
export type ApplyGrantPlanReq = {
  domainCode?: string | null;
  roleTypeCode: string;
  roleExternalId: string;
  plan: GrantPlan;
};

// ========== API 函数 ==========

/**
 * 查询角色权限配置（POST /api/perm/role-resource-permission/list）。
 * 门禁：目标抽象角色 ROLE:VIEW（失败返回空列表）。
 * 本页调用约定：includeChildren=true 一次取全量（主+子），来源链计算仅消费 dependOn==null
 * 主权限，详情层按 dependOn 分组子权限（免逐项懒加载，对齐设计 §12 缺口 5）。
 */
export const getRolePermissionList = async (
  params: RolePermissionListReq
): Promise<ItemsResp<RolePermissionItem>> => {
  const res = await http.request<PermResult<ItemsResp<RolePermissionItem>>>(
    "post",
    "/api/perm/role-resource-permission/list",
    { data: params }
  );
  return unwrap(res);
};

/**
 * 聚合授权提交（POST /api/perm/role-resource-permission/apply-grant-plan，唯一写入口）。
 * 记录级 plan = creates（主权限可带 children 一次性建树 / 子权限 parentPermissionId 挂父）
 * + updates（canGrant/conditionCode 微变更）+ removes（主权限级联删子/子权限单条删），
 * 单事务原子执行（任一失败整体回滚）。
 * 响应 = 完整持久化结果（结构同 list 响应 data），成功后前端整体替换 baseline。
 */
export const applyGrantPlan = async (
  params: ApplyGrantPlanReq
): Promise<ItemsResp<RolePermissionItem>> => {
  const res = await http.request<PermResult<ItemsResp<RolePermissionItem>>>(
    "post",
    "/api/perm/role-resource-permission/apply-grant-plan",
    { data: params }
  );
  return unwrap(res);
};

export { type ItemsResp };
