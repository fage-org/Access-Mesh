/**
 * 系统配置 API
 * 经 @/utils/http 调用 access-service 端点（`/api/perm/system-config/*`）；
 * Phase 1 由 mock/system-config.ts（vite-plugin-fake-server）提供假数据。
 * 响应统一为后端 PermResult<T> 信封（code=200 为成功），本层按 code 解包并抛错，对组件暴露裸数据。
 * 信封类型与 unwrap 工具函数共享自 `@/api/_envelope`；列表包络复用 role-manage 定义。
 *
 * 契约依据：docs/design/permission-center/api-contract.md §5.8（系统配置仅 3 行表格条目，无独立字段契约章节，
 *   字段由后端 DTO 落地——🔧 登记 T-PERM-024：Phase 2 补 api-contract 字段契约）
 * 后端实现：access-service SystemConfigController + SystemConfigAppServiceImpl
 *
 * 后端端点仅 3 个（list/detail/save），无 create/update/remove——save 为 upsert 幂等语义
 * （按 configKey 查存在则 update 不存在则 insert），无删除接口。
 */
import { http } from "@/utils/http";
import { type PermResult, unwrap } from "./_envelope";
import type { ItemsResp } from "./role-manage";

// ========== 系统配置定义 ==========

/** 系统配置响应（对齐后端 SystemConfigResp）。
 *  configValue 为 JSON 字符串（后端 schema 是 JSONB，entity 映射为 String；前端按字符串提交/展示，
 *  提交前由 JSON.parse 校验合法性）。🔧 JSONB↔String 映射登记 T-PERM-024。 */
export type SystemConfigResp = {
  id: number;
  tenantId?: number;
  /** 配置键（租户内唯一，如 ROLE_NAME_UNIQUE_MODE / UNREGISTERED_API_POLICY） */
  configKey: string;
  /** 配置值（JSON 字符串，如 {"mode":"DOMAIN_UNIQUE"}） */
  configValue: string;
  /** 描述（可空） */
  description: string | null;
  updatedAt?: string;
  createdAt?: string;
};

/** 系统配置列表查询参数。
 *  🔧 API 核对项（登记 T-PERM-024）：后端 list 接 EmptyReq 无参，返回租户全量 ItemsResp
 *  （无分页、无 keyword 过滤）。前端按 keyword 本地过滤 + 切片分页。
 *  Phase 2 后端补 keyword/pageNum/pageSize 参数并返回 PaginatedResp 后，前端可切回服务端分页。
 *  本类型预留以备 Phase 2 扩展，当前调用方传 {}。 */
export type SystemConfigListQuery = Record<string, never>;

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

/** 查询系统配置列表（POST /api/perm/system-config/list）。
 *  后端 EmptyReq 无参，返回 ItemsResp<SystemConfigResp>（租户全量，无分页/无过滤）。
 *  前端 hook 拿全量 items 后本地做 keyword 过滤 + 切片分页。
 *  🔧 后端补 keyword/pageNum/pageSize 参数 + 返回 PaginatedResp 登记于 T-PERM-024。 */
export const getSystemConfigList = async (
  params: SystemConfigListQuery
): Promise<ItemsResp<SystemConfigResp>> => {
  const res = await http.request<PermResult<ItemsResp<SystemConfigResp>>>(
    "post",
    "/api/perm/system-config/list",
    { data: params }
  );
  return unwrap(res);
};

/** 查询系统配置详情（POST /api/perm/system-config/detail，SystemConfigGetReq{configKey}） */
export const getSystemConfigDetail = async (
  configKey: string
): Promise<SystemConfigResp> => {
  const res = await http.request<PermResult<SystemConfigResp>>(
    "post",
    "/api/perm/system-config/detail",
    { data: { configKey } satisfies SystemConfigGetReq }
  );
  return unwrap(res);
};

/** 保存系统配置（POST /api/perm/system-config/save，upsert 幂等）。
 *  按 configKey 查存在则 update（configValue/description/updatedAt），不存在则 insert。
 *  新建/编辑统一走本接口——前端无需区分 create/update 调用。 */
export const saveSystemConfig = async (
  data: SystemConfigSaveReq
): Promise<SystemConfigResp> => {
  const res = await http.request<PermResult<SystemConfigResp>>(
    "post",
    "/api/perm/system-config/save",
    { data }
  );
  return unwrap(res);
};

export { type ItemsResp };
