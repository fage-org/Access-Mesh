/**
 * 权限授予 API
 * 经 @/utils/http 调用 permission-center 端点（/api/perm/*）；
 * Phase 1 由 mock/permission-grant.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.5 / §6.4 / §6.5
 * 后端实现：permission-center RoleController + ResourceController + OperationController
 *   + ConditionController + RoleResourcePermissionController
 *
 * 🔧 API 核对项（登记 T-PERM-034，详见 docs/design/frontend/permission-grant.md §13.2）：
 * - list 返回 RolePermissionItem 缺 grantSource/grantDepId（缺口 3），mock 自行补充
 * - 缺候选权限 grantableByOperator + denyReason（缺口 4），mock 通过 operatorCapability 补充
 * - 角色响应缺 canView/canManage/directGrantable（缺口 5），mock 自行补充
 * - save 与 add-child 非原子（缺口 6），hook 实现两步保存 + 部分失败处理
 * - 缺 configVersion/updatedAt 乐观并发（缺口 9），P0 标 TODO
 * - 大规模角色树/资源树懒加载契约（缺口 11），P0 标 TODO
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

/** 授权侧范围模式（§6.4：只允许 INSTANCE/ALL；DENIED/EMPTY 是查询侧四态） */
export type GrantScopeMode = "INSTANCE" | "ALL";

// ========== 角色树（§5.5 abstract-role/tree）==========

/** 角色树节点（带能力字段，决策点 6 归属：角色节点） */
export interface RoleTreeNode {
  roleTypeCode: string;
  roleExternalId: string;
  roleName: string;
  domainCode: string;
  parentId: string | null;
  enabled: boolean;
  /** 是否可直接配置权限（GROUP_ROLE 默认 false，§3.4） */
  directGrantable: boolean;
  /** 操作者是否可查看该角色权限 */
  canView: boolean;
  /** 操作者是否可管理（编辑/保存）该角色权限 */
  canManage: boolean;
  children: RoleTreeNode[];
}

export interface RoleTreeReq {
  domainCode?: string;
  roleTypeCodes?: string[];
  keyword?: string;
}

export interface RoleTreeResp {
  items: RoleTreeNode[];
}

// ========== 资源类型（type-definition/list）==========

/** 资源类型（带能力字段，决策点 6 归属：资源类型） */
export interface ResourceTypeItem {
  resourceTypeCode: string;
  resourceTypeName: string;
  supportsInstance: boolean;
  supportsAll: boolean;
  supportsCondition: boolean;
  supportsDelegation: boolean;
}

export interface ResourceTypeListReq {
  domainCode?: string;
}

export interface ResourceTypeListResp {
  items: ResourceTypeItem[];
}

// ========== 资源树（resource-entity/tree）==========

export interface ResourceTreeNode {
  resourceCode: string;
  codeType: string;
  resourceName: string;
  parentCode: string | null;
  children: ResourceTreeNode[];
}

export interface ResourceTreeReq {
  domainCode: string;
  resourceTypeCode: string;
  keyword?: string;
}

export interface ResourceTreeResp {
  items: ResourceTreeNode[];
}

// ========== 操作（operation-permission/list）==========

export interface OperationItem {
  operationCode: string;
  operationName: string;
  resourceTypeCode: string;
  /** 继承掩码摘要（操作包含其他操作语义时非空） */
  inheritMask: string | null;
}

export interface OperationListReq {
  resourceTypeCode: string;
}

export interface OperationListResp {
  items: OperationItem[];
}

// ========== 条件候选（permission-condition/list）==========

export interface ConditionOption {
  conditionId: number;
  code: string;
  name: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
  summary: string;
}

export interface ConditionListReq {
  keyword?: string;
  enabledOnly?: boolean;
}

export interface ConditionListResp {
  items: ConditionOption[];
}

// ========== 已有权限 list（§5.5 role-resource-permission/list）==========

/** 角色权限事实项（对齐后端 RolePermissionItemResp + 能力扩展） */
export interface RolePermissionItem {
  id: number;
  domainCode: string;
  resourceTypeCode: string;
  /** null = ALL */
  resourceCode: string | null;
  /** null = ALL */
  codeType: string | null;
  resourceName: string | null;
  operationCode: string;
  scopeMode: GrantScopeMode;
  conditionCode: string | null;
  canGrant: boolean;
  /** 父权限 id（子权限），null = 主权限 */
  dependOn: number | null;
  /** 来源：MANUAL / AUTO_DEP / COMPOSED */
  grantSource: string;
}

/** 业务域能力（决策点 6 归属：业务域，嵌在 list 响应） */
export interface DomainCapability {
  supportsChildren: boolean;
  childResourceTypeCodes: string[];
}

/** 操作者能力（决策点 6 归属：候选权限单元 grantableByOperator 的数据来源） */
export interface OperatorCapability {
  canManage: boolean;
  /** 可授予的资源类型码列表（grantableByOperator 判定依据） */
  grantableResourceTypeCodes: string[];
  /** 可授予的操作码列表 */
  grantableOperationCodes: string[];
}

export interface RolePermissionListReq {
  domainCode: string;
  roleTypeCode: string;
  roleExternalId: string;
}

export interface RolePermissionListResp {
  items: RolePermissionItem[];
  domainCapability: DomainCapability;
  operatorCapability: OperatorCapability;
}

// ========== save（§6.4 三段式授权）==========

export interface RolePermissionAddItem {
  resourceTypeCode: string;
  /** scopeMode=INSTANCE 时必填，ALL 时不传 */
  resourceCode?: string;
  /** scopeMode=INSTANCE 时必填，ALL 时不传 */
  codeType?: string;
  operationCode: string;
  scopeMode: GrantScopeMode;
  conditionCode?: string | null;
  canGrant?: boolean;
}

export interface RolePermissionUpdateItem {
  id: number;
  conditionCode?: string | null;
  canGrant?: boolean;
}

export interface RolePermissionSaveReq {
  domainCode: string;
  roleTypeCode: string;
  roleExternalId: string;
  add: RolePermissionAddItem[];
  update: RolePermissionUpdateItem[];
  remove: number[];
}

export interface RolePermissionSaveResp {
  items: RolePermissionItem[];
}

// ========== children（§6.5 子权限查询）==========

export interface ChildPermissionQueryReq {
  permissionId: number;
}

export interface ChildPermissionQueryResp {
  items: RolePermissionItem[];
}

// ========== add-child（§6.5 添加子权限）==========

export interface AddChildReq {
  parentPermissionId: number;
  children: RolePermissionAddItem[];
}

export interface AddChildResp {
  items: RolePermissionItem[];
}

// ========== remove-child（§6.5 删除子权限）==========

export interface RemoveChildReq {
  permissionId: number;
}

export interface RemoveChildResp {
  success: boolean;
}

// ========== API 函数 ==========

/** 角色树（五类型虚拟根 + 能力字段） */
export const getRoleTree = async (data: RoleTreeReq): Promise<RoleTreeResp> => {
  const res = await http.request<PermResult<RoleTreeResp>>(
    "post",
    "/api/perm/abstract-role/tree",
    { data }
  );
  return unwrap(res);
};

/** 资源类型列表 */
export const getResourceTypeList = async (
  data: ResourceTypeListReq
): Promise<ResourceTypeListResp> => {
  const res = await http.request<PermResult<ResourceTypeListResp>>(
    "post",
    "/api/perm/type-definition/list",
    { data }
  );
  return unwrap(res);
};

/** 资源树（按资源类型） */
export const getResourceTree = async (
  data: ResourceTreeReq
): Promise<ResourceTreeResp> => {
  const res = await http.request<PermResult<ResourceTreeResp>>(
    "post",
    "/api/perm/resource-entity/tree",
    { data }
  );
  return unwrap(res);
};

/** 操作列表（按资源类型） */
export const getOperationList = async (
  data: OperationListReq
): Promise<OperationListResp> => {
  const res = await http.request<PermResult<OperationListResp>>(
    "post",
    "/api/perm/operation-permission/list",
    { data }
  );
  return unwrap(res);
};

/** 条件候选列表 */
export const getConditionList = async (
  data: ConditionListReq
): Promise<ConditionListResp> => {
  const res = await http.request<PermResult<ConditionListResp>>(
    "post",
    "/api/perm/permission-condition/list",
    { data }
  );
  return unwrap(res);
};

/** 查询角色已有权限事实 + 业务域能力 + 操作者能力 */
export const getRolePermissionList = async (
  data: RolePermissionListReq
): Promise<RolePermissionListResp> => {
  const res = await http.request<PermResult<RolePermissionListResp>>(
    "post",
    "/api/perm/role-resource-permission/list",
    { data }
  );
  return unwrap(res);
};

/** 三段式批量保存授权（add/update/remove 同事务，§6.4） */
export const saveRolePermission = async (
  data: RolePermissionSaveReq
): Promise<RolePermissionSaveResp> => {
  const res = await http.request<PermResult<RolePermissionSaveResp>>(
    "post",
    "/api/perm/role-resource-permission/save",
    { data }
  );
  return unwrap(res);
};

/** 查询主权限下的子权限（§6.5） */
export const getChildPermissions = async (
  data: ChildPermissionQueryReq
): Promise<ChildPermissionQueryResp> => {
  const res = await http.request<PermResult<ChildPermissionQueryResp>>(
    "post",
    "/api/perm/role-resource-permission/children",
    { data }
  );
  return unwrap(res);
};

/** 为已保存主权限添加子权限（§6.5，parentPermissionId 必须 depend_on IS NULL） */
export const addChildPermission = async (
  data: AddChildReq
): Promise<AddChildResp> => {
  const res = await http.request<PermResult<AddChildResp>>(
    "post",
    "/api/perm/role-resource-permission/add-child",
    { data }
  );
  return unwrap(res);
};

/** 删除子权限（§6.5） */
export const removeChildPermission = async (
  data: RemoveChildReq
): Promise<RemoveChildResp> => {
  const res = await http.request<PermResult<RemoveChildResp>>(
    "post",
    "/api/perm/role-resource-permission/remove-child",
    { data }
  );
  return unwrap(res);
};
