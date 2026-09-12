import type { TypeDefResp, TypeKey } from "@/api/type-def";
import { ALL_TYPE_KEYS, TYPE_KEY_LABEL, TYPE_KEY } from "@/api/type-def";

/** 类型定义表单数据（新建/编辑共用）。无 typeValue——服务端自动分配（D1）。 */
export interface TypeDefFormData {
  /** 类型分组键（新建必填，编辑只读） */
  typeKey: TypeKey | "";
  /** 对外稳定编码（新建可空=自动生成，编辑只读） */
  typeCode: string;
  /** 显示名称 */
  name: string;
  /** 描述（可空） */
  description: string;
  /** 系统预置标记（仅编辑态只读展示；新建不暴露，前端创建固定 false=租户自定义，系统预置走初始化种子） */
  isSystem: boolean;
  /** 排序顺序 */
  sortOrder: number;
  /** 扩展属性 JSON（可空） */
  extra: string;
  /**
   * 类型所有者角色 externalId（T-PERM-062，仅 typeKey=resource_type 表单项可见）；
   * 空串 = 未指定（create 走后端缺省引导角色 bootstrap-admin；update 不变更指针）。
   * 选项固定 BASIC_ROLE 功能角色（启用态），提交时 create 走 ownerRole* 请求字段、
   * update 由表单将指针同步进 extra JSON（后端按 extra.grantOriginRole 变更触发同事务迁移）。
   */
  ownerRoleExternalId: string;
}

/** 新建默认表单 */
export function createEmptyTypeDefForm(
  typeKey: TypeKey | "" = ""
): TypeDefFormData {
  return {
    typeKey,
    typeCode: "",
    name: "",
    description: "",
    isSystem: false,
    sortOrder: 0,
    extra: "",
    ownerRoleExternalId: ""
  };
}

/** type_key 下拉选项（全部 4 类） */
export const TYPE_KEY_OPTIONS = ALL_TYPE_KEYS.map(key => ({
  label: TYPE_KEY_LABEL[key],
  value: key
}));

/**
 * 是否系统预置类型（不可删改 name/typeCode/typeKey，仅可改 description/sortOrder/extra）。
 * isSystem=true 的行由租户初始化写入，schema:47 注释明示不可删改。
 */
export function isSystemPreset(row: { isSystem?: boolean }): boolean {
  return !!row.isSystem;
}

export type { TypeDefResp, TypeKey };
export { TYPE_KEY, TYPE_KEY_LABEL };
