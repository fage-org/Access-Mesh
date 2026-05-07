import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 业务域项 */
export interface BizDomainItem {
  id: number;
  tenantId: number;
  code: string;
  name: string;
  description: string | null;
  status: number;
  createdAt: string;
  updatedAt: string | null;
}

/** 业务域列表响应 */
export interface BizDomainListResult {
  success: boolean;
  data: {
    items: Array<BizDomainItem>;
  };
}

/** 业务域详情响应 */
export interface BizDomainDetailResult {
  success: boolean;
  data: BizDomainItem;
}

/** 创建业务域请求 */
export interface BizDomainCreateRequest {
  code: string;
  name: string;
  description?: string;
  status?: number;
}

/** 更新业务域请求 */
export interface BizDomainUpdateRequest {
  id: number;
  code?: string;
  name?: string;
  description?: string;
  status?: number;
}

// ========== API 函数 ==========

/** 获取业务域列表 */
export const getBizDomainList = () => {
  return http.request<BizDomainListResult>(
    "post",
    "/api/perm/biz-domain/list",
    { data: {} }
  );
};

/** 获取业务域详情 */
export const getBizDomainDetail = (data: { code: string }) => {
  return http.request<BizDomainDetailResult>(
    "post",
    "/api/perm/biz-domain/detail",
    { data }
  );
};

/** 创建业务域 */
export const createBizDomain = (data: BizDomainCreateRequest) => {
  return http.request<BizDomainDetailResult>(
    "post",
    "/api/perm/biz-domain/create",
    { data }
  );
};

/** 更新业务域 */
export const updateBizDomain = (data: BizDomainUpdateRequest) => {
  return http.request<BizDomainDetailResult>(
    "post",
    "/api/perm/biz-domain/update",
    { data }
  );
};

/** 删除业务域 */
export const deleteBizDomain = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/api/perm/biz-domain/remove",
    { data }
  );
};

// ========== 工具函数 ==========

/** 业务域状态标签 */
export const DOMAIN_STATUS_TAG = {
  ENABLED: { text: "启用", type: "success" as const },
  DISABLED: { text: "停用", type: "danger" as const }
};

export const getDomainStatusTag = (
  status: number
): {
  text: string;
  type: "primary" | "success" | "warning" | "danger" | "info";
} => {
  return status === 1 ? DOMAIN_STATUS_TAG.ENABLED : DOMAIN_STATUS_TAG.DISABLED;
};
