/**
 * 角色管理 API
 * 经 @/utils/http 调用 access-service 端点（`/api/perm/abstract-role/*`）；
 * Phase 1 由 mock/role-manage.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.2 / §6.10.3
 * 后端实现：access-service RoleController + RoleManageAppService
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

// ========== 角色类型常量 ==========

/**
 * 5 种抽象角色类型编码（对齐 access-service overview §角色模型）。
 * - ORG / POSITION / PERSONAL：外部同步自动生成（ORG/POSITION 由组织同步、
 *   PERSONAL 由用户同步连带创建 PERSONAL_{external_id}），不在角色管理页展示，
 *   其权限分配归「权限授予」(页面待重做，原 T-FE-014 已废弃) 与「用户详情」(2.1)。
 * - BASIC_ROLE / GROUP_ROLE：功能角色，本页可 CRUD。
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
 * 仅 BASIC_ROLE / GROUP_ROLE——其余三类（ORG/POSITION/PERSONAL）由外部同步生成，
 * 不在本页管理（schema access-service.sql，abstract_user 创建时自动生成 PERSONAL）。
 */
export const MANAGEABLE_ROLE_TYPES: RoleTypeCode[] = [
  ROLE_TYPE_CODE.BASIC_ROLE,
  ROLE_TYPE_CODE.GROUP_ROLE
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
  /** 扩展属性（后端 tree 节点未返回，前端编辑表单按需从 detail 获取；mock 提供） */
  extra?: string | null;
  children: RoleTreeNode[];
};

/** 角色树查询参数（对齐 RoleTreeReq） */
export type RoleTreeQuery = {
  /** 业务域编码，可省略：仅返回全局域角色树 */
  domainCode?: string | null;
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
};

/** 角色移动请求（对齐 RoleMoveReq） */
export type RoleMoveReq = {
  roleId: number;
  parentId?: number | null;
};

/** 角色摘要（对齐 RoleSummaryResp，分组角色额外角色列表项） */
export type RoleSummaryResp = {
  id: number;
  roleTypeCode: string;
  externalId: string;
  name: string;
};

/** 分组角色额外角色查询（对齐 GroupRoleExtraRolesListReq） */
export type GroupRoleExtraRolesQuery = {
  domainCode?: string | null;
  groupRoleTypeCode: string;
  groupRoleExternalId: string;
};

/** 分组角色额外角色增删（对齐 GroupRoleExtraRoleReq） */
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
 * 查询角色树（POST /api/perm/abstract-role/tree）
 * 响应 ItemsResp<RoleTreeResp>，data.items[0].root 为根节点树。
 */
export const getRoleTree = async (
  params: RoleTreeQuery = {}
): Promise<RoleTreeNode[]> => {
  const res = await http.request<PermResult<ItemsResp<{ root: RoleTreeNode }>>>(
    "post",
    "/api/perm/abstract-role/tree",
    { data: params }
  );
  const items = unwrap(res).items ?? [];
  // 后端返回 items:[{root}],前端摊平为根节点数组（通常单根）
  return items.map(it => it.root).filter(Boolean);
};

/** 分页查询角色列表（POST /api/perm/abstract-role/list） */
export const getRoleList = async (
  params: RoleListQuery
): Promise<PaginatedResp<RoleResp>> => {
  const res = await http.request<PermResult<PaginatedResp<RoleResp>>>(
    "post",
    "/api/perm/abstract-role/list",
    { data: params }
  );
  return unwrap(res);
};

/** 创建角色（POST /api/perm/abstract-role/create） */
export const createRole = async (data: RoleCreateReq): Promise<RoleResp> => {
  const res = await http.request<PermResult<RoleResp>>(
    "post",
    "/api/perm/abstract-role/create",
    { data }
  );
  return unwrap(res);
};

/** 更新角色（POST /api/perm/abstract-role/update） */
export const updateRole = async (data: RoleUpdateReq): Promise<RoleResp> => {
  const res = await http.request<PermResult<RoleResp>>(
    "post",
    "/api/perm/abstract-role/update",
    { data }
  );
  return unwrap(res);
};

/** 移动角色树节点（POST /api/perm/abstract-role/move） */
export const moveRole = async (data: RoleMoveReq): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/abstract-role/move",
      {
        data
      }
    )
  );
};

/** 删除角色，支持批量（POST /api/perm/abstract-role/remove） */
export const removeRoles = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/abstract-role/remove",
      {
        data: { ids }
      }
    )
  );
};

/**
 * 查询角色详情（POST /api/perm/abstract-role/detail）
 *
 * 🔧 API 核对项（登记 T-PERM-022）：后端 Controller 现用 IdReq{id}（内部主键），
 * 与 api-contract §6.10.3 / 项目铁律「调用方不应存储 access-service 内部主键」不符；
 * 已有未使用的 RoleDetailReq（业务键 domainCode+roleTypeCode+roleExternalId）待启用。
 * Phase 1 mock 阶段用树节点 id 工作正常；联调需后端切换业务键。详见 docs/design/frontend/role-manage.md。
 */
export const getRoleDetail = async (id: number): Promise<RoleResp> => {
  const res = await http.request<PermResult<RoleResp>>(
    "post",
    "/api/perm/abstract-role/detail",
    { data: { id } }
  );
  return unwrap(res);
};

// ========== 分组角色额外角色 ==========

/** 查询分组角色额外基本角色（POST /api/perm/abstract-role/extra-roles/list） */
export const listExtraRoles = async (
  params: GroupRoleExtraRolesQuery
): Promise<RoleSummaryResp[]> => {
  const res = await http.request<PermResult<ItemsResp<RoleSummaryResp>>>(
    "post",
    "/api/perm/abstract-role/extra-roles/list",
    { data: params }
  );
  return unwrap(res).items ?? [];
};

/** 分组角色添加基本角色（POST /api/perm/abstract-role/extra-roles/add） */
export const addExtraRole = async (
  data: GroupRoleExtraRoleReq
): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/abstract-role/extra-roles/add",
      { data }
    )
  );
};

/** 分组角色移除基本角色（POST /api/perm/abstract-role/extra-roles/remove） */
export const removeExtraRole = async (
  data: GroupRoleExtraRoleReq
): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/abstract-role/extra-roles/remove",
      { data }
    )
  );
};
