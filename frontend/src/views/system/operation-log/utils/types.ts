import type { OperationLogResp } from "@/api/operation-log";

/** 操作日志筛选表单（对齐后端 OperationLogListReq 过滤维度，T-PERM-025 扩展）。 */
export interface OperationLogSearchForm {
  /** 模块筛选（null=全部） */
  module: string | null;
  /** 操作类型筛选（null=全部；选项由 /log/operation/action-options 动态拉取） */
  action: string | null;
  /** 操作者用户 ID 筛选（null=全部） */
  operatorId: number | null;
  /** 创建时间范围 [起, 止]（null=全部；序列化为 since/until） */
  timeRange: [Date, Date] | null;
  /** 目标类型筛选（null=全部，精确匹配） */
  targetType: string | null;
}

/** 空筛选表单工厂（全部维度 null=不过滤） */
export function createEmptySearchForm(): OperationLogSearchForm {
  return {
    module: null,
    action: null,
    operatorId: null,
    timeRange: null,
    targetType: null
  };
}

/**
 * module 下拉选项（前端本地硬编码，三值封闭集合）。
 * 取值对齐后端 operation_log.module 三值（T-ACCESS-007 收敛：按事务边界判定，
 * ACCESS 域/PERMISSION 域/ADMIN 域），非旧的细粒度对象名。
 */
export const MODULE_OPTIONS: ReadonlyArray<{ label: string; value: string }> = [
  { label: "管理域", value: "ADMIN" },
  { label: "权限域", value: "PERMISSION" },
  { label: "跨域编排", value: "ACCESS" }
];

export type { OperationLogResp };
