import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 用户分页查询请求 */
export interface UserPageRequest {
  pageNum?: number;
  pageSize?: number;
  sort?: string;
  username?: string;
  name?: string;
  phone?: string;
  email?: string;
  status?: number;
  orgId?: number; // 前端扩展: 组织筛选
}

/** 用户列表项 */
export interface UserPageItem {
  id: number;
  username: string;
  name: string;
  phone?: string;
  email?: string;
  status: number;
  orgs: Array<OrgBrief>;
  createdAt: string;
}

/** 用户列表项（简化版） */
export interface UserListItem {
  id: number;
  username: string;
  nickname: string;
  phone?: string;
  email?: string;
  status: number;
}

/** 组织简要信息 */
export interface OrgBrief {
  orgId: number;
  orgName: string;
  orgType?: string;
  isPrimary: boolean;
}

/** 用户详情 */
export interface UserDetail {
  id: number;
  username: string;
  name: string;
  phone?: string;
  email?: string;
  status: number;
  orgs: Array<OrgBrief>;
  createdAt: string;
  updatedAt?: string;
}

/** 创建用户请求 */
export interface UserCreateRequest {
  username: string;
  name: string;
  phone?: string;
  email?: string;
  status?: number;
}

/** 更新用户请求 */
export interface UserUpdateRequest {
  id: number;
  name?: string;
  phone?: string;
  email?: string;
  status?: number;
}

/** 用户分页响应 */
export interface UserPageResult {
  success: boolean;
  data: {
    list: Array<UserPageItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

/** 用户详情响应 */
export interface UserDetailResult {
  success: boolean;
  data: UserDetail;
}

/** 用户操作响应 */
export interface UserActionResult {
  success: boolean;
  data?: number; // 创建时返回ID
}

/** 重置密码请求 */
export interface ResetPasswordRequest {
  userId: number;
  newPassword: string;
}

/** 启用/停用用户请求 */
export interface UserStatusRequest {
  ids: Array<number>;
}

// ========== API 函数 ==========

/** 用户分页查询 */
export const getUserPage = (data: UserPageRequest) => {
  return http.request<UserPageResult>("post", "/admin/api/user/page", { data });
};

/** 用户列表查询（简化版） */
export interface UserListRequest {
  pageNum?: number;
  pageSize?: number;
  username?: string;
  name?: string;
}

export interface UserListResult {
  success: boolean;
  data: {
    items: Array<UserListItem>;
    total?: number;
  };
}

export const getUserList = (
  data: UserListRequest = { pageNum: 1, pageSize: 100 }
) => {
  return http.request<UserListResult>("post", "/admin/api/user/list", {
    data
  });
};

/** 获取用户详情 */
export const getUserDetail = (data: { id: number }) => {
  return http.request<UserDetailResult>("post", "/admin/api/user/detail", {
    data
  });
};

/** 创建用户 */
export const createUser = (data: UserCreateRequest) => {
  return http.request<UserActionResult>("post", "/admin/api/user/create", {
    data
  });
};

/** 更新用户 */
export const updateUser = (data: UserUpdateRequest) => {
  return http.request<UserActionResult>("post", "/admin/api/user/update", {
    data
  });
};

/** 删除用户 */
export const deleteUser = (data: { ids: Array<number> }) => {
  return http.request<UserActionResult>("post", "/admin/api/user/delete", {
    data
  });
};

/** 启用用户 */
export const enableUser = (data: { ids: Array<number> }) => {
  return http.request<UserActionResult>("post", "/admin/api/user/enable", {
    data
  });
};

/** 重置密码 */
export const resetPassword = (data: ResetPasswordRequest) => {
  return http.request<UserActionResult>(
    "post",
    "/admin/api/user/reset-password",
    { data }
  );
};

/** 用户菜单响应 */
export interface UserMenusResult {
  success: boolean;
  data: {
    menus: Array<{
      id: number;
      name: string;
      path?: string;
      icon?: string;
      children?: Array<{
        id: number;
        name: string;
        path?: string;
        icon?: string;
      }>;
    }>;
    permissions: Array<string>;
  };
}

/** 获取用户菜单和权限 */
export const getUserMenus = (data: { id: number }) => {
  return http.request<UserMenusResult>("post", "/admin/api/user/user-menus", {
    data
  });
};
