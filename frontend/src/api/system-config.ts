/**
 * 系统配置 API
 * 经 @/utils/http 调用 Gateway 外部路径 `/perm/api/perm/system-config/*`
 *（Gateway StripPrefix=1 后到 access-service `/api/perm/system-config`）。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.8 system-config 契约要点（T-PERM-024 收口）
 * 后端实现：access-service SystemConfigController + SystemConfigAppServiceImpl
 *
 * 后端端点仅 3 个（list/detail/save），无 create/update/remove——save 为 upsert 幂等语义
 * （按 configKey 查存在则 update 不存在则 insert），无删除接口。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp, PaginatedResp } from "./role-manage";

// ========== 系统配置定义 ==========

/** 系统配置响应（对齐后端 SystemConfigResp）。
 *  configValue 为 JSON 字符串（后端 schema 是 JSONB，entity 映射为 String；前端按字符串提交/展示，
 *  提交前由 JSON.parse 校验合法性）。T-PERM-024 实证（SystemConfigJsonbPgIT）：JSONB 读出为
 *  DB 规范化后的 JSON 文本——与提交值语义等价（解析树相等）但非字节回显（空白/键序规范化），
 *  展示值可直接再提交（规范化幂等），无截断/转义问题。 */
export type SystemConfigResp = {
  id: number;
  tenantId?: number;
  /** 配置键（租户内唯一，后端强制 admin./permission./access. 前缀，如 permission.MY_SETTING） */
  configKey: string;
  /** 配置值（JSON 字符串，如 {"mode":"DOMAIN_UNIQUE"}） */
  configValue: string;
  /** 描述（可空） */
  description: string | null;
  updatedAt?: string;
  createdAt?: string;
};

/** 系统配置列表查询参数（T-PERM-024 收口：服务端 keyword 过滤 + 分页）。
 *  keyword 匹配 configKey/description（LIKE，大小写敏感）；pageNum/pageSize 均不传 = 字典全量
 *  （后端上限 200，先例 /role/list）。 */
export type SystemConfigListQuery = {
  keyword?: string | null;
  pageNum?: number;
  pageSize?: number;
};

/** 系统配置详情查询（POST /detail，SystemConfigGetReq{configKey}）。
 *  注意：按 configKey 查，非按 id（对齐后端 SystemConfigGetReq.configKey @NotNull）。 */
export type SystemConfigGetReq = {
  configKey: string;
};

/** 系统配置保存请求（POST /save，SystemConfigReq，upsert 幂等）。
 *  configValue 为 JSON 字符串，后端 JsonValidationUtils.validateJson 校验。
 *  不含 id——save 按 configKey upsert（存在则覆盖，不存在则新建），新建/编辑统一走本接口。 */
export type SystemConfigSaveReq = {
  configKey: string;
  configValue: string;
  description?: string | null;
};

// ========== API 函数 ==========

/** 查询系统配置列表（POST /perm/api/perm/system-config/list）。
 *  T-PERM-024 收口：服务端 keyword 过滤（configKey/description，LIKE）+ 分页，返回 PaginatedResp
 *  （ORDER BY configKey,id）；不传分页参数 = 字典全量（上限 200）。 */
export const getSystemConfigList = async (
  params: SystemConfigListQuery
): Promise<PaginatedResp<SystemConfigResp>> => {
  const res = await http.request<PermResult<PaginatedResp<SystemConfigResp>>>(
    "post",
    "/perm/api/perm/system-config/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询系统配置详情（POST /perm/api/perm/system-config/detail，SystemConfigGetReq{configKey}） */
export const getSystemConfigDetail = async (
  configKey: string
): Promise<SystemConfigResp> => {
  const res = await http.request<PermResult<SystemConfigResp>>(
    "post",
    "/perm/api/perm/system-config/detail",
    { data: { configKey } satisfies SystemConfigGetReq }
  );
  return unwrap(res);
};

/** 保存系统配置（POST /perm/api/perm/system-config/save，upsert 幂等）。
 *  按 configKey 查存在则 update（configValue/description/updatedAt），不存在则 insert。
 *  新建/编辑统一走本接口——前端无需区分 create/update 调用。 */
export const saveSystemConfig = async (
  data: SystemConfigSaveReq
): Promise<SystemConfigResp> => {
  const res = await http.request<PermResult<SystemConfigResp>>(
    "post",
    "/perm/api/perm/system-config/save",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
