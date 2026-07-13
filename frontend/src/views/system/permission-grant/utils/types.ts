/**
 * 权限授予页视图层类型与常量。
 *
 * 共享类型（PermCellKey / permCellKey / DraftPermission / CellState /
 * PermissionCellContext / ChildPermissionContext）已抽取到
 * `@/utils/permission-grant-types`（页面无关共享模块），此处 re-export
 * 保持向后兼容；本文件保留页面级类型（角色类型常量 / DiffEntry /
 * AdditionalSettingContext）。
 *
 * 设计依据：docs/design/frontend/permission-grant.md
 * 后端契约：api-contract.md §5.5 / §6.4 / §6.5
 */

// 共享类型 re-export（向后兼容，实际定义见 @/utils/permission-grant-types）
export {
  permCellKey,
  type PermCellKey,
  type DraftPermission,
  type CellState,
  type PermissionCellContext,
  type ChildPermissionContext
} from "@/utils/permission-grant-types";

import type { DraftPermission } from "@/utils/permission-grant-types";

/** 角色类型选项（对齐后端 RoleTypeCode 域：BASIC_ROLE/GROUP_ROLE/ORG/POSITION/PERSONAL） */
export const ROLE_TYPE_OPTIONS = [
  { value: "BASIC_ROLE", label: "基础角色", domainRequired: false },
  { value: "GROUP_ROLE", label: "组合角色", domainRequired: false },
  { value: "ORG", label: "组织角色", domainRequired: true },
  { value: "POSITION", label: "岗位角色", domainRequired: true },
  { value: "PERSONAL", label: "个人角色", domainRequired: false }
] as const;

/** 角色类型是否要求 domainCode 必填（ORG/POSITION） */
export function isDomainRequired(roleTypeCode: string): boolean {
  return (
    ROLE_TYPE_OPTIONS.find(r => r.value === roleTypeCode)?.domainRequired ??
    false
  );
}

// ========== 差异（右栏本次变更） ==========

export type DiffType = "add" | "update" | "remove";

/** 差异条目（右栏本次变更展示） */
export interface DiffEntry {
  type: DiffType;
  /** 稳定键 */
  key: string;
  /** 当前权限（add/update=draft 项；remove=baseline 项） */
  permission: DraftPermission;
  /** update 时的变更前快照 */
  before: DraftPermission | null;
  /** 变更字段名列表（update 时非空：conditionCode / canGrant） */
  changedFields: string[];
  /** 是否子权限变更 */
  isChild: boolean;
}

// ========== 附加设置弹窗 ==========

/** 附加设置弹窗上下文（AdditionalSettingDialog 入参） */
export interface AdditionalSettingContext {
  /** 目标权限稳定键（已有权限用服务端 id 路径；新权限用 tempKey） */
  key: string;
  draft: DraftPermission;
  /** 是否新增权限（打开未授权单元的"授予并配置"） */
  isNew: boolean;
  /** 是否只读 */
  readonly: boolean;
  /** 是否子权限（子权限用 setChildCellAttr） */
  isChild?: boolean;
  /** 子权限稳定键（isChild=true 时用，主权限时等于 key） */
  childKey?: string;
  /** 资源类型能力（补充修复：禁用条件/canGrant 用） */
  supportsCondition?: boolean;
  supportsDelegation?: boolean;
}
