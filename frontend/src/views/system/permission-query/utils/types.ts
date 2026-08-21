/**
 * 权限排查页类型与常量。
 *
 * 主体模型（核实 access-service 后端）：
 * - query-resources/query-scopes：仅支持用户主体（subjectTypeCode + subjectExternalId）
 * - effective-permissions/explain：支持 targetType=USER/ROLE
 *   - USER：subjectTypeCode（ADMIN_USER/USER）+ subjectExternalId
 *   - ROLE：roleTypeCode（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE）+ roleExternalId + domainCode
 *
 * 角色类型码对齐 access-service RoleType.java（ORG/POSITION/PERSONAL/GROUP_ROLE/BASIC_ROLE），
 * 不存在 ORG_ROLE/POSITION_ROLE（后者仅归并前 admin-service 同步阶段名，非 roleTypeCode）。
 *
 * scopeMode 四态对齐 @/utils/scope-mode（DENIED/INSTANCE/ALL/EMPTY）。
 * 🔧 T-PERM-033：正式选项应来自类型定义/主体候选接口，Phase 1 硬编码。
 */

/** 排查目标类型 */
export type TargetType = "USER" | "ROLE";

/** 目标类型选项 */
export const TARGET_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: TargetType;
}> = [
  { label: "用户", value: "USER" },
  { label: "角色", value: "ROLE" }
];

/**
 * 用户主体类型码选项（subjectTypeCode）。
 * ADMIN_USER 是 AccessMesh 管理端用户的主要真实类型；USER 为通用用户类型。
 * 仅当确实存在 USER 类型主体时才展示 USER（Phase 1 mock 两项都保留）。
 */
export const SUBJECT_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
}> = [
  { label: "管理用户 ADMIN_USER", value: "ADMIN_USER" },
  { label: "普通用户 USER", value: "USER" }
];

/**
 * 角色类型码选项（roleTypeCode，对齐 RoleType.java）。
 * - ORG/POSITION：domainCode 必填（组织域绑定）
 * - BASIC_ROLE/GROUP_ROLE/PERSONAL：允许全局域，domainCode 可空
 */
export const ROLE_TYPE_OPTIONS: ReadonlyArray<{
  label: string;
  value: string;
  /** 是否要求 domainCode 必填 */
  domainRequired: boolean;
}> = [
  { label: "基本角色 BASIC_ROLE", value: "BASIC_ROLE", domainRequired: false },
  { label: "组织角色 ORG", value: "ORG", domainRequired: true },
  { label: "职位角色 POSITION", value: "POSITION", domainRequired: true },
  { label: "个人角色 PERSONAL", value: "PERSONAL", domainRequired: false },
  { label: "分组角色 GROUP_ROLE", value: "GROUP_ROLE", domainRequired: false }
];

/** scopeMode 四态展示 meta（对齐 §6.7 L1322） */
export const SCOPE_MODE_META: Record<
  string,
  { label: string; type: "success" | "warning" | "info" | "danger" }
> = {
  DENIED: { label: "无权限", type: "danger" },
  INSTANCE: { label: "实例授权", type: "success" },
  ALL: { label: "全量授权", type: "warning" },
  EMPTY: { label: "有权限无数据", type: "info" }
};

/** 拒绝原因展示 meta */
export const REASON_META: Record<
  string,
  { label: string; type: "danger" | "warning" | "info" }
> = {
  NO_PERMISSION: { label: "无权限", type: "danger" },
  USER_NOT_FOUND: { label: "用户不存在", type: "warning" },
  ROLE_NOT_FOUND: { label: "角色不存在", type: "warning" },
  OBJECT_KEY_NOT_FOUND: { label: "资源不存在", type: "warning" }
};

/** 影响级别 meta（recentChanges.impactLevel） */
export const IMPACT_LEVEL_META: Record<
  string,
  { label: string; type: "danger" | "warning" | "info" }
> = {
  POSSIBLE: { label: "可能影响", type: "warning" },
  CONFIRMED: { label: "确认影响", type: "danger" },
  NONE: { label: "无影响", type: "info" }
};

/** scopeMode 是否为 INSTANCE（要求 resourceCode + codeType） */
export function isInstanceMode(mode: string | null | undefined): boolean {
  return mode === "INSTANCE";
}

/** scopeMode 是否为 ALL（清空并禁用 resourceCode/codeType） */
export function isAllMode(mode: string | null | undefined): boolean {
  return mode === "ALL";
}
