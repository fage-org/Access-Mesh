import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 服务配置项 */
export interface ServiceConfigItem {
  id: number;
  tenantId: number;
  serviceCode: string;
  name: string;
  basePath: string | null;
  description: string | null;
  status: number;
  extra: string | null;
  createdAt: string;
}

/** 服务配置列表响应 */
export interface ServiceConfigListResult {
  success: boolean;
  data: {
    items: Array<ServiceConfigItem>;
  };
}

/** 服务配置详情响应 */
export interface ServiceConfigDetailResult {
  success: boolean;
  data: ServiceConfigItem;
}

/** 创建/更新服务请求 */
export interface ServiceConfigRequest {
  serviceCode: string;
  name: string;
  basePath?: string;
  description?: string;
  status?: number;
  extra?: string;
}

/** 同步结果 */
export interface ServiceSyncResult {
  createdResources: number;
  updatedResources: number;
  createdMappings: number;
  updatedMappings: number;
  deletedResources: number;
  deletedMappings: number;
}

/** 同步响应 */
export interface ServiceSyncResultResponse {
  success: boolean;
  data: ServiceSyncResult;
}

/** API映射项 */
export interface ApiMappingItem {
  id: number;
  tenantId: number;
  resourceEntityId: number;
  serviceCode: string;
  httpMethod: string;
  pathPattern: string;
  matchOrder: number;
  enabled: boolean;
  extra: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** API映射列表响应 */
export interface ApiMappingListResult {
  success: boolean;
  data: {
    items: Array<ApiMappingItem>;
  };
}

// ========== API 函数 ==========

/** 获取服务配置列表 */
export const getServiceConfigList = () => {
  return http.request<ServiceConfigListResult>(
    "post",
    "/api/perm/service-config/list",
    { data: {} }
  );
};

/** 获取服务配置详情 */
export const getServiceConfigDetail = (data: { serviceCode: string }) => {
  return http.request<ServiceConfigDetailResult>(
    "post",
    "/api/perm/service-config/detail",
    { data }
  );
};

/** 创建/更新服务配置 */
export const saveServiceConfig = (data: ServiceConfigRequest) => {
  return http.request<ServiceConfigDetailResult>(
    "post",
    "/api/perm/service-config/save",
    { data }
  );
};

/** 删除服务配置 */
export const deleteServiceConfig = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/service-config/remove",
    { data }
  );
};

/** 同步服务接口 */
export const syncServiceConfig = (data: { serviceCode: string }) => {
  return http.request<ServiceSyncResultResponse>(
    "post",
    "/api/perm/service-config/sync",
    { data }
  );
};

/** 获取服务API映射列表 */
export const getServiceApis = (data: { serviceCode: string }) => {
  return http.request<ApiMappingListResult>(
    "post",
    "/api/perm/service-config/apis",
    { data }
  );
};

// ========== 工具函数 ==========

/** HTTP Method 标签颜色 */
export const HTTP_METHOD_TAG = {
  GET: { text: "GET", type: "success" as const },
  POST: { text: "POST", type: "primary" as const },
  PUT: { text: "PUT", type: "warning" as const },
  DELETE: { text: "DELETE", type: "danger" as const },
  PATCH: { text: "PATCH", type: "info" as const }
} as const;

export const getHttpMethodTag = (
  method: string
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    HTTP_METHOD_TAG[method.toUpperCase() as keyof typeof HTTP_METHOD_TAG] || {
      text: method,
      type: "info" as const
    }
  );
};

/** 服务状态标签 */
export const SERVICE_STATUS_TAG = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
} as const;

export const getServiceStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === 1
    ? SERVICE_STATUS_TAG.ENABLED
    : SERVICE_STATUS_TAG.DISABLED;
};

/** 计算同步变更总数 */
export const getTotalSyncChanges = (result: ServiceSyncResult): number => {
  return (
    result.createdResources +
    result.updatedResources +
    result.createdMappings +
    result.updatedMappings +
    result.deletedResources +
    result.deletedMappings
  );
};
