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
 * 取值对齐后端 operation_log.module 三值（T-ACCESS-007 收敛：按事务边界判定，
 * ACCESS 域/PERMISSION 域/ADMIN 域），非旧的细粒度对象名。
 */
export const MODULE_OPTIONS: ReadonlyArray<{ label: string; value: string }> = [
  { label: "管理域", value: "ADMIN" },
  { label: "权限域", value: "PERMISSION" },
  { label: "跨域编排", value: "ACCESS" }
];

/**
 * action 下拉选项（前端本地硬编码，后端无枚举接口）。
 * 后端 action 为 `{业务对象}_{动作}` 大写事件码（如 USER_CREATE / CONFIG_UPDATE），
 * 由各业务方法 @OperationLog 注解维护，集合开放增长——本列表为代表性非穷尽子集，
 * 覆盖三个模块边界；后端按 action 精确匹配过滤。T-PERM-025 登记后端补枚举/字典接口。
 */
export const ACTION_OPTIONS: ReadonlyArray<{ label: string; value: string }> = [
  { label: "OAuth2 令牌签发", value: "OAUTH2_TOKEN_ISSUE" },
  { label: "OAuth2 令牌刷新", value: "OAUTH2_TOKEN_REFRESH" },
  { label: "配置更新", value: "CONFIG_UPDATE" },
  { label: "通知发布", value: "NOTICE_PUBLISH" },
  { label: "任务触发", value: "JOB_TRIGGER" },
  { label: "角色同步", value: "ABSTRACT_ROLE_SYNC" },
  { label: "角色全量同步", value: "ABSTRACT_ROLE_FULL_SYNC" },
  { label: "资源权限授予", value: "ROLE_RESOURCE_PERMISSION_GRANT" },
  { label: "资源权限撤销", value: "ROLE_RESOURCE_PERMISSION_REVOKE" },
  { label: "用户创建", value: "USER_CREATE" },
  { label: "组织创建", value: "ORG_CREATE" },
  { label: "菜单更新", value: "MENU_UPDATE" }
];

export type { OperationLogResp };
