/**
 * 域配置 API
 * 经 @/utils/http 调用 access-service 端点（`/perm/api/perm/domain-config/*`，Gateway /perm 前缀——T-FE-021 切换）；
 * mock/domain-config.ts 已于 T-FE-021 联调退役删除。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.6（高级能力）
 * 后端实现：access-service DomainConfigController + DomainConfigAppServiceImpl
 *
 * 后端端点 4 个：list / detail / save（upsert 幂等）/ remove（批量软删）。
 * - save 按 domainCode + configType upsert（存在则 update extra，不存在则 insert），新建/编辑统一走 save。
 * - detail 按 domainCode + configType 业务键二元组查（非内部主键，与 biz-domain detail 不同）。
 * - list 按 domainCode 过滤（不传则全量）。
 * - extra 为 JSON 字符串（schema 是 JSONB，entity 映射为 String；前端按字符串编辑 + JSON.parse 校验，
 *   后端 save 亦经 JsonValidationUtils 校验；JSONB↔String 映射已随 T-PERM-026 PgIT 确认语义等价）。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 域配置定义 ==========

/** 域配置响应（对齐后端 DomainConfigResp）。
 *  extra 为 JSON 字符串（schema 是 JSONB，前端按字符串处理）。 */
export type DomainConfigResp = {
  id: number;
  tenantId?: number;
  /** 所属业务域 ID（后端 save 时由 domainCode 解析） */
  bizDomainId: number;
  /** 配置类型编码（仅 SUB_PERM / CLASSIFY 已实现，写入白名单校验拒绝其余历史类型） */
  configType: string;
  /** 配置值（JSON 字符串，schema 是 JSONB） */
  extra: string;
  updatedAt?: string;
};

/** 域配置保存请求（POST /save，DomainConfigReq，upsert 幂等）。
 *  domainCode + configType 为业务键二元组，extra 为 JSON 字符串（@NotBlank）。
 *  不含 id——save 按 domainCode+configType upsert（存在则覆盖 extra，不存在则新建），新建/编辑统一走本接口。 */
export type DomainConfigSaveReq = {
  domainCode: string;
  configType: string;
  extra: string;
};

/** 域配置列表请求（POST /list，DomainConfigListReq{domainCode?}）。
 *  domainCode 可选过滤；不传则返回租户全量。 */
export type DomainConfigListReq = {
  domainCode?: string | null;
};

/** 域配置详情请求（POST /detail，DomainConfigGetReq{domainCode, configType}，业务键二元组）。 */
export type DomainConfigGetReq = {
  domainCode: string;
  configType: string;
};

// ========== API 函数 ==========

/** 查询域配置列表（POST /api/perm/domain-config/list，DomainConfigListReq{domainCode?}）。
 *  按 domainCode 过滤（不传则全量），返回 ItemsResp<DomainConfigResp>（无分页）。 */
export const getDomainConfigList = async (
  domainCode?: string | null
): Promise<ItemsResp<DomainConfigResp>> => {
  const res = await http.request<PermResult<ItemsResp<DomainConfigResp>>>(
    "post",
    "/perm/api/perm/domain-config/list",
    { data: { domainCode: domainCode ?? null } satisfies DomainConfigListReq }
  );
  return unwrap(res);
};

/** 查询域配置详情（POST /api/perm/domain-config/detail，DomainConfigGetReq{domainCode, configType}）。 */
export const getDomainConfigDetail = async (
  domainCode: string,
  configType: string
): Promise<DomainConfigResp | null> => {
  const res = await http.request<PermResult<DomainConfigResp | null>>(
    "post",
    "/perm/api/perm/domain-config/detail",
    { data: { domainCode, configType } satisfies DomainConfigGetReq }
  );
  return unwrap(res);
};

/** 保存域配置（POST /api/perm/domain-config/save，upsert 幂等）。
 *  按 domainCode+configType 查存在则 update extra，不存在则 insert。
 *  新建/编辑统一走本接口——前端无需区分 create/update 调用。 */
export const saveDomainConfig = async (
  data: DomainConfigSaveReq
): Promise<DomainConfigResp> => {
  const res = await http.request<PermResult<DomainConfigResp>>(
    "post",
    "/perm/api/perm/domain-config/save",
    { data }
  );
  return unwrap(res);
};

/** 删除域配置（POST /api/perm/domain-config/remove，IdsReq 批量软删）。 */
export const removeDomainConfig = async (ids: number[]): Promise<void> => {
  const res = await http.request<PermResult<void>>(
    "post",
    "/perm/api/perm/domain-config/remove",
    { data: { ids } }
  );
  unwrap(res);
};

export { type ItemsResp };
