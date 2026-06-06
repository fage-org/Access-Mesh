/**
 * 用户管理 API
 * 经 @/utils/http 调用 admin-service 端点；Phase 1 由 mock/user-manage.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 */
import { http } from "@/utils/http";

// ========== 统一响应信封（对齐 common.model.PermResult） ==========

/** 后端统一响应包装：code=200 为成功 */
export type PermResult<T> = {
  code: number;
  message: string;
  data: T;
  requestId?: string;
  traceId?: string;
};

/** 按 code 解包，非 200 抛错，交由调用方 try/catch 处理 */
function unwrap<T>(res: PermResult<T>): T {
  if (res.code !== 200) {
    throw new Error(res.message || "请求失败");
  }
  return res.data;
}

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
  roleId: number;
  roleName: string;
  roleTypeCode: "ORG" | "POSITION" | "PERSONAL" | "GROUP_ROLE" | "BASIC_ROLE";
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

/** 创建用户响应（对齐后端 POST /user/create + 初始密码返回） */
export type CreateUserResult = {
  id: number;
  /** 系统生成的随机初始密码（Phase 1 mock：后端暂未返回，此处前端模拟） */
  initialPassword: string;
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
  const res = await http.request<PermResult<UserRoleItem[]>>(
    "post",
    "/user-role/list",
    { data: { userId } }
  );
  return unwrap(res);
};

/** 分配角色（POST /user-role/assign） */
export const assignRole = async (data: {
  userId: number;
  roleId: number;
  validFrom?: string;
  validTo?: string;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-role/assign", { data })
  );
};

/** 回收角色（POST /user-role/revoke） */
export const revokeRole = async (data: {
  userId: number;
  roleId: number;
}): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>("post", "/user-role/revoke", { data })
  );
};
