import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 角色树节点 */
export interface RoleTreeNode {
  id: number;
  tenantId: number;
  parentId: number | null;
  roleTypeCode: string;
  name: string;
  externalId: string;
  status: number;
  sortOrder: number;
  children?: Array<RoleTreeNode>;
}

/** 角色树响应 */
export interface RoleTreeResult {
  success: boolean;
  data: {
    items: Array<{ root: RoleTreeNode }>;
  };
}

/** 角色详情 */
export interface RoleDetail {
  id: number;
  tenantId: number;
  bizDomainId: number | null;
  parentId: number | null;
  roleTypeCode: string;
  roleTypeName: string;
  externalId: string;
  name: string;
  status: number;
  sortOrder: number;
  extra: string | null;
  createdAt: string;
  updatedAt: string | null;
}

/** 角色详情响应 */
export interface RoleDetailResult {
  success: boolean;
  data: RoleDetail;
}

/** 创建角色请求 */
export interface RoleCreateRequest {
  bizDomainId?: number;
  parentId?: number;
  roleTypeCode: string;
  externalId?: string;
  name: string;
  sortOrder?: number;
  extra?: string;
}

/** 更新角色请求 */
export interface RoleUpdateRequest {
  roleId: number;
  name?: string;
  status?: number;
  sortOrder?: number;
  extra?: string;
}

/** 角色简要信息(额外角色列表) */
export interface RoleSummary {
  id: number;
  roleTypeCode: string;
  externalId: string;
  name: string;
}

/** 额外角色列表响应 */
export interface ExtraRolesResult {
  success: boolean;
  data: {
    items: Array<RoleSummary>;
  };
}

/** 额外角色操作请求 */
export interface ExtraRoleRequest {
  groupDomainCode?: string;
  groupRoleTypeCode: string;
  groupRoleExternalId: string;
  basicDomainCode?: string;
  basicRoleTypeCode: string;
  basicRoleExternalId: string;
}

/** 角色操作响应 */
export interface RoleActionResult {
  success: boolean;
  data?: RoleDetail;
}

// ========== API 函数 ==========

/** 获取角色树 */
export const getRoleTree = (data?: { domainCode?: string }) => {
  return http.request<RoleTreeResult>("post", "/api/perm/abstract-role/tree", {
    data: data || {}
  });
};

/** 获取角色详情 */
export const getRoleDetail = (data: { id: number }) => {
  return http.request<RoleDetailResult>(
    "post",
    "/api/perm/abstract-role/detail",
    {
      data
    }
  );
};

/** 创建角色 */
export const createRole = (data: RoleCreateRequest) => {
  return http.request<RoleActionResult>(
    "post",
    "/api/perm/abstract-role/create",
    {
      data
    }
  );
};

/** 更新角色 */
export const updateRole = (data: RoleUpdateRequest) => {
  return http.request<RoleActionResult>(
    "post",
    "/api/perm/abstract-role/update",
    {
      data
    }
  );
};

/** 删除角色 */
export const deleteRole = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/abstract-role/remove",
    {
      data
    }
  );
};

/** 查询分组角色额外基本角色列表 */
export const getExtraRoles = (data: {
  domainCode?: string;
  groupRoleTypeCode: string;
  groupRoleExternalId: string;
}) => {
  return http.request<ExtraRolesResult>(
    "post",
    "/api/perm/abstract-role/extra-roles/list",
    { data }
  );
};

/** 分组角色添加基本角色 */
export const addExtraRole = (data: ExtraRoleRequest) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/abstract-role/extra-roles/add",
    { data }
  );
};

/** 分组角色移除基本角色 */
export const removeExtraRole = (data: ExtraRoleRequest) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/abstract-role/extra-roles/remove",
    { data }
  );
};

// ========== 工具函数 ==========

/** 将后端角色树响应转换为前端树数组 */
export const transformRoleTreeResponse = (
  response: RoleTreeResult
): Array<RoleTreeNode> => {
  if (!response.success || !response.data?.items) {
    return [];
  }
  // 提取每个 item 的 root 节点作为顶层节点
  return response.data.items.map(item => item.root);
};

/** 扁平化角色树(用于下拉选择) */
export const flattenRoleTree = (
  tree: Array<RoleTreeNode>
): Array<RoleTreeNode> => {
  const result: Array<RoleTreeNode> = [];
  const traverse = (nodes: Array<RoleTreeNode>) => {
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

/** 角色类型标签 */
export const ROLE_TYPE_TAG = {
  GROUP_ROLE: { text: "分组角色", type: "primary" as const },
  BASIC_ROLE: { text: "基本角色", type: "success" as const }
};

/** 角色类型编码常量 */
export const ROLE_TYPE_CODES = {
  GROUP_ROLE: "GROUP_ROLE",
  BASIC_ROLE: "BASIC_ROLE"
} as const;

export const getRoleTypeTag = (
  type: string
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return (
    ROLE_TYPE_TAG[type as keyof typeof ROLE_TYPE_TAG] || {
      text: "未知",
      type: "info" as const
    }
  );
};

/** 状态标签 */
export const STATUS_TAG = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
};

export const getStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === 1 ? STATUS_TAG.ENABLED : STATUS_TAG.DISABLED;
};
