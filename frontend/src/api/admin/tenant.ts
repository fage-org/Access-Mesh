import { http } from "@/utils/http";

// ========== 类型定义 ==========

export interface TenantQueryResult {
  success: boolean;
  data: {
    items: Array<{
      id: number;
      name: string;
      code: string;
    }>;
  };
}

// ========== API 函数 ==========

/** 查询租户列表 */
export const getTenantList = () => {
  return http.request<TenantQueryResult>("post", "/admin/api/tenant/query", {
    data: {}
  });
};
