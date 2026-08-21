/**
 * 资源依赖 API
 * 经 @/utils/http 调用 access-service 端点
 * （`/api/perm/resource-dependency/*`）；
 * Phase 1 由 mock/resource-dependency.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6 / §6.9
 * 后端实现：access-service ResourceDependencyController + DependencyAppServiceImpl
 *
 * 🔧 API 核对项（登记 T-PERM-031）：
 * - ResourceDependencyResp 字段不全：缺 sourceResourceTypeCode/targetResourceTypeCode（资源类型）、
 *   资源 name（只有 code）、sourceOperationCodes/requiredOperationCodes（只有 bits）、
 *   ownerServiceCode/maintainSource/updatedAt。前端通过 getResourceTree + getOperationList
 *   建映射补全（bits->操作码、id->资源名称/类型）。
 * - ResourceDependencyUpdateReq 缺 sourceResourceCode/targetResourceCode/sourceCodeType/targetCodeType，
 *   无法切换资源对；前端按全量替换契约提交完整字段（对齐 conflict-rule 范式），mock 支持，
 *   真后端 🔧 补全 update DTO。
 * - list/graph/check 三端点无权限校验（public 方法）；DEPENDENCY 权限种子缺失
 *   （schema 无 INSERT 预置操作位），联调全账号 403。
 * - list 无分页、仅按内部主键 resourceEntityId 过滤；graph 返回扁平列表非图结构（前端建图）。
 * - maintainSource 枚举不一致（DTO 注释 SERVICE/MANUAL vs schema ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC），
 *   前端按 schema 4 种值。
 * - batch-sync FULL diff 匹配只比 sourceCode+targetCode，未比 sourceOperationCodes，同资源对不同触发操作可能误删。
 * - batch-sync 端点本任务 P0 标 TODO（Q5=B），前端不调用，mock 不实现。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 响应类型 ==========

/** 资源依赖响应（对齐后端 ResourceDependencyResp）。
 *  🔧 后端 Resp 缺 sourceResourceTypeCode/targetResourceTypeCode/资源 name/operationCodes/
 *  ownerServiceCode/maintainSource/updatedAt，前端通过引用数据映射补全（见 hook.ts）。 */
export type ResourceDependencyResp = {
  id: number;
  tenantId: number;
  /** 源资源实体 ID（被授权资源，对应 resource_dependency.resource_entity_id） */
  resourceEntityId: number;
  /** 源资源编码 */
  sourceResourceCode: string;
  /** 被依赖资源实体 ID（自动补全目标，对应 resource_dependency.depends_on_resource_entity_id） */
  dependsOnResourceEntityId: number;
  /** 被依赖资源编码 */
  depResourceCode: string;
  /** 源操作位（触发条件，null=任意操作触发） */
  sourceOperationBits: number | null;
  /** 要求操作位（目标资源需补全的操作） */
  requiredOperationBits: number;
  /** 是否自动授权 */
  autoGrant: boolean;
  description: string | null;
  createdAt: string;
};

/** 依赖循环检测结果（对齐后端 DependencyCycleCheckResp） */
export type DependencyCycleCheckResp = {
  hasCycle: boolean;
  sourceResourceTypeCode: string;
  sourceResourceCode: string;
  targetResourceTypeCode: string;
  targetResourceCode: string;
};

// ========== 请求类型 ==========

/** 资源依赖创建请求（对齐 ResourceDependencyCreateReq，业务键） */
export type ResourceDependencyCreateReq = {
  sourceResourceTypeCode: string;
  sourceResourceCode: string;
  sourceCodeType?: string | null;
  sourceOperationCodes?: string[] | null;
  targetResourceTypeCode: string;
  targetResourceCode: string;
  targetCodeType?: string | null;
  requiredOperationCodes: string[];
  autoGrant?: boolean | null;
  description?: string | null;
};

/** 资源依赖更新请求（全量替换契约，对齐 conflict-rule UpdateReq 范式）。
 *  🔧 后端 DTO 缺 sourceResourceCode/targetResourceCode/sourceCodeType/targetCodeType，
 *  无法切换资源对；前端按全量替换提交完整字段，mock 支持，真后端 🔧 补全（T-PERM-031）。 */
export type ResourceDependencyUpdateReq = {
  id: number;
  sourceResourceTypeCode: string;
  sourceResourceCode: string;
  sourceCodeType?: string | null;
  sourceOperationCodes?: string[] | null;
  targetResourceTypeCode: string;
  targetResourceCode: string;
  targetCodeType?: string | null;
  requiredOperationCodes: string[];
  autoGrant?: boolean | null;
  description?: string | null;
};

/** 依赖循环检查请求（对齐 ResourceDependencyCheckReq，业务键） */
export type ResourceDependencyCheckReq = {
  sourceResourceTypeCode: string;
  sourceResourceCode: string;
  sourceCodeType?: string | null;
  targetResourceTypeCode: string;
  targetResourceCode: string;
  targetCodeType?: string | null;
};

/** 依赖列表查询请求（对齐 DependencyListReq）。
 *  🔧 仅按内部主键 resourceEntityId 过滤，前端本地过滤分页。 */
export type DependencyListReq = {
  resourceEntityId?: number | null;
};

/** 依赖图查询请求（复用 DependencyListReq，resourceEntityId=null 返回全量图） */
export type DependencyGraphReq = DependencyListReq;

// ========== API 函数 ==========

/** 查询资源依赖列表（POST /api/perm/resource-dependency/list）。
 *  🔧 无分页、无 VIEW 校验、仅按 resourceEntityId 过滤（T-PERM-031）。 */
export const getDependencyList = async (
  params: DependencyListReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<PermResult<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/perm/resource-dependency/list",
    { data: params }
  );
  return unwrap(res);
};

/** 创建资源依赖（POST /api/perm/resource-dependency/create，业务键） */
export const createDependency = async (
  data: ResourceDependencyCreateReq
): Promise<ResourceDependencyResp> => {
  const res = await http.request<PermResult<ResourceDependencyResp>>(
    "post",
    "/api/perm/resource-dependency/create",
    { data }
  );
  return unwrap(res);
};

/** 更新资源依赖（POST /api/perm/resource-dependency/update，全量替换）。
 *  🔧 后端 update DTO 缺资源对字段，真后端会忽略 sourceResourceCode/targetResourceCode（T-PERM-031）。 */
export const updateDependency = async (
  data: ResourceDependencyUpdateReq
): Promise<ResourceDependencyResp> => {
  const res = await http.request<PermResult<ResourceDependencyResp>>(
    "post",
    "/api/perm/resource-dependency/update",
    { data }
  );
  return unwrap(res);
};

/** 删除资源依赖，支持批量（POST /api/perm/resource-dependency/remove，IdsReq{ids}） */
export const removeDependencies = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<PermResult<void>>(
      "post",
      "/api/perm/resource-dependency/remove",
      { data: { ids } }
    )
  );
};

/** 查询依赖图（POST /api/perm/resource-dependency/graph）。
 *  返回扁平依赖列表，前端自行构建 nodes/edges。
 *  🔧 无权限校验、返回非图结构（T-PERM-031）。 */
export const getDependencyGraph = async (
  params: DependencyGraphReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<PermResult<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/perm/resource-dependency/graph",
    { data: params }
  );
  return unwrap(res);
};

/** 检测循环依赖（POST /api/perm/resource-dependency/check，业务键）。
 *  🔧 无权限校验（T-PERM-031）。 */
export const checkDependencyCycle = async (
  data: ResourceDependencyCheckReq
): Promise<DependencyCycleCheckResp> => {
  const res = await http.request<PermResult<DependencyCycleCheckResp>>(
    "post",
    "/api/perm/resource-dependency/check",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
