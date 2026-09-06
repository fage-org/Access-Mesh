/**
 * 资源依赖 API
 * 经 @/utils/http 调用 access-service 端点
 * （`/api/perm/resource-dependency/*`）；
 * Phase 1 由 mock/resource-dependency.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 R<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6 / §6.9
 * 后端实现：access-service ResourceDependencyController + DependencyAppServiceImpl
 *
 * T-PERM-031 收口（2026-08-30，原 🔧 清单 8 项处置）：
 * - Resp 补静态字段：sourceResourceTypeCode/targetResourceTypeCode、source/targetResourceName、
 *   ownerServiceCode/maintainSource/updatedAt；操作位改字符串线格式（63 位 bigint 位值列，
 *   T-PERM-028 binaryBit 同款）。operationCodes 数组不反解（前端经操作列表建 bit 映射拆解）。
 * - update 补全资源对业务键字段（PUT 全量覆盖契约，Q3=B）：资源对可改，
 *   sourceOperationCodes=null/空=任意触发，description=null 清空。
 * - 门禁五档类型级：读 list/graph/check = DEPENDENCY:VIEW、写三档 + SYNC；
 *   bootstrap 固定图已补五条（空库死锁防护）。
 * - list 维持全量不分页（量小非流水表，029/030 同款定案），resourceEntityId 内部过滤保留，
 *   关键词过滤由前端本地完成；graph 维持扁平列表（前端建图）。
 * - 业务错误码：等价重复 20054 / 依赖不存在 20019 / 资源不存在 20004 / 操作码不存在 20005（fail-closed，
 *   不再静默丢弃）/ 自依赖 20044 / autoGrant=true 20048。
 * - maintainSource 四值白名单（ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC，batch-sync 请求校验）；
 *   batch-sync FULL diff 按三元组（源+目标+触发位）匹配。
 * - batch-sync 端点 P0 标 TODO（Q5=B），前端不调用，mock 不实现。
 */
import { http } from "@/utils/http";
import { type R, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 响应类型 ==========

/** 资源依赖响应（对齐后端 ResourceDependencyResp，T-PERM-031 已补全静态字段）。
 *  操作位为 63 位 bigint 位值列，后端按字符串线格式下发（避免 JS Number 丢精度）；
 *  操作码数组不反解——bitsToOpCodes(bits, typeCode) 经操作列表 bit 映射拆解（见 hook.ts）。 */
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
  /** 是否自动授权（预留禁用，恒 false） */
  autoGrant: boolean;
  description: string | null;
  /** 维护方服务编码（UI 创建行为 null；T-PERM-031 补） */
  ownerServiceCode: string | null;
  /** 维护来源（ADMIN_UI/SDK_SCAN/MANIFEST/SERVICE_SYNC；T-PERM-031 补） */
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

/** 资源依赖创建请求（对齐 ResourceDependencyCreateReq，业务键；description ≤512） */
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

/** 资源依赖更新请求（对齐 ResourceDependencyUpdateReq，PUT 全量替换契约）。
 *  T-PERM-031 后端已补全资源对业务键字段：资源对可改、全部字段按提交值覆盖；
 *  sourceOperationCodes 空=清空为"任意操作触发"，description null=清空。 */
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
 *  维持全量不分页定案（029/030 同款），resourceEntityId 为内部主键可选过滤。 */
export type DependencyListReq = {
  resourceEntityId?: number | null;
};

/** 依赖图查询请求（复用 DependencyListReq，resourceEntityId=null 返回全量图） */
export type DependencyGraphReq = DependencyListReq;

// ========== API 函数 ==========

/** 查询资源依赖列表（POST /api/perm/resource-dependency/list）。
 *  门禁 DEPENDENCY:VIEW 类型级；全量不分页（关键词过滤前端本地完成）。 */
export const getDependencyList = async (
  params: DependencyListReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<R<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/perm/resource-dependency/list",
    { data: params }
  );
  return unwrap(res);
};

/** 创建资源依赖（POST /api/perm/resource-dependency/create，业务键）。
 *  错误码：20004 资源不存在 / 20005 操作码不存在（fail-closed）/ 20044 自依赖 /
 *  20054 等价重复 / 20048 autoGrant=true。 */
export const createDependency = async (
  data: ResourceDependencyCreateReq
): Promise<ResourceDependencyResp> => {
  const res = await http.request<R<ResourceDependencyResp>>(
    "post",
    "/api/perm/resource-dependency/create",
    { data }
  );
  return unwrap(res);
};

/** 更新资源依赖（POST /api/perm/resource-dependency/update，PUT 全量替换）。
 *  错误码：20019 依赖不存在（先解析后门禁）/ 20004/20005/20044/20054/20048 同 create。 */
export const updateDependency = async (
  data: ResourceDependencyUpdateReq
): Promise<ResourceDependencyResp> => {
  const res = await http.request<R<ResourceDependencyResp>>(
    "post",
    "/api/perm/resource-dependency/update",
    { data }
  );
  return unwrap(res);
};

/** 删除资源依赖，支持批量（POST /api/perm/resource-dependency/remove，IdsReq{ids}）。
 *  类型级 DELETE 全有或全无；幽灵 id 幂等跳过，响应 data=null 无行数。 */
export const removeDependencies = async (ids: number[]): Promise<void> => {
  unwrap(
    await http.request<R<void>>(
      "post",
      "/api/perm/resource-dependency/remove",
      { data: { ids } }
    )
  );
};

/** 查询依赖图（POST /api/perm/resource-dependency/graph）。
 *  门禁 DEPENDENCY:VIEW；返回扁平依赖列表，前端自行构建 nodes/edges（设计定案维持）。 */
export const getDependencyGraph = async (
  params: DependencyGraphReq = {}
): Promise<ItemsResp<ResourceDependencyResp>> => {
  const res = await http.request<R<ItemsResp<ResourceDependencyResp>>>(
    "post",
    "/api/perm/resource-dependency/graph",
    { data: params }
  );
  return unwrap(res);
};

/** 检测循环依赖（POST /api/perm/resource-dependency/check，业务键）。
 *  门禁 DEPENDENCY:VIEW；资源不存在 20004。 */
export const checkDependencyCycle = async (
  data: ResourceDependencyCheckReq
): Promise<DependencyCycleCheckResp> => {
  const res = await http.request<R<DependencyCycleCheckResp>>(
    "post",
    "/api/perm/resource-dependency/check",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
