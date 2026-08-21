/**
 * 用户管理 API
 * 经 @/utils/http 调用 access-service 端点；Phase 1 由 mock/user-manage.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

// ========== 类型定义 ==========

/** 组织树查询参数（对齐后端 OrgQuery + treeConfigId） */
export type OrgQuery = {
  /** 操作权限码，区分场景：VIEW=查看 / CREATE=新增用户时可选组织 */
  operationCode: string;
  /** 组织树配置ID，不传则返回默认树 */
  treeConfigId?: number;
  orgName?: string;
  orgType?: number;
  status?: number;
};

/** 组织树配置（对齐后端 OrgTreeConfigResp） */
export type OrgTreeConfig = {
  id: number;
  treeName: string;
  rootOrgId: number;
  isDefault: boolean;
};

/** 组织树节点（对齐后端 OrgResp） */
export type OrgTreeNode = {
  id: number;
  orgName: string;
  code: string;
  parentOrgId: number | null;
  orgType: number; // 字典值（后端 Integer）
  status: number;
  sort: number;
  children: OrgTreeNode[];
};

export type UserItem = {
  id: number;
  username: string;
  name: string;
  phone: string | null;
  email: string | null;
  status: 0 | 1;
  orgs: {
    orgId: number;
    orgName: string;
    isPrimary: boolean;
  }[];
  createdAt: string;
};

/** 分页查询参数（对齐后端 UserPageReq + orgId） */
export type UserPageQuery = {
  pageNum: number;
  pageSize: number;
  sort?: string;
  username?: string;
  name?: string;
  phone?: string;
  email?: string;
  status?: number;
  /** 按组织筛选（后端需新增此字段） */
  orgId?: number;
};

/** 分页响应（对齐后端 PaginatedResult） */
export type PaginatedResult<T> = {
  items: T[];
  pagination: {
    total: number;
    page: number;
    size: number;
    totalPages: number;
  };
};

export type UserRoleItem = {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  roleTypeLabel: string;
  targetType: string;
  relationId: number | null;
  relationOrgName: string | null;
  validFrom: string | null;
  validTo: string | null;
};

/** 组织简要信息（对齐后端 OrgBrief） */
export type OrgBrief = {
  orgId: number;
  orgName: string;
  orgType?: string;
  isPrimary: boolean;
};

/** 组织下的用户项（对齐后端 OrgUserItem） */
export type OrgUserItem = {
  userId: number;
  username: string;
  name: string;
  avatar?: string;
  isPrimary: boolean;
};

/** 创建用户响应（对齐后端 POST /user/create + 初始密码返回） */
export type CreateUserResult = {
  id: number;
  /** 系统生成的随机初始密码（Phase 1 mock：后端暂未返回，此处前端模拟） */
  initialPassword: string;
};

// ========== 组织 CRUD 类型 ==========

/** 组织创建请求（对齐后端 OrgCreateReq，必填字段完整） */
export type OrgCreateReq = {
  orgName: string;
  code: string;
  orgType: number;
  parentOrgId?: number | null;
  status?: number;
  sort?: number;
};

/** 组织更新请求（对齐后端 OrgUpdateReq，仅需传变更字段） */
export type OrgUpdateReq = {
  id: number;
  orgName?: string;
  code?: string;
  orgType?: number;
  parentOrgId?: number | null;
  status?: number;
  sort?: number;
};

/** 组织分页查询参数 */
export type OrgPageQuery = {
  pageNum: number;
  pageSize: number;
  orgName?: string;
  orgType?: number;
  status?: number;
  /** 按选中组织子树筛选（用于岗位 Tab） */
  orgId?: number;
};

/** 组织分页项（平铺，不含 children） */
export type OrgPageItem = {
  id: number;
  orgName: string;
  code: string;
  parentOrgId: number | null;
  orgType: number;
  status: number;
  sort: number;
  parentOrgName?: string;
};

// ========== 功能角色类型 ==========

/** 功能角色列表项（仅 BASIC_ROLE / GROUP_ROLE / PERSONAL，不含 ORG / POSITION） */
export type RoleItem = {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  roleTypeLabel?: string;
};

// ========== API 函数 ==========

/** 获取可用组织树配置列表（POST /org-tree-config/page，分页取前 100 条） */
export const getOrgTreeConfigs = async (): Promise<OrgTreeConfig[]> => {
  const res = await http.request<PermResult<PaginatedResult<OrgTreeConfig>>>(
    "post",
    "/org-tree-config/page",
    { data: { pageNum: 1, pageSize: 100 } }
  );
  return unwrap(res).items;
};

/** 获取组织树（POST /org/tree） */
export const getOrgTree = async (params: OrgQuery): Promise<OrgTreeNode[]> => {
  const res = await http.request<PermResult<OrgTreeNode[]>>(
    "post",
    "/org/tree",
    { data: params }
  );
  return unwrap(res);
};

/** 获取用户分页列表（POST /user/page） */
export const getUserPage = async (
  params: UserPageQuery
): Promise<PaginatedResult<UserItem>> => {
  const res = await http.request<PermResult<PaginatedResult<UserItem>>>(
    "post",
    "/user/page",
    { data: params }
  );
  return unwrap(res);
};

/** 创建用户（POST /user/create，mock 阶段一步完成组织分配） */
export const createUser = async (data: {
  username: string;
  name: string;
  phone?: string;
  email?: string;
  /** 所属组织ID（可选，mock 阶段一步完成；后端需分两步调用 /user-org/assign） */
  orgId?: number;
}): Promise<CreateUserResult> => {
  const res = await http.request<PermResult<CreateUserResult>>(
    "post",
    "/user/create",
    { data }
  );
  return unwrap(res);
};

/** 更新用户（POST /user/update） */
export const updateUser = async (data: {
  id: number;
  name?: string;
  phone?: string | null;
  email?: string | null;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user/update", { data })
  );
};

/** 删除用户（POST /user/delete，IdsReq） */
export const deleteUser = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user/delete", {
      data: { ids }
    })
  );
};

// ========== /user-org API ==========

/** 查询用户所属组织（POST /user-org/list，IdReq） */
export const getUserOrgs = async (userId: number): Promise<OrgBrief[]> => {
  const res = await http.request<PermResult<OrgBrief[]>>(
    "post",
    "/user-org/list",
    { data: { id: userId } }
  );
  return unwrap(res);
};

/** 分配组织（POST /user-org/assign） */
export const assignUserOrgs = async (data: {
  userId: number;
  orgIds: number[];
  primaryOrgId?: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-org/assign", { data })
  );
};

/** 移除组织关联（POST /user-org/remove） */
export const removeUserOrg = async (data: {
  userId: number;
  orgId: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-org/remove", { data })
  );
};

/** 设置主组织（POST /user-org/set-primary） */
export const setPrimaryOrg = async (data: {
  userId: number;
  orgId: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-org/set-primary", {
      data
    })
  );
};

// ========== /user-role API（Phase 1 mock；Phase 2 admin 代理） ==========

/** 获取用户角色列表（POST /user-role/list） */
export const getUserRoles = async (userId: number): Promise<UserRoleItem[]> => {
  const res = await http.request<PermResult<{ items: UserRoleItem[] }>>(
    "post",
    "/user-role/list",
    { data: { userId } }
  );
  return unwrap(res).items;
};

/** 分配角色（POST /user-role/assign，业务键标识） */
export const assignRole = async (data: {
  userId: number;
  roleTypeCode: string;
  roleExternalId: string;
  validFrom?: string;
  validTo?: string;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-role/assign", { data })
  );
};

/** 回收角色（POST /user-role/revoke，业务键标识） */
export const revokeRole = async (data: {
  userId: number;
  roleTypeCode: string;
  roleExternalId: string;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-role/revoke", { data })
  );
};

// ========== /org CRUD API ==========

/** 创建组织（POST /org/create） */
export const createOrg = async (
  data: OrgCreateReq
): Promise<{ id: number }> => {
  const res = await http.request<PermResult<{ id: number }>>(
    "post",
    "/org/create",
    { data }
  );
  return unwrap(res);
};

/** 更新组织（POST /org/update，仅传变更字段） */
export const updateOrg = async (data: OrgUpdateReq): Promise<void> => {
  unwrap(await http.request<PermResult<void>>("post", "/org/update", { data }));
};

/** 删除组织（POST /org/delete，IdReq） */
export const deleteOrg = async (id: number): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/org/delete", {
      data: { id }
    })
  );
};

/** 组织分页列表（POST /org/page，平铺不含 children） */
export const getOrgPage = async (
  params: OrgPageQuery
): Promise<PaginatedResult<OrgPageItem>> => {
  const res = await http.request<PermResult<PaginatedResult<OrgPageItem>>>(
    "post",
    "/org/page",
    { data: params }
  );
  return unwrap(res);
};

// ========== /user 启停 & 重置密码 API ==========

/** 批量启用/禁用用户（POST /user/enable，IdsReq + status） */
export const enableUsers = async (data: {
  ids: number[];
  status: 0 | 1;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user/enable", { data })
  );
};

/** 重置用户密码（POST /user/reset-password） */
export const resetUserPassword = async (data: {
  userId: number;
  newPassword?: string;
}): Promise<{ newPassword: string }> => {
  const res = await http.request<PermResult<{ newPassword: string }>>(
    "post",
    "/user/reset-password",
    { data }
  );
  return unwrap(res);
};

// ========== /role API（仅功能角色） ==========

/** 获取功能角色列表（POST /role/list，仅 BASIC_ROLE / GROUP_ROLE / PERSONAL） */
export const getRoleList = async (): Promise<RoleItem[]> => {
  const res = await http.request<PermResult<{ items: RoleItem[] }>>(
    "post",
    "/role/list",
    {
      data: {}
    }
  );
  return unwrap(res).items;
};

// ========== /org/users 查询组织下用户 ==========

/** 组织下的用户（POST /org/users） */
export const getOrgUsers = async (orgId: number): Promise<OrgUserItem[]> => {
  const res = await http.request<PermResult<OrgUserItem[]>>(
    "post",
    "/org/users",
    { data: { orgId } }
  );
  return unwrap(res);
};
