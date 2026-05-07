import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 用户角色关系项 */
export interface UserRoleItem {
  roleExternalId: string;
  roleName: string;
  roleTypeCode: string;
  targetType: string;
  relationId: number;
  validFrom: string | null;
  validTo: string | null;
}

/** 用户角色列表响应 */
export interface UserRolesResult {
  success: boolean;
  data: {
    subjectTypeCode: string;
    subjectExternalId: string;
    roles: Array<UserRoleItem>;
  };
}

/** 用户角色列表请求 */
export interface UserRoleListRequest {
  subjectTypeCode: string;
  subjectExternalId: string;
}

/** 批量分配角色请求 */
export interface UserRoleBatchAssignRequest {
  subjectExternalIds: Array<string>;
  subjectTypeCode: string;
  domainCode: string;
  roleTypeCode: string;
  roleExternalId: string;
  relationId?: number;
}

/** 批量回收角色请求项 */
export interface RevokeItem {
  subjectTypeCode: string;
  subjectExternalId: string;
  domainCode: string;
  roleTypeCode: string;
  roleExternalId: string;
  relationId?: number;
}

/** 批量回收角色请求 */
export interface UserRoleBatchRevokeRequest {
  items: Array<RevokeItem>;
}

// ========== API 函数 ==========

/** 查询用户角色列表 */
export const getUserRoles = (data: UserRoleListRequest) => {
  return http.request<UserRolesResult>("post", "/api/perm/user-role/list", {
    data
  });
};

/** 批量分配角色 */
export const batchAssignRoles = (data: UserRoleBatchAssignRequest) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/user-role/batch-assign",
    { data }
  );
};

/** 批量回收角色 */
export const batchRevokeRoles = (data: UserRoleBatchRevokeRequest) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/user-role/revoke",
    { data }
  );
};

// ========== 常量 ==========

/** 主体类型编码 */
export const SUBJECT_TYPE_CODES = {
  USER: "USER"
} as const;
