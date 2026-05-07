import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 文件信息 */
export interface FileItem {
  id: number;
  fileName: string;
  filePath: string;
  fileSize: number;
  fileType: string;
  bizType: string;
  uploader: string;
  uploadedAt: string;
}

/** 文件分页请求 */
export interface FilePageReq {
  pageNum?: number;
  pageSize?: number;
  bizType?: string;
  fileName?: string;
}

/** 文件分页响应 */
export interface FilePageResult {
  success: boolean;
  data: {
    list: Array<FileItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

// ========== API 函数 ==========

/** 文件分页查询 */
export const pageFiles = (data: FilePageReq) => {
  return http.request<FilePageResult>("post", "/admin/api/file/page", { data });
};

/** 文件详情 */
export const getFile = (data: { id: number }) => {
  return http.request<{ success: boolean; data: FileItem }>(
    "post",
    "/admin/api/file/detail",
    { data }
  );
};

/** 删除文件 */
export const deleteFiles = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/file/delete", {
    data
  });
};

/** 获取文件下载URL */
export const getFileDownloadUrl = (id: number) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  return `${baseUrl}/admin/api/file/download?id=${id}`;
};
