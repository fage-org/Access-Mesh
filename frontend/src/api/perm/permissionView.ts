import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 有效权限项 */
export interface EffectivePermissionItem {
  resourceTypeCode: string;
  resourceCode: string;
  resourceName: string;
  codeType: string;
  operationCodes: Array<string>;
  scopeAll: boolean;
  sourceRoles: Array<SourceRole>;
  sourceRoleCount: number;
  sourceRolesTruncated: boolean;
  matchedPermissionIds: Array<number>;
}

/** 来源角色 */
export interface SourceRole {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  via: Array<string>;
}

/** 用户权限查询请求 */
export interface UserPermissionViewRequest {
  targetType: string;
  subjectTypeCode?: string;
  subjectExternalId?: string;
  domainCode?: string;
  roleTypeCode?: string;
  roleExternalId?: string;
  resourceTypeCodes?: Array<string>;
  operationCodes?: Array<string>;
  resourceKeyword?: string;
  sourceRoleExternalId?: string;
  includeScopes?: boolean;
  includeApiResources?: boolean;
  includeSourceRoles?: boolean;
  sourceRoleLimit?: number;
  pageNum?: number;
  pageSize?: number;
}

/** 有效权限响应 */
export interface EffectivePermissionsResult {
  success: boolean;
  data: {
    targetType: string;
    items: Array<EffectivePermissionItem>;
    total: number;
    pageNum: number;
    pageSize: number;
    hasNext: boolean;
  };
}

/** 权限解释请求 */
export interface PermissionExplainRequest {
  targetType: string;
  subjectTypeCode?: string;
  subjectExternalId?: string;
  roleTypeCode?: string;
  roleExternalId?: string;
  domainCode?: string;
  resourceTypeCode: string;
  resourceCode: string;
  codeType?: string;
  operationCode: string;
  includeSourceRoles?: boolean;
  includeRecentChanges?: boolean;
  recentDays?: number;
}

/** 权限解释响应 */
export interface PermissionExplainResult {
  success: boolean;
  data: {
    targetType: string;
    allowed: boolean;
    reason: string;
    permission: {
      domainCode: string;
      resourceTypeCode: string;
      resourceCode: string;
      codeType: string;
      operationCode: string;
      scopeAll: boolean;
    };
    sourceRoles: Array<SourceRole>;
    matchedPermissionIds: Array<number>;
    recentChanges: Array<RecentChange>;
  };
}

/** 近期变更 */
export interface RecentChange {
  changeLogId: number;
  eventType: string;
  changeType: string;
  impactLevel: string;
  message: string;
  permission: {
    domainCode: string;
    resourceTypeCode: string;
    resourceCode: string;
    codeType: string;
    operationCode: string;
    scopeAll: boolean;
  };
  sourceRole: {
    roleTypeCode: string;
    roleExternalId: string;
    roleName: string;
  };
  operatorId: number;
  operatorName: string;
  changeReason: string;
  createdAt: string;
}

// ========== API 函数 ==========

/** 查询用户/角色有效权限 */
export const getEffectivePermissions = (data: UserPermissionViewRequest) => {
  return http.request<EffectivePermissionsResult>(
    "post",
    "/api/perm/permission-view/effective-permissions",
    { data }
  );
};

/** 解释单个权限 */
export const explainPermission = (data: PermissionExplainRequest) => {
  return http.request<PermissionExplainResult>(
    "post",
    "/api/perm/permission-view/explain",
    { data }
  );
};

// ========== 工具函数 ==========

/** 获取影响级别标签 */
export const IMPACT_LEVEL_TAG = {
  HIGH: { text: "高影响", type: "danger" as const },
  MEDIUM: { text: "中等影响", type: "warning" as const },
  LOW: { text: "低影响", type: "info" as const },
  NONE: { text: "无影响", type: "success" as const }
} as const;

export const getImpactLevelTag = (
  level: string
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    IMPACT_LEVEL_TAG[level.toUpperCase() as keyof typeof IMPACT_LEVEL_TAG] || {
      text: level,
      type: "info" as const
    }
  );
};

/** 获取变更类型标签 */
export const CHANGE_TYPE_TAG = {
  CREATED: { text: "新增", type: "success" as const },
  UPDATED: { text: "更新", type: "warning" as const },
  DELETED: { text: "删除", type: "danger" as const }
} as const;

export const getChangeTypeTag = (
  type: string
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    CHANGE_TYPE_TAG[type.toUpperCase() as keyof typeof CHANGE_TYPE_TAG] || {
      text: type,
      type: "info" as const
    }
  );
};
