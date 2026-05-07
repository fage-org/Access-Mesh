import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 字典类型 */
export interface DictTypeItem {
  id: number;
  code: string;
  name: string;
  description?: string;
  status: number;
  createdAt: string;
}

/** 字典数据 */
export interface DictDataItem {
  id: number;
  dictTypeId: number;
  label: string;
  value: string;
  sort: number;
  description?: string;
  status: number;
}

/** 字典类型创建请求 */
export interface DictTypeCreateReq {
  code: string;
  name: string;
  description?: string;
  status?: number;
}

/** 字典数据创建请求 */
export interface DictDataCreateReq {
  dictTypeId: number;
  label: string;
  value: string;
  sort?: number;
  description?: string;
  status?: number;
}

/** 字典类型列表响应 */
export interface DictTypeListResult {
  success: boolean;
  data: Array<DictTypeItem>;
}

/** 字典类型分页响应 */
export interface DictTypePageResult {
  success: boolean;
  data: {
    list: Array<DictTypeItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

/** 字典数据列表响应 */
export interface DictDataListResult {
  success: boolean;
  data: Array<DictDataItem>;
}

// ========== API 函数 ==========

/** 字典类型列表 */
export const listDictTypes = () => {
  return http.request<DictTypeListResult>("post", "/admin/api/dict/type/list");
};

/** 字典类型分页 */
export const pageDictTypes = (data: {
  pageNum?: number;
  pageSize?: number;
}) => {
  return http.request<DictTypePageResult>("post", "/admin/api/dict/type/page", {
    data
  });
};

/** 创建字典类型 */
export const createDictType = (data: DictTypeCreateReq) => {
  return http.request<{ success: boolean; data: number }>(
    "post",
    "/admin/api/dict/type/create",
    { data }
  );
};

/** 删除字典类型 */
export const deleteDictType = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/dict/type/delete",
    { data }
  );
};

/** 字典数据列表 */
export const listDictData = (data: { id: number }) => {
  return http.request<DictDataListResult>("post", "/admin/api/dict/data/list", {
    data
  });
};

/** 创建字典数据 */
export const createDictData = (data: DictDataCreateReq) => {
  return http.request<{ success: boolean; data: number }>(
    "post",
    "/admin/api/dict/data/create",
    { data }
  );
};

/** 更新字典数据 */
export const updateDictData = (data: DictDataCreateReq & { id: number }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/dict/data/update",
    { data }
  );
};

/** 删除字典数据 */
export const deleteDictData = (data: { id: number }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/dict/data/delete",
    { data }
  );
};
