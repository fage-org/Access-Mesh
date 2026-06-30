import type {
  RoleResp,
  RoleTreeNode,
  RoleStatus,
  RoleTypeCode
} from "@/api/role-manage";
import { MANAGEABLE_ROLE_TYPES } from "@/api/role-manage";

/** 角色表单数据（新建/编辑共用） */
export interface RoleFormData {
  /** 角色类型编码（新建时必填，编辑时只读） */
  roleTypeCode: RoleTypeCode | "";
  /** 角色名称 */
  name: string;
  /** 外部标识（可空，用于与外部系统关联） */
  externalId: string;
  /** 父角色 ID（可空=顶层森林根，C2 后无类型虚拟根） */
  parentId: number | null;
  /** 状态：0=禁用，1=启用 */
  status: RoleStatus;
  /** 排序顺序 */
  sortOrder: number;
  /** 扩展属性 JSON（可空） */
  extra: string;
}

/** 新建默认表单 */
export function createEmptyRoleForm(
  roleTypeCode: RoleTypeCode = "BASIC_ROLE"
): RoleFormData {
  return {
    roleTypeCode,
    name: "",
    externalId: "",
    parentId: null,
    status: 1,
    sortOrder: 0,
    extra: ""
  };
}

/**
 * 角色管理页是否展示该类型节点。
 * 仅 BASIC_ROLE / GROUP_ROLE 进角色管理页（MANAGEABLE_ROLE_TYPES）；
 * ORG/POSITION/PERSONAL 由外部同步生成，不归角色管理（归权限授予/用户详情）。
 */
export function isPageVisibleRoleType(roleTypeCode: string): boolean {
  return (MANAGEABLE_ROLE_TYPES as string[]).includes(roleTypeCode);
}

/** 节点是否为只读类型（ORG/POSITION/PERSONAL，由外部同步生成） */
export function isReadonlyRoleType(roleTypeCode: string): boolean {
  return (
    roleTypeCode === "ORG" ||
    roleTypeCode === "POSITION" ||
    roleTypeCode === "PERSONAL"
  );
}

export type { RoleResp, RoleTreeNode, RoleStatus, RoleTypeCode };
