import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 系统配置 */
export interface ConfigItem {
  id: number;
  configKey: string;
  configValue: string;
  configName: string;
  remark?: string;
  updatedAt: string;
}

/** 配置更新请求 */
export interface ConfigUpdateReq {
  id: number;
  configValue: string;
  remark?: string;
}

/** 配置分页响应 */
export interface ConfigPageResult {
  success: boolean;
  data: {
    list: Array<ConfigItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

// ========== API 函数 ==========

/** 配置分页查询 */
export const pageConfigs = (data: { pageNum?: number; pageSize?: number }) => {
  return http.request<ConfigPageResult>("post", "/admin/api/config/page", {
    data
  });
};

/** 配置详情 */
export const getConfig = (data: { id: number }) => {
  return http.request<{ success: boolean; data: ConfigItem }>(
    "post",
    "/admin/api/config/detail",
    { data }
  );
};

/** 更新配置 */
export const updateConfig = (data: ConfigUpdateReq) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/config/update",
    { data }
  );
};

/** 删除配置 */
export const deleteConfig = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/config/delete",
    { data }
  );
};
