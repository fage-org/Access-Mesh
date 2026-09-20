/** 资源依赖只读接口；写入由服务 manifest 发布通道承担。 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 响应类型 ==========

/** 资源依赖响应（对齐后端 ResourceDependencyResp，T-PERM-031 已补全静态字段）。
 *  操作位为 63 位 bigint 位值列，后端按字符串线格式下发（避免 JS Number 丢精度）；
 *  操作码数组不反解——bitsToOpNames(bits, typeCode) 经操作列表 bit 映射拆解（见 hook.ts）。 */
export type ResourceDependencyResp = {
  id: number;
  tenantId: number;
  /** 源资源实体 ID（被授权资源，对应 resource_dependency.resource_entity_id） */
  resourceEntityId: number;
  /** 源资源编码 */
  sourceResourceCode: string;
  /** 源资源类型编码（T-PERM-031 补） */
  sourceResourceTypeCode: string | null;
  /** 源资源名称（T-PERM-031 补） */
  sourceResourceName: string | null;
  /** 被依赖资源实体 ID（自动补全目标，对应 resource_dependency.depends_on_resource_entity_id） */
  dependsOnResourceEntityId: number;
  /** 被依赖资源编码 */
  depResourceCode: string;
  /** 目标资源类型编码（T-PERM-031 补） */
  targetResourceTypeCode: string | null;
  /** 目标资源名称（T-PERM-031 补） */
  targetResourceName: string | null;
  /** 源操作位（触发条件，字符串线格式；null=任意操作触发） */
  sourceOperationBits: string | null;
  /** 要求操作位（目标资源需补全的操作，字符串线格式） */
  requiredOperationBits: string;
  /** 依赖描述 */
  description: string | null;
  /** 维护方服务编码，旧数据可能为空 */
  ownerServiceCode: string | null;
  /** 维护来源；新声明编译结果为 MANIFEST */
  maintainSource: string | null;
  createdAt: string;
  /** 最后更新时间（T-PERM-031 补） */
  updatedAt: string;
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
 *  维持全量不分页定案（029/030 同款），resourceEntityId 为内部主键可选过滤。 */
export type DependencyListReq = {
  resourceEntityId?: number | null;
};

/** 依赖图查询请求（复用 DependencyListReq，resourceEntityId=null 返回全量图） */
export type DependencyGraphReq = DependencyListReq;

// ========== API 函数 ==========

/** 查询资源依赖列表（POST /api/access/resource-dependency/list）。
 *  门禁 DEPENDENCY:VIEW 类型级；全量不分页（关键词过滤前端本地完成）。 */
export const getDependencyList = async (
  params: DependencyListReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<R<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/access/resource-dependency/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询依赖图（POST /api/access/resource-dependency/graph）。
 *  门禁 DEPENDENCY:VIEW；返回扁平依赖列表，前端自行构建 nodes/edges（设计定案维持）。 */
export const getDependencyGraph = async (
  params: DependencyGraphReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<R<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/access/resource-dependency/graph",
    { data: params }
  );
  return unwrap(res);
};

/** 检测循环依赖（POST /api/access/resource-dependency/check，业务键）。
 *  门禁 DEPENDENCY:VIEW；资源不存在 20004。 */
export const checkDependencyCycle = async (
  data: ResourceDependencyCheckReq
): Promise<DependencyCycleCheckResp> => {
  const res = await http.request<R<DependencyCycleCheckResp>>(
    "post",
    "/api/access/resource-dependency/check",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
