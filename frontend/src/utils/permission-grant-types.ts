/**
 * 权限授予草稿与单元格契约类型（页面无关共享模块）。
 *
 * 从 views/system/permission-grant/utils/types.ts 抽取，供：
 * - RePermissionCell / ChildPermissionInline（共享组件，消除反向依赖页面工具）
 * - permission-grant 页面（hook / child-binding / types.ts re-export）
 * 统一引用。
 *
 * 设计依据：docs/design/frontend/permission-grant.md
 * - §9.1 草稿模型（baseline + draft + 稳定键）
 * - §4.4 单元格 6 态
 * - §7 子权限 / 范围权限
 */
import type { GrantScopeMode } from "@/api/permission-grant";

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

/** 计算权限单元稳定键字符串（用 JSON.stringify 避免控制字符，对齐 T-FE-013 mock 分组键修复） */
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

/** 单元格上下文（RePermissionCell 渲染入参） */
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

// ========== 子权限内联组件 ==========

/** 子权限内联组件上下文（ChildPermissionInline 入参） */
export interface ChildPermissionContext {
  /** 父权限草稿（主权限） */
  parent: DraftPermission;
  /** 允许的子资源类型码列表（domainCapability.childResourceTypeCodes） */
  childResourceTypeCodes: string[];
  /** 是否只读 */
  readonly: boolean;
}
