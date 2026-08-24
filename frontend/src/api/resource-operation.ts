/**
 * 资源与操作定义 API
 * 经 @/utils/http 调用 Gateway 外部路径
 * （`/perm/api/perm/resource-entity/*` + `/perm/api/perm/operation-permission/*`，
 * Gateway StripPrefix=1 后到 access-service `/api/perm/...`）。
 * T-FE-041 切换真实链路后，mock/resource-operation.ts 的旧 `/api/perm/**` 路径已自然失配。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；分页/列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.3
 * 后端实现：access-service ResourceController + OperationController
 *
 * 🔧 API 核对项（登记 T-PERM-028）：
 * - resource-entity detail/update/move/remove 与 operation-permission detail/update/remove 均用内部主键 id，
 *   应切业务键（resource: resourceTypeCode+code+codeType；operation: resourceTypeCode+code）。
 *   schema uk_resource_entity / uk_operation_permission_typed 已保证唯一，Phase 2 后端收敛。
 * - resource-entity list/tree 与 operation-permission list 未见 RESOURCE:VIEW / OPERATION:VIEW 门禁校验，
 *   种子可能缺失，联调真后端时可能全账号 403--前端仍按 VIEW 门控路由可达性。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp, PaginatedResp } from "./role-manage";

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

/** 资源更新请求（对齐 ResourceUpdateReq） */
export type ResourceUpdateReq = {
  id: number;
  code?: string;
  name?: string;
  path?: string | null;
  status?: number;
  sortOrder?: number;
  extra?: string | null;
};

/** 资源移动请求（对齐 ResourceMoveReq） */
export type ResourceMoveReq = {
  resourceId: number;
  parentId: number | null;
};

// ========== 操作权限 ==========

/** 操作权限响应（对齐后端 OperationPermissionResp） */
export type OperationPermissionResp = {
  id: number;
  tenantId: number;
  /** 资源类型编码（null 表示适用所有资源类型） */
  resourceTypeCode: string | null;
  resourceTypeName: string | null;
  code: string;
  name: string;
  /** 本操作独占位（2 的幂次） */
  binaryBit: number;
  /** 继承掩码，实际权限 = binaryBit | inheritMask */
  inheritMask: number;
  createdAt: string;
  updatedAt: string;
};

/** 操作权限列表查询参数（对齐 OperationListReq；无分页；🔧 T-PERM-040 增加 includeGlobalFallback） */
export type OperationListQuery = {
  resourceTypeCode?: string | null;
  domainCode?: string | null;
  /**
   * 可选，默认 false；true 时后端完成"专属优先、全局回退"合并，响应直接返回
   * 当前 resourceTypeCode 最终可用的操作集合（前端不再重复领域规则）。
   * resourceTypeCode=null/缺省 + true = 仅全局操作集合。
   * T-FE-038 本页矩阵操作列一律走 includeGlobalFallback=true。
   */
  includeGlobalFallback?: boolean;
};

/** 操作权限创建请求（对齐 OperationCreateReq） */
export type OperationCreateReq = {
  resourceTypeCode: string;
  code: string;
  name: string;
  binaryBit: number;
  inheritMask?: number;
};

/** 操作权限更新请求（对齐 OperationUpdateReq；code/resourceType 不可改--稳定编码） */
export type OperationUpdateReq = {
  operationId: number;
  name?: string;
  binaryBit?: number;
  inheritMask?: number;
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

/** 查询资源详情（POST /perm/api/perm/resource-entity/detail，IdReq{id}）。
 *  🔧 用内部主键 id，Phase 2 切业务键（T-PERM-028）。 */
export const getResourceDetail = async (id: number): Promise<ResourceResp> => {
  const res = await http.request<PermResult<ResourceResp>>(
    "post",
    "/perm/api/perm/resource-entity/detail",
    { data: { id } }
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

/** 更新资源（POST /perm/api/perm/resource-entity/update）。
 *  🔧 用内部 id，Phase 2 切业务键（T-PERM-028）。 */
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

/** 移动资源（POST /perm/api/perm/resource-entity/move）。
 *  🔧 resourceId/parentId 均为内部 id，Phase 2 切业务键（T-PERM-028）。 */
export const moveResource = async (data: ResourceMoveReq): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/resource-entity/move",
      { data }
    )
  );
};

/** 删除资源，支持批量（POST /perm/api/perm/resource-entity/remove，IdsReq{ids}）。
 *  🔧 用内部 id，Phase 2 切业务键（T-PERM-028）。 */
export const removeResources = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/resource-entity/remove",
      { data: { ids } }
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

/** 查询操作权限详情（POST /perm/api/perm/operation-permission/detail，IdReq{id}）。
 *  🔧 用内部 id，Phase 2 切业务键（T-PERM-028）。 */
export const getOperationDetail = async (
  id: number
): Promise<OperationPermissionResp> => {
  const res = await http.request<PermResult<OperationPermissionResp>>(
    "post",
    "/perm/api/perm/operation-permission/detail",
    { data: { id } }
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

/** 更新操作权限（POST /perm/api/perm/operation-permission/update）。
 *  🔧 operationId 为内部 id；code/resourceType 不可改（稳定编码），Phase 2 切业务键（T-PERM-028）。 */
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

/** 删除操作权限，支持批量（POST /perm/api/perm/operation-permission/remove，IdsReq{ids}）。
 *  🔧 用内部 id，Phase 2 切业务键（T-PERM-028）。 */
export const removeOperations = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/perm/api/perm/operation-permission/remove",
      { data: { ids } }
    )
  );
};

export { type PaginatedResp, type ItemsResp };
