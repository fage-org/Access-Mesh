import { http } from "@/utils/http";

// ========== 类型定义 ==========

/** 定时任务 */
export interface JobItem {
  id: number;
  jobName: string;
  jobGroup: string;
  invokeTarget: string;
  cronExpression: string;
  misfirePolicy: number; // 1:立即执行 2:执行一次 3:放弃执行
  concurrent: number; // 0:允许 1:禁止
  status: number; // 0:正常 1:暂停
  remark?: string;
  createdAt: string;
}

/** 任务执行日志 */
export interface JobLogItem {
  id: number;
  jobId: number;
  jobName: string;
  jobGroup: string;
  invokeTarget: string;
  jobMessage: string;
  status: number; // 0:成功 1:失败
  exceptionInfo?: string;
  executeTime: string;
}

/** 任务创建请求 */
export interface JobCreateReq {
  jobName: string;
  jobGroup: string;
  invokeTarget: string;
  cronExpression: string;
  misfirePolicy?: number;
  concurrent?: number;
  status?: number;
  remark?: string;
}

/** 任务日志分页请求 */
export interface JobLogPageReq {
  pageNum?: number;
  pageSize?: number;
  jobId?: number;
}

/** 任务分页响应 */
export interface JobPageResult {
  success: boolean;
  data: {
    list: Array<JobItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

/** 任务日志分页响应 */
export interface JobLogPageResult {
  success: boolean;
  data: {
    list: Array<JobLogItem>;
    total: number;
    pageNum: number;
    pageSize: number;
  };
}

// ========== API 函数 ==========

/** 任务分页查询 */
export const pageJobs = (data: { pageNum?: number; pageSize?: number }) => {
  return http.request<JobPageResult>("post", "/admin/api/job/page", { data });
};

/** 任务详情 */
export const getJob = (data: { id: number }) => {
  return http.request<{ success: boolean; data: JobItem }>(
    "post",
    "/admin/api/job/detail",
    { data }
  );
};

/** 创建任务 */
export const createJob = (data: JobCreateReq) => {
  return http.request<{ success: boolean; data: number }>(
    "post",
    "/admin/api/job/create",
    { data }
  );
};

/** 更新任务 */
export const updateJob = (data: JobCreateReq & { id: number }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/job/update", {
    data
  });
};

/** 删除任务 */
export const deleteJobs = (data: { ids: Array<number> }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/job/delete", {
    data
  });
};

/** 切换任务状态 */
export const toggleJobStatus = (data: { id: number; status: number }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/job/toggle", {
    data
  });
};

/** 手动触发任务 */
export const triggerJob = (data: { id: number }) => {
  return http.request<{ success: boolean }>("post", "/admin/api/job/trigger", {
    data
  });
};

/** 任务日志分页 */
export const pageJobLogs = (data: JobLogPageReq) => {
  return http.request<JobLogPageResult>("post", "/admin/api/job/log/page", {
    data
  });
};
