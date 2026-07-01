import type { OperationLogResp } from "@/api/operation-log";

/** 操作日志筛选表单（对齐后端 OperationLogListReq 的可选过滤维度）。
 *  仅 module/action 两项——后端 Req 只支持这两个筛选维度。
 *  🔧 其余维度（operatorId/createdAt 时间范围/targetType）登记 T-PERM-025 后端补。 */
export interface OperationLogSearchForm {
  /** 模块筛选（null=全部） */
  module: string | null;
  /** 操作类型筛选（null=全部） */
  action: string | null;
}

/** 空筛选表单工厂（module/action 均 null=全部） */
export function createEmptySearchForm(): OperationLogSearchForm {
  return {
    module: null,
    action: null
  };
}

/**
 * module 下拉选项（前端本地硬编码，后端无枚举接口）。
 * 取值对齐 schema 注释（permission-center.sql:682）+ mock 数据。
 */
export const MODULE_OPTIONS: ReadonlyArray<{ label: string; value: string }> = [
  { label: "类型定义", value: "type_definition" },
  { label: "用户", value: "abstract_user" },
  { label: "角色", value: "abstract_role" },
  { label: "系统配置", value: "system_config" },
  { label: "权限授予", value: "permission_grant" }
];

/**
 * action 下拉选项（前端本地硬编码，后端无枚举接口）。
 * 取值对齐 schema 注释（permission-center.sql:683）+ mock 数据。
 */
export const ACTION_OPTIONS: ReadonlyArray<{ label: string; value: string }> = [
  { label: "创建", value: "CREATE" },
  { label: "更新", value: "UPDATE" },
  { label: "删除", value: "DELETE" },
  { label: "同步", value: "SYNC" },
  { label: "分配", value: "ASSIGN" },
  { label: "批量授予", value: "BATCH_GRANT" },
  { label: "保存", value: "SAVE" }
];

export type { OperationLogResp };
