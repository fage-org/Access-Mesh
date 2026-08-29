import type {
  DiffEventType,
  DiffChangeType
} from "@/api/permission-change-log";

/** 变更日志筛选表单（对齐后端 ChangeLogListReq 的可选过滤维度，T-PERM-032 收口：筛选全集）。 */
export interface ChangeLogSearchForm {
  /** 实体类型筛选（null=全部） */
  entityType: string | null;
  /** 实体 ID 筛选（null=全部） */
  entityId: number | null;
  /** 事件类型筛选（diff_snapshot.eventType，null=全部） */
  eventType: DiffEventType | null;
  /** 变更来源筛选（MANUAL/SERVICE_SYNC，null=全部） */
  changeSource: string | null;
  /** 受影响用户 ID 筛选（null=全部） */
  affectedUserId: number | null;
  /** 受影响角色 ID 筛选（null=全部） */
  affectedRoleId: number | null;
  /** 创建时间范围（datetimerange；序列化取墙钟分量对齐展示数字） */
  timeRange: [Date, Date] | null;
}

/** 空筛选表单工厂（全部维度 null=不过滤） */
export function createEmptySearchForm(): ChangeLogSearchForm {
  return {
    entityType: null,
    entityId: null,
    eventType: null,
    changeSource: null,
    affectedUserId: null,
    affectedRoleId: null,
    timeRange: null
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
  { label: "删除", value: "DELETE" },
  /** entityId=0 批量聚合行专用（T-PERM-032 对齐后端写入值与 schema 注释） */
  { label: "批量删除", value: "BATCH_DELETE" },
  { label: "批量移除", value: "BATCH_REMOVE" }
];

/**
 * changeSource（变更来源）标签选项（前端本地硬编码）。
 * 取值对齐后端代码实际（PermConstants.MaintainSource 复用）：MANUAL/SERVICE_SYNC
 * （schema 注释已随 T-PERM-032 修正）。
 */
export const CHANGE_SOURCE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "手动", value: "MANUAL" },
  { label: "服务同步", value: "SERVICE_SYNC" }
];

/**
 * eventType（diff_snapshot 事件类型）标签映射（api-contract §6.8 固定枚举）。
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
  RESOURCE_DEPENDENCY_CHANGE: { label: "资源依赖变更", type: "warning" },
  /** 批量删除角色的聚合事件（entityId=0 + operation=BATCH_DELETE；T-PERM-032 契约收口补枚举） */
  ROLE_BATCH_DELETE: { label: "角色批量删除", type: "danger" }
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
