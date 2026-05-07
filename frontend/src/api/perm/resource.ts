import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 资源树节点 */
export interface ResourceTreeNode {
  id: number;
  parentId: number | null;
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
  path: string;
  status: number;
  sortOrder: number;
  children?: Array<ResourceTreeNode>;
}

/** 资源树响应 */
export interface ResourceTreeResult {
  success: boolean;
  data: {
    items: Array<{ root: ResourceTreeNode }>;
  };
}

/** 资源详情 */
export interface ResourceDetail {
  id: number;
  tenantId: number;
  bizDomainId: number | null;
  parentId: number | null;
  resourceTypeCode: string;
  resourceTypeName: string;
  code: string;
  codeType: string;
  name: string;
  path: string;
  status: number;
  sortOrder: number;
  extra: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** 资源详情响应 */
export interface ResourceDetailResult {
  success: boolean;
  data: ResourceDetail;
}

/** 资源树查询请求 */
export interface ResourceTreeRequest {
  resourceTypeCode?: string;
  domainCode?: string;
}

// ========== API 函数 ==========

/** 获取资源树 */
export const getResourceTree = (data?: ResourceTreeRequest) => {
  return http.request<ResourceTreeResult>(
    "post",
    "/api/perm/resource-entity/tree",
    { data: data || {} }
  );
};

/** 获取资源详情 */
export const getResourceDetail = (data: { id: number }) => {
  return http.request<ResourceDetailResult>(
    "post",
    "/api/perm/resource-entity/detail",
    { data }
  );
};

// ========== 工具函数 ==========

/** 将后端资源树响应转换为前端树数组 */
export const transformResourceTreeResponse = (
  response: ResourceTreeResult
): Array<ResourceTreeNode> => {
  if (!response.success || !response.data?.items) {
    return [];
  }
  return response.data.items.map(item => item.root);
};

/** 扁平化资源树(用于下拉选择) */
export const flattenResourceTree = (
  tree: Array<ResourceTreeNode>
): Array<ResourceTreeNode> => {
  const result: Array<ResourceTreeNode> = [];
  const traverse = (nodes: Array<ResourceTreeNode>) => {
    nodes.forEach(node => {
      result.push(node);
      if (node.children) {
        traverse(node.children);
      }
    });
  };
  traverse(tree);
  return result;
};

/** 资源状态标签 */
export const RESOURCE_STATUS_TAG = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
};

export const getResourceStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === 1
    ? RESOURCE_STATUS_TAG.ENABLED
    : RESOURCE_STATUS_TAG.DISABLED;
};

// ========== 资源类型常量 ==========

export const RESOURCE_TYPE_CODES = {
  MENU: "MENU",
  API: "API",
  BUTTON: "BUTTON",
  DATA: "DATA"
} as const;

export const RESOURCE_TYPE_TAG = {
  MENU: { text: "菜单", type: "primary" as const },
  API: { text: "API", type: "warning" as const },
  BUTTON: { text: "按钮", type: "success" as const },
  DATA: { text: "数据", type: "info" as const }
} as const;

export const getResourceTypeTag = (
  type: string
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    RESOURCE_TYPE_TAG[type as keyof typeof RESOURCE_TYPE_TAG] || {
      text: type,
      type: "info" as const
    }
  );
};

// ========== CRUD 请求类型 ==========

/** 创建资源请求 */
export interface ResourceCreateRequest {
  bizDomainId?: number;
  parentId?: number;
  resourceTypeCode: string;
  code: string;
  codeType?: string;
  name: string;
  path?: string;
  status?: number;
  sortOrder?: number;
  extra?: string;
}

/** 更新资源请求 */
export interface ResourceUpdateRequest {
  id: number;
  code?: string;
  name?: string;
  path?: string;
  status?: number;
  sortOrder?: number;
  extra?: string;
}

/** 资源创建/更新响应 */
export interface ResourceActionResult {
  success: boolean;
  data: ResourceDetail;
}

// ========== CRUD API 函数 ==========

/** 创建资源 */
export const createResource = (data: ResourceCreateRequest) => {
  return http.request<ResourceActionResult>(
    "post",
    "/api/perm/resource-entity/create",
    { data }
  );
};

/** 更新资源 */
export const updateResource = (data: ResourceUpdateRequest) => {
  return http.request<ResourceActionResult>(
    "post",
    "/api/perm/resource-entity/update",
    { data }
  );
};

/** 删除资源 */
export const deleteResource = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/resource-entity/remove",
    { data }
  );
};
