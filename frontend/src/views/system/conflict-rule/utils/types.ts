/**
 * 冲突规则页表单类型与常量。
 *
 * 字段对齐后端 DTO（api-contract.md §5.6）：
 * - ConflictRuleReq / ConflictRuleUpdateReq / ConflictRuleDetectReq
 * - conflictType: ROLE_MUTEX（角色互斥）/ PERM_MUTEX（权限互斥）
 * - ROLE_MUTEX 字段集：firstAbstractRoleId + secondAbstractRoleId
 * - PERM_MUTEX 字段集：firstOperationPermissionId + secondOperationPermissionId + resourceTypeValue
 *
 * resourceTypeValue 为 type_definition 表 type_key='resource_type' 的 type_value（数值），
 * 非 resourceTypeCode（字符串）。前端通过 getTypeDefList 建立映射（设计 §Q1）。
 */

import { CONFLICT_TYPE, type ConflictTypeCode } from "@/api/conflict-rule";

/** 冲突类型选项（下拉用） */
export const CONFLICT_TYPE_OPTIONS = [
  { label: "角色互斥", value: CONFLICT_TYPE.ROLE_MUTEX },
  { label: "权限互斥", value: CONFLICT_TYPE.PERM_MUTEX }
] as const;

/** 冲突规则新增/编辑表单数据 */
export type ConflictFormData = {
  conflictType: ConflictTypeCode;
  /** ROLE_MUTEX：第一个角色 ID */
  firstAbstractRoleId: number | null;
  /** ROLE_MUTEX：第二个角色 ID */
  secondAbstractRoleId: number | null;
  /** PERM_MUTEX：第一个操作权限 ID */
  firstOperationPermissionId: number | null;
  /** PERM_MUTEX：第二个操作权限 ID */
  secondOperationPermissionId: number | null;
  /** PERM_MUTEX：资源类型值（type_definition.type_value，null 表示全部资源类型） */
  resourceTypeValue: number | null;
  description: string;
};

/** 冲突规则表单空值工厂（新建用，默认 ROLE_MUTEX） */
export function createEmptyConflictForm(): ConflictFormData {
  return {
    conflictType: CONFLICT_TYPE.ROLE_MUTEX,
    firstAbstractRoleId: null,
    secondAbstractRoleId: null,
    firstOperationPermissionId: null,
    secondOperationPermissionId: null,
    resourceTypeValue: null,
    description: ""
  };
}
