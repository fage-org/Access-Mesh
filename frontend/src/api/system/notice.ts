import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 系统通知 */
export interface NoticeItem {
  id: number;
  title: string;
  content: string;
  type: number; // 1:系统通知 2:公告
  status: number; // 0:未发布 1:已发布
  publisher?: string;
  publishTime?: string;
  createdAt: string;
  updatedAt?: string;
}

/** 通知创建请求 */
export interface NoticeCreateReq {
  title: string;
  content: string;
  type: number;
}

/** 通知更新请求 */
export interface NoticeUpdateReq {
  id: number;
  title?: string;
  content?: string;
  type?: number;
}

/** 通知分页响应 */
export interface NoticePageResult {
  success: boolean;
  data: {
    list: Array<NoticeItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

/** 用户通知项 */
export interface UserNoticeItem {
  id: number;
  noticeId: number;
  title: string;
  content: string;
  isRead: boolean;
  readTime?: string;
}

// ========== API 函数 ==========

/** 通知分页查询 */
export const pageNotices = (data: { pageNum?: number; pageSize?: number }) => {
  return http.request<NoticePageResult>("post", "/admin/api/notice/page", {
    data
  });
};

/** 通知详情 */
export const getNotice = (data: { id: number }) => {
  return http.request<{ success: boolean; data: NoticeItem }>(
    "post",
    "/admin/api/notice/detail",
    { data }
  );
};

/** 创建通知 */
export const createNotice = (data: NoticeCreateReq) => {
  return http.request<{ success: boolean; data: number }>(
    "post",
    "/admin/api/notice/create",
    { data }
  );
};

/** 更新通知 */
export const updateNotice = (data: NoticeUpdateReq) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/notice/update",
    { data }
  );
};

/** 删除通知 */
export const deleteNotice = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/notice/delete",
    { data }
  );
};

/** 发布通知 */
export const publishNotice = (data: { id: number }) => {
  return http.request<{ success: boolean }>(
    "post",
    "/admin/api/notice/publish",
    { data }
  );
};

/** 标记已读 */
export const markNoticeAsRead = (data: { id: number }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/notice/read", {
    data
  });
};

/** 我的未读通知 */
export const listMyNotices = () => {
  return http.request<{ success: boolean; data: Array<UserNoticeItem> }>(
    "post",
    "/admin/api/notice/my-notices"
  );
};
