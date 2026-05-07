import { http } from "@/utils/http";
import type { OrgBrief } from "@/api/admin/user";

// ========== 类型定义 ==========

/** 组织树节点 */
export interface OrgNode {
  id: number;
  orgType: number;
  orgName: string;
  parentOrgId?: string;
  code?: string;
  phone?: string;
  email?: string;
  status: number;
  sort?: number;
  createdAt?: string;
  updatedAt?: string;
  children?: Array<OrgNode>;
}

/** 组织分页查询请求 */
export interface OrgPageRequest {
  pageNum?: number;
  pageSize?: number;
  sort?: string;
  orgName?: string;
  orgType?: number;
  status?: number;
}

/** 组织查询(用于树查询) */
export interface OrgQuery {
  orgName?: string;
  orgType?: number;
  status?: number;
}

/** 创建组织请求 */
export interface OrgCreateRequest {
  orgType: number;
  orgName: string;
  parentOrgId?: string;
  code?: string;
  phone?: string;
  email?: string;
  status?: number;
  sort?: number;
}

/** 更新组织请求 */
export interface OrgUpdateRequest {
  id: number;
  orgType?: number;
  orgName?: string;
  parentOrgId?: string;
  code?: string;
  phone?: string;
  email?: string;
  status?: number;
  sort?: number;
}

/** 组织分页响应 */
export interface OrgPageResult {
  success: boolean;
  data: {
    list: Array<OrgNode>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

/** 组织树响应 */
export interface OrgTreeResult {
  success: boolean;
  data: Array<OrgNode>;
}

/** 组织详情响应 */
export interface OrgDetailResult {
  success: boolean;
  data: OrgNode;
}

/** 组织操作响应 */
export interface OrgActionResult {
  success: boolean;
  data?: number;
}

/** 分配用户到组织请求 */
export interface UserOrgAssignRequest {
  userId: number;
  orgIds: Array<number>;
}

/** 移除用户组织请求 */
export interface UserOrgRemoveRequest {
  userId: number;
  orgId: number;
}

/** 设置主组织请求 */
export interface UserOrgSetPrimaryRequest {
  userId: number;
  orgId: number;
}

/** 用户组织列表响应 */
export interface UserOrgListResult {
  success: boolean;
  data: Array<OrgBrief>;
}

// ========== API 函数 ==========

/** 获取组织树 */
export const getOrgTree = (data?: OrgQuery) => {
  return http.request<OrgTreeResult>("post", "/admin/api/org/tree", {
    data: data || {}
  });
};

/** 组织分页查询 */
export const getOrgPage = (data: OrgPageRequest) => {
  return http.request<OrgPageResult>("post", "/admin/api/org/page", { data });
};

/** 获取组织详情 */
export const getOrgDetail = (data: { id: number }) => {
  return http.request<OrgDetailResult>("post", "/admin/api/org/detail", {
    data
  });
};

/** 创建组织 */
export const createOrg = (data: OrgCreateRequest) => {
  return http.request<OrgActionResult>("post", "/admin/api/org/create", {
    data
  });
};

/** 更新组织 */
export const updateOrg = (data: OrgUpdateRequest) => {
  return http.request<OrgActionResult>("post", "/admin/api/org/update", {
    data
  });
};

/** 删除组织 */
export const deleteOrg = (data: { id: number }) => {
  return http.request<OrgActionResult>("post", "/admin/api/org/delete", {
    data
  });
};

/** 分配用户到组织 */
export const assignUserToOrgs = (data: UserOrgAssignRequest) => {
  return http.request<OrgActionResult>("post", "/admin/api/user-org/assign", {
    data
  });
};

/** 从组织移除用户 */
export const removeUserFromOrg = (data: UserOrgRemoveRequest) => {
  return http.request<OrgActionResult>("post", "/admin/api/user-org/remove", {
    data
  });
};

/** 设置用户主组织 */
export const setPrimaryOrg = (data: UserOrgSetPrimaryRequest) => {
  return http.request<OrgActionResult>(
    "post",
    "/admin/api/user-org/set-primary",
    { data }
  );
};

/** 获取用户的组织列表 */
export const getUserOrgs = (data: { id: number }) => {
  return http.request<UserOrgListResult>("post", "/admin/api/user-org/list", {
    data
  });
};
