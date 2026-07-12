/**
 * 权限授予页视图层类型与常量。
 *
 * 设计依据：docs/design/frontend/permission-grant.md
 * 后端契约：api-contract.md §5.5 / §6.4 / §6.5
 *
 * 草稿模型（§9.1）：
 * - baseline：服务端权限事实（role-resource-permission/list 返回的 RolePermissionItem[]）
 * - draft：baseline 拷贝 + 本地修改（单元格操作 / 附加设置 / 子权限）
 * - diff：draft 相对 baseline 的 add/update/remove
 * - 主权限稳定键：domainCode + resourceTypeCode + scopeMode + resourceCode? + codeType? + operationCode
 * - 子权限键额外含父权限稳定键（新主权限保存前用 tempKey 关联）
 *
 * 能力驱动（§11.3，按归属拆分，决策点 6 调整）：
 * - 角色节点：directGrantable / canView / canManage
 * - 资源类型：supportsInstance / supportsAll / supportsCondition / supportsDelegation
 * - 业务域：supportsChildren / childResourceTypeCodes
 * - 候选权限单元：grantableByOperator / denyReason（由 operatorCapability + 资源类型能力组合判定）
 */
import type { GrantScopeMode } from "@/api/permission-grant";

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

// ========== 稳定键 ==========

/** 权限单元稳定键（§9.1） */
export interface PermCellKey {
  domainCode: string;
  resourceTypeCode: string;
  scopeMode: GrantScopeMode;
  /** null = ALL（类型级全量授权，不含 resourceCode/codeType） */
  resourceCode: string | null;
  /** null = ALL */
  codeType: string | null;
  operationCode: string;
}

/** 计算权限单元稳定键字符串（用 JSON.stringify 避免控制字节，对齐 T-FE-013 mock 分组键修复） */
export function permCellKey(k: PermCellKey): string {
  return JSON.stringify([
    k.domainCode,
    k.resourceTypeCode,
    k.scopeMode,
    k.resourceCode ?? "",
    k.codeType ?? "",
    k.operationCode
  ]);
}

// ========== 草稿 ==========

/** 草稿权限项（baseline 拷贝 + 本地修改） */
export interface DraftPermission extends PermCellKey {
  /** 服务端 id，新增时 null */
  id: number | null;
  /** 权限条件码，null = 无条件 */
  conditionCode: string | null;
  /** 是否允许继续授权（canGrant） */
  canGrant: boolean;
  /** 父权限服务端 id（子权限），null = 主权限 */
  dependOn: number | null;
  /** 父权限稳定键（新增子权限挂未保存主权限时），null = 主权限或已保存子权限 */
  dependOnTempKey: string | null;
  /** 来源：MANUAL=直接配置 / AUTO_DEP=资源依赖自动补全 / COMPOSED=角色组合 */
  grantSource: string;
  /** 资源名称（展示用，ALL 时 null） */
  resourceName: string | null;
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

// ========== 单元格状态（§4.4） ==========

/**
 * P0 实现 6 态（决策点修正 A：已修改属性态必须实现，否则中栏右栏不一致）：
 * - UNAUTHORIZED 未授权
 * - GRANTED 已直接授权
 * - PENDING_ADD 待新增
 * - PENDING_REMOVE 待移除
 * - MODIFIED 已修改属性（条件/canGrant 变化但授权未变）
 * - ALL_COVERED 被 ALL 覆盖（实例单元对应操作有 ALL 授权）
 * 暂缓：派生/自动补全态（AUTO_DEP/COMPOSED）、操作者无法授予 disabled 另由 grantableByOperator 控制
 */
export type CellState =
  | "UNAUTHORIZED"
  | "GRANTED"
  | "PENDING_ADD"
  | "PENDING_REMOVE"
  | "MODIFIED"
  | "ALL_COVERED";

/** 单元格上下文（PermissionCell 渲染入参） */
export interface PermissionCellContext {
  state: CellState;
  /** 当前草稿权限（GRANTED/PENDING_ADD/PENDING_REMOVE/MODIFIED 时非 null） */
  draft: DraftPermission | null;
  /** 是否被 ALL 覆盖（实例单元格 + 同操作有 ALL 授权） */
  allCovered: boolean;
  /** 操作者是否可授予（决策点 6：候选权限单元能力） */
  grantableByOperator: boolean;
  /** 不可授予原因（grantableByOperator=false 时） */
  denyReason: string | null;
  /** 是否只读（角色 directGrantable=false 或 操作者无 MANAGE） */
  readonly: boolean;
  /** 子权限数量（附加设置入口标记） */
  childCount: number;
  /** 条件摘要（已绑定条件时展示） */
  conditionSummary: string | null;
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
}

// ========== 子权限抽屉 ==========

/** 子权限抽屉上下文（ChildPermissionDrawer 入参） */
export interface ChildPermissionContext {
  /** 父权限草稿（主权限） */
  parent: DraftPermission;
  /** 允许的子资源类型码列表（domainCapability.childResourceTypeCodes） */
  childResourceTypeCodes: string[];
  /** 是否只读 */
  readonly: boolean;
}
