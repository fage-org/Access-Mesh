import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 操作权限 */
export interface OperationPermission {
  id: number;
  tenantId: number;
  resourceTypeCode: string;
  resourceTypeName: string;
  code: string;
  name: string;
  binaryBit: number;
  inheritMask: number;
  createdAt: string;
  updatedAt: string | null;
}

/** 操作权限列表响应 */
export interface OperationListResult {
  success: boolean;
  data: {
    items: Array<OperationPermission>;
  };
}

/** 操作权限列表请求 */
export interface OperationListRequest {
  resourceTypeCode?: string;
  domainCode?: string;
}

// ========== API 函数 ==========

/** 获取操作权限列表 */
export const getOperationList = (data?: OperationListRequest) => {
  return http.request<OperationListResult>(
    "post",
    "/api/perm/operation-permission/list",
    { data: data || {} }
  );
};

// ========== 工具函数 ==========

/** 操作权限标签颜色映射 */
export const OPERATION_TAG_COLORS: Record<string, string> = {
  VIEW: "info",
  CREATE: "success",
  UPDATE: "warning",
  DELETE: "danger",
  MANAGE: "primary",
  GRANT: "primary"
};

export const getOperationTagType = (
  code: string
): "primary" | "success" | "warning" | "danger" | "info" => {
  return (OPERATION_TAG_COLORS[code] || "info") as
    | "primary"
    | "success"
    | "warning"
    | "danger"
    | "info";
};
