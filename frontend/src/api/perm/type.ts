import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 类型定义项 */
export interface ResourceTypeItem {
  id: number;
  tenantId: number;
  typeCategory: string;
  code: string;
  name: string;
  value: number;
  description: string | null;
  status: number;
  createdAt: string;
  updatedAt: string | null;
}

/** 类型定义列表响应 */
export interface TypeListResult {
  success: boolean;
  data: {
    items: Array<ResourceTypeItem>;
  };
}

/** 类型定义列表请求 */
export interface TypeListRequest {
  typeCategory?: string;
}

/** 类型定义详情响应 */
export interface TypeDetailResult {
  success: boolean;
  data: ResourceTypeItem;
}

/** 创建类型定义请求 */
export interface TypeCreateRequest {
  typeCategory: string;
  code: string;
  name: string;
  value: number;
  description?: string;
  status?: number;
}

/** 更新类型定义请求 */
export interface TypeUpdateRequest {
  id: number;
  code?: string;
  name?: string;
  value?: number;
  description?: string;
  status?: number;
}

// ========== API 函数 ==========

/** 获取类型定义列表 */
export const getResourceTypeList = (data?: TypeListRequest) => {
  return http.request<TypeListResult>(
    "post",
    "/api/perm/type-definition/list",
    { data: data || {} }
  );
};

/** 获取类型定义详情 */
export const getTypeDetail = (data: { id: number }) => {
  return http.request<TypeDetailResult>(
    "post",
    "/api/perm/type-definition/detail",
    { data }
  );
};

/** 创建类型定义 */
export const createType = (data: TypeCreateRequest) => {
  return http.request<TypeDetailResult>(
    "post",
    "/api/perm/type-definition/create",
    { data }
  );
};

/** 更新类型定义 */
export const updateType = (data: TypeUpdateRequest) => {
  return http.request<TypeDetailResult>(
    "post",
    "/api/perm/type-definition/update",
    { data }
  );
};

/** 删除类型定义 */
export const deleteType = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/type-definition/remove",
    { data }
  );
};

// ========== 工具函数 ==========

/** 类型分类常量 */
export const TYPE_CATEGORIES = {
  RESOURCE_TYPE: "RESOURCE_TYPE",
  SUBJECT_TYPE: "SUBJECT_TYPE",
  ROLE_TYPE: "ROLE_TYPE"
} as const;

/** 类型状态标签 */
export const TYPE_STATUS_TAG = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
};

export const getTypeStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === 1 ? TYPE_STATUS_TAG.ENABLED : TYPE_STATUS_TAG.DISABLED;
};
