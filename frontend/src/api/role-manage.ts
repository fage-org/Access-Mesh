/**
 * 角色管理 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/abstract-role/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/abstract-role`）。
 * T-FE-041 切换真实链路后旧 `/api/perm/**` mock 路径自然失配；T-FE-016 联调收口，
 * mock/role-manage.ts 已随切换退役删除（Phase 3 模式，同 mock/user-manage.ts）。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.2 / §6.10.3
 * 后端实现：access-service RoleController + RoleManageAppService
 * Gateway 注册：bootstrap 管理 API 清单（BootstrapGraphDefinition.apiRoutes，
 * tree/create 先在册，update/remove/move/detail 随 T-FE-016 补注册）
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

// ========== 角色类型常量 ==========

/**
 * 5 种抽象角色类型编码（对齐 access-service overview §角色模型）。
 * - ORG / POSITION / PERSONAL：外部同步自动生成（ORG/POSITION 由组织同步、
 *   PERSONAL 由用户同步连带创建 PERSONAL_{external_id}），不在角色管理页展示，
 *   其权限分配归「权限授予」(页面待重做，原 T-FE-014 已废弃) 与「用户详情」(2.1)。
 * - BASIC_ROLE：首期唯一功能角色，本页可 CRUD。
 * - GROUP_ROLE：T-PERM-043 后端写入口已删除，本页隐藏（读模型类型码保留，
 *   供权限授予等读场景识别）。
 */
export const ROLE_TYPE_CODE = {
  ORG: "ORG",
  POSITION: "POSITION",
  BASIC_ROLE: "BASIC_ROLE",
  GROUP_ROLE: "GROUP_ROLE",
  PERSONAL: "PERSONAL"
} as const;

export type RoleTypeCode = (typeof ROLE_TYPE_CODE)[keyof typeof ROLE_TYPE_CODE];

/** 角色类型中文标签（前端展示用，roleTypeName 后端已返回但统一映射更稳） */
export const ROLE_TYPE_LABEL: Record<string, string> = {
  ORG: "组织角色",
  POSITION: "岗位角色",
  BASIC_ROLE: "基础角色",
  GROUP_ROLE: "分组角色",
  PERSONAL: "个人角色"
};

/**
 * 角色管理页可手工 CRUD 的功能角色类型。
 * T-PERM-043 后仅 BASIC_ROLE——首期唯一功能角色；GROUP_ROLE 后端写入口已删除
 * （extra-roles/* 退役、create/update 拒绝 20022），选项从本页隐藏。
 * 代码保留：ROLE_TYPE_CODE/ROLE_TYPE_LABEL 与额外角色面板/API 封装不删，
 * 待未来按 role_inclusion 单事实源立项后随常量恢复。
 * 其余三类（ORG/POSITION/PERSONAL）由外部同步生成，不在本页管理。
 */
export const MANAGEABLE_ROLE_TYPES: RoleTypeCode[] = [
  ROLE_TYPE_CODE.BASIC_ROLE
];

// ========== 类型定义 ==========

/** 角色状态：0=禁用，1=启用（对齐 RoleResp.status） */
export type RoleStatus = 0 | 1;

/** 角色响应（对齐后端 RoleResp） */
export type RoleResp = {
  id: number;
  tenantId?: number;
  parentId: number | null;
  roleTypeCode: string;
  roleTypeName?: string;
  externalId: string | null;
  name: string;
  status: RoleStatus;
  sortOrder: number;
  extra: string | null;
  createdAt?: string;
  updatedAt?: string;
};

/** 角色树节点（对齐后端 RoleTreeResp.RoleTreeNode） */
export type RoleTreeNode = {
  id: number;
  tenantId?: number;
  parentId: number | null;
  roleTypeCode: string;
  name: string;
  externalId: string | null;
  status: RoleStatus;
  sortOrder: number;
  /** 扩展属性（后端 tree 节点不返回，恒 undefined；编辑表单按业务键拉 getRoleDetail 回填，T-FE-016 接线） */
  extra?: string | null;
  children: RoleTreeNode[];
};

/** 角色树查询参数（对齐 RoleTreeReq） */
export type RoleTreeQuery = {
  /** 业务域编码，可省略或 null：返回全部角色树；有值仅校验域覆盖性，不按域过滤（§6.10.3） */
  domainCode?: string | null;
  /**
   * 仅返回启用角色（T-PERM-022 定案）：默认 false 返回全部有效角色（含禁用，
   * 角色管理页需禁用角色可见可再启用）；授权页主体树传 true，后端 SQL 过滤。
   */
  enabledOnly?: boolean | null;
};

/** 角色列表查询参数（对齐 RoleListReq） */
export type RoleListQuery = {
  domainCode?: string | null;
  /** 单类型过滤（兼容） */
  roleTypeCode?: string | null;
  /** 多类型过滤（并集去重） */
  roleTypeCodes?: string[];
  keyword?: string | null;
  pageNum?: number;
  pageSize?: number;
  sort?: string | null;
};

/** 分页响应（对齐后端 PaginatedResp） */
export type PaginatedResp<T> = {
  items: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
};

/** 列表响应（对齐后端 ItemsResp） */
export type ItemsResp<T> = {
  items: T[];
};

/** 角色创建请求（对齐 RoleCreateReq） */
export type RoleCreateReq = {
  parentId?: number | null;
  roleTypeCode: string;
  externalId?: string | null;
  name: string;
  sortOrder?: number;
  extra?: string | null;
};

/** 角色更新请求（对齐 RoleUpdateReq） */
export type RoleUpdateReq = {
  roleId: number;
  name?: string;
  status?: RoleStatus;
  sortOrder?: number;
  extra?: string | null;
  /** 清空 extra 为 null 的显式标志，true 时优先于 extra（JSON null 无法区分「未传」与「清空」，T-FE-016 对齐 T-PERM-028 资源域口径） */
  extraClear?: boolean;
};

/** 角色移动请求（对齐 RoleMoveReq） */
export type RoleMoveReq = {
  roleId: number;
  parentId?: number | null;
};

/**
 * 角色摘要（历史对齐 RoleSummaryResp；T-PERM-043 后后端 DTO 已删，类型仅供下方
 * 已退役的 extra-roles 封装引用，勿在新代码中使用）
 */
export type RoleSummaryResp = {
  id: number;
  roleTypeCode: string;
  externalId: string;
  name: string;
};

/** 分组角色额外角色查询（历史对齐 GroupRoleExtraRolesListReq，后端 DTO 已删；已退役保留） */
export type GroupRoleExtraRolesQuery = {
  domainCode?: string | null;
  groupRoleTypeCode: string;
  groupRoleExternalId: string;
};

/** 分组角色额外角色增删（历史对齐 GroupRoleExtraRoleReq，后端 DTO 已删；已退役保留） */
export type GroupRoleExtraRoleReq = {
  groupDomainCode?: string | null;
  groupRoleTypeCode: string;
  groupRoleExternalId: string;
  basicDomainCode?: string | null;
  basicRoleTypeCode: string;
  basicRoleExternalId: string;
};

// ========== API 函数 ==========

/**
 * 查询角色树（POST /perm/api/perm/abstract-role/tree）
 * 响应 ItemsResp<RoleTreeResp>，data.items[0].root 为根节点树。
 */
export const getRoleTree = async (
  params: RoleTreeQuery = {}
): Promise<RoleTreeNode[]> => {
  const res = await http.request<PermResult<ItemsResp<{ root: RoleTreeNode }>>>(
    "post",
    "/perm/api/perm/abstract-role/tree",
    { data: params }
  );
  const items = unwrap(res).items ?? [];
  // 后端返回 items:[{root}],前端摊平为根节点数组（通常单根）
  return items.map(it => it.root).filter(Boolean);
};

/** 分页查询角色列表（POST /perm/api/perm/abstract-role/list） */
export const getRoleList = async (
  params: RoleListQuery
): Promise<PaginatedResp<RoleResp>> => {
  const res = await http.request<PermResult<PaginatedResp<RoleResp>>>(
    "post",
    "/perm/api/perm/abstract-role/list",
    { data: params }
  );
  return unwrap(res);
};

/** 创建角色（POST /perm/api/perm/abstract-role/create） */
export const createRole = async (data: RoleCreateReq): Promise<RoleResp> => {
  const res = await http.request<PermResult<RoleResp>>(
    "post",
    "/perm/api/perm/abstract-role/create",
    { data }
  );
  return unwrap(res);
};

/** 更新角色（POST /perm/api/perm/abstract-role/update） */
export const updateRole = async (data: RoleUpdateReq): Promise<RoleResp> => {
  const res = await http.request<PermResult<RoleResp>>(
    "post",
    "/perm/api/perm/abstract-role/update",
    { data }
  );
  return unwrap(res);
};

/** 移动角色树节点（POST /perm/api/perm/abstract-role/move） */
export const moveRole = async (data: RoleMoveReq): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/abstract-role/move",
      {
        data
      }
    )
  );
};

/** 删除角色，支持批量（POST /perm/api/perm/abstract-role/remove） */
export const removeRoles = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/abstract-role/remove",
      {
        data: { ids }
      }
    )
  );
};

/**
 * 角色详情查询参数（对齐 RoleDetailReq，T-PERM-022 定案：业务键二元组，
 * tenantId 走上下文；依据 schema 唯一索引 uk_abstract_role_external）
 */
export type RoleDetailQuery = {
  roleTypeCode: string;
  roleExternalId: string;
};

/**
 * 查询角色详情（POST /perm/api/perm/abstract-role/detail）
 *
 * T-PERM-022 收口：业务键二元组定位（原 IdReq{id} 内部主键废弃——对齐
 * 「调用方不应存储 access-service 内部主键」口径）；未命中 data=null。
 * RoleTreeNode 已返回 roleTypeCode + externalId，调用方可直接取用。
 */
export const getRoleDetail = async (
  params: RoleDetailQuery
): Promise<RoleResp | null> => {
  const res = await http.request<PermResult<RoleResp | null>>(
    "post",
    "/perm/api/perm/abstract-role/detail",
    { data: params }
  );
  return unwrap(res);
};

// ========== 分组角色额外角色（T-PERM-043 已退役：后端 extra-roles/* 三接口已删，
// 以下封装保留供角色页隐藏面板代码引用，恢复 role_inclusion 立项时随 MANAGEABLE_ROLE_TYPES 放开；勿在新代码中调用） ==========

/** 查询分组角色额外基本角色（已退役，POST /perm/api/perm/abstract-role/extra-roles/list） */
export const listExtraRoles = async (
  params: GroupRoleExtraRolesQuery
): Promise<RoleSummaryResp[]> => {
  const res = await http.request<PermResult<ItemsResp<RoleSummaryResp>>>(
    "post",
    "/perm/api/perm/abstract-role/extra-roles/list",
    { data: params }
  );
  return unwrap(res).items ?? [];
};

/** 分组角色添加基本角色（已退役，POST /perm/api/perm/abstract-role/extra-roles/add） */
export const addExtraRole = async (
  data: GroupRoleExtraRoleReq
): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/abstract-role/extra-roles/add",
      { data }
    )
  );
};

/** 分组角色移除基本角色（已退役，POST /perm/api/perm/abstract-role/extra-roles/remove） */
export const removeExtraRole = async (
  data: GroupRoleExtraRoleReq
): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/abstract-role/extra-roles/remove",
      { data }
    )
  );
};
