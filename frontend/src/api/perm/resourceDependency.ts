import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 资源依赖项 */
export interface ResourceDependencyItem {
  id: number;
  tenantId: number;
  resourceEntityId: number;
  sourceResourceCode: string;
  dependsOnResourceEntityId: number;
  depResourceCode: string;
  sourceOperationBits: number;
  requiredOperationBits: number;
  autoGrant: boolean;
  description: string | null;
  createdAt: string;
}

/** 资源依赖列表响应 */
export interface DependencyListResult {
  success: boolean;
  data: {
    items: Array<ResourceDependencyItem>;
  };
}

/** 资源依赖列表请求 */
export interface DependencyListRequest {
  resourceEntityId?: number;
}

/** 创建资源依赖请求 */
export interface DependencyCreateRequest {
  sourceResourceTypeCode: string;
  sourceResourceCode: string;
  sourceCodeType?: string;
  sourceOperationCodes?: Array<string>;
  targetResourceTypeCode: string;
  targetResourceCode: string;
  targetCodeType?: string;
  requiredOperationCodes: Array<string>;
  autoGrant?: boolean;
  description?: string;
}

/** 更新资源依赖请求 */
export interface DependencyUpdateRequest {
  id: number;
  sourceOperationCodes?: Array<string>;
  requiredOperationCodes?: Array<string>;
  autoGrant?: boolean;
  description?: string;
}

/** 资源依赖创建/更新响应 */
export interface DependencyActionResult {
  success: boolean;
  data: ResourceDependencyItem;
}

// ========== API 函数 ==========

/** 查询资源依赖列表 */
export const getDependencyList = (data?: DependencyListRequest) => {
  return http.request<DependencyListResult>(
    "post",
    "/api/perm/resource-dependency/list",
    { data: data || {} }
  );
};

/** 查询资源依赖图(全部) */
export const getDependencyGraph = (data?: DependencyListRequest) => {
  return http.request<DependencyListResult>(
    "post",
    "/api/perm/resource-dependency/graph",
    { data: data || {} }
  );
};

/** 创建资源依赖 */
export const createDependency = (data: DependencyCreateRequest) => {
  return http.request<DependencyActionResult>(
    "post",
    "/api/perm/resource-dependency/create",
    { data }
  );
};

/** 更新资源依赖 */
export const updateDependency = (data: DependencyUpdateRequest) => {
  return http.request<DependencyActionResult>(
    "post",
    "/api/perm/resource-dependency/update",
    { data }
  );
};

/** 删除资源依赖 */
export const deleteDependency = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/resource-dependency/remove",
    { data }
  );
};

// ========== 工具函数 ==========

/** 依赖关系显示文本 */
export const getDependencyDisplayText = (
  dep: ResourceDependencyItem
): string => {
  return `${dep.sourceResourceCode} → ${dep.depResourceCode}`;
};
