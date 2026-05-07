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
