/**
 * 域配置 Mock 共享注册表。
 *
 * 用途：domain-config 与 biz-domain 两个 mock 文件共享同一份内存域配置数据——
 * biz-domain 的 remove 需要「域下存在有效域配置时拒删」引用检查（对齐后端
 * DomainConfigMapper.selectValidByDomainIds 删除保护，T-PERM-026），
 * 配置数据必须与 domain-config mock 运行时（save/remove）实时一致。
 *
 * 铁律：本文件是 mock 内部基础设施，**零 src 依赖**（不 import @/api/*），
 * 与 _bizDomainRegistry.ts / system-config.ts 等零 src 依赖范式一致。
 *
 * 后端等价：DomainConfigMapper（selectValidByTypeString / selectByTenantAndDomainId /
 * selectValidByDomainIds / softDeleteBatch）。
 */

/** 域配置注册表项（对齐 src/api/domain-config.ts 的 DomainConfigResp / 后端 entity）。
 *  extra 为 JSON 字符串（schema 是 JSONB，entity 映射为 String；JSONB↔String 映射已随
 *  T-PERM-026 BizDomainConfigPgIT 确认语义等价）。 */
export type DomainConfigRecord = {
  id: number;
  tenantId?: number;
  bizDomainId: number;
  configType: string;
  extra: string;
  updatedAt?: string;
  /** mock 内部：软删标记（对齐 schema delete_flag） */
  deleted?: boolean;
  /** mock 内部：所属域编码（便于按域过滤，后端 Resp 不返回——后端按 bizDomainId 关联） */
  domainCode?: string;
};

/** 默认租户（mock 统一 tenantId=1） */
const DEFAULT_TENANT_ID = 1;

/**
 * domain_config 内存数据（唯一权威源）。
 *
 * schema 注释与后端白名单（access-service.sql / DomainConfigReq @Pattern）：
 * - config_type 取值：SUB_PERM / CLASSIFY（仅此两类已实现，写入校验拒绝其余值）
 * - 每个域独立，无继承
 */
const registry: DomainConfigRecord[] = [
  {
    id: 1,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 2,
    domainCode: "HR",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["USER","ORG","POSITION"]}',
    updatedAt: "2026-01-02 10:00:00"
  },
  {
    id: 2,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 2,
    domainCode: "HR",
    configType: "SUB_PERM",
    extra: '{"allowed":[{"parent_type":"USER","child_types":["POSITION"]}]}',
    updatedAt: "2026-01-02 11:00:00"
  },
  {
    id: 3,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 3,
    domainCode: "ORDER",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["ORDER","ORDER_ITEM"]}',
    updatedAt: "2026-01-03 10:00:00"
  },
  {
    id: 4,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 3,
    domainCode: "ORDER",
    configType: "SUB_PERM",
    extra: '{"allowed":[{"parent_type":"ORDER","child_types":["ORDER_ITEM"]}]}',
    updatedAt: "2026-01-03 11:00:00"
  },
  {
    id: 5,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 4,
    domainCode: "CRM",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["CUSTOMER","CONTACT"]}',
    updatedAt: "2026-01-04 10:00:00"
  },
  {
    id: 6,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 4,
    domainCode: "CRM",
    configType: "SUB_PERM",
    extra: '{"allowed":[{"parent_type":"CUSTOMER","child_types":["CONTACT"]}]}',
    updatedAt: "2026-01-04 11:00:00"
  },
  {
    id: 7,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId: 5,
    domainCode: "ASSET",
    configType: "CLASSIFY",
    extra: '{"typeCodes":["ASSET","LICENSE"]}',
    updatedAt: "2026-01-05 10:00:00"
  }
];

/** 下一个自增 id（已用最大 id + 1）。新建配置时分配。 */
let _nextId = registry.reduce((max, c) => Math.max(max, c.id), 0) + 1;

/** 查询全部域配置（含已软删，调用方按需过滤）。返回引用——调用方不应直接修改。 */
export function listAllConfigs(): readonly DomainConfigRecord[] {
  return registry;
}

/** 按域编码过滤有效配置（domain-config mock list 用）。 */
export function listValidConfigsByDomainCode(
  domainCode: string
): DomainConfigRecord[] {
  return registry.filter(c => !c.deleted && c.domainCode === domainCode);
}

/** 按业务键 bizDomainId+configType 查有效配置（detail/save upsert 查存在用）。 */
export function findValidConfig(
  bizDomainId: number,
  configType: string
): DomainConfigRecord | undefined {
  return registry.find(
    c =>
      c.bizDomainId === bizDomainId &&
      c.configType === configType &&
      !c.deleted
  );
}

/** 引用检查：任一目标域下存在有效配置（biz-domain mock remove 删除保护用，
 *  后端 selectValidByDomainIds 等价）。返回命中的配置列表（非空即存在引用）。 */
export function findValidConfigsByDomainIds(
  domainIds: number[]
): DomainConfigRecord[] {
  return registry.filter(c => !c.deleted && domainIds.includes(c.bizDomainId));
}

/** upsert：存在则更新 extra，不存在则新建（domain-config mock save 用）。
 *  返回落库后的记录（调用方 clone 后再暴露给响应）。 */
export function upsertConfig(
  bizDomainId: number,
  domainCode: string,
  configType: string,
  extra: string
): DomainConfigRecord {
  const found = findValidConfig(bizDomainId, configType);
  if (found) {
    found.extra = extra;
    found.updatedAt = "2026-07-01 00:00:00";
    return found;
  }
  const record: DomainConfigRecord = {
    id: _nextId++,
    tenantId: DEFAULT_TENANT_ID,
    bizDomainId,
    domainCode,
    configType,
    extra,
    updatedAt: "2026-07-01 00:00:00"
  };
  registry.push(record);
  return record;
}

/** 批量软删（domain-config mock remove 用）。 */
export function softDeleteConfigs(ids: number[]): void {
  registry.forEach(c => {
    if (ids.includes(c.id)) c.deleted = true;
  });
}
