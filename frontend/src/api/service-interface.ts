/**
 * 服务注册与接口映射 API。
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/service-config/*` 与
 * `/perm/api/perm/resource-api-mapping/*`（Gateway StripPrefix=1 后到
 * access-service 的 `/api/perm/service-config`、`/api/perm/resource-api-mapping`）。
 * 接口形状对齐 api-contract 契约（T-PERM-027 收口）。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";

export type ItemsResp<T> = {
  items: T[];
};

export type ServiceConfigResp = {
  id: number;
  tenantId?: number;
  serviceCode: string;
  name: string;
  basePath?: string | null;
  description?: string | null;
  /** 0=停用，1=启用 */
  status: number;
  extra?: string | null;
  createdAt?: string;
  /** 最近更新（保存与 FULL 同步回写 basePath 时刷新，T-PERM-027） */
  updatedAt?: string;
};

export type ServiceConfigSaveReq = {
  serviceCode: string;
  name: string;
  basePath?: string | null;
  description?: string | null;
  status?: number;
  extra?: string | null;
};

export type ApiMappingResp = {
  id: number;
  tenantId?: number;
  resourceEntityId: number;
  serviceCode: string;
  httpMethod: string;
  pathPattern: string;
  matchOrder?: number | null;
  enabled: boolean;
  extra?: string | null;
  createdAt?: string;
  updatedAt?: string;
  /** 关联资源业务编码（T-PERM-027；资源已删时为 null，回退展示 #id） */
  resourceCode?: string | null;
  /** 关联资源名称 */
  resourceName?: string | null;
  /** 关联资源类型编码（如 "API"） */
  resourceTypeCode?: string | null;
  /** 关联资源维护来源 MANUAL/SERVICE_SYNC */
  maintainSource?: string | null;
};

export type ApiMappingCreateReq = {
  resourceId: number;
  serviceCode: string;
  httpMethod: string;
  pathPattern: string;
  matchOrder?: number | null;
  enabled?: boolean;
  extra?: string | null;
};

export type ApiMappingUpdateReq = {
  resourceId: number;
  mappingId: number;
  httpMethod?: string;
  pathPattern?: string;
  matchOrder?: number | null;
  enabled?: boolean;
  extra?: string | null;
};

export type SyncApiItem = {
  name: string;
  httpMethod: string;
  path: string;
  operationCode: string;
  resourceCode: string;
  description?: string | null;
};

export type SyncApiGroup = {
  groupCode: string;
  groupName: string;
  apis: SyncApiItem[];
};

export type ServiceConfigSyncReq = {
  serviceCode: string;
  basePath?: string | null;
  /** 当前权威契约仅允许 FULL。 */
  syncMode: "FULL";
  groups: SyncApiGroup[];
};

export type ServiceConfigSyncResp = {
  createdResources: number;
  updatedResources: number;
  createdMappings: number;
  updatedMappings: number;
  deletedResources: number;
  deletedMappings: number;
};

export const getServiceConfigList = async (): Promise<
  ItemsResp<ServiceConfigResp>
> => {
  const res = await http.request<PermResult<ItemsResp<ServiceConfigResp>>>(
    "post",
    "/perm/api/perm/service-config/list",
    { data: {} }
  );
  return unwrap(res);
};

export const getServiceConfigDetail = async (
  serviceCode: string
): Promise<ServiceConfigResp> => {
  const res = await http.request<PermResult<ServiceConfigResp>>(
    "post",
    "/perm/api/perm/service-config/detail",
    { data: { serviceCode } }
  );
  return unwrap(res);
};

export const saveServiceConfig = async (
  data: ServiceConfigSaveReq
): Promise<ServiceConfigResp> => {
  const res = await http.request<PermResult<ServiceConfigResp>>(
    "post",
    "/perm/api/perm/service-config/save",
    { data }
  );
  return unwrap(res);
};

export const removeServiceConfigs = async (ids: number[]): Promise<void> => {
  const res = await http.request<PermResult<void>>(
    "post",
    "/perm/api/perm/service-config/remove",
    { data: { ids } }
  );
  unwrap(res);
};

/** 查询当前服务的接口资源映射；用于页面的服务工作区。 */
export const getServiceApis = async (
  serviceCode: string
): Promise<ItemsResp<ApiMappingResp>> => {
  const res = await http.request<PermResult<ItemsResp<ApiMappingResp>>>(
    "post",
    "/perm/api/perm/service-config/apis",
    { data: { serviceCode } }
  );
  return unwrap(res);
};

/** 用于一次取回映射计数，避免左侧服务列表逐项发起请求。 */
export const getApiMappingList = async (data: {
  resourceId?: number;
  serviceCode?: string;
}): Promise<ItemsResp<ApiMappingResp>> => {
  const res = await http.request<PermResult<ItemsResp<ApiMappingResp>>>(
    "post",
    "/perm/api/perm/resource-api-mapping/list",
    { data }
  );
  return unwrap(res);
};

export const createApiMapping = async (
  data: ApiMappingCreateReq
): Promise<ApiMappingResp> => {
  const res = await http.request<PermResult<ApiMappingResp>>(
    "post",
    "/perm/api/perm/resource-api-mapping/create",
    { data }
  );
  return unwrap(res);
};

export const updateApiMapping = async (
  data: ApiMappingUpdateReq
): Promise<ApiMappingResp> => {
  const res = await http.request<PermResult<ApiMappingResp>>(
    "post",
    "/perm/api/perm/resource-api-mapping/update",
    { data }
  );
  return unwrap(res);
};

export const removeApiMappings = async (ids: number[]): Promise<void> => {
  const res = await http.request<PermResult<void>>(
    "post",
    "/perm/api/perm/resource-api-mapping/remove",
    { data: { ids } }
  );
  unwrap(res);
};

export const syncServiceInterfaces = async (
  data: ServiceConfigSyncReq
): Promise<ServiceConfigSyncResp> => {
  const res = await http.request<PermResult<ServiceConfigSyncResp>>(
    "post",
    "/perm/api/perm/service-config/sync",
    { data }
  );
  return unwrap(res);
};
