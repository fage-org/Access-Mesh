import type {
  DiffEventType,
  DiffChangeType
} from "@/api/permission-change-log";

/** 变更日志筛选表单（对齐后端 ChangeLogListReq 的可选过滤维度）。
 *  仅 entityType/entityId 两项--后端 Req 只支持这两个筛选维度。
 *  🔧 其余维度（eventType/changeSource/时间范围/affected user·role）登记 T-PERM-032 后端补。 */
export interface ChangeLogSearchForm {
  /** 实体类型筛选（null=全部） */
  entityType: string | null;
  /** 实体 ID 筛选（null=全部） */
  entityId: number | null;
}

/** 空筛选表单工厂（entityType/entityId 均 null=全部） */
export function createEmptySearchForm(): ChangeLogSearchForm {
  return {
    entityType: null,
    entityId: null
  };
}

/**
 * entityType 下拉选项（前端本地硬编码，后端无枚举接口）。
 * 取值对齐 schema 注释（access-service.sql）：user_role/role_resource_permission/abstract_user/abstract_role 等。
 */
export const ENTITY_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "用户角色关系", value: "user_role" },
  { label: "角色资源权限", value: "role_resource_permission" },
  { label: "用户", value: "abstract_user" },
  { label: "角色", value: "abstract_role" },
  { label: "资源", value: "resource_entity" },
  { label: "权限条件", value: "permission_condition" },
  { label: "冲突规则", value: "permission_conflict_rule" },
  { label: "资源依赖", value: "resource_dependency" }
];

/**
 * operation（实体层操作）标签选项（前端本地硬编码）。
 * 取值对齐 schema 注释（access-service.sql）：INSERT/UPDATE/DELETE。
 * 注意：区别于 diff_snapshot.items[].changeType（ADD/REMOVE/UPDATE）。
 */
export const OPERATION_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "新增", value: "INSERT" },
  { label: "更新", value: "UPDATE" },
  { label: "删除", value: "DELETE" }
];

/**
 * changeSource（变更来源）标签选项（前端本地硬编码）。
 * 取值对齐后端代码实际（PermConstants.MaintainSource 复用）：MANUAL/SERVICE_SYNC。
 * 🔧 schema L631 注释写 ADMIN/SYNC/API/SYSTEM 与代码不符，登记 T-PERM-032。
 */
export const CHANGE_SOURCE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "手动", value: "MANUAL" },
  { label: "服务同步", value: "SERVICE_SYNC" }
];

/**
 * eventType（diff_snapshot 事件类型）标签映射（§6.8 L1666 固定枚举）。
 * 用于表格/详情展示事件类型中文标签 + tag 颜色。
 */
export const EVENT_TYPE_META: Record<
  DiffEventType,
  { label: string; type: "primary" | "success" | "warning" | "danger" | "info" }
> = {
  USER_ROLE_CHANGE: { label: "用户角色变更", type: "primary" },
  ROLE_PERMISSION_CHANGE: { label: "角色权限变更", type: "warning" },
  ROLE_STATUS_CHANGE: { label: "角色状态变更", type: "info" },
  RESOURCE_STATUS_CHANGE: { label: "资源状态变更", type: "info" },
  CONDITION_CHANGE: { label: "条件变更", type: "primary" },
  GROUP_ROLE_CHANGE: { label: "分组角色变更", type: "primary" },
  RESOURCE_DEPENDENCY_CHANGE: { label: "资源依赖变更", type: "warning" }
};

/**
 * changeType（diff_snapshot.items[].changeType）标签映射（§6.8 L1667 固定枚举）。
 * 用于 diff 面板展示变更动作中文标签 + tag 颜色。
 */
export const CHANGE_TYPE_META: Record<
  DiffChangeType,
  { label: string; type: "success" | "danger" | "warning" }
> = {
  ADD: { label: "新增", type: "success" },
  REMOVE: { label: "移除", type: "danger" },
  UPDATE: { label: "更新", type: "warning" }
};

/** entityType 编码 -> 中文标签（匹配不上回退原值） */
export const entityTypeLabel = (code?: string | null): string => {
  if (!code) return "-";
  return ENTITY_TYPE_OPTIONS.find(o => o.value === code)?.label ?? code;
};

/** operation 编码 -> 中文标签 */
export const operationLabel = (code?: string | null): string => {
  if (!code) return "-";
  return OPERATION_OPTIONS.find(o => o.value === code)?.label ?? code;
};

/** changeSource 编码 -> 中文标签 */
export const changeSourceLabel = (code?: string | null): string => {
  if (!code) return "-";
  return CHANGE_SOURCE_OPTIONS.find(o => o.value === code)?.label ?? code;
};
