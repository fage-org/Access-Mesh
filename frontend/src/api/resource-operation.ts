/**
 * 资源与操作定义 API
 * 经 @/utils/http 调用 Gateway 外部路径
 * （`/perm/api/perm/resource-entity/*` + `/perm/api/perm/operation-permission/*`，
 * Gateway StripPrefix=1 后到 access-service `/api/perm/...`）。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页/列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.3
 * 后端实现：access-service ResourceController + OperationController
 *
 * T-PERM-028 收口：
 * - detail/update/move/remove 均切业务键定位（resource: resourceTypeCode+code+codeType，
 *   codeType 缺省 default；operation: resourceTypeCode+code），不再接受内部 id。
 * - binaryBit/inheritMask 线格式为十进制字符串（63 位 bigint 防 JSON number >2^53 丢精度，
 *   契约 §5.3 定稿）；表单内部可用数值控件，提交时转字符串。
 * - update 支持 extraClear 显式清空 extra（JSON null 无法区分「未传」与「清空」）。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp, PaginatedResp } from "./role-manage";

// ========== 业务键 ==========

/** 资源实体业务键（uk_resource_entity: tenant+resourceType+code+codeType） */
export type ResourceKey = {
  resourceTypeCode: string;
  code: string;
  /** 可选，缺省 "default" */
  codeType?: string | null;
};

/** 操作权限业务键（uk_operation_permission_typed；全局操作概念已退役，resourceTypeCode 必填） */
export type OperationKey = {
  resourceTypeCode: string;
  code: string;
};

// ========== 资源实体 ==========

/** 资源实体响应（对齐后端 ResourceResp） */
export type ResourceResp = {
  id: number;
  tenantId: number;
  parentId: number | null;
  resourceTypeCode: string;
  resourceTypeName: string | null;
  code: string;
  codeType: string;
  name: string;
  path: string | null;
  status: number;
  sortOrder: number;
  extra: string | null;
  createdAt: string;
  updatedAt: string;
};

/** 资源树节点（对齐后端 ResourceTreeResp.ResourceTreeNode） */
export type ResourceTreeNode = {
  id: number;
  parentId: number | null;
  resourceTypeCode: string;
  code: string;
  codeType: string;
  name: string;
  path: string | null;
  status: number;
  sortOrder: number;
  children: ResourceTreeNode[] | null;
};

/** 资源树响应（对齐后端 ResourceTreeResp，单 root 包裹） */
export type ResourceTreeResp = {
  root: ResourceTreeNode | null;
};

/** 资源树查询参数（对齐 ResourceTreeReq） */
export type ResourceTreeQuery = {
  resourceTypeCode?: string | null;
  domainCode?: string | null;
};

/** 资源列表查询参数（对齐 ResourceListReq；本页以树为主不消费，保留对齐契约） */
export type ResourceListQuery = {
  resourceTypeCode?: string | null;
  domainCode?: string | null;
  pageNum?: number;
  pageSize?: number;
  sort?: string | null;
};

/** 资源创建请求（对齐 ResourceCreateReq） */
export type ResourceCreateReq = {
  parentId?: number | null;
  resourceTypeCode: string;
  code: string;
  codeType?: string | null;
  name: string;
  path?: string | null;
  status?: number;
  sortOrder?: number;
  extra?: string | null;
};

/** 资源更新请求（对齐 ResourceUpdateReq；业务键定位 + 可编辑字段） */
export type ResourceUpdateReq = {
  resourceTypeCode: string;
  code: string;
  codeType?: string | null;
  name?: string;
  path?: string | null;
  status?: number;
  sortOrder?: number;
  extra?: string | null;
  /** true=清空 extra 为 null（优先于 extra；JSON null 无法区分「未传」与「清空」） */
  extraClear?: boolean;
};

/** 资源移动请求（对齐 ResourceMoveReq；parent null=移动到顶层） */
export type ResourceMoveReq = {
  resource: ResourceKey;
  parent: ResourceKey | null;
};

// ========== 操作权限 ==========

/** 操作权限响应（对齐后端 OperationPermissionResp；位字段为十进制字符串） */
export type OperationPermissionResp = {
  id: number;
  tenantId: number;
  /** 资源类型编码（全局操作概念已退役，恒非空） */
  resourceTypeCode: string;
  resourceTypeName: string | null;
  code: string;
  name: string;
  /** 本操作独占位（2 的幂次），十进制字符串（63 位 bigint 线格式） */
  binaryBit: string;
  /** 继承掩码，实际权限 = binaryBit | inheritMask，十进制字符串 */
  inheritMask: string;
  createdAt: string;
  updatedAt: string;
};

/** 操作权限列表查询参数（对齐 OperationListReq；无分页；全局操作概念已退役，操作定义按类型返回） */
export type OperationListQuery = {
  resourceTypeCode?: string | null;
  domainCode?: string | null;
};

/** 操作权限创建请求（对齐 OperationCreateReq；位字段十进制字符串） */
export type OperationCreateReq = {
  resourceTypeCode: string;
  code: string;
  name: string;
  binaryBit: string;
  inheritMask?: string;
};

/** 操作权限更新请求（对齐 OperationUpdateReq；业务键定位，code/resourceType 不可改且必填） */
export type OperationUpdateReq = {
  resourceTypeCode: string;
  code: string;
  name?: string;
  binaryBit?: string;
  inheritMask?: string;
};

// ========== API 函数 ==========

/** 查询资源树（POST /perm/api/perm/resource-entity/tree）。
 *  后端返回 ItemsResp<ResourceTreeResp>，每个 item.root 为一棵树的根；
 *  按 resourceTypeCode 过滤后，顶层资源各自作为 root 返回（森林）。 */
export const getResourceTree = async (
  params: ResourceTreeQuery
): Promise<ItemsResp<ResourceTreeResp>> => {
  const res = await http.request<PermResult<ItemsResp<ResourceTreeResp>>>(
    "post",
    "/perm/api/perm/resource-entity/tree",
    { data: params }
  );
  return unwrap(res);
};

/** 查询资源列表（POST /perm/api/perm/resource-entity/list，分页）。
 *  本页以树为主不消费，保留对齐契约。 */
export const getResourceList = async (
  params: ResourceListQuery
): Promise<PaginatedResp<ResourceResp>> => {
  const res = await http.request<PermResult<PaginatedResp<ResourceResp>>>(
    "post",
    "/perm/api/perm/resource-entity/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询资源详情（POST /perm/api/perm/resource-entity/detail，业务键定位）。 */
export const getResourceDetail = async (
  key: ResourceKey
): Promise<ResourceResp> => {
  const res = await http.request<PermResult<ResourceResp>>(
    "post",
    "/perm/api/perm/resource-entity/detail",
    { data: key }
  );
  return unwrap(res);
};

/** 创建资源（POST /perm/api/perm/resource-entity/create） */
export const createResource = async (
  data: ResourceCreateReq
): Promise<ResourceResp> => {
  const res = await http.request<PermResult<ResourceResp>>(
    "post",
    "/perm/api/perm/resource-entity/create",
    { data }
  );
  return unwrap(res);
};

/** 更新资源（POST /perm/api/perm/resource-entity/update，业务键定位）。 */
export const updateResource = async (
  data: ResourceUpdateReq
): Promise<ResourceResp> => {
  const res = await http.request<PermResult<ResourceResp>>(
    "post",
    "/perm/api/perm/resource-entity/update",
    { data }
  );
  return unwrap(res);
};

/** 移动资源（POST /perm/api/perm/resource-entity/move，业务键对定位）。
 *  parent 为 null 表示移动到顶层。 */
export const moveResource = async (data: ResourceMoveReq): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/resource-entity/move",
      { data }
    )
  );
};

/** 删除资源，支持批量（POST /perm/api/perm/resource-entity/remove，业务键集合）。 */
export const removeResources = async (keys: ResourceKey[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/resource-entity/remove",
      { data: { items: keys } }
    )
  );
};

/** 查询操作权限列表（POST /perm/api/perm/operation-permission/list）。
 *  后端返回 ItemsResp（无分页），前端本地处理。 */
export const getOperationList = async (
  params: OperationListQuery
): Promise<ItemsResp<OperationPermissionResp>> => {
  const res = await http.request<
    PermResult<ItemsResp<OperationPermissionResp>>
  >("post", "/perm/api/perm/operation-permission/list", { data: params });
  return unwrap(res);
};

/** 查询操作权限详情（POST /perm/api/perm/operation-permission/detail，业务键定位）。
 *  resourceTypeCode 必填（全局操作概念已退役）。 */
export const getOperationDetail = async (
  key: OperationKey
): Promise<OperationPermissionResp> => {
  const res = await http.request<PermResult<OperationPermissionResp>>(
    "post",
    "/perm/api/perm/operation-permission/detail",
    { data: key }
  );
  return unwrap(res);
};

/** 创建操作权限（POST /perm/api/perm/operation-permission/create） */
export const createOperation = async (
  data: OperationCreateReq
): Promise<OperationPermissionResp> => {
  const res = await http.request<PermResult<OperationPermissionResp>>(
    "post",
    "/perm/api/perm/operation-permission/create",
    { data }
  );
  return unwrap(res);
};

/** 更新操作权限（POST /perm/api/perm/operation-permission/update，业务键定位）。 */
export const updateOperation = async (
  data: OperationUpdateReq
): Promise<OperationPermissionResp> => {
  const res = await http.request<PermResult<OperationPermissionResp>>(
    "post",
    "/perm/api/perm/operation-permission/update",
    { data }
  );
  return unwrap(res);
};

/** 删除操作权限，支持批量（POST /perm/api/perm/operation-permission/remove，业务键集合）。 */
export const removeOperations = async (keys: OperationKey[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/operation-permission/remove",
      { data: { items: keys } }
    )
  );
};

export { type PaginatedResp, type ItemsResp };
