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
import { summarizeRules } from "@/views/system/permission-condition/utils/types";

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
  /** P2-3：父权限域（adapt 补齐 domainCode 用，未来懒加载调用方提供） */
  domainCode?: string;
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

// ========== 现有 mock 结构适配（P1-1：复用共享端点，结构转换 + 能力推断） ==========
// 现有 mock 端点结构与本页 API 类型不同，adapt* 函数做字段映射 + 能力推断：
// - 角色树（role-manage.ts）：items[0].root.children，字段 name/externalId/status，无能力字段
// - 资源类型（type-def.ts）：TypeDefResp（typeKey=resource_type），无 supportsXxx
// - 资源树（resource-operation.ts）：items[].root，字段 code/name
// - 操作（resource-operation.ts）：字段 code/name，inheritMask 为 number
// - 条件（permission-condition.ts）：ConditionResp，id（非 conditionId），无 summary

/** 现有 mock 角色树节点 */
interface RawRoleNode {
  id: number;
  roleTypeCode: string;
  name: string;
  externalId: string;
  status: number; // 1=启用 0=禁用
  children?: RawRoleNode[];
}

/** 角色能力推断（§3.4：GROUP_ROLE directGrantable=false） */
function adaptRoleNode(r: RawRoleNode): RoleTreeNode {
  const directGrantable = r.roleTypeCode !== "GROUP_ROLE";
  const enabled = r.status === 1;
  return {
    roleTypeCode: r.roleTypeCode,
    roleExternalId: r.externalId,
    roleName: r.name,
    domainCode: "",
    parentId: r.id?.toString() ?? null,
    enabled,
    directGrantable,
    canView: true,
    canManage: directGrantable && enabled,
    children: (r.children ?? []).map(adaptRoleNode)
  };
}

/** P2：递归过滤角色树，保留命中节点及其祖先链（GROUP_403 嵌套在 GROUP_401 下可搜到） */
function filterRoleTree(nodes: RoleTreeNode[], kw: string): RoleTreeNode[] {
  const result: RoleTreeNode[] = [];
  for (const node of nodes) {
    const matchedChildren = filterRoleTree(node.children, kw);
    const selfMatched =
      node.roleName.toLowerCase().includes(kw) ||
      node.roleExternalId.toLowerCase().includes(kw);
    if (selfMatched || matchedChildren.length > 0) {
      // 自身匹配：保留全部 children；仅子孙匹配：只保留匹配的 children
      result.push({
        ...node,
        children: selfMatched ? node.children : matchedChildren
      });
    }
  }
  return result;
}

function adaptRoleTree(
  rawItems: Array<{ root: RawRoleNode }>,
  keyword?: string
): RoleTreeNode[] {
  const forest = rawItems[0]?.root?.children ?? [];
  const typeOrder = ["BASIC_ROLE", "GROUP_ROLE", "ORG", "POSITION", "PERSONAL"];
  const typeLabels: Record<string, string> = {
    BASIC_ROLE: "基础角色",
    GROUP_ROLE: "组合角色",
    ORG: "组织角色",
    POSITION: "岗位角色",
    PERSONAL: "个人角色"
  };
  const typeRoots = typeOrder
    .map(typeCode => {
      const roles = forest.filter(r => r.roleTypeCode === typeCode);
      if (roles.length === 0) return null;
      return {
        roleTypeCode: typeCode,
        roleExternalId: `__virtual_root_${typeCode}`,
        roleName: typeLabels[typeCode],
        domainCode: "",
        parentId: null,
        enabled: true,
        directGrantable: false,
        canView: false,
        canManage: false,
        children: roles.map(adaptRoleNode)
      } as RoleTreeNode;
    })
    .filter((n): n is RoleTreeNode => n !== null);
  // P2：共享 tree 端点不消费 keyword，适配后本地递归过滤 roleName/roleExternalId，
  // 保留命中节点及其祖先链
  if (!keyword) return typeRoots;
  const kw = keyword.toLowerCase();
  return filterRoleTree(typeRoots, kw);
}

/** 现有 mock 资源类型定义 */
interface RawTypeDef {
  typeKey: string;
  typeCode: string;
  name: string;
}

/** 资源类型能力推断（API/BUTTON supportsAll=false supportsDelegation=false） */
function adaptResourceTypes(rawItems: RawTypeDef[]): ResourceTypeItem[] {
  return rawItems
    .filter(t => t.typeKey === "resource_type")
    .map(t => {
      const limited = t.typeCode === "API" || t.typeCode === "BUTTON";
      return {
        resourceTypeCode: t.typeCode,
        resourceTypeName: t.name,
        supportsInstance: true,
        supportsAll: !limited,
        supportsCondition: true,
        supportsDelegation: !limited
      };
    });
}

/** 现有 mock 资源树节点 */
interface RawResourceNode {
  code: string;
  codeType: string;
  name: string;
  children?: RawResourceNode[];
}

function adaptResourceNode(n: RawResourceNode): ResourceTreeNode {
  return {
    resourceCode: n.code,
    codeType: n.codeType,
    resourceName: n.name,
    parentCode: null,
    children: (n.children ?? []).map(adaptResourceNode)
  };
}

function adaptResourceTree(
  rawItems: Array<{ root: RawResourceNode }>
): ResourceTreeNode[] {
  return rawItems.map(item => adaptResourceNode(item.root));
}

/** 现有 mock 操作 */
interface RawOperation {
  resourceTypeCode: string | null;
  code: string;
  name: string;
  inheritMask: number;
}

function adaptOperations(rawItems: RawOperation[]): OperationItem[] {
  return rawItems.map(op => ({
    operationCode: op.code,
    operationName: op.name,
    resourceTypeCode: op.resourceTypeCode ?? "",
    inheritMask: op.inheritMask ? String(op.inheritMask) : null
  }));
}

/** 现有 mock 条件 */
interface RawCondition {
  id: number;
  code: string;
  name: string;
  conditionRules: string;
  enabled: boolean;
  gatewayEvaluable: boolean;
}

function adaptConditions(rawItems: RawCondition[]): ConditionOption[] {
  return rawItems.map(c => ({
    conditionId: c.id,
    code: c.code,
    name: c.name,
    enabled: c.enabled,
    gatewayEvaluable: c.gatewayEvaluable,
    summary: summarizeRules(c.conditionRules)
  }));
}

// ========== role-resource-permission 真实响应适配（P1-1） ==========
// 真实后端 RolePermissionItemsResp 只返回 items；RolePermissionItemResp 缺
// domainCode/grantSource，scopeMode 可能以 scopeAll(boolean) 旧字段返回。
// domainCapability/operatorCapability 后端尚未返回（T-PERM-034 待补齐）。
// 本层做双字段容错 + 请求域补齐 + 保守 default capability，避免连真实后端时
// 能力对象 undefined 崩溃或 stable key 因 domainCode 缺失无法命中。

/** 真实后端 RolePermissionItemResp（字段可能缺失） */
interface RawRolePermissionItem {
  id: number;
  resourceTypeCode: string;
  resourceCode: string | null;
  codeType: string | null;
  resourceName: string | null;
  operationCode: string;
  canGrant: boolean;
  conditionCode: string | null;
  /** 新契约：scopeMode "INSTANCE"|"ALL" */
  scopeMode?: GrantScopeMode;
  /** 旧契约：scopeAll boolean（true=ALL） */
  scopeAll?: boolean;
  dependOn: number | null;
  /** 后端可能不返回 */
  domainCode?: string;
  grantSource?: string;
}

/** 真实后端 list 响应（domainCapability/operatorCapability 可能缺失） */
interface RawRolePermissionListResp {
  items: RawRolePermissionItem[];
  domainCapability?: DomainCapability;
  operatorCapability?: OperatorCapability;
}

/** 保守 default capability（T-PERM-034 后端补齐前，禁止默认全可用） */
const DEFAULT_DOMAIN_CAPABILITY: DomainCapability = {
  supportsChildren: false,
  childResourceTypeCodes: []
};
const DEFAULT_OPERATOR_CAPABILITY: OperatorCapability = {
  canManage: false,
  grantableResourceTypeCodes: [],
  grantableOperationCodes: []
};

/** 单项适配：scopeAll->scopeMode 双字段容错，domainCode 从请求补齐，grantSource 默认 MANUAL */
function adaptRolePermissionItem(
  raw: RawRolePermissionItem,
  reqDomainCode: string
): RolePermissionItem {
  let scopeMode: GrantScopeMode;
  if (raw.scopeMode === "INSTANCE" || raw.scopeMode === "ALL") {
    scopeMode = raw.scopeMode;
  } else if (raw.scopeAll) {
    scopeMode = "ALL";
  } else {
    scopeMode = "INSTANCE";
  }
  return {
    id: raw.id,
    domainCode: raw.domainCode ?? reqDomainCode,
    resourceTypeCode: raw.resourceTypeCode,
    resourceCode: raw.resourceCode,
    codeType: raw.codeType,
    resourceName: raw.resourceName,
    operationCode: raw.operationCode,
    scopeMode,
    conditionCode: raw.conditionCode,
    canGrant: raw.canGrant,
    dependOn: raw.dependOn,
    // P0 默认 MANUAL，待 T-PERM-034 后端补齐 grantSource
    grantSource: raw.grantSource ?? "MANUAL"
  };
}

/** list 响应适配：items 逐项映射 + capability 防御 default */
function adaptRolePermissionList(
  raw: RawRolePermissionListResp,
  reqDomainCode: string
): RolePermissionListResp {
  return {
    items: raw.items.map(i => adaptRolePermissionItem(i, reqDomainCode)),
    domainCapability: raw.domainCapability ?? DEFAULT_DOMAIN_CAPABILITY,
    operatorCapability: raw.operatorCapability ?? DEFAULT_OPERATOR_CAPABILITY
  };
}

// ========== API 函数 ==========

/** 角色树（复用 abstract-role/tree，适配为五类型虚拟根 + 能力字段） */
export const getRoleTree = async (data: RoleTreeReq): Promise<RoleTreeResp> => {
  const res = await http.request<
    PermResult<{ items: Array<{ root: RawRoleNode }> }>
  >("post", "/api/perm/abstract-role/tree", { data });
  return { items: adaptRoleTree(unwrap(res).items, data.keyword) };
};

/** 资源类型列表（复用 type-definition/list，过滤 resource_type + 能力推断） */
export const getResourceTypeList = async (
  data: ResourceTypeListReq
): Promise<ResourceTypeListResp> => {
  const res = await http.request<PermResult<{ items: RawTypeDef[] }>>(
    "post",
    "/api/perm/type-definition/list",
    { data }
  );
  return { items: adaptResourceTypes(unwrap(res).items) };
};

/** 资源树（复用 resource-entity/tree，解包 items[].root + 字段映射） */
export const getResourceTree = async (
  data: ResourceTreeReq
): Promise<ResourceTreeResp> => {
  const res = await http.request<
    PermResult<{ items: Array<{ root: RawResourceNode }> }>
  >("post", "/api/perm/resource-entity/tree", { data });
  return { items: adaptResourceTree(unwrap(res).items) };
};

/** 操作列表（复用 operation-permission/list，字段映射 code->operationCode） */
export const getOperationList = async (
  data: OperationListReq
): Promise<OperationListResp> => {
  const res = await http.request<PermResult<{ items: RawOperation[] }>>(
    "post",
    "/api/perm/operation-permission/list",
    { data }
  );
  return { items: adaptOperations(unwrap(res).items) };
};

/** 条件候选列表（复用 permission-condition/list，映射 + summary 派生） */
export const getConditionList = async (
  data: ConditionListReq
): Promise<ConditionListResp> => {
  const res = await http.request<PermResult<{ items: RawCondition[] }>>(
    "post",
    "/api/perm/permission-condition/list",
    { data }
  );
  return { items: adaptConditions(unwrap(res).items) };
};

/** 查询角色已有权限事实 + 业务域能力 + 操作者能力（P1-1：适配真实响应） */
export const getRolePermissionList = async (
  data: RolePermissionListReq
): Promise<RolePermissionListResp> => {
  const res = await http.request<PermResult<RawRolePermissionListResp>>(
    "post",
    "/api/perm/role-resource-permission/list",
    { data }
  );
  return adaptRolePermissionList(unwrap(res), data.domainCode);
};

/** 三段式批量保存授权（add/update/remove 同事务，§6.4；P1-1：适配真实响应） */
export const saveRolePermission = async (
  data: RolePermissionSaveReq
): Promise<RolePermissionSaveResp> => {
  const res = await http.request<
    PermResult<{ items: RawRolePermissionItem[] }>
  >("post", "/api/perm/role-resource-permission/save", { data });
  const raw = unwrap(res);
  return {
    items: (raw.items ?? []).map(i =>
      adaptRolePermissionItem(i, data.domainCode)
    )
  };
};

/** 查询主权限下的子权限（§6.5；P2-3：复用 adaptRolePermissionItem 适配真实响应；P2：wire body 只发 permissionId，domainCode 仅本地适配） */
export const getChildPermissions = async (
  data: ChildPermissionQueryReq
): Promise<ChildPermissionQueryResp> => {
  const res = await http.request<
    PermResult<{ items: RawRolePermissionItem[] }>
  >("post", "/api/perm/role-resource-permission/children", {
    data: { permissionId: data.permissionId }
  });
  const raw = unwrap(res);
  return {
    items: (raw.items ?? []).map(i =>
      adaptRolePermissionItem(i, data.domainCode ?? "")
    )
  };
};

/** 为已保存主权限添加子权限（§6.5，parentPermissionId 必须 depend_on IS NULL；P1-1：适配真实响应） */
export const addChildPermission = async (
  data: AddChildReq
): Promise<AddChildResp> => {
  const res = await http.request<
    PermResult<{ items: RawRolePermissionItem[] }>
  >("post", "/api/perm/role-resource-permission/add-child", { data });
  const raw = unwrap(res);
  // add-child 不传 domainCode；子权限 domainCode 从父权限继承，响应仅用于确认成功
  return { items: (raw.items ?? []).map(i => adaptRolePermissionItem(i, "")) };
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
