/**
 * 业务域 Mock 共享注册表（Phase 1）。
 *
 * 用途：biz-domain 与 domain-config 两个 mock 文件共享同一份内存业务域数据，
 * 使运行时新建/删除的业务域能被 domain-config 的 save（解析 domainCode → bizDomainId）
 * 实时感知，避免 domain-config 单独维护静态 DOMAIN_CODE_TO_ID 反查表导致
 * 新建域保存配置时返回「未知域编码」。
 *
 * 铁律：本文件是 mock 内部基础设施，**零 src 依赖**（不 import @/api/*），
 * 与 system-config.ts / biz-domain.ts / domain-config.ts 零 src 依赖范式一致。
 * 类型本地声明，与 src/api/biz-domain.ts 的 BizDomainResp 保持对齐。
 *
 * 后端等价：typeResolutionService.resolveDomainId(tenantId, domainCode)。
 */

/** 业务域注册表项（对齐 src/api/biz-domain.ts 的 BizDomainResp / 后端 entity）。
 *  global/deleted 为注册表内部字段（后端 Resp 不返回 global，🔧 登记 T-PERM-026）。 */
export type BizDomainRecord = {
  id: number;
  tenantId?: number;
  code: string;
  name: string;
  description?: string | null;
  createdAt?: string;
  /** 内部：是否全局域（每租户仅一个，不可删）——对齐 schema global */
  global?: boolean;
  /** 内部：软删标记（对齐 schema delete_flag） */
  deleted?: boolean;
};

/** 默认租户（mock 统一 tenantId=1） */
const DEFAULT_TENANT_ID = 1;

/**
 * 业务域内存注册表（唯一权威源）。
 *
 * schema（permission-center.sql:55-72）：
 * - uk_biz_domain(tenant_id, code) WHERE delete_flag=0 —— 租户内 code 唯一
 * - uk_biz_domain_global(tenant_id) WHERE global=TRUE AND delete_flag=0 —— 每租户仅一个全局域
 *
 * 全局域 GLOBAL：隐式包含未被其他域认领的资源类型（DomainClassifyService 全局域语义）。
 * id 分配与原 mock/biz-domain.ts 一致：GLOBAL=1/HR=2/ORDER=3/CRM=4/ASSET=5，新建域从 6 起递增。
 */
const registry: BizDomainRecord[] = [
  {
    id: 1,
    tenantId: DEFAULT_TENANT_ID,
    code: "GLOBAL",
    name: "全局域",
    description:
      "全局域：隐式包含未被其他域认领的资源类型，每租户仅一个，不可删除",
    createdAt: "2026-01-01 00:00:00",
    global: true
  },
  {
    id: 2,
    tenantId: DEFAULT_TENANT_ID,
    code: "HR",
    name: "人事域",
    description: "组织与用户相关资源的业务域",
    createdAt: "2026-01-02 00:00:00",
    global: false
  },
  {
    id: 3,
    tenantId: DEFAULT_TENANT_ID,
    code: "ORDER",
    name: "订单域",
    description: "订单业务相关资源的业务域",
    createdAt: "2026-01-03 00:00:00",
    global: false
  },
  {
    id: 4,
    tenantId: DEFAULT_TENANT_ID,
    code: "CRM",
    name: "客户域",
    description: "客户关系管理相关资源的业务域",
    createdAt: "2026-01-04 00:00:00",
    global: false
  },
  {
    id: 5,
    tenantId: DEFAULT_TENANT_ID,
    code: "ASSET",
    name: "资产域",
    description: "资产设备管理相关资源的业务域",
    createdAt: "2026-01-05 00:00:00",
    global: false
  }
];

/** 下一个自增 id（已用最大 id + 1）。新建域时分配。 */
let _nextId = registry.reduce((max, d) => Math.max(max, d.id), 0) + 1;

/** 分配下一个 id（供 biz-domain mock create 使用，确保 id 与注册表一致）。 */
export function nextDomainId(): number {
  return _nextId++;
}

/** 查询全部业务域（含已软删，调用方按需过滤）。返回引用——调用方不应直接修改。 */
export function listAllDomains(): readonly BizDomainRecord[] {
  return registry;
}

/** 按 id 查（含已软删，调用方按需过滤 deleted）。 */
export function findDomainById(id: number): BizDomainRecord | undefined {
  return registry.find(d => d.id === id);
}

/**
 * 按 code 解析 bizDomainId（后端 typeResolutionService.resolveDomainId 等价）。
 * 仅匹配未软删的域。找不到返回 undefined。
 */
export function resolveDomainId(code: string): number | undefined {
  const found = registry.find(d => d.code === code && !d.deleted);
  return found?.id;
}

/** 注册新业务域（biz-domain mock create 调用）。返回新建记录（调用方 clone 后再暴露给响应）。 */
export function registerDomain(
  code: string,
  name: string,
  description: string | null,
  createdAt: string
): BizDomainRecord {
  const record: BizDomainRecord = {
    id: nextDomainId(),
    tenantId: DEFAULT_TENANT_ID,
    code,
    name,
    description,
    createdAt,
    global: false
  };
  registry.push(record);
  return record;
}

/** 更新业务域（biz-domain mock update 调用）。返回更新后的记录或 undefined。 */
export function updateDomain(
  id: number,
  name?: string | null,
  description?: string | null
): BizDomainRecord | undefined {
  const found = registry.find(d => d.id === id && !d.deleted);
  if (!found) return undefined;
  if (name != null) found.name = name;
  if (description != null) found.description = description;
  return found;
}

/** 软删业务域（biz-domain mock remove 调用）。返回被删记录数组（含全局域拦截信息）。 */
export function softDeleteDomains(ids: number[]): {
  deleted: BizDomainRecord[];
  blockedGlobals: BizDomainRecord[];
} {
  const targets = registry.filter(d => ids.includes(d.id) && !d.deleted);
  const blockedGlobals = targets.filter(d => d.global);
  const deletable = targets.filter(d => !d.global);
  deletable.forEach(d => {
    d.deleted = true;
  });
  return { deleted: deletable, blockedGlobals };
}
