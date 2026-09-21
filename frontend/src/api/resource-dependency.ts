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

// ========== 来源解释（T-PERM-073，契约 §12.3.1） ==========

/** 条件身份（对齐后端 GrantPlanPreviewResp.ConditionRef；explain 不出现 PREVIEW_INLINE）。 */
export type ExplainConditionRef = {
  kind: "NONE" | "EXISTING" | "PREVIEW_INLINE";
  conditionId: number | null;
  conditionCode: string | null;
  requestItemRef: string | null;
};

/** 资源业务键 */
export type ExplainResourceKey = {
  resourceTypeCode: string | null;
  resourceCode: string | null;
  codeType: string | null;
};

/** 完整逻辑事实键 */
export type ExplainFactKey = {
  resource: ExplainResourceKey;
  operationCode: string | null;
  conditionRef: ExplainConditionRef;
};

/** 显式种子引用（现有授权返回 permissionId） */
export type ExplainSeedRef = {
  permissionId: number | null;
  requestItemRef: string | null;
};

/** 解释节点（nodeKey 为响应内展示 ID，不跨请求合并；节点身份始终来自 fact） */
export type ExplainNode = {
  nodeKey: string;
  fact: ExplainFactKey;
  explicitSeed: boolean;
  seedRefs: ExplainSeedRef[];
  desired: boolean;
  actualPermissionIds: number[];
};

/** 编译声明引用 */
export type ExplainDeclarationRef = {
  declarationId: number;
  declarationKey: string;
  sourceService: string;
};

/** 直接推导边（实际触发操作 + 声明引用） */
export type ExplainEdge = {
  fromNodeKey: string;
  toNodeKey: string;
  triggerOperationCode: string | null;
  declarationRefs: ExplainDeclarationRef[];
};

/** 来源解释响应：desired/actual 漂移显式标识，不把「应生成」当「已生效」。 */
export type AutoGrantExplainResp = {
  viewedAt: string;
  nodes: ExplainNode[];
  edges: ExplainEdge[];
  totalNodeCount: number;
  totalEdgeCount: number;
  truncated: boolean;
  driftDetected: boolean;
};

/** 解释目标事实（conditionId 传 null=无条件变体；缺省 target=全集视图） */
export type ExplainTargetReq = {
  resourceTypeCode: string;
  resourceCode: string;
  codeType?: string | null;
  operationCode: string;
  conditionId?: number | null;
};

/** 来源解释请求（角色业务键 + 可选目标 + 输出限额） */
export type AutoGrantExplainReq = {
  domainCode?: string | null;
  roleTypeCode: string;
  roleExternalId: string;
  target?: ExplainTargetReq | null;
  maxDepth?: number;
  maxNodes?: number;
  maxEdges?: number;
};

/** 角色自动授权来源解释（POST /api/access/resource-dependency/explain）。
 *  门禁 DEPENDENCY:VIEW 类型级；只读一致视图；截断/读取失败不得解释为「无来源」。 */
export const explainAutoGrant = async (
  data: AutoGrantExplainReq
): Promise<AutoGrantExplainResp> => {
  const res = await http.request<R<AutoGrantExplainResp>>(
    "post",
    "/api/access/resource-dependency/explain",
    { data }
  );
  return unwrap(res);
};

// ========== 声明诊断（T-PERM-073，契约 §12.3 declaration-status） ==========

/** 服务依赖发布状态（tenant+service 单行） */
export type ManifestSyncItem = {
  sourceService: string;
  publicationGeneration: number;
  revision: string | null;
  syncStatus: string | null;
  isDirty: boolean | null;
  lastSyncedAt: string | null;
};

/** 声明行（含 REJECTED 原因；payload 业务键回显，损坏时降级 null） */
export type DeclarationItem = {
  id: number;
  sourceService: string;
  declarationKey: string;
  compileStatus: string | null;
  rejectReason: string | null;
  description: string | null;
  sourceResourceTypeCode: string | null;
  sourceResourceCode: string | null;
  sourceCodeType: string | null;
  sourceOperationCode: string | null;
  targetResourceTypeCode: string | null;
  targetResourceCode: string | null;
  targetCodeType: string | null;
  requiredOperationCodes: string[];
  updatedAt: string | null;
};

/** 声明诊断响应 */
export type DependencyDeclarationStatusResp = {
  manifestSyncs: ManifestSyncItem[];
  declarations: DeclarationItem[];
};

/** 依赖声明诊断（POST /api/access/resource-dependency/declaration-status）。
 *  门禁 DEPENDENCY:VIEW；只读——变更由所属服务 manifest 发布承担。 */
export const getDeclarationStatus = async (
  sourceService?: string | null
): Promise<DependencyDeclarationStatusResp> => {
  const res = await http.request<R<DependencyDeclarationStatusResp>>(
    "post",
    "/api/access/resource-dependency/declaration-status",
    { data: { sourceService: sourceService ?? null } }
  );
  return unwrap(res);
};

export { type ItemsResp };
