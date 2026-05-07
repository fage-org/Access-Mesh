import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 角色权限项 */
export interface RolePermissionItem {
  id: number;
  resourceTypeCode: string;
  resourceCode: string;
  codeType: string;
  resourceName: string;
  operationCode: string;
  canGrant: boolean;
  conditionCode: string | null;
  scopeAll: boolean;
  dependOn: number | null;
}

/** 角色权限列表响应 */
export interface RolePermissionListResult {
  success: boolean;
  data: {
    items: Array<RolePermissionItem>;
  };
}

/** 角色权限列表请求 */
export interface RolePermissionListRequest {
  domainCode?: string;
  roleTypeCode: string;
  roleExternalId: string;
}

/** 权限授权添加项 */
export interface GrantAddItem {
  resourceTypeCode: string;
  resourceCode?: string;
  codeType?: string;
  operationCode: string;
  scopeAll?: boolean;
  canGrant?: boolean;
  conditionCode?: string;
}

/** 权限授权更新项 */
export interface GrantUpdateItem {
  id: number;
  canGrant?: boolean;
  conditionCode?: string;
}

/** 权限授权请求 */
export interface RoleGrantRequest {
  domainCode?: string;
  roleTypeCode: string;
  roleExternalId: string;
  add?: Array<GrantAddItem>;
  update?: Array<GrantUpdateItem>;
  remove?: Array<number>;
}

/** 批量回收请求 */
export interface BatchRevokeRequest {
  domainCode?: string;
  roleTypeCode: string;
  roleExternalId: string;
  permissionIds: Array<number>;
}

// ========== API 函数 ==========

/** 获取角色权限列表 */
export const getRolePermissions = (data: RolePermissionListRequest) => {
  return http.request<RolePermissionListResult>(
    "post",
    "/api/perm/role-resource-permission/list",
    { data }
  );
};

/** 批量授权/更新/回收权限 */
export const saveRolePermissions = (data: RoleGrantRequest) => {
  return http.request<RolePermissionListResult>(
    "post",
    "/api/perm/role-resource-permission/save",
    { data }
  );
};

/** 批量回收权限 */
export const revokeRolePermissions = (data: BatchRevokeRequest) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/role-resource-permission/revoke",
    { data }
  );
};

// ========== 工具函数 ==========

/** 检查权限是否已配置 */
export const isPermissionConfigured = (
  existing: Array<RolePermissionItem>,
  newItem: GrantAddItem
): RolePermissionItem | null => {
  return (
    existing.find(
      p =>
        p.resourceTypeCode === newItem.resourceTypeCode &&
        p.resourceCode === newItem.resourceCode &&
        p.operationCode === newItem.operationCode
    ) || null
  );
};
